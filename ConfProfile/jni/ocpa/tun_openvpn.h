/*
 * tun_openvpn.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef TUN_OPENVPN_H_
#define TUN_OPENVPN_H_

#include "tun.h"

typedef tun_ctx_t openvpn_tun_ctx_t;

openvpn_tun_ctx_t* create_openvpn_tun_ctx(openvpn_tun_ctx_t* ptr, ssize_t len);

#endif /* TUN_OPENVPN_H_ */
