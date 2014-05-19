#include <errno.h>
#include <netinet/ip.h>
#include <netinet/ip6.h>
#include "router.h"

typedef struct iphdr ip4_header;
typedef struct ip6_hdr ip6_header;

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

    	ctx->dev_fd = -1;
        ctx->ip4_routes = NULL;
        ctx->ip4_routes_count = 0;
        ctx->ip4_default_tun_ctx = (intptr_t) NULL;

        ctx->ip4_pkt_buff = malloc(1500);
        if(ctx->ip4_pkt_buff == NULL) {
        	free(ctx);
        	return NULL;
        }
        ctx->ip4_pkt_buff_size = 1500;

        ctx->ip4_default_tun_send_func = NULL;
        ctx->ip4_default_tun_recv_func = NULL;

        ctx->poll_fds = NULL;
        ctx->poll_fds_count = 0;
        ctx->poll_fds_nfds = 0;
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
                curr->tun_ctx = (intptr_t) NULL;
                curr->tun_send_func = NULL;
                curr->tun_recv_func = NULL;
                free(curr);
                curr = next;
            }
        }

        if(ctx->dev_fd >= 0) {
        	close(ctx->dev_fd);
        }

        ctx->dev_fd = -1;
        ctx->ip4_routes = NULL;
        ctx->ip4_routes_count = 0;
        ctx->ip4_default_tun_ctx = (intptr_t) NULL;

        if(ctx->ip4_pkt_buff != NULL) {
        	free(ctx->ip4_pkt_buff);
        }
        ctx->ip4_pkt_buff = NULL;
        ctx->ip4_pkt_buff_size = 0;

        ctx->ip4_default_tun_send_func = NULL;
        ctx->ip4_default_tun_recv_func = NULL;

        if(ctx->poll_fds != NULL) {
        	free(ctx->poll_fds);
        }

        ctx->poll_fds = NULL;
        ctx->poll_fds_count = 0;
        ctx->poll_fds_nfds = 0;

        pthread_rwlock_unlock(ctx->rwlock4);
        pthread_rwlock_destroy(ctx->rwlock4);
        free(ctx->rwlock4);
        ctx->rwlock4 = NULL;

        free(ctx);
    }
}

