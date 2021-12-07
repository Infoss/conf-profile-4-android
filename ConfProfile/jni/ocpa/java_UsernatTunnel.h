/*
 * java_UsernatTunnel.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef JAVA_USERNATTUNNEL_H_
#define JAVA_USERNATTUNNEL_H_

#include <stdint.h>
#include <jni.h>

typedef struct java_UsernatTunnel java_UsernatTunnel;

struct java_UsernatTunnel {
	int32_t (*buildSocatTunnel)(java_UsernatTunnel* instance, int32_t fdAccept, int32_t fdConnect, const char* remoteAddr, int32_t remotePort, int64_t nativeLinkPtr);
	int32_t (*getLocalAddress4)(java_UsernatTunnel* instance);
	int32_t (*getRemoteAddress4)(java_UsernatTunnel* instance);
};

java_UsernatTunnel* wrap_into_UsernatTunnel(jobject obj);
void destroy_UsernatTunnel(java_UsernatTunnel* instance);

#endif /* JAVA_USERNATTUNNEL_H_ */
