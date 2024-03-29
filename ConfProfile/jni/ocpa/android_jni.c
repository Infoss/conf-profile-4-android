/*
 * Copyright (C) 2012 Tobias Brunner
 * Copyright (C) 2012 Giuliano Grassi
 * Copyright (C) 2012 Ralf Sager
 * Hochschule fuer Technik Rapperswil
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.  See <http://www.fsf.org/copyleft/gpl.txt>.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 */

#include "android_jni.h"

#include <library.h>
#include <threading/thread_value.h>

#include "android_log_utils.h"
#include "debug.h"

#define LOG_TAG "android_jni.c"

/**
 * JVM
 */
static JavaVM *android_jvm;

jclass *android_ocpavpnservice_class;
jclass *android_ocpavpnservice_builder_class;
jclass *android_routerloop_class;
jclass *android_ipsectunnel_class;
android_sdk_version_t android_sdk_version;

/**
 * Thread-local variable. Only used because of the destructor
 */
static thread_value_t *androidjni_threadlocal;

/**
 * Thread-local destructor to ensure that a native thread is detached
 * from the JVM even if androidjni_detach_thread() is not called.
 */
static void attached_thread_cleanup(void *arg) {
	(*android_jvm)->DetachCurrentThread(android_jvm);
}

/*
 * Described in header
 */
bool androidjni_attach_thread(JNIEnv **env) {
	if ((*android_jvm)->GetEnv(android_jvm, (void**)env,
							   JNI_VERSION_1_6) == JNI_OK) {
		/* already attached or even a Java thread */
		LOGDIF(JNI_CORE_DEBUG, LOG_TAG, "androidjni_attach_thread(): false");
		return false;
	}
	(*android_jvm)->AttachCurrentThread(android_jvm, env, NULL);
	LOGDIF(JNI_CORE_DEBUG, LOG_TAG, "androidjni_attach_thread(): true (androidjni_threadlocal=%p, env=%p)",
			androidjni_threadlocal, *env);

	/* use a thread-local value with a destructor that automatically detaches
	 * the thread from the JVM before it terminates, if not done manually */
	androidjni_threadlocal->set(androidjni_threadlocal, (void*)*env);
	return true;
}

/*
 * Described in header
 */
void androidjni_detach_thread() {
	LOGDIF(JNI_CORE_DEBUG, LOG_TAG, "androidjni_detach_thread(): true (androidjni_threadlocal=%p)", androidjni_threadlocal);
	androidjni_threadlocal->set(androidjni_threadlocal, NULL);
	(*android_jvm)->DetachCurrentThread(android_jvm);
}

/**
 * Called when this library is loaded by the JVM
 */
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
	JNIEnv *env;
	jclass jversion;
	jfieldID jsdk_int;

	android_jvm = vm;

	if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
		return -1;
	}

	androidjni_threadlocal = thread_value_create(attached_thread_cleanup);

	android_ocpavpnservice_class =
				(*env)->NewGlobalRef(env, (*env)->FindClass(env,
						JNI_PACKAGE_STRING "/OcpaVpnService"));
	android_ocpavpnservice_builder_class =
				(*env)->NewGlobalRef(env, (*env)->FindClass(env,
						JNI_PACKAGE_STRING "/OcpaVpnService$BuilderAdapter"));
	android_routerloop_class =
				(*env)->NewGlobalRef(env, (*env)->FindClass(env,
						JNI_PACKAGE_STRING "/RouterLoop"));
	android_ipsectunnel_class =
				(*env)->NewGlobalRef(env, (*env)->FindClass(env,
						JNI_PACKAGE_STRING "/IpSecTunnel"));

	jversion = (*env)->FindClass(env, "android/os/Build$VERSION");
	jsdk_int = (*env)->GetStaticFieldID(env, jversion, "SDK_INT", "I");
	android_sdk_version = (*env)->GetStaticIntField(env, jversion, jsdk_int);

	return JNI_VERSION_1_6;
}

/**
 * Called when this library is unloaded by the JVM (which never happens on
 * Android)
 */
void JNI_OnUnload(JavaVM *vm, void *reserved) {
	androidjni_threadlocal->destroy(androidjni_threadlocal);
}

