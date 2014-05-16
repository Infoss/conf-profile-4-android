
#include "router.h"

router_ctx_t* router_init(router_ctx_t* ctx) {
    router_ctx_t* context = ctx;

    if(context == NULL) {
        context = (router_ctx_t*) malloc(sizeof(router_ctx_t));
    }

    if(context != NULL) {
    	context->rwlock4 = malloc(sizeof(pthread_rwlock_t));
    	if(context->rwlock4 == NULL) {
    		free(context);
    		return NULL;
    	}
    	if(pthread_rwlock_init(context->rwlock4, NULL) != 0) {
    		free(context->rwlock4);
    		context->rwlock4 = NULL;
    		free(context);
    		return NULL;
    	}

        context->ip4_routes = NULL;
        ctx->ip4_default_tun_ctx = (intptr_t) NULL;
        ctx->ip4_default_tun_send_func = NULL;
        ctx->ip4_default_tun_recv_func = NULL;
    }

    return context;
}

void router_deinit(router_ctx_t* ctx) {
    if(ctx != NULL) {
    	pthread_rwlock_destroy(ctx->rwlock4);
    	free(ctx->rwlock4);
    	ctx->rwlock4 = NULL;

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

        ctx->ip4_routes = NULL;
        ctx->ip4_default_tun_ctx = (intptr_t) NULL;
        ctx->ip4_default_tun_send_func = NULL;
        ctx->ip4_default_tun_recv_func = NULL;

        free(ctx);
    }
}

void route4(router_ctx_t* ctx, uint32_t ip4, intptr_t tun_ctx, tun_send_func_ptr send_func, tun_recv_func_ptr recv_func) {
	if(ctx == NULL) {
		return;
	}

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
}

void route6(router_ctx_t* ctx, uint8_t* ip6, intptr_t tun_ctx, tun_send_func_ptr send_func, tun_recv_func_ptr recv_func) {
	//TODO: implement this
}

void unroute4(router_ctx_t* ctx, uint32_t ip4) {
	if(ctx == NULL) {
		return;
	}

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
}

void unroute6(router_ctx_t* ctx, uint8_t* ip6) {
	//TODO: implement this
}

void default4(router_ctx_t* ctx, intptr_t tun_ctx, tun_send_func_ptr send_func, tun_recv_func_ptr recv_func) {
	if(ctx != NULL) {
		ctx->ip4_default_tun_ctx = tun_ctx;
		ctx->ip4_default_tun_send_func = send_func;
		ctx->ip4_default_tun_recv_func = recv_func;
	}
}

void default6(router_ctx_t* ctx, intptr_t tun_ctx, tun_send_func_ptr send_func, tun_recv_func_ptr recv_func) {
	//TODO: implement this
}

void send(router_ctx_t* ctx, uint8_t* buff, int len) {
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
}

void send6(router_ctx_t* ctx, uint8_t* buff, int len) {
	//TODO: implement this
}
