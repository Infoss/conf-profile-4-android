/*
 * tun_ipsec.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef TUN_IPSEC_H_
#define TUN_IPSEC_H_

#include "tun.h"

typedef tun_ctx_t ipsec_tun_ctx_t;

ipsec_tun_ctx_t* create_ipsec_tun_ctx(ipsec_tun_ctx_t* ptr, ssize_t len);

#endif /* TUN_IPSEC_H_ */
