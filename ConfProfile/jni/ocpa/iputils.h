/*
 * iputils.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef IPUTILS_H_
#define IPUTILS_H_

#include <stdint.h>
#include <stdbool.h>
#include "router.h"

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
inline void ip6_find_payload(ocpa_ip_packet_t* ip_packet);

inline void ip_detect_ipver(ocpa_ip_packet_t* ip_packet);

#endif /* IPUTILS_H_ */
