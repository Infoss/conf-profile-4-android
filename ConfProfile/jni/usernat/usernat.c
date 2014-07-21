/*
 * usernat.c
 *
 *      Author: Dmitry Vorobiev
 */

#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include <poll.h>

#define LOG_TAG "usernat"

static int control;
static int fds[2];

static void (*log_print)(int level, char* tag, char* fmt, ...) = NULL;

static void send_cmd(int sock, char* cmd, int len);

static void log_android(int level, char* tag, char* fmt, ...) {
	va_list args;
	va_start(args, fmt);
	__android_log_vprint(level, tag, fmt, args);
	va_end(args);
}

static char* bytes;

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

		written = snprintf(log_buff + offs, log_buff_size - offs, "[%s] ", tag);
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

static void process_socat(char* cmd, int len, int* fds) {
	pid_t child = fork();
	const char* response_fmt = "resp %d\0";
	char buff[18];
	memset(buff, 0, sizeof(buff));

	if(child == -1) {
		//fork error
		snprintf(buff, sizeof(buff), response_fmt, child);
		send_cmd(control, buff, strlen(buff));
		return;
	} else if(child > 0) {
		//parent
		snprintf(buff, sizeof(buff), response_fmt, child);
		send_cmd(control, buff, strlen(buff));
		return;
	}
	//child == 0, it's a child
	char buff_from[32];
	char buff_to[32];
	memset(buff_from, 0, sizeof(buff_from));
	memset(buff_to, 0, sizeof(buff_to));
	char* params[] = {buff_from, buff_to};

	snprintf(buff_from, sizeof(buff_from), "socket-fd-accept:%d", fds[0]);
	snprintf(buff_from, sizeof(buff_from), "socket-fd-accept:%d:%s", fds[1], cmd);

	execv("ocpasocat", params);

	log_android(ANDROID_LOG_ERROR, LOG_TAG, "Error while execv()");
	exit(-1);
}

static void parse_resp(char* cmd, int len) {
	log_remote(ANDROID_LOG_INFO, LOG_TAG, "Received response (%d bytes): %s", len, cmd);
}

static void parse_cmd(char* cmd, int len, int* fds) {
	if(cmd == NULL) {
		return;
	}

	int param_offs;

	param_offs = find_cmd("resp", cmd, len);
	if(param_offs != -1) {
		parse_resp(cmd + param_offs, len - param_offs);
		return;
	}

	param_offs = find_cmd("socat", cmd, len);
	if(param_offs != -1) {
		process_socat(cmd + param_offs, len - param_offs, fds);
	}

	log_remote(ANDROID_LOG_INFO, LOG_TAG, "Received command (%d bytes): %s", len, cmd);

	if(strncmp(cmd, "halt\0", strlen("halt") + 1) == 0) {
		log_remote(ANDROID_LOG_INFO, LOG_TAG, "Received 'halt'. Exiting with error code 0.");
		exit(0);
	}
}

static int socket_process_cmsg(struct msghdr* pMsg) {
    struct cmsghdr *cmsgptr;
    fds[0] = -1;
    fds[1] = -1;

    for(cmsgptr = CMSG_FIRSTHDR(pMsg);
            cmsgptr != NULL; cmsgptr = CMSG_NXTHDR(pMsg, cmsgptr)) {

        if(cmsgptr->cmsg_level != SOL_SOCKET) {
            continue;
        }

        if(cmsgptr->cmsg_type == SCM_RIGHTS) {
            int *pDescriptors = (int *)CMSG_DATA(cmsgptr);
            int count
                = ((cmsgptr->cmsg_len - CMSG_LEN(0)) / sizeof(int));

            if(count < 0) {
            	log_android(ANDROID_LOG_ERROR, LOG_TAG, "invalid cmsg length");
                exit(-1);
            }

            int i;
            for(i = 0; i < count; i++) {
            	if(count >= 2) {
            		log_android(ANDROID_LOG_WARN, LOG_TAG, "skipping fd=%d due to insufficient storage", pDescriptors[i]);
            	}

            	fds[i] = pDescriptors[i];
            }
        }
    }

    return 0;
}

