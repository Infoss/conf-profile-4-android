/*
 * tun_l2tp.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef TUN_L2TP_H_
#define TUN_L2TP_H_

#include "tun.h"

typedef struct l2tp_tun_ctx_t l2tp_tun_ctx_t;

struct l2tp_tun_ctx_t {
	common_tun_ctx_t common;
};

l2tp_tun_ctx_t* l2tp_tun_init(jobject jtun_instance);
void l2tp_tun_deinit(l2tp_tun_ctx_t* ctx);

#endif /* TUN_L2TP_H_ */
