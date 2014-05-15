
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

}
