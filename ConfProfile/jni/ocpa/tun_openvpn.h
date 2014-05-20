/*
 * tun_openvpn.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef TUN_OPENVPN_H_
#define TUN_OPENVPN_H_

#include "router.h"

typedef struct openvpn_tun_ctx_t openvpn_tun_ctx_t;

struct openvpn_tun_ctx_t {
	common_tun_ctx_t common;
};


#endif /* TUN_OPENVPN_H_ */
