/*
 * protoheaders.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef PROTOHEADERS_H_
#define PROTOHEADERS_H_

#include <linux/tcp.h>
#include <linux/udp.h>
#include <netinet/ip.h>
#include <netinet/ip6.h>

typedef struct iphdr ip4_header;
typedef struct ip6_hdr ip6_header;
typedef struct tcphdr tcp_header;
typedef struct udphdr udp_header;

#endif /* PROTOHEADERS_H_ */
