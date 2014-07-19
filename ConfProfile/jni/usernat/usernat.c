/*
 * usernat.c
 *
 *      Author: Dmitry Vorobiev
 */

#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <fcntl.h>

#define LOG_TAG "usernat"

static int control;

static void (*log_print)(int level, char* tag, char* fmt, ...) = NULL;

static void send_cmd(int sock, char* cmd, int len);

static void log_android(int level, char* tag, char* fmt, ...) {
	va_list args;
	va_start(args, fmt);
	__android_log_vprint(level, tag, fmt, args);
	va_end(args);
}

static void log_remote(int level, char* tag, char* fmt, ...) {
	unsigned int log_buff_size = 0xfffe;
	char* log_buff = malloc(log_buff_size);
	if(log_buff != NULL) {

		int written = 0;
		unsigned int offs = 0;
		memset(log_buff, 0, log_buff_size);

		written = snprintf(log_buff + offs, log_buff_size - offs, "%s", "log ");
		if(written <= 0) {
			goto send_log;
		}
		offs += written;

		written = snprintf(log_buff + offs, log_buff_size - offs, "[%s]", tag);
		if(written <= 0) {
			goto send_log;
		}
		offs += written;

		va_list args;
		va_start(args, fmt);
		written = vsnprintf(log_buff + offs, log_buff_size - offs, fmt, args);
		va_end(args);

send_log:
		send_cmd(control, log_buff, strlen(log_buff));
		free(log_buff);
	}
}

static void usage() {
	log_print(ANDROID_LOG_INFO, LOG_TAG, "usage: usernat <ctrl_unix_socket>");
}

static int find_cmd(const char* cmd, char* buff, int len) {
	int cmd_len = strlen(cmd);
	if(len <= cmd_len) {
		return -1;
	}

	if(strncmp(buff, cmd, cmd_len) == 0 && (buff[cmd_len] == '\0' || buff[cmd_len] == ' ')) {
		return cmd_len;
	}

	return -1;
}

static void parse_resp(char* cmd, int len) {

}

static void parse_cmd(char* cmd, int len) {
	if(cmd == NULL) {
		return;
	}

	int param_offs;

	param_offs = find_cmd("resp", cmd, len);
	if(param_offs != -1) {
		parse_resp(cmd + param_offs, len - param_offs);
		return;
	}

	if(strncmp(cmd, "halt\0", strlen("halt") + 1) == 0) {
		log_remote(ANDROID_LOG_INFO, LOG_TAG, "Received 'halt'. Exiting with error code 0.");
		exit(0);
	}
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

		parse_cmd(cmd, length + 1);
		free(cmd);
	}
}

static void send_cmd(int sock, char* cmd, int len) {
	if(len < 0 || cmd == NULL) {
		return;
	}

	int total_len = len;
	if(len > 0xfffe) {
		total_len = 0xfffe;
	}

	char* buff = malloc(total_len + 2);
	if(buff != NULL) {
		buff[0] = (total_len >> 8) & 0x00ff;
		buff[1] = total_len & 0x00ff;
		memcpy(buff + 2, cmd, total_len);
		send(sock, buff, total_len + 2, 0);
		free(buff);
	}
}

static void send_cmd_with_fd(int sock, char* cmd, int len, int* fds_to_send, int fds_count) {
	struct msghdr socket_message;
	struct iovec io_vector[1];
	struct cmsghdr *control_message = NULL;
	/* storage space needed for an ancillary element with a paylod of length is CMSG_SPACE(sizeof(length)) */
	char ancillary_element_buffer[CMSG_SPACE(sizeof(int) * fds_count)];
	int available_ancillary_element_buffer_space;

	if(len < 0 || cmd == NULL) {
		return;
	}

	int total_len = len;
	if(len > 0xfffe) {
		total_len = 0xfffe;
	}

	char* buff = malloc(total_len + 2);
	if(buff == NULL) {
		return;
	}

	buff[0] = (total_len >> 8) & 0x00ff;
	buff[1] = total_len & 0x00ff;
	memcpy(buff + 2, cmd, total_len);

	/* at least one vector of one byte must be sent */
	io_vector[0].iov_base = buff;
	io_vector[0].iov_len = total_len + 2;

	/* initialize sock message */
	memset(&socket_message, 0, sizeof(struct msghdr));
	socket_message.msg_iov = io_vector;
	socket_message.msg_iovlen = 1;

	/* provide space for the ancillary data */
	available_ancillary_element_buffer_space = CMSG_SPACE(sizeof(int) * fds_count);
	memset(ancillary_element_buffer, 0, available_ancillary_element_buffer_space);
	socket_message.msg_control = ancillary_element_buffer;
	socket_message.msg_controllen = available_ancillary_element_buffer_space;

	/* initialize a single ancillary data element for fd passing */
	control_message = CMSG_FIRSTHDR(&socket_message);
	control_message->cmsg_level = SOL_SOCKET;
	control_message->cmsg_type = SCM_RIGHTS;
	control_message->cmsg_len = CMSG_LEN(sizeof(int) * fds_count);
	int i;
	for(i = 0; i < fds_count; i++) {
		//*((int *) CMSG_DATA(control_message)) = fd_to_send;
		((int *) CMSG_DATA(control_message))[i] = fds_to_send[i];
	}


	sendmsg(sock, &socket_message, 0);
	free(buff);
}

int main(int argc, char **argv) {
	log_print = log_android;

	if(argc != 2) {
		usage();
		exit(1);
	}

	struct sockaddr_un remote;
	int addr_len;

	remote.sun_family = AF_UNIX;
	strcpy(remote.sun_path, argv[1]);
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

	log_print(ANDROID_LOG_DEBUG, LOG_TAG, "test");
	log_print = log_remote;

	while(true) {
		rcvd_cmd(control);
	}

}
