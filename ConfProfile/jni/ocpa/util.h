/*
 * util.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef UTIL_H_
#define UTIL_H_

#ifndef __func__
# if __STDC_VERSION__ < 199901L
#  if __GNUC__ >= 2
#   define __func__ __FUNCTION__
#  else
#   define __func__ "<unknown>"
#  endif
# endif
#endif

#include <stdint.h>
#include <stdio.h>
#include <pthread.h>
#include "debug.h"
#include "android_log_utils.h"

#define LOWER32(x) \
	((int32_t)(((intptr_t) x) & 0x00000000ffffffff))

#define ULOWER32(x) \
	((uint32_t)(((intptr_t) x) & 0x00000000ffffffff))

#define LOGD(LOG_TAG, ...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define TRACEPRINT(...) \
	{\
		if(TRACE_DEBUG) {\
			char msg[1024];\
			snprintf(msg, 1024, __VA_ARGS__);\
			LOGD("trace", "[%lx] %s: %s", pthread_self(), __func__, msg);\
		}\
	} while(0);\



void memset64(void * dest, uint64_t value, uintptr_t size);

#endif /* UTIL_H_ */
