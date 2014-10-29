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
#include "tun_private.h"
#include "router.h"
#include "iputils.h"
#include "util.h"

#define LOG_TAG "tun.c"

REFS_DECLARE_METHODS_BODIES(tun_ctx_t, struct tun_ctx_private_t)

static void destroy_tun_ctx(tun_ctx_t* ptr) {
	TRACEPRINT("(ptr=%p)", ptr);
	LOGDIF(TUN_DEBUG, LOG_TAG, "Destroying tun_ctx_t at %p", ptr);
	if(ptr == NULL) {
		return;
	}

	struct tun_ctx_private_t* instance = (struct tun_ctx_private_t*) ptr;

	LOGDIF(TUN_DEBUG, LOG_TAG, "Shutdown local socket %d", instance->local_fd);
	if(instance->local_fd != UNDEFINED_FD) {
		shutdown(instance->local_fd, SHUT_RDWR);
	}

	LOGDIF(TUN_DEBUG, LOG_TAG, "Shutdown remote socket %d", instance->remote_fd);
	if(instance->remote_fd != UNDEFINED_FD) {
		shutdown(instance->remote_fd, SHUT_RDWR);
	}

	pthread_rwlock_t* rwlock = instance->rwlock;

	if(rwlock != NULL) {
		pthread_rwlock_wrlock(rwlock);
	}

	instance->masquerade4 = 0;
	instance->use_masquerade4 = false;
	memset(instance->masquerade6, 0, sizeof(instance->masquerade6));
	instance->use_masquerade6 = false;
	instance->router_ctx = NULL;

	instance->bytes_in = 0;
	instance->bytes_out = 0;

	LOGDIF(TUN_DEBUG, LOG_TAG, "Prepare to destroy java_VpnTunnel at %p", instance->j_vpn_tun);
	destroy_VpnTunnel(instance->j_vpn_tun);
	instance->j_vpn_tun = NULL;

	LOGDIF(TUN_DEBUG, LOG_TAG, "Prepare to destroy pcap_output_t at %p", instance->pcap_output);
	pcap_output_destroy(instance->pcap_output);
	instance->pcap_output = NULL;

	if(rwlock != NULL) {
		pthread_rwlock_unlock(rwlock);
		pthread_rwlock_destroy(rwlock);
		free(rwlock);
	}

	instance->rwlock = NULL;

#if TUN_DEBUG
	uint64_t pattern = (((uint64_t) ULOWER32(instance)) << 32) | ULOWER32(instance->__ref_generation);
	LOGDIF(TUN_DEBUG, LOG_TAG, "Using pattern 0x%016llx for erasing memory (ptr=%p, gen=0x%x)", pattern, instance, instance->__ref_generation);
	memset64(instance, pattern, sizeof(struct tun_ctx_private_t));
#else
	memset(instance, 0, sizeof(struct tun_ctx_private_t));
#endif

	free(instance);

	LOGDIF(TUN_DEBUG, LOG_TAG, "End of destroying tun_ctx_t at %p", ptr);
}

static ssize_t tun_ctx_send(tun_ctx_t* tun_ctx, uint8_t* buff, int len) {
	if(tun_ctx == NULL) {
		errno = EBADF;
		return -1;
	}
	struct tun_ctx_private_t* ctx = (struct tun_ctx_private_t*) tun_ctx;

	//start capture
	pcap_output_write(ctx->pcap_output, buff, 0, len);
	//end capture

	ctx->bytes_out += len;

	return write(ctx->local_fd, buff, len);
}

static ssize_t tun_ctx_recv(tun_ctx_t* tun_ctx, uint8_t* buff, int len) {
	if(tun_ctx == NULL) {
		errno = EBADF;
		return -1;
	}

	struct tun_ctx_private_t* ctx = (struct tun_ctx_private_t*) tun_ctx;

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
	tun_ctx_t* dev_tun_ctx = ctx->router_ctx->dev_tun_ctx;
	dev_tun_ctx->masqueradeDst(dev_tun_ctx, buff, res);

	res = dev_tun_ctx->send(dev_tun_ctx, buff, res);
	if(res < 0) {
		LOGE(LOG_TAG, "Error while writing to a /dev/net/tun socket (fd=%d)",
				dev_tun_ctx->getLocalFd(dev_tun_ctx));
		LOGE(LOG_TAG, "Packet dump (buff=%p, len=%d)", buff, res);
		log_dump_packet(LOG_TAG, buff, len);
	}
	pthread_rwlock_unlock(ctx->router_ctx->rwlock4);
	return res;
}

static void set_router_context(tun_ctx_t* instance, router_ctx_t* router_ctx) {
	TRACEPRINT("(instance=%p, ctx=%p)", instance, router_ctx);
	if(instance == NULL) {
		return;
	}

	struct tun_ctx_private_t* ctx = (struct tun_ctx_private_t*) instance;
	if(ctx->router_ctx != NULL && ctx->router_ctx != router_ctx) {
		LOGW(LOG_TAG, "Potential memory leak while setting router context (%p rewrites %p)", router_ctx, ctx->router_ctx);
	}

	ctx->router_ctx = router_ctx;
}

