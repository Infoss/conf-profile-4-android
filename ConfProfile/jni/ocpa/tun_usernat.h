/*
 * tun_usernat.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef TUN_USERNAT_H_
#define TUN_USERNAT_H_

#include "tun.h"

typedef struct usernat_tun_ctx_t usernat_tun_ctx_t;

struct usernat_tun_ctx_t {
	common_tun_ctx_t common;
};

usernat_tun_ctx_t* usernat_tun_init();
void usernat_tun_deinit(usernat_tun_ctx_t* ctx);

#endif /* TUN_USERNAT_H_ */
