/*
 * tun.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef TUN_H_
#define TUN_H_

#include <stdbool.h>
#include <stdint.h>
#include <stdbool.h>
#include <jni.h>
#include <pthread.h>

#include "java_VpnTunnel.h"
#include "pcap_output.h"

#include "refs.h"

#define UNDEFINED_FD -1

struct router_ctx_t;
typedef struct router_ctx_t router_ctx_t;
typedef struct tun_jni_data_t tun_jni_data_t;

//typedef ssize_t (*tun_send_func_ptr)(intptr_t tun_ctx, uint8_t* buff, int len);
//typedef ssize_t (*tun_recv_func_ptr)(intptr_t tun_ctx, uint8_t* buff, int len);

typedef union tun_ctx_t tun_ctx_t;
union tun_ctx_t {
	struct {
		REFS_DECLARE_METHODS(tun_ctx_t)
		void (*destroy)(tun_ctx_t* instance);
		ssize_t (*send)(tun_ctx_t* instance, uint8_t* buff, int len);
		ssize_t (*recv)(tun_ctx_t* instance, uint8_t* buff, int len);
		void (*masqueradeSrc)(tun_ctx_t* instance, uint8_t* buff, int len);
		void (*masqueradeDst)(tun_ctx_t* instance, uint8_t* buff, int len);
		int (*getLocalFd)(tun_ctx_t* instance);
		int (*getRemoteFd)(tun_ctx_t* instance);
		void (*setDnsIp4)(tun_ctx_t* instance, uint32_t idx, uint32_t ip);
		void (*setVirtualDnsIp4)(tun_ctx_t* instance, uint32_t idx, uint32_t ip);
		void (*setMasquerade4Mode)(tun_ctx_t* instance, bool mode);
		void (*setMasquerade4Ip)(tun_ctx_t* instance, uint32_t ip);
		void (*setMasquerade6Mode)(tun_ctx_t* instance, bool mode);
		void (*setMasquerade6Ip)(tun_ctx_t* instance, uint8_t* ip);
		void (*setRouterContext)(tun_ctx_t* instance, router_ctx_t* router_ctx);
		void (*setJavaVpnTunnel)(tun_ctx_t* instance, jobject object);
		void (*debugRestartPcap)(tun_ctx_t* instance, jobject object);
		void (*debugStopPcap)(tun_ctx_t* instance);
	};
	void* raw_func_pointers[20];
};

/**
 * Initializes tun_ctx by given pointer.
 */
tun_ctx_t* create_tun_ctx(tun_ctx_t* ptr, ssize_t len);

#endif /* TUN_H_ */
