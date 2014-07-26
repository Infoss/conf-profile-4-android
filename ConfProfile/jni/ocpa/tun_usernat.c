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
static nat_link_t* find_link(usernat_tun_ctx_t* ctx, ocpa_ip_packet_t* packet);
static nat_link_t* find_link4(nat_link_t* root_link, uint8_t local_link_type, uint32_t local_addr, uint16_t local_port, uint32_t remote_addr, uint16_t remote_port);

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
	ip_parse_packet(&packet);

	nat_link_t* link = find_link(ctx, &packet);
	if(link == NULL) {
		//this packet is invalid, drop it
		LOGD(LOG_TAG, "can't find appropriate link, dropping a packet");
		return 0;
	}

	pthread_rwlock_rdlock(ctx->common.rwlock);
	if(packet.ipver == 4) {
		if(ip4_addr_eq(ctx->local4, ntohl(packet.ip_header.v4->saddr))) {
			//outgoing usernat packet
			packet.ip_header.v4->saddr = htonl(ctx->remote4);
			packet.ip_header.v4->daddr = htonl(ctx->local4);
		} else if(ip4_addr_eq(ctx->remote4, ntohl(packet.ip_header.v4->daddr))) {
			//incoming usernat packet
			packet.ip_header.v4->saddr = htonl(link->ip4.real_dst_addr);
			packet.ip_header.v4->daddr = htonl(ctx->local4);
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

	}

	//TODO: calculate traffic
	//ctx->router_ctx->dev_tun_ctx.bytes_out += res;

	//start capture
	pcap_output_write(ctx->common.pcap_output, buff, 0, len);
	//end capture
	pthread_rwlock_unlock(ctx->common.rwlock);

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

	usernat_tun_ctx_t* ctx = (usernat_tun_ctx_t*) tun_ctx;
	nat_link_t* link = (nat_link_t*) link_ptr;

	//first, try to find a link
	pthread_rwlock_wrlock(ctx->common.rwlock);
	nat_link_t* curr_link = ctx->links;
	nat_link_t* prev_link = NULL;

	while(curr_link != NULL) {

		//decrease cycles_to_live to links with pid == -1
		if(curr_link->common.cycles_to_live > 0 && curr_link->common.socat_pid == -1) {
			curr_link->common.cycles_to_live--;
		}

		//remoce links with pid == -1 and cycles_to_live == 0
		if(curr_link->common.cycles_to_live == 0 && curr_link->common.socat_pid == -1) {
			nat_link_t* tmp_link = curr_link;
			curr_link = curr_link->common.next;

			//if prev_link is NULL, it means that we're at the 1st element of list
			if(prev_link == NULL) {
				ctx->links = curr_link;
			} else {
				prev_link->common.next = curr_link;
			}

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
		curr_link->common.socat_pid = pid;
		if(pid == -1) {
			curr_link->common.cycles_to_live = 10;
		}
	}

	pthread_rwlock_unlock(ctx->common.rwlock);
}

static nat_link_t* link_init() {
	nat_link_t* result = malloc(sizeof(nat_link_t));
	if(result != NULL) {
		memset(result, 0, sizeof(nat_link_t));
	}

	return result;
}

static void link_deinit(nat_link_t* link) {
	if(link == NULL) {
		return;
	}

	memset(link, 0, sizeof(nat_link_t));
	free(link);
}

static nat_link_t* find_link(usernat_tun_ctx_t* ctx, ocpa_ip_packet_t* packet) {
	if(ctx == NULL || packet == NULL || packet->ip_header.raw == NULL) {
		return NULL;
	}

	bool is_incoming = false;
	nat_link_t* result = NULL;

	uint16_t local_port = 0;
	uint16_t remote_port = 0;
	sockaddr_uni tmp_sa;

	if(packet->ipver == 4) {
		uint32_t remote_addr = 0;

		pthread_rwlock_rdlock(ctx->common.rwlock);
		if(ip4_addr_eq(ctx->local4, ntohl(packet->ip_header.v4->saddr))) {
			//outgoing usernat packet
			is_incoming = false;
			local_port = packet->src_port;
			remote_addr = ntohl(packet->ip_header.v4->daddr);
			remote_port = packet->dst_port;
		} else if(ip4_addr_eq(ctx->remote4, ntohl(packet->ip_header.v4->daddr))) {
			//incoming usernat packet
			is_incoming = true;
			local_port = packet->dst_port;
			remote_addr = ctx->remote4;
			remote_port = packet->src_port;
		} else {
			//stray packet
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
			return NULL;
		}


		pthread_rwlock_rdlock(ctx->common.rwlock);
		root_link = ctx->links;
		result = find_link4(root_link, local_link_type, ctx->local4, local_port, remote_addr, remote_port);
		pthread_rwlock_unlock(ctx->common.rwlock);

		if(result == NULL) {
			if(is_incoming) {
				//drop incoming packet if link doesn't exist
				//this is a NAT
				return NULL;
			}

			//not found, create a new one
			result = link_init();
			if(result == NULL) {
				//not enough memory
				return NULL;
			}
			result->common.usernat_ctx = ctx;

			result->common.link_type = local_link_type;
			switch(local_link_type) {
			case NAT_LINK_TCP4: {
				if(packet->payload_header.tcp->syn == 0) {
					//create a link on SYN packet only
					free(result);
					return NULL;
				}

				result->ip4.real_src_addr = ntohl(packet->ip_header.v4->saddr);
				result->ip4.real_src_port = ntohs(packet->payload_header.tcp->source);
				result->ip4.real_dst_addr = ntohl(packet->ip_header.v4->daddr);
				result->ip4.real_dst_port = ntohs(packet->payload_header.tcp->dest);

				result->common.sock_accept = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
				if(result->common.sock_accept == -1) {
					free(result);
					return NULL;
				}

				memset(&tmp_sa.sa, 0, sizeof(tmp_sa));
				tmp_sa.sa.sa_family = AF_INET;
				tmp_sa.in.sin_addr.s_addr = htonl(ctx->local4);
				if(bind(result->common.sock_accept, &tmp_sa.sa, sizeof(tmp_sa)) == -1) {
					free(result);
					return NULL;
				}

				result->common.sock_connect = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
				if(result->common.sock_connect == -1) {
					close(result->common.sock_accept);
					free(result);
					return NULL;
				}

				break;
			}
			case NAT_LINK_UDP4: {
				result->ip4.real_src_addr = ntohl(packet->ip_header.v4->saddr);
				result->ip4.real_src_port = ntohs(packet->payload_header.udp->source);
				result->ip4.real_dst_addr = ntohl(packet->ip_header.v4->daddr);
				result->ip4.real_dst_port = ntohs(packet->payload_header.udp->dest);

				result->common.sock_accept = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
				if(result->common.sock_accept == -1) {
					LOGD(LOG_TAG, "find_link(): 9");
					free(result);
					return NULL;
				}

				memset(&tmp_sa.sa, 0, sizeof(tmp_sa));
				tmp_sa.sa.sa_family = AF_INET;
				tmp_sa.in.sin_addr.s_addr = htonl(ctx->local4);
				if(bind(result->common.sock_accept, &tmp_sa.sa, sizeof(tmp_sa)) == -1) {
					LOGD(LOG_TAG, "find_link(): 10");
					free(result);
					return NULL;
				}

				result->common.sock_connect = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
				if(result->common.sock_connect == -1) {
					LOGD(LOG_TAG, "find_link(): 11");
					close(result->common.sock_accept);
					free(result);
					return NULL;
				}
				break;
			}
			default: {
				//unsupported link type, should never be happen
				LOGE(LOG_TAG, "Unsupported link type %d", local_link_type);
				link_deinit(result);
				result = NULL;
				return NULL;
			}
			}

			result->common.hop_port = tmp_sa.in.sin_port;

			//TODO: init sockets, add a link
			pthread_rwlock_rdlock(ctx->common.rwlock);
			bool is_protected = ctx->common.j_vpn_tun->protectSocket(
					ctx->common.j_vpn_tun,
					result->common.sock_connect
					);
			pthread_rwlock_unlock(ctx->common.rwlock);

			if(!is_protected) {
				LOGE(LOG_TAG, "Can't protect socket");
				close(result->common.sock_accept);
				close(result->common.sock_connect);
				link_deinit(result);
				result = NULL;
				return NULL;
			}

			//adding a link
			pthread_rwlock_wrlock(ctx->common.rwlock);
			result->common.next = ctx->links;
			ctx->links = result;
			pthread_rwlock_unlock(ctx->common.rwlock);

			tmp_sa.in.sin_addr.s_addr = result->ip4.real_dst_addr;

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
		return NULL;
	}

	return result;
}

static nat_link_t* find_link4(nat_link_t* root_link, uint8_t local_link_type, uint32_t local_addr, uint16_t local_port, uint32_t remote_addr, uint16_t remote_port) {
	if(root_link == NULL) {
		return NULL;
	}

	nat_link_t* curr_link = root_link;
	while(curr_link != NULL) {
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

		if(!(ip4_addr_eq(curr_link->ip4.real_dst_addr, remote_addr) ||
				ip4_addr_eq(curr_link->common.usernat_ctx->remote4, remote_addr)) ||
				remote_port != curr_link->ip4.real_dst_port) {
			//remote address doesn't match
			curr_link = curr_link->common.next;
			continue;
		}

		break;
	}

	return curr_link;
}

