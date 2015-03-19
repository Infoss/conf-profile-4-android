/*
 * tun_usernat.c
 *
 *      Author: Dmitry Vorobiev
 */

#include <stdlib.h>
#include <sys/socket.h>
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>

#include "android_log_utils.h"
#include "android_jni.h"
#include "tun_usernat.h"
#include "tun_private.h"
#include "protoheaders.h"
#include "sockaddrs.h"
#include "router.h"

#define LOG_TAG "tun_usernat.c"

struct usernat_tun_ctx_private_t {
	union {
		tun_ctx_t public;
		struct tun_ctx_private_t tun_ctx;
	};

	void (*original_destroy)(tun_ctx_t* instance);

	uint32_t local4;
	uint8_t local6[16];
	uint32_t remote4;
	uint8_t remote6[16];

	nat_link_t* links;

	java_UsernatTunnel* j_usernat_tun;
};

static nat_link_t* link_init();
static void link_deinit(nat_link_t* link);
static nat_link_t* find_link(struct usernat_tun_ctx_private_t* instance, ocpa_ip_packet_t* packet, bool* is_incoming);
static nat_link_t* find_link4(nat_link_t* root_link, uint8_t local_link_type, uint32_t local_addr, uint16_t local_port, uint32_t remote_addr, uint16_t remote_port, bool* is_incoming);
static nat_link_t* create_link(usernat_tun_ctx_t* ctx, ocpa_ip_packet_t* packet, uint8_t local_link_type);
static nat_link_t* create_link_tcp4(struct usernat_tun_ctx_private_t* instance, ocpa_ip_packet_t* packet);
static nat_link_t* create_link_udp4(struct usernat_tun_ctx_private_t* instance, ocpa_ip_packet_t* packet);

static int create_socket(struct usernat_tun_ctx_private_t* instance, int family, int socktype, int protocol, bool protect) {
	int sock = socket(family, socktype, protocol);
	if(sock == -1) {
		LOGD(LOG_TAG, "create_socket(): can't create socket");
		return sock;
	}

	if(protect) {
		LOGD(LOG_TAG, "create_socket(): protecting socket %d", sock);
		bool is_protected = instance->tun_ctx.j_vpn_tun->protectSocket(
				instance->tun_ctx.j_vpn_tun,
				sock
				);

		if(!is_protected) {
			LOGE(LOG_TAG, "create_socket(): can't protect socket");
			close(sock);
			errno = EBADF;
			return -1;
		}
	}

	return sock;
}

static void destroy_usernat_tun_ctx(usernat_tun_ctx_t* ctx) {
	if(ctx == NULL) {
		return;
	}

	struct usernat_tun_ctx_private_t* instance = (struct usernat_tun_ctx_private_t*) ctx;

	pthread_rwlock_t* rwlock = instance->tun_ctx.rwlock;

	if(rwlock != NULL) {
		pthread_rwlock_wrlock(rwlock);
	}

	destroy_UsernatTunnel(instance->j_usernat_tun);
	instance->j_usernat_tun = NULL;
	if(rwlock != NULL) {
		pthread_rwlock_unlock(rwlock);
	}

	instance->original_destroy(&instance->public);
}

