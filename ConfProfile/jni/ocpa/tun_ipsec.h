/*
 * tun_ipsec.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef TUN_IPSEC_H_
#define TUN_IPSEC_H_

#include "tun.h"

typedef struct ipsec_tun_ctx_t ipsec_tun_ctx_t;

struct ipsec_tun_ctx_t {
	common_tun_ctx_t common;
};

ipsec_tun_ctx_t* ipsec_tun_init();
void ipsec_tun_deinit(ipsec_tun_ctx_t* ctx);

#endif /* TUN_IPSEC_H_ */
