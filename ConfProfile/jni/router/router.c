
#include <errno.h>
#include <linux/tcp.h>
#include <netinet/ip.h>
#include <netinet/ip6.h>
#include <stdbool.h>
#include "android_log_utils.h"
#include "router.h"

#define LOG_TAG "router.c"

typedef struct iphdr ip4_header;
typedef struct ip6_hdr ip6_header;
typedef struct tcphdr tcp_header;

uint16_t ip4_calc_ip_checksum(uint8_t* buff, int len);
uint16_t ip4_calc_tcp_checksum(uint8_t* buff, int len);
uint16_t ip4_calc_pseudoheader_checksum(uint8_t* buff, int len);
inline uint32_t ip4_update_sum(uint32_t previous, uint16_t data);


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

    	ctx->dev_tun_ctx.local_fd = -1;
    	ctx->dev_tun_ctx.remote_fd = -1;
    	ctx->dev_tun_ctx.masquerade4 = 0;
    	memset(ctx->dev_tun_ctx.masquerade6, 0, sizeof(ctx->dev_tun_ctx.masquerade6));
    	ctx->dev_tun_ctx.use_masquerade4 = false;
    	ctx->dev_tun_ctx.use_masquerade6 = false;
    	ctx->dev_tun_ctx.send_func = dev_tun_send;
    	ctx->dev_tun_ctx.recv_func = dev_tun_recv;
    	ctx->dev_tun_ctx.router_ctx = ctx;

        ctx->ip4_routes = NULL;
        ctx->ip4_routes_count = 0;
        ctx->ip4_default_tun_ctx = NULL;

        ctx->ip4_pkt_buff = malloc(1500);
        if(ctx->ip4_pkt_buff == NULL) {
        	free(ctx);
        	return NULL;
        }
        ctx->ip4_pkt_buff_size = 1500;

        ctx->routes_updated = false;
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

        ctx->dev_tun_ctx.local_fd = -1;
		ctx->dev_tun_ctx.remote_fd = -1;
		ctx->dev_tun_ctx.send_func = NULL;
		ctx->dev_tun_ctx.recv_func = NULL;
		ctx->dev_tun_ctx.router_ctx = NULL;

        ctx->ip4_routes = NULL;
        ctx->ip4_routes_count = 0;
        ctx->ip4_default_tun_ctx = NULL;

        if(ctx->ip4_pkt_buff != NULL) {
        	free(ctx->ip4_pkt_buff);
        }
        ctx->ip4_pkt_buff = NULL;
        ctx->ip4_pkt_buff_size = 0;

        ctx->routes_updated = false;
        ctx->terminate = false;

        pthread_rwlock_unlock(ctx->rwlock4);
        pthread_rwlock_destroy(ctx->rwlock4);
        free(ctx->rwlock4);
        ctx->rwlock4 = NULL;

        free(ctx);
    }
}

void route4(router_ctx_t* ctx, uint32_t ip4, common_tun_ctx_t* tun_ctx) {
	if(ctx == NULL) {
		return;
	}

	pthread_rwlock_wrlock(ctx->rwlock4);

	route4_link_t* link = (route4_link_t*) malloc(sizeof(route4_link_t));
	link->ip4 = ip4;
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

	ctx->routes_updated = true;
	pthread_rwlock_unlock(ctx->rwlock4);
}

void route6(router_ctx_t* ctx, uint8_t* ip6, common_tun_ctx_t* tun_ctx) {
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

			curr->ip4 = 0;
			curr->next = NULL;
			curr->tun_ctx = NULL;
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

	ctx->routes_updated = true;
	pthread_rwlock_unlock(ctx->rwlock4);
}

void unroute6(router_ctx_t* ctx, uint8_t* ip6) {
	//TODO: implement this

	pthread_rwlock_wrlock(ctx->rwlock4);
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
	//TODO: implement this
	pthread_rwlock_wrlock(ctx->rwlock4);
	pthread_rwlock_unlock(ctx->rwlock4);
}

ssize_t ipsend(router_ctx_t* ctx, uint8_t* buff, int len) {
	ssize_t result;
	if((buff[0] & 0xf0) == 0x40) {
		result = send4(ctx, buff, len);
	} else if((buff[0] & 0xf0) == 0x60) {
		result = send6(ctx, buff, len);
	}
	return result;
}

