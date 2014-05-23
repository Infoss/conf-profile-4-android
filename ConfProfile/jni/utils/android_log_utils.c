/*
 * android_log_utils.c
 *
 *      Author: Dmitry Vorobiev
 */

#include <stdio.h>
#include <string.h>
#include "android_log_utils.h"

inline void log_dump_packet(const char *tag, uint8_t* buff, int len) {
	LOGD(tag, "Packet buffer dump (%d byte(s) at %p):", len, buff);
	uint8_t local[49];
	int i;
	int pos = 0;
	while(pos < len) {
		memset(&local, 0, sizeof(local));
		int cnt = 16;
		if(pos + 16 > len) {
			cnt = len - pos;
		}

		for(i = 0; i < cnt; i++) {
			sprintf((uint8_t*)(&local) + (i * 3), "%02x ", buff[pos]);
			pos++;
		}

		LOGD(tag, "%s", (char*) &local);
	}
}
