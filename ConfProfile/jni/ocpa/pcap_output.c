/*
 * pcap_output.c
 *
 *      Author: Dmitry Vorobiev
 */

#include "android_log_utils.h"
#include "android_jni.h"
#include "pcap_output.h"

#define LOG_TAG "pcap_output.c"
#define JNI_UTIL_PACKAGE no_infoss_confprofile_util
#define JNI_UTIL_PACKAGE_STRING "no/infoss/confprofile/util"

pcap_output_t* pcap_output_init(jobject jpcap) {
	pcap_output_t* result = (pcap_output_t*) malloc(sizeof(pcap_output_t));
	if(result == NULL) {
		return result;
	}

	result->jpcap = NULL;

	JNIEnv* jnienv;
	bool need_detach = androidjni_attach_thread(&jnienv);
	result->_jclass =
					(*jnienv)->NewGlobalRef(jnienv,
							(*jnienv)->FindClass(jnienv, JNI_UTIL_PACKAGE_STRING "/PcapOutputStream"));

	result->_jwriteID = (*jnienv)->GetMethodID(jnienv, result->_jclass, "writePacket", "([BII)V");
	result->_jflushID = (*jnienv)->GetMethodID(jnienv, result->_jclass, "flush", "()V");
	result->_jcloseID = (*jnienv)->GetMethodID(jnienv, result->_jclass, "close", "()V");

	if(jpcap != NULL) {
		result->jpcap = (*jnienv)->NewGlobalRef(jnienv, jpcap);
	}

	if(need_detach) {
		androidjni_detach_thread();
	}

	return result;
}

void pcap_output_reset(pcap_output_t* output, jobject jpcap_new) {
	LOGD(LOG_TAG, "pcap_output_reset(%p, %p)", output, jpcap_new);
	if(output == NULL) {
		LOGE(LOG_TAG, "returning from pcap_output_reset()");
		return;
	}

	pcap_output_flush(output);
	pcap_output_close(output);

	jobject jpcap_old = output->jpcap;
	output->jpcap = NULL;

	jobject jpcap = output->jpcap;
	JNIEnv* jnienv;
	bool need_detach = androidjni_attach_thread(&jnienv);

	if(jpcap_old != NULL) {
		(*jnienv)->DeleteGlobalRef(jnienv, output->jpcap);
	}

	if(jpcap_new != NULL) {
		output->jpcap = (*jnienv)->NewGlobalRef(jnienv, jpcap);
	}

	if(need_detach) {
		androidjni_detach_thread();
	}
}

void pcap_output_write(pcap_output_t* output, uint8_t* buff, int32_t offs, int32_t len) {
	LOGD(LOG_TAG, "pcap_output_write(%p, %p, %d, %d)", output, buff, offs, len);
	if(output == NULL || output->_jclass == NULL || output->_jwriteID == NULL || offs < 0) {
		LOGE(LOG_TAG, "returning from pcap_output_write()");
		return;
	}

	JNIEnv* jnienv;
	bool need_detach = androidjni_attach_thread(&jnienv);
	jbyteArray jarr = (*jnienv)->NewByteArray(jnienv, len);
	LOGD(LOG_TAG, "SetByteArrayRegion(%p, %p, %d, %d, %p)", jnienv, jarr, 0, len, buff + offs);
	(*jnienv)->SetByteArrayRegion(jnienv, jarr, 0, len, buff + offs);
	LOGD(LOG_TAG, "CallVoidMethod(%p, %p, %p, %p, %d, %d)", jnienv, output->jpcap, output->_jwriteID, jarr, 0, len);
	(*jnienv)->CallVoidMethod(jnienv, output->jpcap, output->_jwriteID, jarr, 0, len);
	if ((*jnienv)->ExceptionOccurred(jnienv)){
		(*jnienv)->ExceptionDescribe(jnienv);
		(*jnienv)->ExceptionClear(jnienv);
	}
	(*jnienv)->DeleteLocalRef(jnienv, jarr);
	if(need_detach) {
		androidjni_detach_thread();
	}
}

void pcap_output_flush(pcap_output_t* output) {
	LOGD(LOG_TAG, "pcap_output_flush(%p)", output);
	if(output == NULL || output->jpcap == NULL) {
		LOGE(LOG_TAG, "returning from pcap_output_flush()");
		return;
	}

	JNIEnv* jnienv;
	bool need_detach = androidjni_attach_thread(&jnienv);
	(*jnienv)->CallVoidMethod(jnienv, output->jpcap, output->_jflushID);
	if(need_detach) {
		androidjni_detach_thread();
	}
}

void pcap_output_close(pcap_output_t* output) {
	LOGD(LOG_TAG, "pcap_output_close(%p)", output);
	if(output == NULL || output->jpcap == NULL) {
		LOGE(LOG_TAG, "returning from pcap_output_close()");
		return;
	}

	JNIEnv* jnienv;
	bool need_detach = androidjni_attach_thread(&jnienv);
	(*jnienv)->CallVoidMethod(jnienv, output->jpcap, output->_jcloseID);
	if(need_detach) {
		androidjni_detach_thread();
	}
}

/**
 * Call this method only after pcap_output_reset(output, NULL)
 */
void pcap_output_destroy(pcap_output_t* output) {
	if(output == NULL) {
		return;
	}

	JNIEnv* jnienv;
	bool need_detach = androidjni_attach_thread(&jnienv);
	if(output->_jclass != NULL) {
		(*jnienv)->DeleteGlobalRef(jnienv, output->_jclass);
		output->_jclass = NULL;
	}
	output->_jwriteID = NULL;
	output->_jflushID = NULL;
	output->_jcloseID = NULL;

	if(output->jpcap != NULL) {
		(*jnienv)->DeleteGlobalRef(jnienv, output->jpcap);
		output->jpcap = NULL;
	}

	if(need_detach) {
		androidjni_detach_thread();
	}

	free(output);
}
