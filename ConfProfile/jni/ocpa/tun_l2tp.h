/*
 * tun_l2tp.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef TUN_L2TP_H_
#define TUN_L2TP_H_

#include "tun.h"

typedef tun_ctx_t l2tp_tun_ctx_t;

l2tp_tun_ctx_t* create_l2tp_tun_ctx(l2tp_tun_ctx_t* ptr, ssize_t len);

#endif /* TUN_L2TP_H_ */
