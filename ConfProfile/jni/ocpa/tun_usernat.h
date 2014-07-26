/*
 * tun_usernat.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef TUN_USERNAT_H_
#define TUN_USERNAT_H_

#include "tun.h"
#include "java_UsernatTunnel.h"

#define NAT_LINK_IP6  2
#define NAT_LINK_TCP4 0
#define NAT_LINK_UDP4 1
#define NAT_LINK_TCP6 (NAT_LINK_TCP4 | NAT_LINK_IP6)
#define NAT_LINK_UDP6 (NAT_LINK_UDP4 | NAT_LINK_IP6)

typedef struct usernat_tun_ctx_t usernat_tun_ctx_t;
typedef struct nat_common_link_t nat_common_link_t;
typedef struct nat_ip4_link_t nat_ip4_link_t;
typedef struct nat_tcp4_link_t nat_tcp4_link_t;
typedef struct nat_udp4_link_t nat_udp4_link_t;
typedef struct nat_ip6_link_t nat_ip6_link_t;
typedef struct nat_tcp6_link_t nat_tcp6_link_t;
typedef struct nat_udp6_link_t nat_udp6_link_t;
typedef union nat_link_t nat_link_t;

struct nat_common_link_t {
	uint8_t link_type;
	int sock_accept;
	int sock_connect;
	pid_t socat_pid;
	uint16_t hop_port;
	int cycles_to_live;
	nat_link_t* next;
	usernat_tun_ctx_t* usernat_ctx;
};

struct nat_ip4_link_t {
	nat_common_link_t common;

    uint32_t real_src_addr;
    uint16_t real_src_port;
    uint32_t real_dst_addr;
    uint16_t real_dst_port;
};

struct nat_tcp4_link_t {
	nat_ip4_link_t ip4;

	//tcp-related fields
};

struct nat_udp4_link_t {
	nat_ip4_link_t ip4;

	//udp-related fields
};

struct nat_ip6_link_t {
	nat_common_link_t common;

    uint8_t real_src_addr[16];
    uint16_t real_src_port;
    uint8_t real_dst_addr[16];
    uint16_t real_dst_port;
};

struct nat_tcp6_link_t {
	nat_ip6_link_t ip6;

	//tcp-related fields
};

struct nat_udp6_link_t {
	nat_ip6_link_t ip6;

	//udp-related fields
};

union nat_link_t {
	nat_common_link_t common;
	nat_ip4_link_t ip4;
	nat_tcp4_link_t tcp4;
	nat_udp4_link_t udp4;
	nat_ip6_link_t ip6;
	nat_tcp6_link_t tcp6;
	nat_udp6_link_t udp6;
};

struct usernat_tun_ctx_t {
	common_tun_ctx_t common;
	uint32_t local4;
	uint8_t local6[16];
	uint32_t remote4;
	uint8_t remote6[16];

	nat_link_t* links;

	java_UsernatTunnel* j_usernat_tun;
};

usernat_tun_ctx_t* usernat_tun_init(jobject jtun_instance);
void usernat_tun_deinit(usernat_tun_ctx_t* ctx);

ssize_t usernat_tun_send(intptr_t tun_ctx, uint8_t* buff, int len);
ssize_t usernat_tun_recv(intptr_t tun_ctx, uint8_t* buff, int len);
void usernat_set_pid_for_link(intptr_t tun_ctx, intptr_t link_ptr, int pid);

#endif /* TUN_USERNAT_H_ */
