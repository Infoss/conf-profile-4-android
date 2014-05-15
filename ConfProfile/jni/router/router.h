#ifndef ROUTER_H_INCLUDED
#define ROUTER_H_INCLUDED

#include <stdlib.h>
#include <stdint.h>

typedef void (*route_func_ptr)(uint8_t* buff, int len);

typedef struct route4_link {
    uint32_t ip4;
    route_func_ptr route_func;
    struct route4_link* next;
} route4_link_t;

typedef struct {
    route4_link_t* ip4_routes;
    route_func_ptr ip4_default_route;
} router_ctx_t;

router_ctx_t* router_init(router_ctx_t* ctx);
void router_deinit(router_ctx_t* ctx);

void route4(router_ctx_t* ctx, uint32_t ip4, void (*routefunc)(uint8_t* buff, int len));
void route6(router_ctx_t* ctx, uint8_t* ip6, void (*routefunc)(uint8_t* buff, int len));
#endif // ROUTER_H_INCLUDED
