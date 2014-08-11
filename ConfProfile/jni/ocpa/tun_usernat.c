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
#include "protoheaders.h"
#include "sockaddrs.h"
#include "router.h"

#define LOG_TAG "tun_usernat.c"

static nat_link_t* link_init();
static void link_deinit(nat_link_t* link);
static nat_link_t* find_link(usernat_tun_ctx_t* ctx, ocpa_ip_packet_t* packet, bool* is_incoming);
static nat_link_t* find_link4(nat_link_t* root_link, uint8_t local_link_type, uint32_t local_addr, uint16_t local_port, uint32_t remote_addr, uint16_t remote_port, bool* is_incoming);
static nat_link_t* create_link(usernat_tun_ctx_t* ctx, ocpa_ip_packet_t* packet, uint8_t local_link_type);
static nat_link_t* create_link_tcp4(usernat_tun_ctx_t* ctx, ocpa_ip_packet_t* packet);
static nat_link_t* create_link_udp4(usernat_tun_ctx_t* ctx, ocpa_ip_packet_t* packet);

usernat_tun_ctx_t* usernat_tun_init(jobject jtun_instance) {
	usernat_tun_ctx_t* ctx = malloc(sizeof(usernat_tun_ctx_t));
	if(ctx == NULL) {
		return NULL;
	}

	memset(ctx, 0, sizeof(usernat_tun_ctx_t));
	if(!common_tun_set((common_tun_ctx_t*) ctx, jtun_instance)) {
		memset(ctx, 0, sizeof(usernat_tun_ctx_t));
		free(ctx);
		return NULL;
	}

	pthread_rwlock_wrlock(ctx->common.rwlock);
	ctx->common.send_func = usernat_tun_send;
	ctx->common.recv_func = usernat_tun_recv;

	ctx->local4 = 0;
	memset(ctx->local6, 0, sizeof(ctx->local6));
	ctx->remote4 = 0;
	memset(ctx->remote6, 0, sizeof(ctx->remote6));

	ctx->j_usernat_tun = wrap_into_UsernatTunnel(jtun_instance);

	ctx->local4 = ctx->j_usernat_tun->getLocalAddress4(ctx->j_usernat_tun);
	ctx->remote4 = ctx->j_usernat_tun->getRemoteAddress4(ctx->j_usernat_tun);
	pthread_rwlock_unlock(ctx->common.rwlock);

	return ctx;
}

void usernat_tun_deinit(usernat_tun_ctx_t* ctx) {
	if(ctx == NULL) {
		return;
	}

	pthread_rwlock_wrlock(ctx->common.rwlock);
	common_tun_free((common_tun_ctx_t*) ctx);
	destroy_UsernatTunnel(ctx->j_usernat_tun);
	ctx->j_usernat_tun = NULL;
	pthread_rwlock_unlock(ctx->common.rwlock);

	free(ctx);
}