static ssize_t usernat_tun_ctx_send(tun_ctx_t* ctx, uint8_t* buff, int len) {
	if(ctx == NULL) {
		errno = EBADF;
		return -1;
	}

	//we received this packet from TUN device
	struct usernat_tun_ctx_private_t* instance = (struct usernat_tun_ctx_private_t*) ctx;
	ocpa_ip_packet_t packet;
	memset(&packet, 0, sizeof(packet));
	packet.buff = buff;
	packet.buff_len = len;
	packet.pkt_len = len;
	ip_parse_packet(&packet);

	bool is_incoming = false;
	int i = 0;

	nat_link_t* link = find_link(instance, &packet, &is_incoming);
	if(link == NULL) {
		//this packet is invalid, drop it
		LOGD(LOG_TAG, "can't find appropriate link, dropping a packet");
		return 0;
	}

	pthread_rwlock_rdlock(instance->tun_ctx.rwlock);
	if(packet.ipver == 4) {
		if(is_incoming) {
			//incoming usernat packet
			packet.ip_header.v4->saddr = htonl(link->ip4.real_dst_addr);
			packet.ip_header.v4->daddr = htonl(instance->local4);
			packet.payload_header.tcp->source = htons(link->ip4.real_dst_port);

			for(i = 0; i < 4; i++) {
				int remoteDnsIp = htonl(instance->tun_ctx.dns_ip4[i]);
				int remoteVirtualIp = htonl(instance->tun_ctx.virtual_dns_ip4[i]);
				if(remoteDnsIp != 0 && remoteVirtualIp != 0 && remoteDnsIp == packet.ip_header.v4->saddr) {
					packet.ip_header.v4->saddr = remoteVirtualIp;
					break;
				}
			}
		} else {
			//outgoing usernat packet
			packet.ip_header.v4->saddr = htonl(instance->remote4);
			packet.ip_header.v4->daddr = htonl(instance->local4);
			packet.payload_header.tcp->dest = htons(link->common.hop_port);
		}

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
	} else if(packet.ipver == 6) {
		LOGD(LOG_TAG, "usernat_tun_send(): IPv6 packet, no action here");
	}

	//TODO: calculate traffic
	//ctx->router_ctx->dev_tun_ctx.bytes_out += res;

	//start capture
	pcap_output_write(instance->tun_ctx.pcap_output, buff, 0, len);
	//end capture

	if(link->common.enqueue_packets) {
		LOGD(LOG_TAG, "Trying to enqueue packet...");
		uint8_t* new_buff = malloc(len);
		queue_link* ql = queue_link_init();
		if(new_buff != NULL && ql != NULL) {
			ql->buff = new_buff;
			ql->size = len;
			memcpy(ql->buff, buff, len);
			if(queue_put(link->common.packets_queue, ql)) {
				LOGD(LOG_TAG, "..success");
			} else {
				LOGD(LOG_TAG, "..failed (invalig queue %p or link %p)", link->common.packets_queue, ql);
			}
			buff = NULL;
		} else {
			LOGD(LOG_TAG, "..failed (not enough memory)");
			if(new_buff != NULL) {
				free(new_buff);
			}

			queue_link_deinit(ql);
			new_buff = NULL;
			ql = NULL;
		}
	}
	pthread_rwlock_unlock(instance->tun_ctx.rwlock);

	if(buff == NULL) {
		//packet was enqueued
		LOGD(LOG_TAG, "Trying to enqueue packet");
		return 0;
	}

	tun_ctx_t* dev_tun_ctx = instance->tun_ctx.router_ctx->dev_tun_ctx;
	len = dev_tun_ctx->send(dev_tun_ctx, buff, len);

	return len;
}

static ssize_t usernat_tun_ctx_recv(tun_ctx_t* tun_ctx, uint8_t* buff, int len) {
	//this function should never be called
	LOGE(LOG_TAG, "usernat_tun_recv() called");
	errno = EBADF;
	return -1;
}

usernat_tun_ctx_t* create_usernat_tun_ctx(usernat_tun_ctx_t* ptr, ssize_t len, jobject jtun_instance) {
	struct usernat_tun_ctx_private_t* instance = (struct usernat_tun_ctx_private_t*) ptr;
	ssize_t instance_size = len;
	if(ptr == NULL || len < sizeof(struct usernat_tun_ctx_private_t)) {
		instance_size = sizeof(struct usernat_tun_ctx_private_t);
		instance = malloc(instance_size);
		if(instance == NULL) {
			return NULL;
		}
	}

	memset(instance, 0, instance_size);

	tun_ctx_t* result = create_tun_ctx(&instance->public, instance_size);
	if(result == NULL) {
		return NULL;
	}

	instance->original_destroy = instance->public.destroy;

	//setting functions
	instance->public.destroy = destroy_usernat_tun_ctx;
	instance->public.send = usernat_tun_ctx_send;
	instance->public.recv = usernat_tun_ctx_recv;

	instance->local4 = 0;
	memset(instance->local6, 0, sizeof(instance->local6));
	instance->remote4 = 0;
	memset(instance->remote6, 0, sizeof(instance->remote6));

	instance->j_usernat_tun = wrap_into_UsernatTunnel(jtun_instance);

	instance->local4 = instance->j_usernat_tun->getLocalAddress4(instance->j_usernat_tun);
	instance->remote4 = instance->j_usernat_tun->getRemoteAddress4(instance->j_usernat_tun);

	return &instance->public;
}



