/*
 * pcap_output.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef PCAP_OUTPUT_H_
#define PCAP_OUTPUT_H_

#include <stdint.h>
#include <jni.h>

typedef struct pcap_output_t pcap_output_t;

struct pcap_output_t {
	jobject jpcap;
};

pcap_output_t* pcap_output_init(jobject jpcap);
void pcap_output_reset(pcap_output_t* output, jobject jpcap_new);
void pcap_output_write(pcap_output_t* output, uint8_t* buff, int32_t offs, int32_t len);
void pcap_output_flush(pcap_output_t* output);
void pcap_output_close(pcap_output_t* output);
void pcap_output_destroy(pcap_output_t* output);

#endif /* PCAP_OUTPUT_H_ */
