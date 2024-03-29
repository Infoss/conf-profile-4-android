/*
 * java_VpnTunnel.c
 *
 *      Author: Dmitry Vorobiev
 */

#include <stdlib.h>
#include "android_jni.h"
#include "android_log_utils.h"
#include "java_VpnTunnel.h"

#define LOG_TAG "java_VpnTunnel.c"

struct java_VpnTunnel_private {
	java_VpnTunnel public;
	jobject obj;
	jmethodID id_protectSocket;
};

union java_VpnTunnel_union {
	java_VpnTunnel public;
	struct java_VpnTunnel_private private;
};

static bool protectSocket(java_VpnTunnel* instance, int32_t sock) {
	if(instance == NULL) {
		return false;
	}

	bool result;
	JNIEnv* jnienv;

	union java_VpnTunnel_union* this_instance = (union java_VpnTunnel_union*) instance;
	bool need_detach = androidjni_attach_thread(&jnienv);

	result = (*jnienv)->CallBooleanMethod(
			jnienv,
			this_instance->private.obj,
			this_instance->private.id_protectSocket,
			sock);
	LOGD(LOG_TAG, "VpnTunnel.protectSocket(%d) returned %d", sock, result);
	jniLogException(jnienv, ANDROID_LOG_ERROR, LOG_TAG, NULL);
	result = (result == JNI_TRUE);

	if ((*jnienv)->ExceptionOccurred(jnienv)){
		(*jnienv)->ExceptionDescribe(jnienv);
		(*jnienv)->ExceptionClear(jnienv);
		result = false;
	}

	if(need_detach) {
		androidjni_detach_thread();
	}

	return result;
}

java_VpnTunnel* wrap_into_VpnTunnel(jobject obj) {
	union java_VpnTunnel_union* result = malloc(sizeof(union java_VpnTunnel_union));
	if(result == NULL) {
		return NULL;
	}

	JNIEnv* jnienv;
	bool need_detach = androidjni_attach_thread(&jnienv);

	result->private.obj = (*jnienv)->NewGlobalRef(jnienv, obj);
	jobject clazz = (*jnienv)->FindClass(jnienv, JNI_PACKAGE_STRING "/VpnTunnel");
	result->private.id_protectSocket =
			(*jnienv)->GetMethodID(jnienv, clazz, "protectSocket", "(I)Z");

	if(need_detach) {
		androidjni_detach_thread();
	}

	result->public.protectSocket = protectSocket;

	return &result->public;
}

void destroy_VpnTunnel(java_VpnTunnel* instance) {
	if(instance == NULL) {
		return;
	}

	JNIEnv* jnienv;

	union java_VpnTunnel_union* this_instance = (union java_VpnTunnel_union*) instance;
	if(this_instance->private.obj != NULL) {
		bool need_detach = androidjni_attach_thread(&jnienv);
		LOGD(LOG_TAG, "Attaching to a thread (need_detach=%d)", need_detach);
		(*jnienv)->DeleteGlobalRef(jnienv, this_instance->private.obj);
		if(need_detach) {
			LOGD(LOG_TAG, "Detaching from a thread");
			androidjni_detach_thread();
		}
	}

	memset(this_instance, 0, sizeof(this_instance));
	free(this_instance);
}

