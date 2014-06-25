/*
 * iputils.c
 *
 *      Author: Dmitry Vorobiev
 */


#include "iputils.h"
#include "android_log_utils.h"
#include "router.h"
#include "protoheaders.h"

#define LOG_TAG "iputils.c"

uint16_t ip4_calc_ip_checksum(uint8_t* buff, int len) {
	if(buff == NULL || len < 20) {
		return 0;
	}

	ip4_header* hdr = (ip4_header*) buff;
	int cycles = hdr->ihl * 2;

	if(len < cycles * sizeof(uint16_t)) {
		return 0;
	}

	int seq_num = -1;
	uint32_t sum = 0;
	uint16_t* ptr = (uint16_t*) buff;
	ptr--; //this is safe due to a following ptr++
	while(cycles > 0) {
		cycles--;
		ptr++;
		seq_num++;
		if(seq_num == 5) {
			//skipping checksum field
			continue;
		}

		sum = ip4_update_sum(sum, ntohs(ptr[0]));
	}

	uint16_t checksum = ip_sum_to_checksum(sum);
	hdr->check = htons(checksum);

	return checksum;
}

uint16_t ip4_calc_tcp_checksum(uint8_t* buff, int len) {
	if(buff == NULL || len < 40) {
		return 0;
	}
	ip4_header* hdr = (ip4_header*) buff;
	uint16_t tcp_len = ntohs(hdr->tot_len) - (hdr->ihl * 4);
	uint32_t sum = ip4_calc_pseudoheader_sum(buff, len);

	int cycles = tcp_len >> 1; //integer division

	int seq_num = -1;
	uint16_t* ptr = (uint16_t*) (buff + hdr->ihl * 4);
	LOGD(LOG_TAG, "TCP frame size is %d, packet address is %p, tcp data starts from %p", tcp_len, buff, ptr);
	tcp_header* tcp_hdr = (tcp_header*) ptr;
	ptr--; //this is safe due to a following ptr++
	while(cycles > 0) {
		cycles--;
		ptr++;
		seq_num++;
		if(seq_num == 8) {
			//skipping checksum field
			continue;
		}

		sum = ip4_update_sum(sum, ntohs(ptr[0]));
	}

	if((tcp_len & 1) == 1) {
		//adding odd byte
		ptr++;
		uint8_t odd_byte = ((uint8_t*) ptr)[0];
		sum = ip4_update_sum(sum, htons((uint16_t) odd_byte));
		LOGD(LOG_TAG, "TCP frame size is odd, adding %02x %p to checksum", htons((uint16_t) odd_byte), ptr);
	}

	uint16_t checksum = ip_sum_to_checksum(sum);
	tcp_hdr->check = htons(checksum);

	return checksum;
}

uint16_t ip4_calc_udp_checksum(uint8_t* buff, int len) {
	if(buff == NULL || len < 40) {
		return 0;
	}
	ip4_header* hdr = (ip4_header*) buff;
	uint16_t udp_len = ntohs(hdr->tot_len) - (hdr->ihl * 4);

	uint16_t* ptr = (uint16_t*) (buff + hdr->ihl * 4);
	LOGD(LOG_TAG, "UDP frame size is %d, packet address is %p, udp data starts from %p", udp_len, buff, ptr);
	udp_header* udp_hdr = (udp_header*) ptr;

	//set UDP checksum as 0x0000
	udp_hdr->check = htons(0x0000);

	return 0;
}

uint32_t ip4_calc_pseudoheader_sum(uint8_t* buff, int len) {
	//packet should be validated before this method call
	uint32_t sum = 0;
	ip4_header* hdr = (ip4_header*) buff;
	uint16_t* ptr = (uint16_t*) buff;

	//src addr
	sum = ip4_update_sum(sum, ntohs(ptr[6]));
	sum = ip4_update_sum(sum, ntohs(ptr[7]));

	//dst addr
	sum = ip4_update_sum(sum, ntohs(ptr[8]));
	sum = ip4_update_sum(sum, ntohs(ptr[9]));

	sum = ip4_update_sum(sum, (uint16_t) hdr->protocol);
	sum = ip4_update_sum(sum, ntohs(hdr->tot_len) - (hdr->ihl * 4));

	return sum;
}

