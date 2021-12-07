/*
 * java_MiscUtils.c
 *
 *      Author: Dmitry Vorobiev
 */

#include <stdlib.h>
#include "android_jni.h"
#include "java_MiscUtils.h"


struct java_MiscUtils_private {
	java_MiscUtils public;
	jclass class_ref;
	jmethodID id_intToFileDescriptor;
	jmethodID id_fileDescriptorToInt;
};

union java_MiscUtils_union {
	java_MiscUtils public;
	struct java_MiscUtils_private private;
};

static jobject intToFileDescriptor(java_MiscUtils* instance, int32_t fd) {
	if(instance == NULL) {
		return false;
	}

	jobject result;
	JNIEnv* jnienv;

	union java_MiscUtils_union* this_instance = (union java_MiscUtils_union*) instance;
	bool need_detach = androidjni_attach_thread(&jnienv);

	result = (*jnienv)->CallStaticObjectMethod(
			jnienv,
			this_instance->private.class_ref,
			this_instance->private.id_intToFileDescriptor,
			fd);

	if ((*jnienv)->ExceptionOccurred(jnienv)){
		(*jnienv)->ExceptionDescribe(jnienv);
		(*jnienv)->ExceptionClear(jnienv);
		result = NULL;
	}

	if(need_detach) {
		androidjni_detach_thread();
	}

	return result;
}

static int32_t fileDescriptorToInt(java_MiscUtils* instance, jobject fd) {
	if(instance == NULL) {
		return false;
	}

	int32_t result;
	JNIEnv* jnienv;

	union java_MiscUtils_union* this_instance = (union java_MiscUtils_union*) instance;
	bool need_detach = androidjni_attach_thread(&jnienv);

	result = (*jnienv)->CallStaticIntMethod(
			jnienv,
			this_instance->private.class_ref,
			this_instance->private.id_fileDescriptorToInt,
			fd);

	if ((*jnienv)->ExceptionOccurred(jnienv)){
		(*jnienv)->ExceptionDescribe(jnienv);
		(*jnienv)->ExceptionClear(jnienv);
		result = -1;
	}

	if(need_detach) {
		androidjni_detach_thread();
	}

	return result;
}

java_MiscUtils* wrap_into_MiscUtils() {
	union java_MiscUtils_union* result = malloc(sizeof(union java_MiscUtils_union));
	if(result == NULL) {
		return NULL;
	}

	JNIEnv* jnienv;
	bool need_detach = androidjni_attach_thread(&jnienv);

	jobject clazz = (*jnienv)->FindClass(jnienv, JNI_UTIL_PACKAGE_STRING "/NetUtils");
	result->private.class_ref = (*jnienv)->NewGlobalRef(jnienv, clazz);
	result->private.id_intToFileDescriptor =
			(*jnienv)->GetMethodID(jnienv, clazz, "intToFileDescriptor", "(I)Ljava/io/FileDescriptor;");
	result->private.id_fileDescriptorToInt =
				(*jnienv)->GetMethodID(jnienv, clazz, "intToFileDescriptor", "(Ljava/io/FileDescriptor;)I");

	if(need_detach) {
		androidjni_detach_thread();
	}

	result->public.intToFileDescriptor = intToFileDescriptor;
	result->public.fileDescriptorToInt = fileDescriptorToInt;

	return &result->public;
}

void destroy_MiscUtils(java_MiscUtils* instance) {
	if(instance == NULL) {
		return;
	}

	JNIEnv* jnienv;

	union java_MiscUtils_union* this_instance = (union java_MiscUtils_union*) instance;

	if(this_instance->private.class_ref != NULL) {
		bool need_detach = androidjni_attach_thread(&jnienv);
		(*jnienv)->DeleteGlobalRef(jnienv, this_instance->private.class_ref);
		if(need_detach) {
			androidjni_detach_thread();
		}
	}

	memset(this_instance, 0, sizeof(this_instance));
	free(this_instance);
}
