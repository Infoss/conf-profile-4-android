/*
 * iputils.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef IPUTILS_H_
#define IPUTILS_H_

#include <stdint.h>
#include <stdbool.h>

#include "protoheaders.h"

typedef union ip_header_t ip_header_t;
typedef union protocol_header_t protocol_header_t;
typedef struct ocpa_ip_packet_t ocpa_ip_packet_t;

union ip_header_t {
	void* raw;
	ip4_header* v4;
	ip6_header* v6;
};

union protocol_header_t {
	void* raw;
	tcp_header* tcp;
	udp_header* udp;
};

struct ocpa_ip_packet_t {
	uint32_t buff_len;
	uint8_t* buff;
	uint32_t pkt_len;
	uint8_t ipver;
	ip_header_t ip_header;
	uint8_t payload_proto;
	protocol_header_t payload_header;
	uint32_t payload_offs;
	uint32_t payload_len;
	uint16_t src_port;
	uint16_t dst_port;
};

//TODO: force all methods to use ocpa_ip_packet_t instead of buffers & sizes
uint16_t ip4_calc_ip_checksum(uint8_t* buff, int len);
uint16_t ip4_calc_tcp_checksum(uint8_t* buff, int len);
uint16_t ip4_calc_udp_checksum(uint8_t* buff, int len);
uint32_t ip4_calc_pseudoheader_sum(uint8_t* buff, int len);
uint16_t ip6_calc_tcp_checksum(uint32_t common_sum, uint8_t* buff, int len);
uint16_t ip6_calc_udp_checksum(uint32_t common_sum, uint8_t* buff, int len);
uint32_t ip6_calc_common_pseudoheader_sum(uint8_t* buff, int len);
uint32_t ip6_calc_pseudoheader_sum(uint8_t* buff, int len, uint8_t proto, uint32_t pktlen);
inline uint32_t ip4_update_sum(uint32_t previous, uint16_t data);
inline uint16_t ip4_update_checksum(uint16_t old_checksum, uint16_t old_data, uint16_t new_data);
inline uint16_t ip_sum_to_checksum(uint32_t sum);

inline bool ip4_addr_match(uint32_t network, uint8_t netmask, uint32_t test_ip);
inline bool ip6_addr_match(uint8_t* network, uint8_t netmask, uint8_t* test_ip);
inline bool ip4_addr_eq(uint32_t addr1, uint32_t addr2);
inline bool ip6_addr_eq(uint8_t* addr1, uint8_t* addr2);
inline void ip6_find_payload(ocpa_ip_packet_t* ip_packet);

inline void ip_detect_ipver(ocpa_ip_packet_t* ip_packet);

inline void ip_parse_packet(ocpa_ip_packet_t* ip_packet);

ssize_t read_ip_packet(int fd, uint8_t* buff, int len);

#endif /* IPUTILS_H_ */