uint16_t ip6_calc_tcp_checksum(uint32_t common_sum, uint8_t* buff, int len) {
	if(buff == NULL || len < 40) {
		return 0;
	}

	uint32_t sum = common_sum;
	sum = ip4_update_sum(sum, IPPROTO_TCP);
	sum = ip4_update_sum(sum, len >> 16);
	sum = ip4_update_sum(sum, len & 0x0000ffff);

	int cycles = len >> 1; //integer division

	int seq_num = -1;
	uint16_t* ptr = (uint16_t*) (buff);
	LOGD(LOG_TAG, "TCP frame size is %d, tcp data starts from %p", len, ptr);
	tcp_header* tcp_hdr = (tcp_header*) ptr;
	ptr--; //this is safe due to a following ptr++
	while(cycles > 0) {
		cycles--;
		ptr++;
		seq_num++;
		if(seq_num == 8) {
			//skipping checksum field
			continue;
		}

		sum = ip4_update_sum(sum, ntohs(ptr[0]));
	}

	if((len & 1) == 1) {
		//adding odd byte
		ptr++;
		uint8_t odd_byte = ((uint8_t*) ptr)[0];
		sum = ip4_update_sum(sum, htons((uint16_t) odd_byte));
		LOGD(LOG_TAG, "TCP frame size is odd, adding %02x %p to checksum", htons((uint16_t) odd_byte), ptr);
	}

	uint16_t checksum = ip_sum_to_checksum(sum);
	tcp_hdr->check = htons(checksum);

	return checksum;
}

uint16_t ip6_calc_udp_checksum(uint32_t common_sum, uint8_t* buff, int len) {
	if(buff == NULL || len < 40) {
		return 0;
	}

	uint32_t sum = common_sum;
	sum = ip4_update_sum(sum, IPPROTO_UDP);
	sum = ip4_update_sum(sum, len >> 16);
	sum = ip4_update_sum(sum, len & 0x0000ffff);

	int cycles = len >> 1; //integer division

	int seq_num = -1;
	uint16_t* ptr = (uint16_t*) (buff);
	LOGD(LOG_TAG, "UDP frame size is %d, udp data starts from %p", len, ptr);
	udp_header* udp_hdr = (udp_header*) ptr;
	ptr--; //this is safe due to a following ptr++
	while(cycles > 0) {
		cycles--;
		ptr++;
		seq_num++;
		if(seq_num == 4) {
			//skipping checksum field
			continue;
		}

		sum = ip4_update_sum(sum, ntohs(ptr[0]));
	}

	if((len & 1) == 1) {
		//adding odd byte
		ptr++;
		uint8_t odd_byte = ((uint8_t*) ptr)[0];
		sum = ip4_update_sum(sum, htons((uint16_t) odd_byte));
		LOGD(LOG_TAG, "UDP frame size is odd, adding %02x %p to checksum", htons((uint16_t) odd_byte), ptr);
	}

	uint16_t checksum = ip_sum_to_checksum(sum);
	udp_hdr->check = htons(checksum);

	return checksum;
}

/**
 * Calculates IPv6 pseudoheader checksum w/o protocol and packet length
 */
uint32_t ip6_calc_common_pseudoheader_sum(uint8_t* buff, int len) {
	//packet should be validated before this method call
	uint32_t sum = 0;
	uint16_t* ptr = (uint16_t*) buff;

	//src addr
	sum = ip4_update_sum(sum, ntohs(ptr[4]));
	sum = ip4_update_sum(sum, ntohs(ptr[5]));
	sum = ip4_update_sum(sum, ntohs(ptr[6]));
	sum = ip4_update_sum(sum, ntohs(ptr[7]));
	sum = ip4_update_sum(sum, ntohs(ptr[8]));
	sum = ip4_update_sum(sum, ntohs(ptr[9]));
	sum = ip4_update_sum(sum, ntohs(ptr[10]));
	sum = ip4_update_sum(sum, ntohs(ptr[11]));

	//dst addr
	sum = ip4_update_sum(sum, ntohs(ptr[12]));
	sum = ip4_update_sum(sum, ntohs(ptr[13]));
	sum = ip4_update_sum(sum, ntohs(ptr[14]));
	sum = ip4_update_sum(sum, ntohs(ptr[15]));
	sum = ip4_update_sum(sum, ntohs(ptr[16]));
	sum = ip4_update_sum(sum, ntohs(ptr[17]));
	sum = ip4_update_sum(sum, ntohs(ptr[18]));
	sum = ip4_update_sum(sum, ntohs(ptr[19]));

	return sum;
}

