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

#include "strongswan_debug.h"

#undef _D_PRINTX
#define _D_PRINTX(type, val, name, func) __print_##type((void*) val, name, func)

#undef _D_PRINT
#define _D_PRINT(type, val, name) _D_PRINT(type, val, name, NULL)

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

#ifndef _D_PRINTX
#define _D_PRINTX(type, val, name, func) while(0){}
#endif


#ifndef _D_PRINT
#define _D_PRINT(type, val, name) while(0){}
#endif

#endif /* DEBUG_H_ */
