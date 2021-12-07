/*
 * tun_private.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef TUN_PRIVATE_H_
#define TUN_PRIVATE_H_

#include "tun.h"

struct tun_ctx_private_t {
	tun_ctx_t public;

	refcount_t __ref_count;
	refcount_t __ref_generation;

	pthread_rwlock_t* rwlock;
	int local_fd;  //router side
	int remote_fd; //vpn implementation side
	uint32_t virtual_dns_ip4[4];
	uint32_t dns_ip4[4];
	uint32_t masquerade4;
	uint8_t masquerade6[16];
	bool use_masquerade4;
	bool use_masquerade6;
	router_ctx_t* router_ctx;

	uint64_t bytes_in;
	uint64_t bytes_out;

	java_VpnTunnel* j_vpn_tun;

	//Debug features
	pcap_output_t* pcap_output;
};

#endif /* TUN_PRIVATE_H_ */