ssize_t send4(router_ctx_t* ctx, uint8_t* buff, int len) {
	tun_send_func_ptr send_func = NULL;
	common_tun_ctx_t* tun_ctx = NULL;

	if(ctx == NULL) {
		errno = EBADF;
		return -1;
	}

	pthread_rwlock_rdlock(ctx->rwlock4);

	uint32_t ip4 = ntohl(((uint32_t*)(buff + 16))[0]);

	if(ctx->ip4_routes != NULL) {
		route4_link_t* curr = ctx->ip4_routes;
		while(curr != NULL) {
			if((curr->ip4 & ip4) == curr->ip4) { //mask & ip == mask
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
			default: {
				LOGE(LOG_TAG, "Can't calculate checksum for protocol %d", hdr->protocol);
				break;
			}
			}
			LOGE(LOG_TAG, "Masquerading:");
			log_dump_packet(LOG_TAG, buff, len);
		}
		result = tun_ctx->send_func((intptr_t) tun_ctx, buff, len);
	} else {
		LOGE(LOG_TAG, "Destination tunnel is not ready, dropping a packet");
		log_dump_packet(LOG_TAG, buff, len);
	}
	pthread_rwlock_unlock(ctx->rwlock4);

	return result;
}

ssize_t send6(router_ctx_t* ctx, uint8_t* buff, int len) {
	//TODO: implement this
	pthread_rwlock_rdlock(ctx->rwlock4);
	pthread_rwlock_unlock(ctx->rwlock4);

	errno = EBADF;
	return -1;
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

	//allocating memory for storing links to: android tun context, default route, all ip4 routes
	common_tun_ctx_t** ctxs = malloc(sizeof(common_tun_ctx_t*) * (ctx->ip4_routes_count + 2));
	if(ctxs == NULL) {
		goto error;
	}

	ctxs[poll_count] = &(ctx->dev_tun_ctx);
	poll_count++;

	if(ctx->ip4_default_tun_ctx != NULL) {
		ctxs[poll_count] = ctx->ip4_default_tun_ctx;
		poll_count++;
	}

	int i;

	route4_link_t* curr = ctx->ip4_routes;
	while(curr != NULL) {
		if(curr->tun_ctx != NULL) {
			bool already_added = false;

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
	return write(ctx->local_fd, buff, len);
}

ssize_t dev_tun_recv(intptr_t tun_ctx, uint8_t* buff, int len) {
	if(tun_ctx == (intptr_t) NULL) {
		errno = EBADF;
		return -1;
	}

	common_tun_ctx_t* ctx = (common_tun_ctx_t*) tun_ctx;
	int res = read_ip_packet(ctx->local_fd, buff, len);
	if(res < 0) {
		return res;
	}

	res = ipsend(ctx->router_ctx, buff, res);
	return res;
}

ssize_t common_tun_send(intptr_t tun_ctx, uint8_t* buff, int len) {
	if(tun_ctx == (intptr_t) NULL) {
		errno = EBADF;
		return -1;
	}

	common_tun_ctx_t* ctx = (common_tun_ctx_t*) tun_ctx;
	return write(ctx->local_fd, buff, len);
}

ssize_t common_tun_recv(intptr_t tun_ctx, uint8_t* buff, int len) {
	if(tun_ctx == (intptr_t) NULL) {
		errno = EBADF;
		return -1;
	}

	common_tun_ctx_t* ctx = (common_tun_ctx_t*) tun_ctx;

	int res = read_ip_packet(ctx->local_fd, buff, len);
	if(res < 0) {
		return res;
	}

	pthread_rwlock_rdlock(ctx->router_ctx->rwlock4);
	if((buff[0] & 0xf0) == 0x40 && ctx->router_ctx->dev_tun_ctx.use_masquerade4) {
		LOGE(LOG_TAG, "Before masquerading back:");
		log_dump_packet(LOG_TAG, buff, res);

		ip4_header* hdr = (ip4_header*) buff;
		hdr->daddr = htonl(ctx->router_ctx->dev_tun_ctx.masquerade4);
		ip4_calc_ip_checksum(buff, res);

		switch(hdr->protocol) {
		case IPPROTO_TCP: {
			ip4_calc_tcp_checksum(buff, res);
			break;
		}
		default: {
			LOGE(LOG_TAG, "Can't calculate checksum for protocol %d", hdr->protocol);
			break;
		}
		}
		LOGE(LOG_TAG, "Masquerading back:");
		log_dump_packet(LOG_TAG, buff, res);
	} else if((buff[0] & 0xf0) == 0x60 && ctx->router_ctx->dev_tun_ctx.use_masquerade6) {

	}
	res = write(ctx->router_ctx->dev_tun_ctx.local_fd, buff, res);
	pthread_rwlock_unlock(ctx->router_ctx->rwlock4);
	return res;
}

ssize_t common_tun_pipe(intptr_t tun_ctx, uint8_t* buff, int len) {
	if(tun_ctx == (intptr_t) NULL) {
		errno = EBADF;
		return -1;
	}

	common_tun_ctx_t* ctx = (common_tun_ctx_t*) tun_ctx;

	int res = read_ip_packet(ctx->local_fd, buff, len);
	if(res < 0) {
		return res;
	}

	res = write(ctx->router_ctx->dev_tun_ctx.local_fd, buff, res);
	return res;
}

uint16_t ip4_calc_ip_checksum(uint8_t* buff, int len) {
	if(buff == NULL || len < 20) {
		return 0;
	}

	ip4_header* hdr = (ip4_header*) buff;
	int cycles = hdr->ihl * 2;

	if(len < cycles * sizeof(uint16_t)) {
		return 0;
	}

	int seq_num = -1;
	uint32_t sum = 0;
	uint16_t* ptr = (uint16_t*) buff;
	ptr--; //this is safe due to a following ptr++
	while(cycles > 0) {
		cycles--;
		ptr++;
		seq_num++;
		if(seq_num == 5) {
			//skipping checksum field
			continue;
		}

		sum = ip4_update_sum(sum, ntohs(ptr[0]));
	}

	uint16_t checksum = ~sum;
	hdr->check = htons(checksum);

	return checksum;
}

uint16_t ip4_calc_tcp_checksum(uint8_t* buff, int len) {
	if(buff == NULL || len < 40) {
		return 0;
	}
	ip4_header* hdr = (ip4_header*) buff;
	uint16_t tcp_len = ntohs(hdr->tot_len) - (hdr->ihl * 4);
	uint32_t sum = ip4_calc_pseudoheader_checksum(buff, len);

	int cycles = tcp_len >> 1; //integer division

	int seq_num = -1;
	uint16_t* ptr = (uint16_t*) (buff + hdr->ihl * 4);
	LOGD(LOG_TAG, "TCP frame size is %d, packet address is %p, tcp data starts from %p", tcp_len, buff, ptr);
	tcp_header* tcp_hdr = (tcp_header*) ptr;
	ptr--; //this is safe due to a following ptr++
	while(cycles > 0) {
		cycles--;
		ptr++;
		seq_num++;
		if(seq_num == 8) {
			//skipping checksum field
			continue;
		}

		sum = ip4_update_sum(sum, ntohs(ptr[0]));
	}

	if((tcp_len & 1) == 1) {
		//adding odd byte
		ptr++;
		uint8_t odd_byte = ((uint8_t*) ptr)[0];
		sum = ip4_update_sum(sum, htons((uint16_t) odd_byte));
		LOGD(LOG_TAG, "TCP frame size is odd, adding %02x %p to checksum", htons((uint16_t) odd_byte), ptr);
	}
	uint16_t checksum = ~sum;
	tcp_hdr->check = htons(checksum);

	return checksum;
}

uint16_t ip4_calc_pseudoheader_checksum(uint8_t* buff, int len) {
	//packet should be validated before this method call
	uint32_t sum = 0;
	ip4_header* hdr = (ip4_header*) buff;
	uint16_t* ptr = (uint16_t*) buff;

	//src addr
	sum = ip4_update_sum(sum, ntohs(ptr[6]));
	sum = ip4_update_sum(sum, ntohs(ptr[7]));

	//dst addr
	sum = ip4_update_sum(sum, ntohs(ptr[8]));
	sum = ip4_update_sum(sum, ntohs(ptr[9]));

	sum = ip4_update_sum(sum, (uint16_t) hdr->protocol);
	sum = ip4_update_sum(sum, ntohs(hdr->tot_len) - (hdr->ihl * 4));

	return sum;
}

inline uint32_t ip4_update_sum(uint32_t previous, uint16_t data) {
	uint32_t sum = previous;
	sum += data;
	sum += sum >> 16;
	sum &= 0x0000ffff;
	return sum;
}

inline uint16_t ip4_update_checksum(uint16_t old_checksum, uint16_t old_data, uint16_t new_data) {
	uint32_t sum = (~old_checksum) & 0x0000ffff;
	sum = ip4_update_sum(sum, old_data);
	sum = ip4_update_sum(sum, ~new_data);
	uint16_t checksum = ~sum;
	return checksum;
}
