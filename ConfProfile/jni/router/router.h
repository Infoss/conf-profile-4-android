#ifndef ROUTER_H_INCLUDED
#define ROUTER_H_INCLUDED

#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <pthread.h>
#include <poll.h>

typedef int (*tun_send_func_ptr)(intptr_t tun_ctx, uint8_t* buff, int len);
typedef int (*tun_recv_func_ptr)(intptr_t tun_ctx, uint8_t* buff, int len);

typedef struct common_tun_ctx_t common_tun_ctx_t;
typedef struct route4_link_t route4_link_t;
typedef struct router_ctx_t router_ctx_t;

struct common_tun_ctx_t {
	int local_fd;  //router side
	int remote_fd; //vpn implementation side
	tun_send_func_ptr send_func;
	tun_recv_func_ptr recv_func;
	router_ctx_t* router_ctx;
};

struct route4_link_t {
    uint32_t ip4;
    common_tun_ctx_t* tun_ctx;
    route4_link_t* next;
};

struct router_ctx_t {
	pthread_rwlock_t* rwlock4;
	int dev_fd;

	//IPv4 route fields
    route4_link_t* ip4_routes;
    int ip4_routes_count;

    //Default IPv4 route fields
    common_tun_ctx_t* ip4_default_tun_ctx;

    //internal packet buffer
    uint8_t* ip4_pkt_buff;
    int ip4_pkt_buff_size;

    //helper fields
    struct pollfd* poll_fds;
    common_tun_ctx_t** poll_ctxs;
    int poll_fds_count;
    int poll_fds_nfds;
    bool terminate;
};

router_ctx_t* router_init();
void router_deinit(router_ctx_t* ctx);

void route4(router_ctx_t* ctx, uint32_t ip4, common_tun_ctx_t* tun_ctx);
void route6(router_ctx_t* ctx, uint8_t* ip6, common_tun_ctx_t* tun_ctx);
void unroute4(router_ctx_t* ctx, uint32_t ip4);
void unroute6(router_ctx_t* ctx, uint8_t* ip6);
void default4(router_ctx_t* ctx, common_tun_ctx_t* tun_ctx);
void default6(router_ctx_t* ctx, common_tun_ctx_t* tun_ctx);
void ipsend(router_ctx_t* ctx, uint8_t* buff, int len);
void send4(router_ctx_t* ctx, uint8_t* buff, int len);
void send6(router_ctx_t* ctx, uint8_t* buff, int len);

int read_ip_packet(int fd, uint8_t* buff, int len);

void rebuild_poll_struct(router_ctx_t* ctx);

int common_tun_send(intptr_t tun_ctx, uint8_t* buff, int len);
int common_tun_recv(intptr_t tun_ctx, uint8_t* buff, int len);

#endif // ROUTER_H_INCLUDED
