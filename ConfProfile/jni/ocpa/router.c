/*
 * router.c
 *
 *      Author: Dmitry Vorobiev
 */

#include <unistd.h>
#include <errno.h>
#include <stdbool.h>
#include "android_log_utils.h"
#include "protoheaders.h"
#include "router.h"
#include "iputils.h"
#include "tun_dev_tun.h"

#include "debug.h"

#define LOG_TAG "router.c"

router_ctx_t* router_init() {
    router_ctx_t* ctx = (router_ctx_t*) malloc(sizeof(router_ctx_t));

    if(ctx != NULL) {
    	ctx->rwlock4 = malloc(sizeof(pthread_rwlock_t));
    	if(ctx->rwlock4 == NULL) {
    		free(ctx);
    		return NULL;
    	}
    	if(pthread_rwlock_init(ctx->rwlock4, NULL) != 0) {
    		free(ctx->rwlock4);
    		ctx->rwlock4 = NULL;
    		free(ctx);
    		return NULL;
    	}

    	ctx->dev_tun_ctx = create_tun_dev_tun_ctx(NULL, 0);
    	if(ctx->dev_tun_ctx == NULL) {
    		free(ctx->rwlock4);
    		ctx->rwlock4 = NULL;
    		free(ctx);
    		return NULL;
    	}

    	ctx->dev_tun_ctx->setRouterContext(ctx->dev_tun_ctx, ctx);

        ctx->ip4_routes = NULL;
        ctx->ip4_routes_count = 0;
        ctx->ip4_default_tun_ctx = NULL;

        ctx->ip_pkt.buff_len = 1500;
        ctx->ip_pkt.buff = malloc(1500);
        if(ctx->ip_pkt.buff == NULL) {
        	ctx->dev_tun_ctx = ctx->dev_tun_ctx->ref_put(ctx->dev_tun_ctx);
        	free(ctx->rwlock4);
        	ctx->rwlock4 = NULL;
        	free(ctx);
        	return NULL;
        }
        ctx->ip_pkt.pkt_len = 0;
		ctx->ip_pkt.ipver = 0;
		ctx->ip_pkt.payload_proto = 0;
		ctx->ip_pkt.payload_offs = 0;
		ctx->ip_pkt.payload_len = 0;

		ctx->epoll_fd = epoll_create(MAX_EPOLL_EVENTS);
		if(ctx->epoll_fd == -1) {
			free(ctx->ip_pkt.buff);
			ctx->ip_pkt.buff = NULL;
			ctx->dev_tun_ctx = ctx->dev_tun_ctx->ref_put(ctx->dev_tun_ctx);
			free(ctx->rwlock4);
			ctx->rwlock4 = NULL;
			free(ctx);
			return NULL;
		}

		ctx->epoll_links_capacity = 4;
		ctx->epoll_links_count = 0;
		ctx->epoll_links = malloc(ctx->epoll_links_capacity * sizeof(epoll_link_t));
		if(ctx->epoll_links == NULL) {
			close(ctx->epoll_fd);
			free(ctx->ip_pkt.buff);
			ctx->ip_pkt.buff = NULL;
			ctx->dev_tun_ctx = ctx->dev_tun_ctx->ref_put(ctx->dev_tun_ctx);
			free(ctx->rwlock4);
			ctx->rwlock4 = NULL;
			free(ctx);
			return NULL;
		}
		memset(ctx->epoll_links, 0, ctx->epoll_links_capacity * sizeof(epoll_link_t));

        ctx->routes_updated = false;
        ctx->paused = false;
        ctx->terminate = false;
    }

    return ctx;
}

