/*
 * jni_NetUtils.c
 *
 *      Author: Dmitry Vorobiev
 */

#include <linux/socket.h>
#include <linux/un.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>

#include "android_log_utils.h"
#include "android_jni.h"

#define LOG_TAG "jni_NetUtils.c"

JNI_METHOD_P(JNI_UTIL_PACKAGE, NetUtils, bindUnixSocket, jint, jstring path) {
	struct sockaddr_un sa;
	memset(&sa, 0, sizeof(sa));
	sa.sun_family = AF_UNIX;

	jsize str_len = (*env)->GetStringUTFLength(env, path);
	if(str_len > UNIX_PATH_MAX) {
		LOGE(LOG_TAG, "Socket name is too long");
		return -1;
	}

	const jbyte* chars = (*env)->GetStringUTFChars(env, path, NULL);
	memcpy(sa.sun_path, chars, str_len);
	(*env)->ReleaseStringUTFChars(env, path, chars);

	int sock = socket(AF_UNIX, SOCK_STREAM, 0);
	if(sock == -1) {
		LOGE(LOG_TAG, "Error while creating socket. %d: %s", errno, strerror(errno));
		return -1;
	}

	/*
	if(fcntl(sock, F_SETFL, O_NONBLOCK) < 0) {
		LOGW(LOG_TAG, "Failed to set O_NONBLOCK to socket. code %d: %s", errno, strerror(errno));
	}
	*/

	int ret = bind(sock, (struct sockaddr*) &sa, sizeof(sa));
	if(ret == -1) {
		LOGE(LOG_TAG, "Error while creating socket. %d: %s", errno, strerror(errno));
		return -1;
	}

	return (jint) sock;
}




