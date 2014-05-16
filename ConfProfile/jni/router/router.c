
#include "router.h"

router_ctx_t* router_init(router_ctx_t* ctx) {
    router_ctx_t* context = ctx;

    if(context == NULL) {
        context = (router_ctx_t*) malloc(sizeof(router_ctx_t));
    }

    if(context != NULL) {
        context->ip4_routes = NULL;
        context->ip4_default_route = NULL;
    }

    return context;
}

void router_deinit(router_ctx_t* ctx) {
    if(ctx != NULL) {
        if(ctx->ip4_routes != NULL) {
            route4_link_t* curr = ctx->ip4_routes;

            while(curr != NULL) {
                route4_link_t* next = curr->next;
                curr->route_func = NULL;
                free(curr);
                curr = next;
            }
        }

        ctx->ip4_routes = NULL;
        ctx->ip4_default_route = NULL;
    }
}

void route4(router_ctx_t* ctx, uint32_t ip4, void (*routefunc)(uint8_t* buff, int len)) {
	if(ctx == NULL) {
		return;
	}

	route4_link_t* link = (route4_link_t*) malloc(sizeof(route4_link_t));
	link->ip4 = ip4;
	link->route_func = routefunc;
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

void route6(router_ctx_t* ctx, uint8_t* ip6, void (*routefunc)(uint8_t* buff, int len)) {
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

void default4(router_ctx_t* ctx, void (*routefunc)(uint8_t* buff, int len)) {
	if(ctx != NULL) {
		ctx->ip4_default_route = routefunc;
	}
}

void default6(router_ctx_t* ctx, void (*routefunc)(uint8_t* buff, int len)) {
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
	route_func_ptr route_func = NULL;
	if(ctx->ip4_routes != NULL) {

	}

	if(route_func == NULL) {
		route_func = ctx->ip4_default_route;
	}

	if(route_func != NULL) {
		route_func(buff, len);
	}
}

void send6(router_ctx_t* ctx, uint8_t* buff, int len) {
	//TODO: implement this
}