void router_deinit(router_ctx_t* ctx) {
    if(ctx != NULL) {
    	pthread_rwlock_wrlock(ctx->rwlock4);

        if(ctx->ip4_routes != NULL) {
            route4_link_t* curr = ctx->ip4_routes;

            while(curr != NULL) {
                route4_link_t* next = curr->next;
                curr->tun_ctx = NULL;
                free(curr);
                curr = next;
            }
        }

        ctx->dev_tun_ctx->ref_put(ctx->dev_tun_ctx);

        ctx->ip4_routes = NULL;
        ctx->ip4_routes_count = 0;
        ctx->ip4_default_tun_ctx = NULL;

        ctx->ip_pkt.buff_len = 0;
        if(ctx->ip_pkt.buff != NULL) {
        	free(ctx->ip_pkt.buff);
        }
        ctx->ip_pkt.buff = NULL;
        ctx->ip_pkt.pkt_len = 0;
        ctx->ip_pkt.ipver = 0;
        ctx->ip_pkt.payload_proto = 0;
        ctx->ip_pkt.payload_offs = 0;
        ctx->ip_pkt.payload_len = 0;

        if(ctx->epoll_fd != -1) {
        	close(ctx->epoll_fd);
        }
        ctx->epoll_fd = -1;

        if(ctx->epoll_links != NULL) {
        	free(ctx->epoll_links);
        }
        ctx->epoll_links = NULL;

        ctx->routes_updated = false;
        ctx->paused = false;
        ctx->terminate = false;

        pthread_rwlock_unlock(ctx->rwlock4);
        pthread_rwlock_destroy(ctx->rwlock4);
        free(ctx->rwlock4);
        ctx->rwlock4 = NULL;

        free(ctx);
    }
}

void route4(router_ctx_t* ctx, uint32_t ip4, uint32_t mask, tun_ctx_t* tun_ctx) {
	if(ctx == NULL || tun_ctx == NULL) {
		return;
	}

	if(mask > 32) {
		mask = 32;
	}

	LOGD(LOG_TAG, "route4(%08x/%d -> %p)", ip4, mask, tun_ctx);

	uint8_t netmask = (uint8_t) mask;

	ip4 = (ip4 >> (32 - netmask)) << (32 - netmask);

	pthread_rwlock_wrlock(ctx->rwlock4);

	route4_link_t* link = (route4_link_t*) malloc(sizeof(route4_link_t));
	link->ip4 = ip4;
	link->mask = netmask;
	//increment link counter for our tun_ctx
	link->tun_ctx = tun_ctx->ref_get(tun_ctx);
	link->next = NULL;

	if(ctx->ip4_routes == NULL) {
		ctx->ip4_routes = link;
	} else {
		route4_link_t* curr = ctx->ip4_routes;
		route4_link_t* prev = NULL;
		route4_link_t* next = NULL;
		while(curr != NULL) {
			next = curr->next;
			if(curr->ip4 < ip4 || (curr->ip4 == ip4 && curr->mask < netmask)) {
				//insert link before this
				link->next = curr;
				if(prev == NULL) {
					ctx->ip4_routes = link;
				} else {
					prev->next = link;
				}

				ctx->ip4_routes_count++;

				//Our job is done
				break;

			} else if(curr->ip4 == ip4 && curr->mask == netmask) {
				//replace this link
				link->next = next;
				if(prev == NULL) {
					ctx->ip4_routes = link;
				} else {
					prev->next = link;
				}

				//decrement link counter for replaced tun_ctx
				curr->tun_ctx = curr->tun_ctx->ref_put(curr->tun_ctx);

				free(curr);

				//Our job is done
				break;
			} else if(next == NULL) {
				//add to the end
				link->next = NULL;
				curr->next = link;

				//Our job is done;
				break;
			}

			prev = curr;
			curr = next;
		}
	}

	ctx->routes_updated = true;
	pthread_rwlock_unlock(ctx->rwlock4);
}

