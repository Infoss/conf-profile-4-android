/*
 * tun_usernat.c
 *
 *      Author: Dmitry Vorobiev
 */

#include <stdlib.h>
#include <sys/socket.h>
#include "tun_usernat.h"

usernat_tun_ctx_t* usernat_tun_init() {
	usernat_tun_ctx_t* ctx = malloc(sizeof(usernat_tun_ctx_t));
	if(ctx == NULL) {
		return NULL;
	}

	int fds[2];
	if(socketpair(AF_UNIX, SOCK_STREAM, PF_UNSPEC, fds) != 0) {
		free(ctx);
		return NULL;
	}

	common_tun_set((common_tun_ctx_t*) ctx);

	ctx->common.local_fd = fds[0];
	ctx->common.remote_fd = fds[1];

	return ctx;
}

void usernat_tun_deinit(usernat_tun_ctx_t* ctx) {
	if(ctx == NULL) {
		return;
	}

	common_tun_free((common_tun_ctx_t*) ctx);

	free(ctx);
}