void route4(router_ctx_t* ctx, uint32_t ip4, intptr_t tun_ctx, tun_send_func_ptr send_func, tun_recv_func_ptr recv_func) {
	if(ctx == NULL) {
		return;
	}

	pthread_rwlock_wrlock(ctx->rwlock4);

	route4_link_t* link = (route4_link_t*) malloc(sizeof(route4_link_t));
	link->ip4 = ip4;
	link->tun_ctx = tun_ctx;
	link->tun_send_func = send_func;
	link->tun_recv_func = recv_func;
	link->next = NULL;

	if(ctx->ip4_routes == NULL) {
		ctx->ip4_routes = link;
	} else {
		route4_link_t* curr = ctx->ip4_routes;
		route4_link_t* prev = NULL;
		route4_link_t* next = NULL;
		while(curr != NULL) {
			next = curr->next;
			if(curr->ip4 < ip4) {
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

			} else if(curr->ip4 == ip4) {
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

	pthread_rwlock_unlock(ctx->rwlock4);
}

void route6(router_ctx_t* ctx, uint8_t* ip6, intptr_t tun_ctx, tun_send_func_ptr send_func, tun_recv_func_ptr recv_func) {
	//TODO: implement this
	pthread_rwlock_wrlock(ctx->rwlock4);
	pthread_rwlock_unlock(ctx->rwlock4);
}

void unroute4(router_ctx_t* ctx, uint32_t ip4) {
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

			free(curr);

			ctx->ip4_routes_count--;

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

void unroute6(router_ctx_t* ctx, uint8_t* ip6) {
	//TODO: implement this

	pthread_rwlock_wrlock(ctx->rwlock4);
	pthread_rwlock_unlock(ctx->rwlock4);
}

void default4(router_ctx_t* ctx, intptr_t tun_ctx, tun_send_func_ptr send_func, tun_recv_func_ptr recv_func) {
	if(ctx != NULL) {
		pthread_rwlock_wrlock(ctx->rwlock4);
		ctx->ip4_default_tun_ctx = tun_ctx;
		ctx->ip4_default_tun_send_func = send_func;
		ctx->ip4_default_tun_recv_func = recv_func;
		pthread_rwlock_unlock(ctx->rwlock4);
	}
}

void default6(router_ctx_t* ctx, intptr_t tun_ctx, tun_send_func_ptr send_func, tun_recv_func_ptr recv_func) {
	//TODO: implement this
	pthread_rwlock_wrlock(ctx->rwlock4);
	pthread_rwlock_unlock(ctx->rwlock4);
}

void ipsend(router_ctx_t* ctx, uint8_t* buff, int len) {
	if((buff[0] & 0xf0) == 0x40) {
		send4(ctx, buff, len);
	} else if((buff[0] & 0xf0) == 0x60) {
		send6(ctx, buff, len);
	}
}

void send4(router_ctx_t* ctx, uint8_t* buff, int len) {
	tun_send_func_ptr send_func = NULL;
	intptr_t tun_ctx = (intptr_t) NULL;

	if(ctx == NULL) {
		return;
	}

	pthread_rwlock_rdlock(ctx->rwlock4);

	uint32_t ip4 = ((uint32_t*)(buff + 16))[0];

	if(ctx->ip4_routes != NULL) {
		route4_link_t* curr = ctx->ip4_routes;
		while(curr != NULL) {
			if((curr->ip4 & ip4) == curr->ip4) { //mask & ip == mask
				send_func = curr->tun_send_func;
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

	if(send_func == NULL) {
		send_func = ctx->ip4_default_tun_send_func;
		tun_ctx = ctx->ip4_default_tun_ctx;
	}

	if(send_func != NULL) {
		send_func(tun_ctx, buff, len);
	}
	pthread_rwlock_unlock(ctx->rwlock4);
}

void send6(router_ctx_t* ctx, uint8_t* buff, int len) {
	//TODO: implement this
	pthread_rwlock_rdlock(ctx->rwlock4);
	pthread_rwlock_unlock(ctx->rwlock4);
}

int read_ip_packet(int fd, uint8_t* buff, int len) {
	int size = 0;
	int res = read(fd, buff, 1); //first, read the first byte to check IP version
	if(res < 0) {
		return res;
	}

	size += res;

	if((buff[0] & 0xf0) == 0x40) {
		res = read(fd, buff + 1, 19); //read the remaining part of the minimal IPv4 header
		if(res < 0) {
			return res;
		}

		size += res;

		ip4_header* ip4hdr = (ip4_header*) buff;
		short pkt_size = htons(ip4hdr->tot_len);

		if(pkt_size > len) {
			return EIO; //TODO: error code for insufficient MTU
		}

		pkt_size -= 20;
		if(pkt_size == 0) {
			return size;
		} else if(pkt_size < 0) {
			return EIO;
		}

		res = read(fd, buff +20 , pkt_size);
		if(res < 0) {
			return res;
		}

		size += res;
	} else if((buff[0] & 0xf0) == 0x60) {
		res = read(fd, buff + 1, 39); //read the remaining part of the minimal IPv6 header
		if(res < 0) {
			return res;
		}

		size += res;

		ip6_header* ip6hdr = (ip6_header*) buff;
		short payload_size = htons(ip6hdr->ip6_ctlun.ip6_un1.ip6_un1_plen);

		if(payload_size + 40 > len) {
			return EIO; //TODO: error code for insufficient MTU
		} else if(payload_size == 0) {
			return size;
		}

		res = read(fd, buff + 40, payload_size);
		if(res < 0) {
			return res;
		}

		size += res;
	} else {
		return EIO;
	}

	return size;
}

void rebuild_poll_struct(router_ctx_t* ctx) {
	if(ctx == NULL) {
		return;
	}

	pthread_rwlock_wrlock(ctx->rwlock4);
	int poll_count;

	if(ctx->poll_fds != NULL) {
		free(ctx->poll_fds);
	}
	pthread_rwlock_unlock(ctx->rwlock4);
}
