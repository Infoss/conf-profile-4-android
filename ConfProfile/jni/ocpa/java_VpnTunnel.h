/*
 * java_VpnTunnel.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef JAVA_VPNTUNNEL_H_
#define JAVA_VPNTUNNEL_H_

#include <stdint.h>
#include <stdbool.h>

#include <jni.h>

typedef struct java_VpnTunnel java_VpnTunnel;

struct java_VpnTunnel {
	bool (*protectSocket)(java_VpnTunnel* instance, int32_t sock);
};

java_VpnTunnel* wrap_into_VpnTunnel(jobject obj);
void destroy_VpnTunnel(java_VpnTunnel* instance);

#endif /* JAVA_VPNTUNNEL_H_ */
