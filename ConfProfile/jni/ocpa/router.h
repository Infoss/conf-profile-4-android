#ifndef ROUTER_H_INCLUDED
#define ROUTER_H_INCLUDED

#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <pthread.h>
#include <poll.h>

#include "tun.h"

typedef struct route4_link_t route4_link_t;
typedef struct router_ctx_t router_ctx_t;
typedef struct poll_helper_struct_t poll_helper_struct_t;



struct route4_link_t {
    uint32_t ip4;
    uint8_t mask;
    common_tun_ctx_t* tun_ctx;
    route4_link_t* next;
};

struct router_ctx_t {
	pthread_rwlock_t* rwlock4;
	common_tun_ctx_t dev_tun_ctx;

	//IPv4 route fields
    route4_link_t* ip4_routes;
    int ip4_routes_count;

    //Default IPv4 route fields
    common_tun_ctx_t* ip4_default_tun_ctx;

    //internal packet buffer
    uint8_t* ip4_pkt_buff;
    int ip4_pkt_buff_size;

    bool routes_updated;
    bool paused;
    bool terminate;
};

struct poll_helper_struct_t {
	//helper fields
	struct pollfd* poll_fds;
	common_tun_ctx_t** poll_ctxs;
	int poll_fds_count;
};

router_ctx_t* router_init();
void router_deinit(router_ctx_t* ctx);

void route4(router_ctx_t* ctx, uint32_t ip4, uint32_t mask, common_tun_ctx_t* tun_ctx);
void route6(router_ctx_t* ctx, uint8_t* ip6, uint32_t mask, common_tun_ctx_t* tun_ctx);
void unroute4(router_ctx_t* ctx, uint32_t ip4, uint32_t mask);
void unroute6(router_ctx_t* ctx, uint8_t* ip6, uint32_t mask);
void default4(router_ctx_t* ctx, common_tun_ctx_t* tun_ctx);
void default6(router_ctx_t* ctx, common_tun_ctx_t* tun_ctx);
ssize_t ipsend(router_ctx_t* ctx, uint8_t* buff, int len);
ssize_t send4(router_ctx_t* ctx, uint8_t* buff, int len);
ssize_t send6(router_ctx_t* ctx, uint8_t* buff, int len);

ssize_t read_ip_packet(int fd, uint8_t* buff, int len);

void rebuild_poll_struct(router_ctx_t* ctx, poll_helper_struct_t* poll_struct);
bool router_is_paused(router_ctx_t* ctx);
bool router_pause(router_ctx_t* ctx, bool paused);

ssize_t dev_tun_send(intptr_t tun_ctx, uint8_t* buff, int len);
ssize_t dev_tun_recv(intptr_t tun_ctx, uint8_t* buff, int len);

#endif // ROUTER_H_INCLUDED