void route6(router_ctx_t* ctx, uint8_t* ip6, uint32_t mask, tun_ctx_t* tun_ctx) {
	if(ctx == NULL || tun_ctx == NULL) {
		return;
	}

	if(mask > 128) {
		mask = 128;
	}

	uint8_t netmask = (uint8_t) mask;
	uint8_t matching_bytes = netmask >> 3;
	uint8_t partial_matching_bytes = 0;
	uint8_t blank_bytes = 16 - matching_bytes;

	// mask ::/14 means the following bitmask:
	// 11111111 11111100 00000000 ...
	// matching byte (0xff of bitmask) does not change corresponding IP address byte after applying logical AND
	// blank byte (0x00 of bitmask) changes corresponding IP address byte into 0x00 after applying logical AND
	// here we have 1 matching byte and 1 partially matching byte which have partial_bitmask bitmask
	uint8_t partial_bitmask_shift = (8 - (netmask - (matching_bytes << 3)));
	uint8_t partial_bitmask = (0xff >> partial_bitmask_shift) << partial_bitmask_shift;
	if(partial_bitmask != 0x00) {
		partial_matching_bytes++;
		blank_bytes--;
	}

	pthread_rwlock_wrlock(ctx->rwlock4);

	route6_link_t* link = (route6_link_t*) malloc(sizeof(route6_link_t));
	memcpy(link->ip6, ip6, matching_bytes);
	memset(link->ip6 + (matching_bytes), *(ip6 + matching_bytes) & partial_bitmask, partial_matching_bytes);
	memset(link->ip6 + (matching_bytes + partial_matching_bytes), 0x00, blank_bytes);
	link->mask = netmask;
	//increment link counter for our tun_ctx
	link->tun_ctx = tun_ctx->ref_get(tun_ctx);
	link->next = NULL;

	if(ctx->ip6_routes == NULL) {
		ctx->ip6_routes = link;
	} else {
		route6_link_t* curr = ctx->ip6_routes;
		route6_link_t* prev = NULL;
		route6_link_t* next = NULL;
		while(curr != NULL) {
			next = curr->next;
			int cmp_addrs = memcmp(curr->ip6, link->ip6, 16);
			if(cmp_addrs < 0 || (cmp_addrs == 0 && curr->mask < netmask)) {
				//insert link before this
				link->next = curr;
				if(prev == NULL) {
					ctx->ip6_routes = link;
				} else {
					prev->next = link;
				}

				ctx->ip6_routes_count++;

				//Our job is done
				break;

			} else if(cmp_addrs == 0 && curr->mask == netmask) {
				//replace this link
				link->next = next;
				if(prev == NULL) {
					ctx->ip6_routes = link;
				} else {
					prev->next = link;
				}

				//decrement link counter for replaced tun_ctx
				curr->tun_ctx = curr->tun_ctx->ref_put(curr->tun_ctx);

				free(curr);

				//Our job is done
				break;
			} else if(next == NULL) {
				//add to the end
				link->next = NULL;
				curr->next = link;

				//Our job is done;
				break;
			}

			prev = curr;
			curr = next;
		}
	}

	ctx->routes_updated = true;
	pthread_rwlock_unlock(ctx->rwlock4);
}

