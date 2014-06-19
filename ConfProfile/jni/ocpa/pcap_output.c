/*
 * pcap_output.c
 *
 *      Author: Dmitry Vorobiev
 */

#include "pcap_output.h"

#define UTIL_PACKAGE no_infoss_confprofile_util

pcap_output_t* pcap_output_init(jobject jpcap) {
	pcap_output_t* result = (pcap_output_t*) malloc(sizeof(pcap_output_t));
	if(result == NULL) {
		return result;
	}

	result->jpcap = jpcap;

	return result;
}

void pcap_output_write(pcap_output_t* output, uint8_t* buff, int32_t offs, int32_t len) {

}

void pcap_output_flush(pcap_output_t* output) {

}

void pcap_output_close(pcap_output_t* output) {

}

void pcap_output_destroy(pcap_output_t* output) {

}
