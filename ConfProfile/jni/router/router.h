#ifndef ROUTER_H_INCLUDED
#define ROUTER_H_INCLUDED

#include <stdlib.h>
#include <stdint.h>
#include <pthread.h>

typedef int (*tun_send_func_ptr)(intptr_t tun_ctx, uint8_t* buff, int len);
typedef int (*tun_recv_func_ptr)(intptr_t tun_ctx, uint8_t* buff, int len);

typedef struct route4_link_t route4_link_t;
typedef struct router_ctx_t router_ctx_t;

struct route4_link_t {
    uint32_t ip4;
    intptr_t tun_ctx;
    tun_send_func_ptr tun_send_func;
    tun_recv_func_ptr tun_recv_func;
    route4_link_t* next;
};

struct router_ctx_t {
	pthread_rwlock_t* rwlock4;
	int dev_fd;
    route4_link_t* ip4_routes;
    intptr_t ip4_default_tun_ctx;
    tun_send_func_ptr ip4_default_tun_send_func;
    tun_recv_func_ptr ip4_default_tun_recv_func;
};

router_ctx_t* router_init();
void router_deinit(router_ctx_t* ctx);

void route4(router_ctx_t* ctx, uint32_t ip4, intptr_t tun_ctx, tun_send_func_ptr send_func, tun_recv_func_ptr recv_func);
void route6(router_ctx_t* ctx, uint8_t* ip6, intptr_t tun_ctx, tun_send_func_ptr send_func, tun_recv_func_ptr recv_func);
void unroute4(router_ctx_t* ctx, uint32_t ip4);
void unroute6(router_ctx_t* ctx, uint8_t* ip6);
void default4(router_ctx_t* ctx, intptr_t tun_ctx, tun_send_func_ptr send_func, tun_recv_func_ptr recv_func);
void default6(router_ctx_t* ctx, intptr_t tun_ctx, tun_send_func_ptr send_func, tun_recv_func_ptr recv_func);
void ipsend(router_ctx_t* ctx, uint8_t* buff, int len);
void send4(router_ctx_t* ctx, uint8_t* buff, int len);
void send6(router_ctx_t* ctx, uint8_t* buff, int len);

int read_ip_packet(int fd, uint8_t* buff, int len);

#endif // ROUTER_H_INCLUDED
