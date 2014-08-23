#ifndef ROUTER_H_INCLUDED
#define ROUTER_H_INCLUDED

#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <pthread.h>
#include <poll.h>
#include <sys/epoll.h>
#include <jni.h>

#include "tun.h"
#include "iputils.h"

#define MAX_EPOLL_EVENTS 10

typedef struct route4_link_t route4_link_t;
typedef struct route6_link_t route6_link_t;
typedef struct router_ctx_t router_ctx_t;
typedef struct poll_helper_struct_t poll_helper_struct_t;
typedef struct epoll_link_t epoll_link_t;

struct route4_link_t {
    uint32_t ip4;
    uint8_t mask;
    tun_ctx_t* tun_ctx;
    route4_link_t* next;
};

struct route6_link_t {
    uint8_t ip6[16];
    uint8_t mask;
    tun_ctx_t* tun_ctx;
    route6_link_t* next;
};

struct router_ctx_t {
	pthread_rwlock_t* rwlock4;
	tun_ctx_t* dev_tun_ctx;

	//IPv4 route fields
    route4_link_t* ip4_routes;
    int ip4_routes_count;

    //Default IPv4 route fields
    tun_ctx_t* ip4_default_tun_ctx;

    //IPv6 route fields
    route6_link_t* ip6_routes;
    int ip6_routes_count;

    //Default IPv6 route fields
    tun_ctx_t* ip6_default_tun_ctx;

    //internal packet buffer
    ocpa_ip_packet_t ip_pkt;

    int epoll_fd;
    struct epoll_event epoll_events[MAX_EPOLL_EVENTS];
    epoll_link_t* epoll_links;
    unsigned int epoll_links_capacity;

    bool routes_updated;
    bool paused;
    bool terminate;
};

struct epoll_link_t {
	int fd;
	tun_ctx_t* tun_ctx;
};

struct poll_helper_struct_t {
	pthread_rwlock_t* rwlock;

	//helper fields
	struct pollfd* poll_fds;
	tun_ctx_t** poll_ctxs;
	int poll_fds_count;
};

router_ctx_t* router_init();
void router_deinit(router_ctx_t* ctx);

void route4(router_ctx_t* ctx, uint32_t ip4, uint32_t mask, tun_ctx_t* tun_ctx);
void route6(router_ctx_t* ctx, uint8_t* ip6, uint32_t mask, tun_ctx_t* tun_ctx);
void unroute(router_ctx_t* ctx, tun_ctx_t* tun_ctx);
void unroute4(router_ctx_t* ctx, uint32_t ip4, uint32_t mask);
void unroute6(router_ctx_t* ctx, uint8_t* ip6, uint32_t mask);
void default4(router_ctx_t* ctx, tun_ctx_t* tun_ctx);
void default6(router_ctx_t* ctx, tun_ctx_t* tun_ctx);
ssize_t ipsend(router_ctx_t* ctx, ocpa_ip_packet_t* ip_packet);
ssize_t send4(router_ctx_t* ctx, ocpa_ip_packet_t* ip_packet);
ssize_t send6(router_ctx_t* ctx, ocpa_ip_packet_t* ip_packet);

void rebuild_epoll_struct(router_ctx_t* ctx);
bool router_is_paused(router_ctx_t* ctx);
bool router_pause(router_ctx_t* ctx, bool paused);

#endif // ROUTER_H_INCLUDED