void unroute(router_ctx_t* ctx, tun_ctx_t* tun_ctx) {
	if(ctx == NULL || tun_ctx == NULL) {
		return;
	}

	pthread_rwlock_wrlock(ctx->rwlock4);
	LOGDIF(ROUTER_DEBUG, LOG_TAG, "unroute(%p) started", tun_ctx);

	if(epoll_ctl(ctx->epoll_fd, EPOLL_CTL_DEL, tun_ctx->getLocalFd(tun_ctx), NULL) == -1) {
	    LOGEIF(ROUTER_DEBUG, LOG_TAG, "Error while epoll_ctl(EPOLL_CTL_DEL) %d: %s", errno, strerror(errno));
	    //just notify about error and continue working
	}

	int i;
	for(i = 0; i < ctx->epoll_links_count; i++) {
		LOGDIF(ROUTER_DEBUG, LOG_TAG, "Checking epoll_links[%d]: tun_ctx=%p, fd=%d",
				i,
				ctx->epoll_links[i].tun_ctx,
				ctx->epoll_links[i].fd);
		if(ctx->epoll_links[i].tun_ctx == tun_ctx) {
			LOGDIF(ROUTER_DEBUG, LOG_TAG, "Remove IPv4 routes");
			LOGDIF(ROUTER_DEBUG, LOG_TAG, "tun_ctx=%p", tun_ctx);
			//free reference to tun_ctx
			tun_ctx->ref_put(tun_ctx);
			//mark tunnel context as inexistent
			LOGDIF(ROUTER_DEBUG, LOG_TAG, "ctx=%p", ctx);
			LOGDIF(ROUTER_DEBUG, LOG_TAG, "ctx->epoll_links=%p", ctx->epoll_links);
			LOGDIF(ROUTER_DEBUG, LOG_TAG, "ctx->epoll_links[i].tun_ctx=%p", ctx->epoll_links[i].tun_ctx);
			ctx->epoll_links[i].tun_ctx = NULL;
		}
	}

	LOGDIF(ROUTER_DEBUG, LOG_TAG, "Remove IPv4 routes");

	//remove IPv4 routes
	route4_link_t* curr4 = ctx->ip4_routes;
	route4_link_t* prev4 = NULL;
	route4_link_t* next4 = NULL;
	while(curr4 != NULL) {
		next4 = curr4->next;
		LOGDIF(ROUTER_DEBUG, LOG_TAG, "Checking ip4_routes: curr=%p [prev=%p, next=%p], search=%p",
				curr4,
				prev4,
				next4,
				tun_ctx);
		if(curr4->tun_ctx == tun_ctx) {
			//remove this link
			if(prev4 == NULL) {
				ctx->ip4_routes = next4;
			} else {
				prev4->next = next4;
			}

			curr4->ip4 = 0;
			curr4->next = NULL;
			tun_ctx->ref_put(tun_ctx);
			curr4->tun_ctx = NULL;
			free(curr4);

			curr4 = prev4;

			ctx->ip4_routes_count--;
			ctx->routes_updated = true;
		}

		prev4 = curr4;
		curr4 = next4;
	}

	if(ctx->ip4_default_tun_ctx == tun_ctx) {
		LOGDIF(ROUTER_DEBUG, LOG_TAG, "Remove ip4_default_tun_ctx");
		ctx->ip4_default_tun_ctx = NULL;
		tun_ctx->ref_put(tun_ctx);
		ctx->routes_updated = true;
	}

	//remove IPv6 routes
	route6_link_t* curr6 = ctx->ip6_routes;
	route6_link_t* prev6 = NULL;
	route6_link_t* next6 = NULL;
	while(curr6 != NULL) {
		next6 = curr6->next;
		LOGDIF(ROUTER_DEBUG, LOG_TAG, "Checking ip6_routes: curr=%p [prev=%p, next=%p], search=%p",
				curr6,
				prev6,
				next6,
				tun_ctx);

		if(curr6->tun_ctx == tun_ctx) {
			//remove this link
			if(prev6 == NULL) {
				ctx->ip6_routes = next6;
			} else {
				prev6->next = next6;
			}

			memset(curr6->ip6, 0x00, 16);
			curr6->next = NULL;
			tun_ctx->ref_put(tun_ctx);
			curr6->tun_ctx = NULL;
			free(curr6);

			curr6 = prev6;

			ctx->ip6_routes_count--;
			ctx->routes_updated = true;
		}

		prev6 = curr6;
		curr6 = next6;
	}

	if(ctx->ip6_default_tun_ctx == tun_ctx) {
		LOGDIF(ROUTER_DEBUG, LOG_TAG, "Remove ip6_default_tun_ctx");
		ctx->ip6_default_tun_ctx = NULL;
		tun_ctx->ref_put(tun_ctx);
		ctx->routes_updated = true;
	}

	LOGDIF(ROUTER_DEBUG, LOG_TAG, "unroute(%p) finished", tun_ctx);
	pthread_rwlock_unlock(ctx->rwlock4);
}

