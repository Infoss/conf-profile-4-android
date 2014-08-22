/*
 * tun_dev_tun.c
 *
 *      Author: Dmitry Vorobiev
 */

#include <unistd.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <errno.h>

#include "android_log_utils.h"
#include "iputils.h"
#include "router.h"
#include "tun_dev_tun.h"
#include "tun_private.h"

#define LOG_TAG "tun_dev_tun.c"

static ssize_t dev_tun_send(tun_ctx_t* ctx, uint8_t* buff, int len) {

	struct tun_ctx_private_t* instance = (struct tun_ctx_private_t*) ctx;

	//start capture
	pcap_output_write(instance->pcap_output, buff, 0, len);
	//end capture

	int size = write(instance->local_fd, buff, len);

	return size;
}

static ssize_t dev_tun_recv(tun_ctx_t* ctx, uint8_t* buff, int len) {
	if(ctx == NULL) {
		errno = EBADF;
		return -1;
	}

	struct tun_ctx_private_t* instance = (struct tun_ctx_private_t*) ctx;

	//TODO: use internal buffer
	ocpa_ip_packet_t* ip_packet = &(instance->router_ctx->ip_pkt);

	int res = read_ip_packet(ctx->getLocalFd(ctx), ip_packet->buff, ip_packet->buff_len);
	if(res < 0) {
		if(errno == EAGAIN || errno == EWOULDBLOCK) {
			//we'll try next time
			LOGW(LOG_TAG, "Got EAGAIN or EWOULDBLOCK on fd=%d", ctx->getLocalFd(ctx));
			errno = 0;
			return res;
		}
		return res;
	}

	ip_packet->pkt_len = res;

	//start capture
	pcap_output_write(instance->pcap_output, ip_packet->buff, 0, ip_packet->pkt_len);
	//end capture

	res = ipsend(instance->router_ctx, ip_packet);

	return res;
}

tun_ctx_t* create_tun_dev_tun_ctx(tun_ctx_t* ptr, ssize_t len) {
	tun_ctx_t* result = create_tun_ctx(ptr, len);
	if(result == NULL) {
		return NULL;
	}

	result->send = dev_tun_send;
	result->recv = dev_tun_recv;

	return result;
}

void set_tun_dev_tun_fd(tun_ctx_t* ctx, int fd) {
	if(ctx == NULL) {
		return;
	}

	struct tun_ctx_private_t* instance = (struct tun_ctx_private_t*) ctx;
	instance->local_fd = fd;
	instance->remote_fd = fd;
}

