/*
 * debug.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef OCPA_DEBUG_H_
#define OCPA_DEBUG_H_

#ifdef __ANDROID__

#ifndef NDEBUG

//debug mode
#define JNI_CORE_DEBUG 1
#define STRONGSWAN_DEBUG 1
#define TUN_DEBUG 1
#define REFS_DEBUG 1
#define ROUTER_DEBUG 1
#define ROUTER_EPOLL_DEBUG 0
#define TRACE_DEBUG 1

#else

//release mode
#define JNI_CORE_DEBUG 0
#define STRONGSWAN_DEBUG 0
#define TUN_DEBUG 0
#define REFS_DEBUG 0
#define ROUTER_DEBUG 0
#define ROUTER_EPOLL_DEBUG 0
#define TRACE_DEBUG 0

#endif /* NDEBUG */

#endif /* __ANDROID__ */

#endif /* DEBUG_H_ */