void unroute4(router_ctx_t* ctx, uint32_t ip4, uint32_t mask) {
	if(ctx == NULL) {
		return;
	}

	LOGD(LOG_TAG, "unroute4(%08x/%d)", ip4, mask);

	pthread_rwlock_wrlock(ctx->rwlock4);

	route4_link_t* curr = ctx->ip4_routes;
	route4_link_t* prev = NULL;
	route4_link_t* next = NULL;
	while(curr != NULL) {
		next = curr->next;
		if(curr->ip4 == ip4) {
			//remove this link
			if(prev == NULL) {
				ctx->ip4_routes = next;
			} else {
				prev->next = next;
			}

			curr->ip4 = 0;
			curr->next = NULL;
			if(curr->tun_ctx != NULL) {
				curr->tun_ctx->ref_put(curr->tun_ctx);
			}
			curr->tun_ctx = NULL;
			free(curr);

			ctx->ip4_routes_count--;
			ctx->routes_updated = true;

			//Our job is done
			break;
		} else if(curr->ip4 < ip4) {
			//no such route
			//Our job is done;
			break;
		}

		prev = curr;
		curr = next;
	}

	pthread_rwlock_unlock(ctx->rwlock4);
}

void unroute6(router_ctx_t* ctx, uint8_t* ip6, uint32_t mask) {
	if(ctx == NULL) {
		return;
	}

	pthread_rwlock_wrlock(ctx->rwlock4);

	route6_link_t* curr = ctx->ip6_routes;
	route6_link_t* prev = NULL;
	route6_link_t* next = NULL;
	while(curr != NULL) {
		next = curr->next;
		int cmp_addrs = memcmp(curr->ip6, ip6, 16);

		if(cmp_addrs == 0) {
			//remove this link
			if(prev == NULL) {
				ctx->ip6_routes = next;
			} else {
				prev->next = next;
			}

			memset(curr->ip6, 0x00, 16);
			curr->next = NULL;
			if(curr->tun_ctx != NULL) {
				curr->tun_ctx->ref_put(curr->tun_ctx);
			}
			curr->tun_ctx = NULL;
			free(curr);

			ctx->ip6_routes_count--;
			ctx->routes_updated = true;

			//Our job is done
			break;
		} else if(cmp_addrs < 0) {
			//no such route
			//Our job is done;
			break;
		}

		prev = curr;
		curr = next;
	}

	pthread_rwlock_unlock(ctx->rwlock4);
}

void default4(router_ctx_t* ctx, tun_ctx_t* tun_ctx) {
	if(ctx != NULL) {
		pthread_rwlock_wrlock(ctx->rwlock4);
		if(ctx->ip4_default_tun_ctx != NULL) {
			ctx->ip4_default_tun_ctx = ctx->ip4_default_tun_ctx->ref_put(ctx->ip4_default_tun_ctx);
		}
		ctx->ip4_default_tun_ctx = tun_ctx->ref_get(tun_ctx);
		ctx->routes_updated = true;
		pthread_rwlock_unlock(ctx->rwlock4);
	}
}

void default6(router_ctx_t* ctx, tun_ctx_t* tun_ctx) {
	if(ctx != NULL) {
		pthread_rwlock_wrlock(ctx->rwlock4);
		if(ctx->ip6_default_tun_ctx != NULL) {
			ctx->ip6_default_tun_ctx = ctx->ip6_default_tun_ctx->ref_put(ctx->ip6_default_tun_ctx);
		}
		ctx->ip6_default_tun_ctx = tun_ctx->ref_get(tun_ctx);
		ctx->routes_updated = true;
		pthread_rwlock_unlock(ctx->rwlock4);
	}
}

