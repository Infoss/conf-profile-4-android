/*
 * tun_openvpn.c
 *
 *      Author: Dmitry Vorobiev
 */

#include <sys/socket.h>
#include "tun_openvpn.h"

openvpn_tun_ctx_t* openvpn_tun_init() {
	openvpn_tun_ctx_t* ctx = malloc(sizeof(openvpn_tun_ctx_t));
	if(ctx == NULL) {
		return NULL;
	}

	int fds[2];
	if(socketpair(AF_UNIX, SOCK_STREAM, PF_UNSPEC, fds) != 0) {
		free(ctx);
		return NULL;
	}

	ctx->common.local_fd = fds[0];
	ctx->common.remote_fd = fds[1];
	ctx->common.send_func = common_tun_send;
	ctx->common.recv_func = common_tun_recv;

	return ctx;
}
