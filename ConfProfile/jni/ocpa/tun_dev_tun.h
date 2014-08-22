/*
 * tun_dev_tun.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef TUN_DEV_TUN_H_
#define TUN_DEV_TUN_H_

#include "tun.h"

tun_ctx_t* create_tun_dev_tun_ctx(tun_ctx_t* ptr, ssize_t len);
void set_tun_dev_tun_fd(tun_ctx_t* instance, int fd);

#endif /* TUN_DEV_TUN_H_ */