ssize_t ipsend(router_ctx_t* ctx, ocpa_ip_packet_t* ip_packet) {
	ssize_t result = 0;
	if(ip_packet->ipver == 0) {
		ip_detect_ipver(ip_packet);
	}

	if(ip_packet->ipver == 4) {
		result = send4(ctx, ip_packet);
	} else if(ip_packet->ipver == 6) {
		//result = send6(ctx, ip_packet);
	} else {

	}
	return result;
}

ssize_t send4(router_ctx_t* ctx, ocpa_ip_packet_t* ip_packet) {
	tun_ctx_t* tun_ctx = NULL;

	if(ctx == NULL) {
		errno = EBADF;
		return -1;
	}

	pthread_rwlock_rdlock(ctx->rwlock4);
	uint8_t* buff = ip_packet->buff;
	int len = ip_packet->pkt_len;

	uint32_t ip4 = ntohl(((uint32_t*)(buff + 16))[0]);

	if(ctx->ip4_routes != NULL) {
		route4_link_t* curr = ctx->ip4_routes;
		while(curr != NULL) {
			if(ip4_addr_match(curr->ip4, curr->mask, ip4)) {
				tun_ctx = curr->tun_ctx;
				//Our job is done
				break;
			} else if(curr->ip4 < ip4) {
				//no such route
				//Our job is done;
				break;
			}

			curr = curr->next;
		}
	}

	if(tun_ctx == NULL) {
		LOGD(LOG_TAG, "Using default route4");
		tun_ctx = ctx->ip4_default_tun_ctx;
	}

	ssize_t result = 0;
	if(tun_ctx != NULL) {
		tun_ctx->masqueradeSrc(tun_ctx, buff, len);
		result = tun_ctx->send(tun_ctx, buff, len);
	} else {
		LOGE(LOG_TAG, "Destination tunnel is not ready, dropping a packet");
	}
	pthread_rwlock_unlock(ctx->rwlock4);

	return result;
}

ssize_t send6(router_ctx_t* ctx, ocpa_ip_packet_t* ip_packet) {
	tun_ctx_t* tun_ctx = NULL;

	if(ctx == NULL) {
		errno = EBADF;
		return -1;
	}

	uint8_t* buff = ip_packet->buff;
	int len = ip_packet->pkt_len;

	pthread_rwlock_rdlock(ctx->rwlock4);

	uint8_t* ip6 = buff + 24;

	if(ctx->ip6_routes != NULL) {
		route6_link_t* curr = ctx->ip6_routes;
		while(curr != NULL) {
			LOGDIF(ROUTER_DEBUG, LOG_TAG, "send6: memcmp(%p->ip6, %p, 16)", curr, ip6);
			int cmp_addrs = memcmp(curr->ip6, ip6, 16);
			if(ip6_addr_match(curr->ip6, curr->mask, ip6)) {
				tun_ctx = curr->tun_ctx;
				//Our job is done
				break;
			} else if(cmp_addrs < 0) {
				//no such route
				//Our job is done;
				break;
			}

			curr = curr->next;
		}
	}

	if(tun_ctx == NULL) {
		LOGD(LOG_TAG, "Using default route6");
		tun_ctx = ctx->ip6_default_tun_ctx;
	}

	ssize_t result = 0;
	if(tun_ctx != NULL) {
		tun_ctx->masqueradeSrc(tun_ctx, buff, len);
		result = tun_ctx->send(tun_ctx, buff, len);
	} else {
		LOGE(LOG_TAG, "Destination tunnel is not ready, dropping a packet");
	}
	pthread_rwlock_unlock(ctx->rwlock4);

	return result;
}

