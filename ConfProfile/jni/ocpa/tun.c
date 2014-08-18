/*
 * tun.c
 *
 *      Author: Dmitry Vorobiev
 */

#include <unistd.h>
#include <errno.h>
#include <stdlib.h>
#include <sys/socket.h>

#include "android_log_utils.h"
#include "android_jni.h"
#include "protoheaders.h"
#include "tun.h"
#include "router.h"
#include "iputils.h"

#define LOG_TAG "tun.c"

bool common_tun_set(common_tun_ctx_t* ctx, jobject jtun_instance) {
	if(ctx == NULL) {
		return false;
	}

	ctx->rwlock = malloc(sizeof(pthread_rwlock_t));
	if(ctx->rwlock == NULL) {
		return false;
	}
	if(pthread_rwlock_init(ctx->rwlock, NULL) != 0) {
		free(ctx->rwlock);
		ctx->rwlock = NULL;
		return false;
	}

	ctx->local_fd = UNDEFINED_FD;
	ctx->remote_fd = UNDEFINED_FD;
	ctx->masquerade4 = 0;
	memset(ctx->masquerade6, 0, sizeof(ctx->masquerade6));
	ctx->use_masquerade4 = false;
	ctx->use_masquerade6 = false;
	ctx->send_func = common_tun_send;
	ctx->recv_func = common_tun_recv;
	ctx->router_ctx = NULL;

	ctx->bytes_in = 0;
	ctx->bytes_out = 0;

	ctx->j_vpn_tun = wrap_into_VpnTunnel(jtun_instance);

	ctx->pcap_output = NULL;

	return true;
}

void common_tun_free(common_tun_ctx_t* ctx) {
	if(ctx == NULL) {
		return;
	}

	if(ctx->local_fd != UNDEFINED_FD) {
		shutdown(ctx->local_fd, SHUT_RDWR);
	}

	if(ctx->remote_fd != UNDEFINED_FD) {
		shutdown(ctx->remote_fd, SHUT_RDWR);
	}

	pthread_rwlock_wrlock(ctx->rwlock);

	ctx->masquerade4 = 0;
	ctx->use_masquerade4 = false;
	memset(ctx->masquerade6, 0, sizeof(ctx->masquerade6));
	ctx->use_masquerade6 = false;
	ctx->send_func = NULL;
	ctx->recv_func = NULL;
	ctx->router_ctx = NULL;

	ctx->bytes_in = 0;
	ctx->bytes_out = 0;

	destroy_VpnTunnel(ctx->j_vpn_tun);
	ctx->j_vpn_tun = NULL;

	pcap_output_destroy(ctx->pcap_output);
	ctx->pcap_output = NULL;

	pthread_rwlock_unlock(ctx->rwlock);
	pthread_rwlock_destroy(ctx->rwlock);
	free(ctx->rwlock);
	ctx->rwlock = NULL;
}

ssize_t common_tun_send(intptr_t tun_ctx, uint8_t* buff, int len) {
	if(tun_ctx == (intptr_t) NULL) {
		errno = EBADF;
		return -1;
	}

	common_tun_ctx_t* ctx = (common_tun_ctx_t*) tun_ctx;

	//start capture
	pcap_output_write(ctx->pcap_output, buff, 0, len);
	//end capture

	ctx->bytes_out += len;

	return write(ctx->local_fd, buff, len);
}

ssize_t common_tun_recv(intptr_t tun_ctx, uint8_t* buff, int len) {
	if(tun_ctx == (intptr_t) NULL) {
		errno = EBADF;
		return -1;
	}

	common_tun_ctx_t* ctx = (common_tun_ctx_t*) tun_ctx;

	int res = read_ip_packet(ctx->local_fd, buff, len);
	if(res < 0) {
		LOGE(LOG_TAG, "Error while reading from a tunnel socket (fd=%d)", ctx->local_fd);
		LOGE(LOG_TAG, "Packet dump (buff=%p, len=%d)", buff, len);
		log_dump_packet(LOG_TAG, buff, len);
		if(errno == EAGAIN || errno == EWOULDBLOCK) {
			//we'll try next time
			LOGW(LOG_TAG, "Got EAGAIN or EWOULDBLOCK on fd=%d", ctx->local_fd);
			errno = 0;
			return res;
		}
		return res;
	}

	ctx->bytes_in += res;

	//start capture
	pcap_output_write(ctx->pcap_output, buff, 0, res);
	//end capture

	pthread_rwlock_rdlock(ctx->router_ctx->rwlock4);
	if((buff[0] & 0xf0) == 0x40 && ctx->router_ctx->dev_tun_ctx.use_masquerade4) {
		ip4_header* hdr = (ip4_header*) buff;
		hdr->daddr = htonl(ctx->router_ctx->dev_tun_ctx.masquerade4);
		ip4_calc_ip_checksum(buff, res);

		switch(hdr->protocol) {
		case IPPROTO_TCP: {
			ip4_calc_tcp_checksum(buff, res);
			break;
		}
		default: {
			LOGE(LOG_TAG, "Can't calculate checksum for protocol %d", hdr->protocol);
			break;
		}
		}
	} else if((buff[0] & 0xf0) == 0x60 && ctx->router_ctx->dev_tun_ctx.use_masquerade6) {
		LOGE(LOG_TAG, "IPv6 masquerading isn't supported yet");
	}

	ctx->router_ctx->dev_tun_ctx.bytes_out += res;

	res = write(ctx->router_ctx->dev_tun_ctx.local_fd, buff, res);
	if(res < 0) {
		LOGE(LOG_TAG, "Error while writing to a /dev/net/tun socket (fd=%d)", ctx->router_ctx->dev_tun_ctx.local_fd);
		LOGE(LOG_TAG, "Packet dump (buff=%p, len=%d)", buff, res);
		log_dump_packet(LOG_TAG, buff, len);
	}
	pthread_rwlock_unlock(ctx->router_ctx->rwlock4);
	return res;
}

ssize_t common_tun_pipe(intptr_t tun_ctx, uint8_t* buff, int len) {
	if(tun_ctx == (intptr_t) NULL) {
		errno = EBADF;
		return -1;
	}

	common_tun_ctx_t* ctx = (common_tun_ctx_t*) tun_ctx;

	int res = read_ip_packet(ctx->local_fd, buff, len);
	if(res < 0) {
		return res;
	}

	res = write(ctx->router_ctx->dev_tun_ctx.local_fd, buff, res);
	return res;
}