uint32_t ip6_calc_pseudoheader_sum(uint8_t* buff, int len, uint8_t proto, uint32_t pktlen) {
	//packet should be validated before this method call
	uint32_t sum = ip6_calc_common_pseudoheader_sum(buff, len);

	sum = ip4_update_sum(sum, (uint16_t)(pktlen >> 16));
	sum = ip4_update_sum(sum, (uint16_t)(pktlen & 0x0000ffff));
	sum = ip4_update_sum(sum, (uint16_t) proto);

	return sum;
}

inline uint32_t ip4_update_sum(uint32_t previous, uint16_t data) {
	uint32_t sum = previous;
	sum += data;
	sum += sum >> 16;
	sum &= 0x0000ffff;
	return sum;
}

inline uint16_t ip4_update_checksum(uint16_t old_checksum, uint16_t old_data, uint16_t new_data) {
	uint32_t sum = (~old_checksum) & 0x0000ffff;
	sum = ip4_update_sum(sum, old_data);
	sum = ip4_update_sum(sum, ~new_data);
	return ip_sum_to_checksum(sum);
}

inline uint16_t ip_sum_to_checksum(uint32_t sum) {
	uint16_t checksum = ~((uint16_t) sum);
	return (checksum == 0x0000 ? 0xffff : checksum);
}

inline bool ip4_addr_match(uint32_t network, uint8_t netmask, uint32_t test_ip) {
	uint32_t prepared_test_ip = (test_ip >> (32 - netmask)) << (32 - netmask);
	if(network == prepared_test_ip) {
		return true;
	}
	return false;
}

inline bool ip6_addr_match(uint8_t* network, uint8_t netmask, uint8_t* test_ip) {
	uint8_t matching_bytes = netmask >> 3;
	uint8_t partial_matching_bytes = 0;

	// see a comment in route6()
	uint8_t partial_bitmask_shift = (8 - (netmask - (matching_bytes << 3)));
	uint8_t partial_bitmask = (0xff >> partial_bitmask_shift) << partial_bitmask_shift;
	if(partial_bitmask != 0x00) {
		partial_matching_bytes++;
	}

	int i = 0;
	for(i = 0; i < matching_bytes; i++) {
		if(network[i] != test_ip[i]) {
			return false;
		}
	}

	if(partial_matching_bytes > 0) {
		if(network[matching_bytes] != (test_ip[matching_bytes] & partial_bitmask)) {
			return false;
		}
	}

	return true;
}

inline void ip6_find_payload(ocpa_ip_packet_t *ip_packet) {
	if(ip_packet == NULL || ip_packet->buff == NULL) {
		return;
	}

	uint8_t proto = ((ip6_header*) ip_packet->buff)->ip6_ctlun.ip6_un1.ip6_un1_nxt;
	uint8_t* ptr = ip_packet->buff + sizeof(ip6_header);
	uint32_t offs = sizeof(ip6_header);
	while(true) {
		if(proto != IPPROTO_HOPOPTS && proto != IPPROTO_ROUTING && proto != IPPROTO_DSTOPTS) {
			break;
		}

		if(ptr >= ip_packet->buff + ip_packet->buff_len - 1) {
			//there is no space for next proto field
			proto = IPPROTO_NONE; //emulate "No next header"
			offs = 0;
			break;
		}

		proto = ptr[0];

		ptr++;
		offs++;

		if(ptr >= ip_packet->buff + ip_packet->buff_len - 1) {
			//there is no space for next len field
			proto = IPPROTO_NONE; //emulate "No next header"
			offs = 0;
			break;
		}

		ptr += ptr[0];
		offs += ptr[0];
	}

	ip_packet->payload_proto = proto;
	ip_packet->payload_offs = offs;
	int ip6plen = ((ip6_header*) ip_packet->buff)->ip6_ctlun.ip6_un1.ip6_un1_plen;
	ip_packet->payload_len = ip6plen + sizeof(ip6_header) - offs;
}

inline void ip_detect_ipver(ocpa_ip_packet_t* ip_packet) {
	if(ip_packet != NULL) {
		ip_packet->ipver = 0;
		if(ip_packet->buff != NULL && ip_packet->pkt_len > 0) {
			if((ip_packet->buff[0] & 0xf0) == 0x40) {
				ip_packet->ipver = 4;
			} else if((ip_packet->buff[0] & 0xf0) == 0x60) {
				ip_packet->ipver = 6;
			}
		}
	}
}