static void set_java_vpn_tunnel(tun_ctx_t* tun_ctx, jobject object) {
	TRACEPRINT("(tun_ctx=%p, jobject=%p)", tun_ctx, object);
	if(tun_ctx == NULL) {
		return;
	}

	struct tun_ctx_private_t* ctx = (struct tun_ctx_private_t*) tun_ctx;
	ctx->j_vpn_tun = wrap_into_VpnTunnel(object);
}

static int get_local_fd(tun_ctx_t* tun_ctx) {
	TRACEPRINT("(tun_ctx=%p)", tun_ctx);
	if(tun_ctx == NULL) {
		return UNDEFINED_FD;
	}

	struct tun_ctx_private_t* ctx = (struct tun_ctx_private_t*) tun_ctx;
	return ctx->local_fd;
}

static int get_remote_fd(tun_ctx_t* tun_ctx) {
	TRACEPRINT("(tun_ctx=%p)", tun_ctx);
	if(tun_ctx == NULL) {
		return UNDEFINED_FD;
	}

	struct tun_ctx_private_t* ctx = (struct tun_ctx_private_t*) tun_ctx;
	return ctx->remote_fd;
}

static void set_masquerade4_mode(tun_ctx_t* tun_ctx, bool mode) {
	TRACEPRINT("(tun_ctx=%p, mode=%s)", tun_ctx, mode ? "on" : "off");
	if(tun_ctx == NULL) {
		return;
	}

	struct tun_ctx_private_t* ctx = (struct tun_ctx_private_t*) tun_ctx;
	ctx->use_masquerade4 = mode;
}

static void set_masquerade4_ip(tun_ctx_t* tun_ctx, uint32_t ip) {
	TRACEPRINT("(tun_ctx=%p, ip=0x%08x)", tun_ctx, ip);
	if(tun_ctx == NULL) {
		return;
	}

	struct tun_ctx_private_t* ctx = (struct tun_ctx_private_t*) tun_ctx;
	ctx->masquerade4 = ip;
}

static void set_masquerade6_mode(tun_ctx_t* tun_ctx, bool mode) {
	if(tun_ctx == NULL) {
		return;
	}

	struct tun_ctx_private_t* ctx = (struct tun_ctx_private_t*) tun_ctx;
	ctx->use_masquerade6 = mode;
}

static void set_masquerade6_ip(tun_ctx_t* tun_ctx, uint8_t* ip) {
	if(tun_ctx == NULL) {
		return;
	}

	struct tun_ctx_private_t* ctx = (struct tun_ctx_private_t*) tun_ctx;
	memcpy(ctx->masquerade6, ip, 16);
}

static void masquerade_src(tun_ctx_t* tun_ctx, uint8_t* buff, int len) {
	if(tun_ctx == NULL) {
		return;
	}

	ocpa_ip_packet_t packet;
	memset(&packet, 0, sizeof(packet));
	packet.buff = buff;
	packet.buff_len = len;
	packet.pkt_len = len;
	ip_parse_packet(&packet);


	struct tun_ctx_private_t* ctx = (struct tun_ctx_private_t*) tun_ctx;

	if(packet.ipver == 4 && ctx->use_masquerade4) {
		packet.ip_header.v4->saddr = htonl(ctx->masquerade4);
		ip4_calc_ip_checksum(buff, len);

		switch(packet.payload_proto) {
		case IPPROTO_TCP: {
			ip4_calc_tcp_checksum(buff, len);
			break;
		}
		case IPPROTO_UDP: {
			ip4_calc_udp_checksum(buff, len);
			break;
		}
		default: {
			LOGE(LOG_TAG, "Can't calculate checksum for protocol %d", packet.payload_proto);
			break;
		}
		}
	} else if(packet.ipver == 6 && ctx->use_masquerade6) {
		memcpy(packet.ip_header.v6->ip6_src.s6_addr, ctx->masquerade6, 16);

		switch(packet.payload_proto) {
		case IPPROTO_TCP: {
			uint16_t common_sum = ip6_calc_common_pseudoheader_sum(buff, len);
			ip6_calc_tcp_checksum(common_sum, buff + packet.payload_offs, packet.payload_len);
			break;
		}
		case IPPROTO_UDP: {
			uint16_t common_sum = ip6_calc_common_pseudoheader_sum(buff, len);
			ip6_calc_udp_checksum(common_sum, buff + packet.payload_offs, packet.payload_len);
			break;
		}
		default: {
			LOGE(LOG_TAG, "Can't calculate checksum for protocol %d", packet.payload_proto);
			break;
		}
		}
	}
}

