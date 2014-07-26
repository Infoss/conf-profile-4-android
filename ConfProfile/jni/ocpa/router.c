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

    	if(!common_tun_set(&ctx->dev_tun_ctx, NULL)) {
    		free(ctx->rwlock4);
    		ctx->rwlock4 = NULL;
    		free(ctx);
    		return NULL;
    	}

    	pthread_rwlock_wrlock(ctx->rwlock4);
    	ctx->dev_tun_ctx.send_func = dev_tun_send;
    	ctx->dev_tun_ctx.recv_func = dev_tun_recv;
    	ctx->dev_tun_ctx.router_ctx = ctx;

        ctx->ip4_routes = NULL;
        ctx->ip4_routes_count = 0;
        ctx->ip4_default_tun_ctx = NULL;

        ctx->ip_pkt.buff_len = 1500;
        ctx->ip_pkt.buff = malloc(1500);
        if(ctx->ip_pkt.buff == NULL) {
        	common_tun_free(&ctx->dev_tun_ctx);
        	pthread_rwlock_unlock(ctx->rwlock4);
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

        ctx->routes_updated = false;
        ctx->paused = false;
        ctx->terminate = false;
        pthread_rwlock_unlock(ctx->rwlock4);
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

        if(ctx->dev_tun_ctx.local_fd == ctx->dev_tun_ctx.remote_fd) {
        	ctx->dev_tun_ctx.remote_fd = -1;
        }

        if(ctx->dev_tun_ctx.local_fd >= 0) {
        	close(ctx->dev_tun_ctx.local_fd);
        	ctx->dev_tun_ctx.local_fd = -1;
        }

        if(ctx->dev_tun_ctx.remote_fd >= 0) {
			close(ctx->dev_tun_ctx.remote_fd);
			ctx->dev_tun_ctx.remote_fd = -1;
		}

        common_tun_free(&ctx->dev_tun_ctx);

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

void route4(router_ctx_t* ctx, uint32_t ip4, uint32_t mask, common_tun_ctx_t* tun_ctx) {
	if(ctx == NULL) {
		return;
	}

	if(mask > 32) {
		mask = 32;
	}

	uint8_t netmask = (uint8_t) mask;

	ip4 = (ip4 >> (32 - netmask)) << (32 - netmask);

	pthread_rwlock_wrlock(ctx->rwlock4);

	route4_link_t* link = (route4_link_t*) malloc(sizeof(route4_link_t));
	link->ip4 = ip4;
	link->mask = netmask;
	link->tun_ctx = tun_ctx;
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

void route6(router_ctx_t* ctx, uint8_t* ip6, uint32_t mask, common_tun_ctx_t* tun_ctx) {
	if(ctx == NULL) {
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
	link->tun_ctx = tun_ctx;
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

void unroute4(router_ctx_t* ctx, uint32_t ip4, uint32_t mask) {
	if(ctx == NULL) {
		return;
	}

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

void default4(router_ctx_t* ctx, common_tun_ctx_t* tun_ctx) {
	if(ctx != NULL) {
		pthread_rwlock_wrlock(ctx->rwlock4);
		ctx->ip4_default_tun_ctx = tun_ctx;
		ctx->routes_updated = true;
		pthread_rwlock_unlock(ctx->rwlock4);
	}
}

void default6(router_ctx_t* ctx, common_tun_ctx_t* tun_ctx) {
	if(ctx != NULL) {
		pthread_rwlock_wrlock(ctx->rwlock4);
		ctx->ip6_default_tun_ctx = tun_ctx;
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
		result = send6(ctx, ip_packet);
	} else {

	}
	return result;
}

ssize_t send4(router_ctx_t* ctx, ocpa_ip_packet_t* ip_packet) {
	common_tun_ctx_t* tun_ctx = NULL;

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

	if(tun_ctx == NULL || tun_ctx->send_func == NULL) {
		LOGD(LOG_TAG, "Using default route4");
		tun_ctx = ctx->ip4_default_tun_ctx;
	}

	ssize_t result = 0;
	if(tun_ctx != NULL && tun_ctx->send_func != NULL) {
		if(tun_ctx->use_masquerade4) {
			ip4_header* hdr = (ip4_header*) buff;
			hdr->saddr = htonl(tun_ctx->masquerade4);
			ip4_calc_ip_checksum(buff, len);

			switch(hdr->protocol) {
			case IPPROTO_TCP: {
				ip4_calc_tcp_checksum(buff, len);
				break;
			}
			case IPPROTO_UDP: {
				ip4_calc_udp_checksum(buff, len);
				break;
			}
			default: {
				LOGE(LOG_TAG, "Can't calculate checksum for protocol %d", hdr->protocol);
				break;
			}
			}
		}
		result = tun_ctx->send_func((intptr_t) tun_ctx, buff, len);
	} else {
		LOGE(LOG_TAG, "Destination tunnel is not ready, dropping a packet");
	}
	pthread_rwlock_unlock(ctx->rwlock4);

	return result;
}

ssize_t send6(router_ctx_t* ctx, ocpa_ip_packet_t* ip_packet) {
	common_tun_ctx_t* tun_ctx = NULL;

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

	if(tun_ctx == NULL || tun_ctx->send_func == NULL) {
		LOGD(LOG_TAG, "Using default route6");
		tun_ctx = ctx->ip6_default_tun_ctx;
	}

	ssize_t result = 0;
	if(tun_ctx != NULL && tun_ctx->send_func != NULL) {
		if(tun_ctx->use_masquerade6) {
			ip6_header* hdr = (ip6_header*) buff;
			memcpy(hdr->ip6_src.s6_addr, tun_ctx->masquerade6, 16);

			ip6_find_payload(ip_packet);

			switch(ctx->ip_pkt.payload_proto) {
			case IPPROTO_TCP: {
				uint16_t common_sum = ip6_calc_common_pseudoheader_sum(buff, len);
				ip6_calc_tcp_checksum(common_sum, buff + ip_packet->payload_len, ip_packet->payload_len);
				break;
			}
			case IPPROTO_UDP: {
				uint16_t common_sum = ip6_calc_common_pseudoheader_sum(buff, len);
				ip6_calc_udp_checksum(common_sum, buff + ip_packet->payload_len, ip_packet->payload_len);
				break;
			}
			default: {
				LOGE(LOG_TAG, "Can't calculate checksum for protocol %d", ip_packet->payload_proto);
				break;
			}
			}
		}
		result = tun_ctx->send_func((intptr_t) tun_ctx, buff, len);
	} else {
		LOGE(LOG_TAG, "Destination tunnel is not ready, dropping a packet");
	}
	pthread_rwlock_unlock(ctx->rwlock4);

	return result;
}

ssize_t read_ip_packet(int fd, uint8_t* buff, int len) {
	ssize_t size = 0;

	ssize_t res = read(fd, buff, len);
	if(res < 0) {
		return res;
	}

	size += res;

	return size;
}

void rebuild_poll_struct(router_ctx_t* ctx, poll_helper_struct_t* poll_struct) {
	if(ctx == NULL || poll_struct == NULL) {
		return;
	}

	LOGD(LOG_TAG, "Start rebuilding poll struct");
	pthread_rwlock_wrlock(ctx->rwlock4);
	int poll_count = 0;
	bool already_added = false;

	//allocating memory for storing links to: android tun context, default route, all ip4 routes
	common_tun_ctx_t** ctxs = malloc(sizeof(common_tun_ctx_t*) * (ctx->ip4_routes_count + 2));
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
		poll_struct->poll_fds[i].fd = ((common_tun_ctx_t*) ctxs[i])->local_fd;
		poll_struct->poll_fds[i].events = POLLIN | POLLHUP;
		poll_struct->poll_fds[i].revents = 0;

		poll_struct->poll_ctxs[i] = ctxs[i];
	}

	poll_struct->poll_fds_count = poll_count;

	goto exit;
error:
	//report error
	LOGD(LOG_TAG, "Error occurred while rebuilding poll struct");
exit:
	ctx->routes_updated = false;
	pthread_rwlock_unlock(ctx->rwlock4);
	if(ctxs != NULL) {
		free(ctxs);
	}
	LOGD(LOG_TAG, "Rebuilding poll struct finished");
}

ssize_t dev_tun_send(intptr_t tun_ctx, uint8_t* buff, int len) {
	if(tun_ctx == (intptr_t) NULL) {
		errno = EBADF;
		return -1;
	}

	common_tun_ctx_t* ctx = (common_tun_ctx_t*) tun_ctx;

	//start capture
	pcap_output_write(ctx->pcap_output, buff, 0, len);
	//end capture

	return write(ctx->local_fd, buff, len);
}

ssize_t dev_tun_recv(intptr_t tun_ctx, uint8_t* buff, int len) {
	if(tun_ctx == (intptr_t) NULL) {
		errno = EBADF;
		return -1;
	}

	common_tun_ctx_t* ctx = (common_tun_ctx_t*) tun_ctx;
	ocpa_ip_packet_t* ip_packet = &ctx->router_ctx->ip_pkt;

	int res = read_ip_packet(ctx->local_fd, ip_packet->buff, ip_packet->buff_len);
	if(res < 0) {
		return res;
	}

	ip_packet->pkt_len = res;

	//start capture
	pcap_output_write(ctx->pcap_output, ip_packet->buff, 0, ip_packet->pkt_len);
	//end capture

	res = ipsend(ctx->router_ctx, ip_packet);
	return res;
}

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