void rebuild_epoll_struct(router_ctx_t* ctx) {
	if(ctx == NULL) {
		return;
	}

	LOGDIF(ROUTER_DEBUG, LOG_TAG, "Start rebuilding epoll struct");
	pthread_rwlock_wrlock(ctx->rwlock4);
	unsigned int poll_count = 0;
	bool already_added = false;

	//allocating memory for storing links to: android tun context, default route, all ip4 routes
	tun_ctx_t** ctxs = malloc(sizeof(tun_ctx_t*) * (ctx->ip4_routes_count + 2));
	if(ctxs == NULL) {
		goto error;
	}

	ctxs[poll_count] = ctx->dev_tun_ctx->ref_get(ctx->dev_tun_ctx);
	poll_count++;

	if(ctx->ip4_default_tun_ctx != NULL &&
			ctx->ip4_default_tun_ctx->getLocalFd(ctx->ip4_default_tun_ctx) != UNDEFINED_FD) {
		ctxs[poll_count] = ctx->ip4_default_tun_ctx->ref_get(ctx->ip4_default_tun_ctx);
		poll_count++;
	}

	int i;

	route4_link_t* curr = ctx->ip4_routes;
	while(curr != NULL) {
		if(curr->tun_ctx != NULL) {
			if(curr->tun_ctx->getLocalFd(curr->tun_ctx) == UNDEFINED_FD) {
				continue;
			}

			already_added = false;

			for(i = 0; i < poll_count; i++) {
				if(ctxs[i] == curr->tun_ctx) {
					already_added = true;
					break;
				}
			}

			if(!already_added) {
				ctxs[poll_count] = curr->tun_ctx->ref_get(curr->tun_ctx);
				poll_count++;
			}
		}
		curr = curr->next;
	}

	if(poll_count > ctx->epoll_links_capacity ||
			poll_count < (ctx->epoll_links_capacity >> 1)) {
		for(i = 0; i < ctx->epoll_links_capacity; i++) {
			if(ctx->epoll_links[i].tun_ctx != NULL) {
				ctx->epoll_links[i].tun_ctx->ref_put(ctx->epoll_links[i].tun_ctx);
			}
		}
		free(ctx->epoll_links);
		unsigned int new_poll_count = (poll_count + (poll_count >> 1));
		LOGDIF(ROUTER_DEBUG, LOG_TAG, "Resizing epoll struct from %d to %d item(s)",
				ctx->epoll_links_capacity,
				new_poll_count);
		ctx->epoll_links = malloc(sizeof(epoll_link_t) * new_poll_count);
		if(ctx->epoll_links == NULL) {
			goto error;
		}
		ctx->epoll_links_capacity = new_poll_count;
		ctx->epoll_links_count = poll_count;
	}

	memset(ctx->epoll_links, 0, ctx->epoll_links_capacity * sizeof(epoll_link_t));

	LOGDIF(ROUTER_DEBUG, LOG_TAG, "Rebuild epoll struct for %d item(s)", poll_count);

	struct epoll_event* ev;
	for(i = 0; i < poll_count; i++) {
		ctx->epoll_links[i].fd = ctxs[i]->getLocalFd(ctxs[i]);
		ctx->epoll_links[i].tun_ctx = ctxs[i];

		ev = &(ctx->epoll_links[i].evt);
		//ev->events = EPOLLIN | EPOLLOUT;
		ev->events = EPOLLIN;
		ev->data.fd = ctx->epoll_links[i].fd;
		LOGDIF(ROUTER_DEBUG, LOG_TAG, "epoll_links[%i]: fd=%d, event=%p", i, ctx->epoll_links[i].fd, ev);

		if(epoll_ctl(ctx->epoll_fd, EPOLL_CTL_ADD, ev->data.fd, ev) == -1) {
			LOGEIF(ROUTER_DEBUG, LOG_TAG, "Error while epoll_ctl(EPOLL_CTL_ADD) %d: %s", errno, strerror(errno));
			//continue
		}
	}

	goto exit;
error:
	//report error
	LOGDIF(ROUTER_DEBUG, LOG_TAG, "Error occurred while rebuilding poll struct");
exit:

	ctx->routes_updated = false;
	pthread_rwlock_unlock(ctx->rwlock4);
	if(ctxs != NULL) {
		free(ctxs);
	}
	LOGDIF(ROUTER_DEBUG, LOG_TAG, "Rebuilding poll struct finished");
}
/*
void rebuild_poll_struct(router_ctx_t* ctx, poll_helper_struct_t* poll_struct) {
	if(ctx == NULL || poll_struct == NULL) {
		return;
	}

	LOGD(LOG_TAG, "Start rebuilding poll struct");
	pthread_rwlock_wrlock(ctx->rwlock4);
	int poll_count = 0;
	bool already_added = false;

	//allocating memory for storing links to: android tun context, default route, all ip4 routes
	tun_ctx_t** ctxs = malloc(sizeof(tun_ctx_t*) * (ctx->ip4_routes_count + 2));
	if(ctxs == NULL) {
		goto error;
	}

	ctxs[poll_count] = &(ctx->dev_tun_ctx);
	poll_count++;

	if(ctx->ip4_default_tun_ctx != NULL && ctx->ip4_default_tun_ctx->local_fd != UNDEFINED_FD) {
		ctxs[poll_count] = ctx->ip4_default_tun_ctx;
		poll_count++;
	}

	int i;

	route4_link_t* curr = ctx->ip4_routes;
	while(curr != NULL) {
		if(curr->tun_ctx != NULL) {
			if(curr->tun_ctx->local_fd == UNDEFINED_FD) {
				continue;
			}

			already_added = false;

			for(i = 0; i < poll_count; i++) {
				if(ctxs[i] == curr->tun_ctx) {
					already_added = true;
					break;
				}
			}

			if(!already_added) {
				ctxs[poll_count] = curr->tun_ctx;
				poll_count++;
			}
		}
		curr = curr->next;
	}

	pthread_rwlock_wrlock(poll_struct->rwlock);
	if(poll_struct->poll_fds != NULL) {
		free(poll_struct->poll_fds);
	}
	poll_struct->poll_fds = NULL;

	if(poll_struct->poll_ctxs != NULL) {
		free(poll_struct->poll_ctxs);
	}
	poll_struct->poll_ctxs = NULL;
	poll_struct->poll_fds_count = 0;

	LOGD(LOG_TAG, "Rebuild poll struct for %d item(s)", poll_count);

	poll_struct->poll_fds = malloc(sizeof(struct pollfd) * poll_count);
	if(poll_struct->poll_fds == NULL) {
		goto error;
	}

	poll_struct->poll_ctxs = malloc(sizeof(intptr_t) * poll_count);
	if(poll_struct->poll_ctxs == NULL) {
		goto error;
	}

	for(i = 0; i < poll_count; i++) {
		poll_struct->poll_fds[i].fd = ctxs[i]->getLocalFd(ctxs[i]);
		poll_struct->poll_fds[i].events = POLLIN | POLLOUT | POLLHUP;
		poll_struct->poll_fds[i].revents = 0;

		poll_struct->poll_ctxs[i] = ctxs[i];
	}

	poll_struct->poll_fds_count = poll_count;

	goto exit;
error:
	//report error
	LOGD(LOG_TAG, "Error occurred while rebuilding poll struct");
exit:
	pthread_rwlock_unlock(poll_struct->rwlock);
	ctx->routes_updated = false;
	pthread_rwlock_unlock(ctx->rwlock4);
	if(ctxs != NULL) {
		free(ctxs);
	}
	LOGD(LOG_TAG, "Rebuilding poll struct finished");
}
*/

bool router_is_paused(router_ctx_t* ctx) {
	if(ctx == NULL) {
		return false;
	}

	bool result;
	pthread_rwlock_rdlock(ctx->rwlock4);
	result = ctx->paused;
	pthread_rwlock_unlock(ctx->rwlock4);
	return result;
}

bool router_pause(router_ctx_t* ctx, bool paused) {
	if(ctx == NULL) {
		return false;
	}

	bool result;
	pthread_rwlock_wrlock(ctx->rwlock4);
	result = ctx->paused;
	ctx->paused = paused;
	pthread_rwlock_unlock(ctx->rwlock4);
	return result;
}