void usernat_set_pid_for_link(intptr_t tun_ctx, intptr_t link_ptr, int pid) {
	if(tun_ctx == (intptr_t) NULL || link_ptr == (intptr_t) NULL) {
		return;
	}

	LOGD(LOG_TAG, "Pid=%d for link %p", pid, (nat_link_t*) link_ptr);

	usernat_tun_ctx_t* ctx = (usernat_tun_ctx_t*) tun_ctx;
	struct usernat_tun_ctx_private_t* instance = (struct usernat_tun_ctx_private_t*) ctx;
	nat_link_t* link = (nat_link_t*) link_ptr;
	queue* q = NULL;

	//first, try to find a link
	pthread_rwlock_wrlock(instance->tun_ctx.rwlock);
	nat_link_t* curr_link = instance->links;
	nat_link_t* prev_link = NULL;

	while(curr_link != NULL) {

		//decrease cycles_to_live to links with pid == -1
		if(curr_link->common.cycles_to_live > 0 && curr_link->common.socat_pid == -1) {
			curr_link->common.cycles_to_live--;
		}

		//remove links with pid == -1 and cycles_to_live == 0
		if(curr_link->common.cycles_to_live == 0 && curr_link->common.socat_pid == -1) {
			nat_link_t* tmp_link = curr_link;
			curr_link = curr_link->common.next;

			//if prev_link is NULL, it means that we're at the 1st element of list
			if(prev_link == NULL) {
				instance->links = curr_link;
			} else {
				prev_link->common.next = curr_link;
			}

			LOGE(LOG_TAG, "Deleting link %p", tmp_link);

			link_deinit(tmp_link);

			continue;
		}

		if(curr_link == link) {
			break;
		}

		prev_link = curr_link;
		curr_link = curr_link->common.next;
	}

	if(curr_link == NULL) {
		//no such link
		LOGE(LOG_TAG, "Can't find link at %p for pid %d", link, pid);
	} else {
		//we found a link
		LOGE(LOG_TAG, "Setting pid %d for link %p (queue=%p)", pid, link, curr_link->common.packets_queue);
		curr_link->common.socat_pid = pid;
		curr_link->common.enqueue_packets = false;
		q = curr_link->common.packets_queue;
		curr_link->common.packets_queue = NULL;

		if(pid == -1) {
			curr_link->common.cycles_to_live = 100;
		}
	}

	pthread_rwlock_unlock(instance->tun_ctx.rwlock);

	if(q != NULL) {
		LOGE(LOG_TAG, "Ready to write enqueued packets");
		tun_ctx_t* dev_tun_ctx = instance->tun_ctx.router_ctx->dev_tun_ctx;
		queue_link* ql;
		while((ql = queue_get(q)) != NULL) {
			dev_tun_ctx->send(dev_tun_ctx, ql->buff, ql->size);
			queue_link_deinit(ql);
		}
		queue_deinit(q);
		q = NULL;
	}
}

static nat_link_t* link_init() {
	nat_link_t* result = malloc(sizeof(nat_link_t));
	if(result != NULL) {
		memset(result, 0, sizeof(nat_link_t));
		result->common.enqueue_packets = true;
		result->common.packets_queue = queue_init();
	}

	return result;
}

static void link_deinit(nat_link_t* link) {
	if(link == NULL) {
		return;
	}

	queue_deinit(link->common.packets_queue);

	memset(link, 0, sizeof(nat_link_t));
	free(link);
}

