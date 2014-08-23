/*
 * tun_ipsec.c
 *
 *      Author: Dmitry Vorobiev
 */

#include <stdlib.h>
#include <sys/socket.h>

#include "tun_ipsec.h"
#include "tun_private.h"
#include "android_log_utils.h"

#define LOG_TAG "tun_ipsec.c"

ipsec_tun_ctx_t* create_ipsec_tun_ctx(ipsec_tun_ctx_t* ptr, ssize_t len) {
	ipsec_tun_ctx_t* result = create_tun_ctx(ptr, len);
	LOGD(LOG_TAG, "new ipsec_tun_ctx_t initialized at %p");
	if(result == NULL) {
		return NULL;
	}

	struct tun_ctx_private_t* ctx = (struct tun_ctx_private_t*) result;

	int fds[2];
	if(socketpair(AF_UNIX, SOCK_STREAM, PF_UNSPEC, fds) != 0) {
		return ctx->public.ref_put(&ctx->public);
	}

	ctx->local_fd = fds[0];
	ctx->remote_fd = fds[1];

	return result;
}
