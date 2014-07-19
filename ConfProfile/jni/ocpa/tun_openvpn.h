/*
 * tun_openvpn.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef TUN_OPENVPN_H_
#define TUN_OPENVPN_H_

#include "tun.h"

typedef struct openvpn_tun_ctx_t openvpn_tun_ctx_t;

struct openvpn_tun_ctx_t {
	common_tun_ctx_t common;
};

openvpn_tun_ctx_t* openvpn_tun_init(jobject jtun_instance);
void openvpn_tun_deinit(openvpn_tun_ctx_t* ctx);

#endif /* TUN_OPENVPN_H_ */