static nat_link_t* find_link(struct usernat_tun_ctx_private_t* instance, ocpa_ip_packet_t* packet, bool* is_incoming) {
	if(instance == NULL || packet == NULL || packet->ip_header.raw == NULL) {
		return NULL;
	}

	nat_link_t* result = NULL;

	uint16_t local_port = 0;
	uint16_t remote_port = 0;


	if(packet->ipver == 4) {
		uint32_t remote_addr = 0;
		pthread_rwlock_rdlock(instance->tun_ctx.rwlock);
		if(ip4_addr_eq(instance->remote4, ntohl(packet->ip_header.v4->daddr))) {
			//incoming usernat packet
			(*is_incoming) = true;
			local_port = packet->dst_port;
			remote_addr = instance->remote4;
			remote_port = packet->src_port;
		} else if(ip4_addr_eq(instance->local4, ntohl(packet->ip_header.v4->saddr))) {
			//outgoing usernat packet
			//handle virtual dns ip
			int i = 0;
			for(i = 0; i < 4; i++) {
				int remoteDnsIp = htonl(instance->tun_ctx.dns_ip4[i]);
				int remoteVirtualIp = htonl(instance->tun_ctx.virtual_dns_ip4[i]);
				if(remoteDnsIp != 0 && remoteVirtualIp != 0 && remoteVirtualIp == packet->ip_header.v4->daddr) {
					packet->ip_header.v4->daddr = remoteDnsIp;
					break;
				}
			}

			(*is_incoming) = false;
			local_port = packet->src_port;
			remote_addr = ntohl(packet->ip_header.v4->daddr);
			remote_port = packet->dst_port;
		} else {
			//stray packet
			LOGD(LOG_TAG, "find_link(): stray packet");
			pthread_rwlock_unlock(instance->tun_ctx.rwlock);
			return NULL;
		}
		pthread_rwlock_unlock(instance->tun_ctx.rwlock);

		nat_link_t* root_link = NULL;
		uint8_t local_link_type;
		if(packet->payload_proto == IPPROTO_TCP) {
			local_link_type = NAT_LINK_TCP4;
		} else if(packet->payload_proto == IPPROTO_UDP) {
			local_link_type = NAT_LINK_UDP4;
		} else {
			LOGE(LOG_TAG, "find_link(): unsupported payload_proto=%d", packet->payload_proto);
			return NULL;
		}


		pthread_rwlock_rdlock(instance->tun_ctx.rwlock);
		root_link = instance->links;
		result = find_link4(root_link, local_link_type, instance->local4, local_port, remote_addr, remote_port, is_incoming);
		pthread_rwlock_unlock(instance->tun_ctx.rwlock);

		if(result == NULL) {
			if((*is_incoming)) {
				//drop incoming packet if link doesn't exist
				//this is a NAT
				LOGD(LOG_TAG, "find_link(): drop due to NAT");
				return NULL;
			}

			result = create_link(instance, packet, local_link_type);
			if(result == NULL) {
				LOGE(LOG_TAG, "find_link(): can't create link, dropping a packet");
				return NULL;
			}

			sockaddr_uni tmp_sa;

			//adding a link
			pthread_rwlock_wrlock(instance->tun_ctx.rwlock);
			result->common.next = instance->links;
			instance->links = result;
			pthread_rwlock_unlock(instance->tun_ctx.rwlock);

			tmp_sa.in.sin_addr.s_addr = htonl(result->ip4.real_dst_addr);

			LOGD(LOG_TAG, "find_link(): call buildSocatTunnel()");
			pthread_rwlock_rdlock(instance->tun_ctx.rwlock);
			result->common.socat_pid = instance->j_usernat_tun->buildSocatTunnel(
					instance->j_usernat_tun,
					result->common.sock_accept,
					result->common.sock_connect,
					inet_ntoa(tmp_sa.in.sin_addr),
					result->ip4.real_dst_port,
					(int64_t) (intptr_t) result
					);
			pthread_rwlock_unlock(instance->tun_ctx.rwlock);

			if(result->common.socat_pid == -1) {
				LOGE(LOG_TAG, "Error while creating socat tunnel");
				close(result->common.sock_accept);
				usernat_set_pid_for_link((intptr_t) instance, (intptr_t) result, -1);
				result = NULL;
				//we don't free result here due to setting  cycles_to_live in usernat_set_pid_for_link()
			}


		}

	} else if(packet->ipver == 6) {
		//TODO: implement IPv6
		LOGD(LOG_TAG, "find_link(): 15");
	} else {
		LOGE(LOG_TAG, "Invalid IP version (supported versions: IPv4, IPv6)");
	}

	return result;
}

static nat_link_t* find_link4(nat_link_t* root_link, uint8_t local_link_type, uint32_t local_addr, uint16_t local_port, uint32_t remote_addr, uint16_t remote_port, bool* is_incoming) {
	if(root_link == NULL) {
		return NULL;
	}

	nat_link_t* curr_link = root_link;
	while(curr_link != NULL) {
		LOGD(LOG_TAG, "find_link(): trying link %p", curr_link);
		if((curr_link->common.link_type & NAT_LINK_IP6) != 0) {
			//this is IPv6 link, skipping
			curr_link = curr_link->common.next;
			continue;
		}

		if(curr_link->common.link_type != local_link_type) {
			//this is IPv4 link, but protocol doesn't match
			curr_link = curr_link->common.next;
			continue;
		}

		if(!ip4_addr_eq(curr_link->ip4.real_src_addr, local_addr) ||
				local_port != curr_link->ip4.real_src_port) {
			//local address doesn't match
			curr_link = curr_link->common.next;
			continue;
		}

		if((*is_incoming)) {
			//incoming packet
			struct usernat_tun_ctx_private_t* curr_usernat =
					(struct usernat_tun_ctx_private_t*) curr_link->common.usernat_ctx;
			if(!(ip4_addr_eq(curr_usernat->remote4, remote_addr) &&
					remote_port == curr_link->common.hop_port)) {
				//remote address doesn't match
				curr_link = curr_link->common.next;
				continue;
			}

			//we'll exit this loop by the following break
		} else {
			//outgoing packet
			if(!(ip4_addr_eq(curr_link->ip4.real_dst_addr, remote_addr) &&
					remote_port == curr_link->ip4.real_dst_port)) {
				//remote address doesn't match
				curr_link = curr_link->common.next;
				continue;
			}

			//we'll exit this loop by the following break
		}

		break;
	}

	return curr_link;
}