static void masquerade_dst(tun_ctx_t* tun_ctx, uint8_t* buff, int len) {
	if(tun_ctx == NULL) {
		return;
	}

	struct tun_ctx_private_t* ctx = (struct tun_ctx_private_t*) tun_ctx;

	ocpa_ip_packet_t packet;
	memset(&packet, 0, sizeof(packet));
	packet.buff = buff;
	packet.buff_len = len;
	packet.pkt_len = len;
	ip_parse_packet(&packet);

	if(packet.ipver == 4 && ctx->use_masquerade4) {
		packet.ip_header.v4->daddr = htonl(ctx->masquerade4);
		ip4_calc_ip_checksum(buff, len);

		switch(packet.payload_proto) {
		case IPPROTO_TCP: {
			ip4_calc_tcp_checksum(buff, len);
			break;
		}
		case IPPROTO_UDP: {
			ip4_calc_udp_checksum(buff, len);
			break;
		}
		default: {
			LOGE(LOG_TAG, "Can't calculate checksum for protocol %d", packet.payload_proto);
			break;
		}
		}
	} else if(packet.ipver == 6 && ctx->use_masquerade6) {
		memcpy(packet.ip_header.v6->ip6_dst.s6_addr, ctx->masquerade6, 16);

		switch(packet.payload_proto) {
		case IPPROTO_TCP: {
			uint16_t common_sum = ip6_calc_common_pseudoheader_sum(buff, len);
			ip6_calc_tcp_checksum(common_sum, buff + packet.payload_offs, packet.payload_len);
			break;
		}
		case IPPROTO_UDP: {
			uint16_t common_sum = ip6_calc_common_pseudoheader_sum(buff, len);
			ip6_calc_udp_checksum(common_sum, buff + packet.payload_offs, packet.payload_len);
			break;
		}
		default: {
			LOGE(LOG_TAG, "Can't calculate checksum for protocol %d", packet.payload_proto);
			break;
		}
		}
	}
}

static void debug_restart_pcap(tun_ctx_t* tun_ctx, jobject object) {
	TRACEPRINT("(tun_ctx=%p, jobject=%p)", tun_ctx, object);
	struct tun_ctx_private_t* instance = (struct tun_ctx_private_t*) tun_ctx;

	if(instance->pcap_output != NULL) {
		pcap_output_reset(instance->pcap_output, object);
	} else {
		instance->pcap_output = pcap_output_init(object);
	}
}

static void debug_stop_pcap(tun_ctx_t* tun_ctx) {
	TRACEPRINT("(tun_ctx=%p)", tun_ctx);
	struct tun_ctx_private_t* instance = (struct tun_ctx_private_t*) tun_ctx;

	if(instance->pcap_output != NULL) {
		pcap_output_flush(instance->pcap_output);
		pcap_output_close(instance->pcap_output);
		pcap_output_destroy(instance->pcap_output);
		instance->pcap_output = NULL;
	}
}

tun_ctx_t* create_tun_ctx(tun_ctx_t* ptr, ssize_t len) {
	TRACEPRINT("(ptr=%p, len=%d)", ptr, len);
	struct tun_ctx_private_t* instance = (struct tun_ctx_private_t*) ptr;
	ssize_t instance_size = len;
	if(ptr == NULL || len < sizeof(struct tun_ctx_private_t)) {
		instance_size = sizeof(struct tun_ctx_private_t);
		instance = malloc(instance_size);
		if(instance == NULL) {
			return NULL;
		}
	}

	memset(instance, 0, instance_size);

	REFS_INIT(tun_ctx_t, instance)
	ref_get_tun_ctx_t(&instance->public); //increment reference count for object itself
	instance->public.destroy = destroy_tun_ctx;
	instance->public.send = tun_ctx_send;
	instance->public.recv = tun_ctx_recv;
	instance->public.masqueradeSrc = masquerade_src;
	instance->public.masqueradeDst = masquerade_dst;
	instance->public.getLocalFd = get_local_fd;
	instance->public.getRemoteFd = get_remote_fd;
	instance->public.setMasquerade4Mode = set_masquerade4_mode;
	instance->public.setMasquerade4Ip = set_masquerade4_ip;
	instance->public.setMasquerade6Mode = set_masquerade6_mode;
	instance->public.setMasquerade6Ip = set_masquerade6_ip;
	instance->public.setRouterContext = set_router_context;
	instance->public.setJavaVpnTunnel = set_java_vpn_tunnel;
	instance->public.debugRestartPcap = debug_restart_pcap;
	instance->public.debugStopPcap = debug_stop_pcap;

	pthread_rwlock_t* rwlock = malloc(sizeof(pthread_rwlock_t));
	if(rwlock == NULL) {
		return instance->public.ref_put((tun_ctx_t*) instance);
	}
	if(pthread_rwlock_init(rwlock, NULL) != 0) {
		free(rwlock);
		return instance->public.ref_put((tun_ctx_t*) instance);
	}

	instance->rwlock = rwlock;
	instance->local_fd = UNDEFINED_FD;
	instance->remote_fd = UNDEFINED_FD;
	instance->masquerade4 = 0;
	memset(instance->masquerade6, 0, sizeof(instance->masquerade6));
	instance->use_masquerade4 = false;
	instance->use_masquerade6 = false;
	instance->router_ctx = NULL;

	instance->bytes_in = 0;
	instance->bytes_out = 0;

	instance->j_vpn_tun = NULL;

	instance->pcap_output = NULL;

	return &instance->public;
}

