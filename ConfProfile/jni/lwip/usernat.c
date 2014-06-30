/*
 * usernat.c
 *
 *      Author: Dmitry Vorobiev
 */

#include <android/log.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <fcntl.h>

#define LOG_TAG "usernat"

static void (*log_print)(int level, char* tag, char* fmt, ...) = NULL;

static void log_android(int level, char* tag, char* fmt, ...) {
	va_list args;
	va_start(args, fmt);
	__android_log_vprint(level, tag, fmt, args);
	va_end(args);
}

static void log_remote(int level, char* tag, char* fmt, ...) {
	//TODO: send logs to remote receiver
}

static void usage() {
	log_print(ANDROID_LOG_INFO, LOG_TAG, "usage: usernat <ctrl_unix_socket>");
}

static void rcvd_cmd(int sock) {
	unsigned char bytes[2];
	if (recv(sock, &bytes[0], 1, 0) != 1 ||
			recv(sock, &bytes[1], 1, 0) != 1) {
		log_print(ANDROID_LOG_FATAL, LOG_TAG, "Can't get argument length");
		exit(4);
	} else {
		int length = bytes[0] << 8 | bytes[1];
		int offset = 0;

		if (length == 0xFFFF) {
			return;
		}
		char* cmd = malloc(length + 1);
		while (offset < length) {
			int n = recv(sock, &cmd[offset], length - offset, 0);
			if (n > 0) {
				offset += n;
			} else {
				log_print(ANDROID_LOG_FATAL, LOG_TAG, "Cannot get argument value");
				exit(5);
			}
		}
		cmd[length] = 0;
		//TODO: parse command and save it
		free(cmd);
	}
}

static void send_cmd(int sock, char* cmd, int len) {

}

int main(int argc, char **argv) {
	log_print = log_android;

	if(argc != 2) {
		usage();
		exit(1);
	}

	int control;
	int i;
	struct sockaddr_un remote;
	int addr_len;

	remote.sun_family = AF_UNIX;
	strcpy(remote.sun_path, (*argv)[1]);
	addr_len = strlen(remote.sun_path) + sizeof(remote.sun_family);

	if((control = socket(AF_UNIX, SOCK_STREAM, 0)) == -1) {
		exit(2);
	}

	log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Connecting to control socket");
	int tries = 5;
	while(tries > 0) {
		if(connect(control, (struct sockaddr *) &remote, addr_len) == -1) {
			log_print(ANDROID_LOG_WARN, LOG_TAG, "Can't connect to control socket, retry after 500ms");
			usleep(500);
		}
		tries --;
	}

	if(tries == 0) {
		log_print(ANDROID_LOG_FATAL, LOG_TAG, "Can't connect to control socket");
		exit(3);
	}
	fcntl(control, F_SETFD, FD_CLOEXEC);

	rcvd_cmd(control);

	log_print(ANDROID_LOG_DEBUG, LOG_TAG, "test");
	log_print = log_remote;
}
