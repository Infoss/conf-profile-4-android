/*
 * tun.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef TUN_H_
#define TUN_H_

#include <stdbool.h>
#include <stdint.h>

struct router_ctx_t;
typedef struct router_ctx_t router_ctx_t;

typedef ssize_t (*tun_send_func_ptr)(intptr_t tun_ctx, uint8_t* buff, int len);
typedef ssize_t (*tun_recv_func_ptr)(intptr_t tun_ctx, uint8_t* buff, int len);

struct common_tun_ctx_t {
	int local_fd;  //router side
	int remote_fd; //vpn implementation side
	uint32_t masquerade4;
	uint8_t masquerade6[16];
	bool use_masquerade4;
	bool use_masquerade6;
	tun_send_func_ptr send_func;
	tun_recv_func_ptr recv_func;
	router_ctx_t* router_ctx;
};

typedef struct common_tun_ctx_t common_tun_ctx_t;


void common_tun_set(common_tun_ctx_t* ctx);
void common_tun_free(common_tun_ctx_t* ctx);

ssize_t common_tun_send(intptr_t tun_ctx, uint8_t* buff, int len);
ssize_t common_tun_recv(intptr_t tun_ctx, uint8_t* buff, int len);

#endif /* TUN_H_ */