static nat_link_t* create_link(usernat_tun_ctx_t* ctx, ocpa_ip_packet_t* packet, uint8_t local_link_type) {
	nat_link_t* result = NULL;

	switch(local_link_type) {
	case NAT_LINK_TCP4: {
		result = create_link_tcp4(ctx, packet);
		break;
	}
	case NAT_LINK_UDP4: {
		result = create_link_udp4(ctx, packet);
		break;
	}
	default: {
		//unsupported link type, should never be happen
		LOGE(LOG_TAG, "Unsupported link type %d", local_link_type);
		break;
	}
	}

	if(result != NULL) {
		result->common.usernat_ctx = ctx;
		result->common.link_type = local_link_type;
	}

	return result;
}

static nat_link_t* create_link_tcp4(struct usernat_tun_ctx_private_t* instance, ocpa_ip_packet_t* packet) {
	if(packet->payload_header.tcp->syn == 0) {
		//create a link on SYN packet only
		LOGD(LOG_TAG, "create_link_tcp4(): not a SYN");
		return NULL;
	}

	LOGD(LOG_TAG, "create_link_tcp4(): %08x:%d->%08x:%d",
			packet->ip_header.v4->saddr,
			ntohs(packet->payload_header.tcp->source),
			packet->ip_header.v4->daddr,
			ntohs(packet->payload_header.tcp->dest));

	sockaddr_uni tmp_sa;

	//preparing sock_accept
	int sock_accept = create_socket(instance, AF_INET, SOCK_STREAM, IPPROTO_TCP, false);
	if(sock_accept == -1) {
		LOGD(LOG_TAG, "create_link_tcp4(): can't create sock_accept %d: %s", errno, strerror(errno));
		return NULL;
	}

	memset(&tmp_sa.sa, 0, sizeof(tmp_sa));
	tmp_sa.sa.sa_family = AF_INET;
	tmp_sa.in.sin_addr.s_addr = htonl(instance->local4);
	if(bind(sock_accept, &tmp_sa.sa, sizeof(tmp_sa)) == -1) {
		LOGD(LOG_TAG, "create_link_tcp4(): can't bind() sock_accept %d: %s", errno, strerror(errno));
		return NULL;
	}

	int tmp_sa_size = sizeof(tmp_sa);
	if(getsockname(sock_accept, &tmp_sa.sa, &tmp_sa_size) == -1) {
		LOGD(LOG_TAG, "create_link_tcp4(): can't getsockname() sock_accept %d: %s", errno, strerror(errno));
		return NULL;
	}

	if(listen(sock_accept, 5) == -1) {
		LOGD(LOG_TAG, "create_link_tcp4(): can't listen() sock_accept %d: %s", errno, strerror(errno));
		return NULL;
	}

	//preparing_sock_connect
	int sock_connect = create_socket(instance, AF_INET, SOCK_STREAM, IPPROTO_TCP, true);
	if(sock_connect == -1) {
		LOGD(LOG_TAG, "create_link_tcp4(): can't create sock_connect %d: %s", errno, strerror(errno));
		close(sock_accept);
		return NULL;
	}

	sockaddr_uni tmp_sa2;
	tmp_sa_size = sizeof(tmp_sa2);
	tmp_sa2.in.sin_family = AF_INET;
	tmp_sa2.in.sin_addr.s_addr = packet->ip_header.v4->daddr;
	tmp_sa2.in.sin_port = packet->payload_header.tcp->dest;
	if(connect(sock_connect, &tmp_sa2.sa, tmp_sa_size) == -1) {
		LOGE(LOG_TAG, "create_link_tcp4(): can't connect sock_connect %d: %s", errno, strerror(errno));
	}

	nat_link_t* result = link_init();

	if(result != NULL) {
		result->ip4.real_src_addr = ntohl(packet->ip_header.v4->saddr);
		result->ip4.real_src_port = ntohs(packet->payload_header.tcp->source);
		result->ip4.real_dst_addr = ntohl(packet->ip_header.v4->daddr);
		result->ip4.real_dst_port = ntohs(packet->payload_header.tcp->dest);

		result->common.sock_accept = sock_accept;
		result->common.sock_connect = sock_connect;
		result->common.hop_port = ntohs(tmp_sa.in.sin_port);
		LOGD(LOG_TAG, "create_link_tcp4(): accept_sock is bound at %s:%d",
				inet_ntoa(tmp_sa.in.sin_addr),
				ntohs(tmp_sa.in.sin_port));
	}

	return result;
}