static ssize_t rcvd_cmd_with_fds(int fd, void *buffer, size_t len) {
    ssize_t ret;
    ssize_t bytesread = 0;
    struct msghdr msg;
    struct iovec iv;
    unsigned char *buf = (unsigned char *)buffer;
    // Enough buffer for a pile of fd's. We throw an exception if
    // this buffer is too small.
    struct cmsghdr cmsgbuf[2 * sizeof(struct cmsghdr) + 0x100];

    memset(&msg, 0, sizeof(msg));
    memset(&iv, 0, sizeof(iv));

    iv.iov_base = buf;
    iv.iov_len = len;

    msg.msg_iov = &iv;
    msg.msg_iovlen = 1;
    msg.msg_control = cmsgbuf;
    msg.msg_controllen = sizeof(cmsgbuf);

    do {
        ret = recvmsg(fd, &msg, MSG_NOSIGNAL);
    } while (ret < 0 && errno == EINTR);

    if (ret < 0 && errno == EPIPE) {
    	log_android(ANDROID_LOG_ERROR, LOG_TAG, "End of stream %d: %s", errno, strerror(errno));
    	log_remote(ANDROID_LOG_ERROR, LOG_TAG, "End of stream %d: %s", errno, strerror(errno));
    	exit(0);
    }

    if (ret < 0) {
    	log_android(ANDROID_LOG_ERROR, LOG_TAG, "Error %d: %s", errno, strerror(errno));
    	log_remote(ANDROID_LOG_ERROR, LOG_TAG, "Error %d: %s", errno, strerror(errno));
        exit(-1);
    }

    if ((msg.msg_flags & (MSG_CTRUNC | MSG_OOB | MSG_ERRQUEUE)) != 0) {
        log_android(ANDROID_LOG_ERROR, LOG_TAG, "Unexpected error or truncation during recvmsg() %d: %s", errno, strerror(errno));
		log_remote(ANDROID_LOG_ERROR, LOG_TAG, "Unexpected error or truncation during recvmsg() %d: %s", errno, strerror(errno));
		exit(-1);
    }

    if (ret >= 0) {
        socket_process_cmsg(&msg);
    }

    return ret;
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

	if(fcntl(control, F_SETFL, O_NONBLOCK) < 0) {
		log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Failed to set O_NONBLOCK to socket with code %d: %s", errno, strerror(errno));
	}

	log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Connecting to control socket");
	int tries = 5;
	while(tries > 0) {
		if(connect(control, (struct sockaddr *) &remote, addr_len) == -1) {
			log_print(ANDROID_LOG_WARN, LOG_TAG, "connect(): error code %d, %s", errno, strerror(errno));
			log_print(ANDROID_LOG_WARN, LOG_TAG, "Can't connect to control socket, retry after 500ms");
			usleep(500);
		} else {
			break;
		}
		tries --;
	}

	if(tries == 0) {
		log_print(ANDROID_LOG_FATAL, LOG_TAG, "Can't connect to control socket %s", argv[1]);
		exit(3);
	}

	if(fcntl(control, F_SETFD, FD_CLOEXEC) < 0) {
		log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Failed to set FD_CLOEXEC to socket with code %d: %s", errno, strerror(errno));
	}

	log_print(ANDROID_LOG_DEBUG, LOG_TAG, "test");
	log_remote(ANDROID_LOG_DEBUG, LOG_TAG, "test");
	//log_print = log_remote;

	bytes = malloc(sizeof(char) * 65537);
	if(bytes == NULL) {
		log_print(ANDROID_LOG_DEBUG, LOG_TAG, "insufficient memory");
		exit(4);
	}

	int res = 0;
	struct pollfd poll_struct;
	poll_struct.fd = control;

	while(true) {
		poll_struct.events = POLLIN | POLLPRI;
		poll_struct.revents = 0;
		res = poll(&poll_struct, 1, 1000);
		log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Event or timeout, res=%d, errno=%d %s", res, errno, strerror(errno));
		if(res > 0) {
			log_print(ANDROID_LOG_DEBUG,
					LOG_TAG,
					"Start receiving data events=%08x, revents=%08x",
					poll_struct.events,
					poll_struct.revents);
			//receive length first
			int length = rcvd_cmd_with_fds(control, bytes, 2);
			log_print(ANDROID_LOG_DEBUG, LOG_TAG, "rcvd_cmd_with_fds(): res=%d, errno=%d %s", length, errno, strerror(errno));
			if(length == 0) {
				continue;
			}
			length = bytes[0] << 8 | bytes[1];
			if(length == 0xFFFF) {
				log_print(ANDROID_LOG_DEBUG, LOG_TAG, "length is 0xffff, halting");
				exit(-1);
			}
			//receive body
			rcvd_cmd_with_fds(control, bytes + 2, length);
			parse_cmd(bytes + 2, length, fds);
		} else if(res == 0) {
			send_cmd(control, "ping", strlen("ping"));
		} else {
			log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Error code %d: %s", errno, strerror(errno));
			exit(-1);
		}
	}

	free(bytes);
	return 0;
}
