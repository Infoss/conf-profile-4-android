/*
 * pcap_output.c
 *
 *      Author: Dmitry Vorobiev
 */

#include "android_jni.h"
#include "pcap_output.h"

#define JNI_UTIL_PACKAGE no_infoss_confprofile_util

pcap_output_t* pcap_output_init(jobject jpcap) {
	pcap_output_t* result = (pcap_output_t*) malloc(sizeof(pcap_output_t));
	if(result == NULL) {
		return result;
	}

	result->jpcap = NULL;

	JNIEnv* jnienv;
	androidjni_attach_thread(&jnienv);
	if(jpcap != NULL) {
		result->jpcap = (*jnienv)->NewGlobalRef(jnienv, jpcap);
	}
	androidjni_detach_thread();

	return result;
}

void pcap_output_reset(pcap_output_t* output, jobject jpcap_new) {
	if(output == NULL) {
		return;
	}

	pcap_output_flush(output);
	pcap_output_close(output);

	output->jpcap = NULL;

	jobject jpcap = output->jpcap;
	JNIEnv* jnienv;
	androidjni_attach_thread(&jnienv);
	if(jpcap_new != NULL) {
		output->jpcap = (*jnienv)->NewGlobalRef(jnienv, jpcap);
	}
	androidjni_detach_thread();
}

void pcap_output_write(pcap_output_t* output, uint8_t* buff, int32_t offs, int32_t len) {
	if(output == NULL) {
		return;
	}
}

void pcap_output_flush(pcap_output_t* output) {
	if(output == NULL) {
		return;
	}
}

void pcap_output_close(pcap_output_t* output) {
	if(output == NULL) {
		return;
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
	androidjni_attach_thread(&jnienv);
	if(output->jpcap != NULL) {
		(*jnienv)->DeleteGlobalRef(jnienv, output->jpcap);
		output->jpcap = NULL;
	}
	androidjni_detach_thread();


	free(output);
}
