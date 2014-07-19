/*
 * sockaddrs.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef SOCKADDRS_H_
#define SOCKADDRS_H_

#include <linux/socket.h>
#include <linux/in.h>
#include <linux/in6.h>

typedef union sockaddr_uni sockaddr_uni;

union sockaddr_uni {
	struct sockaddr sa;
	struct sockaddr_in in;
	struct sockaddr_in6 in6;
};

#endif /* SOCKADDRS_H_ */