static nat_link_t* create_link_udp4(struct usernat_tun_ctx_private_t* instance, ocpa_ip_packet_t* packet) {
	sockaddr_uni tmp_sa;

	LOGD(LOG_TAG, "create_link_udp4(): %08x:%d->%08x:%d",
			packet->ip_header.v4->saddr,
			ntohs(packet->payload_header.udp->source),
			packet->ip_header.v4->daddr,
			ntohs(packet->payload_header.udp->dest));

	int sock_accept = create_socket(instance, AF_INET, SOCK_DGRAM, IPPROTO_UDP, false);
	if(sock_accept == -1) {
		LOGD(LOG_TAG, "create_link_udp4(): can't create sock_accept %d: %s", errno, strerror(errno));
		return NULL;
	}
	memset(&tmp_sa.sa, 0, sizeof(tmp_sa));
	tmp_sa.sa.sa_family = AF_INET;
	tmp_sa.in.sin_addr.s_addr = htonl(instance->local4);
	if(bind(sock_accept, &tmp_sa.sa, sizeof(tmp_sa)) == -1) {
		LOGD(LOG_TAG, "create_link_udp4(): can't bind sock_accept %d: %s", errno, strerror(errno));
		close(sock_accept);
		return NULL;
	}

	int tmp_sa_size = sizeof(tmp_sa);
	if(getsockname(sock_accept, &tmp_sa.sa, &tmp_sa_size) == -1) {
		LOGD(LOG_TAG, "create_link_udp4(): can't getsockname() sock_accept %d: %s", errno, strerror(errno));
		close(sock_accept);
		return NULL;
	}

	int sock_connect = create_socket(instance, AF_INET, SOCK_DGRAM, IPPROTO_UDP, true);
	if(sock_connect == -1) {
		LOGD(LOG_TAG, "create_link_udp4(): can't create sock_connect %d: %s", errno, strerror(errno));
		close(sock_accept);
		return NULL;
	}

	sockaddr_uni tmp_sa2;
	tmp_sa_size = sizeof(tmp_sa2);

	tmp_sa2.in.sin_family = AF_INET;
	tmp_sa2.in.sin_addr.s_addr = htonl(instance->remote4);
	tmp_sa2.in.sin_port = packet->payload_header.tcp->source;
	if(connect(sock_accept, &tmp_sa2.sa, tmp_sa_size) == -1) {
		LOGE(LOG_TAG, "create_link_udp4(): can't connect sock_accept %d: %s", errno, strerror(errno));
	}

	tmp_sa2.in.sin_family = AF_INET;
	tmp_sa2.in.sin_addr.s_addr = packet->ip_header.v4->daddr;
	tmp_sa2.in.sin_port = packet->payload_header.tcp->dest;
	if(connect(sock_connect, &tmp_sa2.sa, tmp_sa_size) == -1) {
		LOGE(LOG_TAG, "create_link_udp4(): can't connect sock_connect %d: %s", errno, strerror(errno));
	}

	nat_link_t* result = link_init();

	if(result != NULL) {
		result->ip4.real_src_addr = ntohl(packet->ip_header.v4->saddr);
		result->ip4.real_src_port = ntohs(packet->payload_header.udp->source);
		result->ip4.real_dst_addr = ntohl(packet->ip_header.v4->daddr);
		result->ip4.real_dst_port = ntohs(packet->payload_header.udp->dest);

		result->common.sock_accept = sock_accept;
		result->common.sock_connect = sock_connect;
		result->common.hop_port = ntohs(tmp_sa.in.sin_port);
	}

	return result;
}

