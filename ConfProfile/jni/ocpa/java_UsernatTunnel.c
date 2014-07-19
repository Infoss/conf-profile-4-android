/*
 * java_UsernatTunnel.c
 *
 *      Author: Dmitry Vorobiev
 */

#include <stdlib.h>
#include "java_UsernatTunnel.h"

struct java_UsernatTunnel_private {
	java_UsernatTunnel public;
	jobject obj;
	jmethodID id_buildSocatTunnel;
};

union java_UsernatTunnel_union {
	java_UsernatTunnel public;
	struct java_UsernatTunnel_private private;
};

static int32_t buildSocatTunnel(java_UsernatTunnel* instance, int32_t fdAccept, int32_t fdConnect, const char* remoteAddr, int32_t remotePort) {
	if(instance == NULL) {
		return -1;
	}

	int32_t result;
	JNIEnv* jnienv;

	union java_UsernatTunnel_union* this_instance = (union java_UsernatTunnel_union*) instance;
	bool need_detach = androidjni_attach_thread(&jnienv);

	jstring jRemoteAddr = (*jnienv)->NewStringUTF(jnienv, remoteAddr);
	result = (*jnienv)->CallIntMethod(
			jnienv,
			this_instance->private.obj,
			this_instance->private.id_buildSocatTunnel,
			fdAccept,
			fdConnect,
			jRemoteAddr,
			remotePort);

	if ((*jnienv)->ExceptionOccurred(jnienv)){
		(*jnienv)->ExceptionDescribe(jnienv);
		(*jnienv)->ExceptionClear(jnienv);
		result = -1;
	}
	(*jnienv)->DeleteLocalRef(jnienv, jRemoteAddr);

	if(need_detach) {
		androidjni_detach_thread();
	}

	return result;
}

java_UsernatTunnel* wrap_into_UsernatTunnel(jobject obj) {
	union java_UsernatTunnel_union* result = malloc(sizeof(union java_UsernatTunnel_union));
	if(result == NULL) {
		return NULL;
	}

	JNIEnv* jnienv;
	bool need_detach = androidjni_attach_thread(&jnienv);

	result->private.obj = (*jnienv)->NewGlobalRef(jnienv, obj);
	jobject clazz = (*jnienv)->FindClass(jnienv, JNI_PACKAGE_STRING "/UsernatTunnel");
	result->private.id_buildSocatTunnel =
			(*jnienv)->GetMethodID(jnienv, clazz, "buildSocatTunnel", "(IILjava/lang/String;I)I");

	if(need_detach) {
		androidjni_detach_thread();
	}

	result->public.buildSocatTunnel = buildSocatTunnel;

	return &result->public;
}

void destroy_UsernatTunnel(java_UsernatTunnel* instance) {
	if(instance == NULL) {
		return;
	}

	JNIEnv* jnienv;

	union java_UsernatTunnel_union* this_instance = (union java_UsernatTunnel_union*) instance;
	if(this_instance->private.obj != NULL) {
		bool need_detach = androidjni_attach_thread(&jnienv);
		(*jnienv)->DeleteGlobalRef(jnienv, this_instance->private.obj);
		if(need_detach) {
			androidjni_detach_thread();
		}
	}

	memset(this_instance, 0, sizeof(this_instance));
	free(this_instance);
}