ssize_t usernat_tun_send(intptr_t tun_ctx, uint8_t* buff, int len) {
	if(tun_ctx == (intptr_t) NULL) {
		errno = EBADF;
		return -1;
	}

	//we received this packet from TUN device
	usernat_tun_ctx_t* ctx = (usernat_tun_ctx_t*) tun_ctx;
	ocpa_ip_packet_t packet;
	memset(&packet, 0, sizeof(packet));
	packet.buff = buff;
	packet.buff_len = len;
	packet.pkt_len = len;
	ip_parse_packet(&packet);

	bool is_incoming = false;

	nat_link_t* link = find_link(ctx, &packet, &is_incoming);
	if(link == NULL) {
		//this packet is invalid, drop it
		LOGD(LOG_TAG, "can't find appropriate link, dropping a packet");
		return 0;
	}

	pthread_rwlock_rdlock(ctx->common.rwlock);
	if(packet.ipver == 4) {
		if(is_incoming) {
			//incoming usernat packet
			packet.ip_header.v4->saddr = htonl(link->ip4.real_dst_addr);
			packet.ip_header.v4->daddr = htonl(ctx->local4);
			packet.payload_header.tcp->source = htons(link->ip4.real_dst_port);
		} else {
			//outgoing usernat packet
			packet.ip_header.v4->saddr = htonl(ctx->remote4);
			packet.ip_header.v4->daddr = htonl(ctx->local4);
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
	pcap_output_write(ctx->common.pcap_output, buff, 0, len);
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
	pthread_rwlock_unlock(ctx->common.rwlock);

	if(buff == NULL) {
		//packet was enqueued
		LOGD(LOG_TAG, "Trying to enqueue packet");
		return 0;
	}

	pthread_rwlock_rdlock(ctx->common.router_ctx->rwlock4);
	len = write(ctx->common.router_ctx->dev_tun_ctx.local_fd, buff, len);
	pthread_rwlock_unlock(ctx->common.router_ctx->rwlock4);

	return len;
}

ssize_t usernat_tun_recv(intptr_t tun_ctx, uint8_t* buff, int len) {
	//this function should never be called
	LOGE(LOG_TAG, "usernat_tun_recv() called");
	errno = EBADF;
	return -1;
}

void usernat_set_pid_for_link(intptr_t tun_ctx, intptr_t link_ptr, int pid) {
	if(tun_ctx == (intptr_t) NULL || link_ptr == (intptr_t) NULL) {
		return;
	}

	LOGD(LOG_TAG, "Pid=%d for link %p", pid, (nat_link_t*) link_ptr);

	usernat_tun_ctx_t* ctx = (usernat_tun_ctx_t*) tun_ctx;
	nat_link_t* link = (nat_link_t*) link_ptr;
	queue* q = NULL;

	//first, try to find a link
	pthread_rwlock_wrlock(ctx->common.rwlock);
	nat_link_t* curr_link = ctx->links;
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
				ctx->links = curr_link;
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

	pthread_rwlock_unlock(ctx->common.rwlock);

	if(q != NULL) {
		LOGE(LOG_TAG, "Ready to write enqueued packets");
		pthread_rwlock_rdlock(ctx->common.router_ctx->rwlock4);
		queue_link* ql;
		while((ql = queue_get(q)) != NULL) {
			write(ctx->common.router_ctx->dev_tun_ctx.local_fd, ql->buff, ql->size);
			queue_link_deinit(ql);
		}
		queue_deinit(q);
		q = NULL;
		pthread_rwlock_unlock(ctx->common.router_ctx->rwlock4);
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

static nat_link_t* find_link(usernat_tun_ctx_t* ctx, ocpa_ip_packet_t* packet, bool* is_incoming) {
	if(ctx == NULL || packet == NULL || packet->ip_header.raw == NULL) {
		return NULL;
	}

	nat_link_t* result = NULL;

	uint16_t local_port = 0;
	uint16_t remote_port = 0;


	if(packet->ipver == 4) {
		uint32_t remote_addr = 0;
		pthread_rwlock_rdlock(ctx->common.rwlock);
		if(ip4_addr_eq(ctx->remote4, ntohl(packet->ip_header.v4->daddr))) {
			//incoming usernat packet
			(*is_incoming) = true;
			local_port = packet->dst_port;
			remote_addr = ctx->remote4;
			remote_port = packet->src_port;
		} else if(ip4_addr_eq(ctx->local4, ntohl(packet->ip_header.v4->saddr))) {
			//outgoing usernat packet
			(*is_incoming) = false;
			local_port = packet->src_port;
			remote_addr = ntohl(packet->ip_header.v4->daddr);
			remote_port = packet->dst_port;
		} else {
			//stray packet
			LOGD(LOG_TAG, "find_link(): stray packet");
			pthread_rwlock_unlock(ctx->common.rwlock);
			return NULL;
		}
		pthread_rwlock_unlock(ctx->common.rwlock);

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


		pthread_rwlock_rdlock(ctx->common.rwlock);
		root_link = ctx->links;
		result = find_link4(root_link, local_link_type, ctx->local4, local_port, remote_addr, remote_port, is_incoming);
		pthread_rwlock_unlock(ctx->common.rwlock);

		if(result == NULL) {
			if((*is_incoming)) {
				//drop incoming packet if link doesn't exist
				//this is a NAT
				LOGD(LOG_TAG, "find_link(): drop due to NAT");
				return NULL;
			}

			result = create_link(ctx, packet, local_link_type);
			if(result == NULL) {
				LOGE(LOG_TAG, "find_link(): can't create link, dropping a packet");
				return NULL;
			}

			sockaddr_uni tmp_sa;

			//adding a link
			pthread_rwlock_wrlock(ctx->common.rwlock);
			result->common.next = ctx->links;
			ctx->links = result;
			pthread_rwlock_unlock(ctx->common.rwlock);

			tmp_sa.in.sin_addr.s_addr = htonl(result->ip4.real_dst_addr);

			LOGD(LOG_TAG, "find_link(): call buildSocatTunnel()");
			pthread_rwlock_rdlock(ctx->common.rwlock);
			result->common.socat_pid = ctx->j_usernat_tun->buildSocatTunnel(
					ctx->j_usernat_tun,
					result->common.sock_accept,
					result->common.sock_connect,
					inet_ntoa(tmp_sa.in.sin_addr),
					result->ip4.real_dst_port,
					(int64_t) (intptr_t) result
					);
			pthread_rwlock_unlock(ctx->common.rwlock);

			if(result->common.socat_pid == -1) {
				LOGE(LOG_TAG, "Error while creating socat tunnel");
				close(result->common.sock_accept);
				usernat_set_pid_for_link((intptr_t) ctx, (intptr_t) result, -1);
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
			if(!(ip4_addr_eq(curr_link->common.usernat_ctx->remote4, remote_addr) &&
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

static int create_socket(usernat_tun_ctx_t* ctx, int family, int socktype, int protocol, bool protect) {
	int sock = socket(family, socktype, protocol);
	if(sock == -1) {
		LOGD(LOG_TAG, "create_socket(): can't create socket");
		return sock;
	}

	if(protect) {
		LOGD(LOG_TAG, "create_socket(): protecting socket %d", sock);
		pthread_rwlock_rdlock(ctx->common.rwlock);
		bool is_protected = ctx->common.j_vpn_tun->protectSocket(
				ctx->common.j_vpn_tun,
				sock
				);
		pthread_rwlock_unlock(ctx->common.rwlock);

		if(!is_protected) {
			LOGE(LOG_TAG, "create_socket(): can't protect socket");
			close(sock);
			errno = EBADF;
			return -1;
		}
	}

	return sock;
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

static nat_link_t* create_link_tcp4(usernat_tun_ctx_t* ctx, ocpa_ip_packet_t* packet) {
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
	//int sock_accept = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
	int sock_accept = create_socket(ctx, AF_INET, SOCK_STREAM, IPPROTO_TCP, false);
	if(sock_accept == -1) {
		LOGD(LOG_TAG, "create_link_tcp4(): can't create sock_accept %d: %s", errno, strerror(errno));
		return NULL;
	}

	memset(&tmp_sa.sa, 0, sizeof(tmp_sa));
	tmp_sa.sa.sa_family = AF_INET;
	tmp_sa.in.sin_addr.s_addr = htonl(ctx->local4);
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
	//int sock_connect = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
	int sock_connect = create_socket(ctx, AF_INET, SOCK_STREAM, IPPROTO_TCP, true);
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

static nat_link_t* create_link_udp4(usernat_tun_ctx_t* ctx, ocpa_ip_packet_t* packet) {
	sockaddr_uni tmp_sa;

	LOGD(LOG_TAG, "create_link_tcp4(): %08x:%d->%08x:%d",
			packet->ip_header.v4->saddr,
			ntohs(packet->payload_header.udp->source),
			packet->ip_header.v4->daddr,
			ntohs(packet->payload_header.udp->dest));

	//int sock_accept = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
	int sock_accept = create_socket(ctx, AF_INET, SOCK_DGRAM, IPPROTO_UDP, false);
	if(sock_accept == -1) {
		LOGD(LOG_TAG, "create_link_udp4(): can't create sock_accept %d: %s", errno, strerror(errno));
		return NULL;
	}
	memset(&tmp_sa.sa, 0, sizeof(tmp_sa));
	tmp_sa.sa.sa_family = AF_INET;
	tmp_sa.in.sin_addr.s_addr = htonl(ctx->local4);
	if(bind(sock_accept, &tmp_sa.sa, sizeof(tmp_sa)) == -1) {
		LOGD(LOG_TAG, "create_link_udp4(): can't bind sock_accept %d: %s", errno, strerror(errno));
		return NULL;
	}

	if(listen(sock_accept, 5) == -1) {
		LOGD(LOG_TAG, "create_link_udp4(): can't listen() sock_accept %d: %s", errno, strerror(errno));
		return NULL;
	}

	//int sock_connect = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
	int sock_connect = create_socket(ctx, AF_INET, SOCK_DGRAM, IPPROTO_UDP, true);
	if(sock_connect == -1) {
		LOGD(LOG_TAG, "create_link_udp4(): can't create sock_connect %d: %s", errno, strerror(errno));
		close(sock_accept);
		return NULL;
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

