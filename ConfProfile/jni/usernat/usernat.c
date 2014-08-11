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
#include <linux/in.h>
#include <sys/un.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include <poll.h>
#include <signal.h>
#include <sys/wait.h>
#include <sys/time.h>
#include <sys/resource.h>

#define LOG_TAG "usernat"

/**
 * Usernat uses the following protocol to communicate with OCPA:
 *
 * Set socat path
 * in: socat_path <full_socat_execuable_path>
 * out: resp <error_code>
 * error codes:
 *    0 - success
 *   -1 = failure
 *
 * Set accept socket descriptor (local relay)
 * in: accept_fd
 * ancillary data: socket descriptor
 * out: resp <error_code>
 * error codes:
 *    0 - success
 *   -1 - failure
 *
 * Set connect socket descriptor (outgoing connection)
 * in: connect_fd
 * ancillary data: socket descriptor
 * out: resp <error_code>
 * error codes:
 *    0 - success
 *   -1 - failure
 *
 * Fork and execute socat with provided remote address
 * in: socat <ip>:<port>
 * out: resp <error_code>
 * error codes:
 *    0 - success
 *   -1 - fork failed
 */

#define EXIT_NO_MEMORY      1
#define EXIT_CMD_SOCK_CONN  2
#define EXIT_CMD_PEER_ERROR 3
#define EXIT_CMD_PEER_HUP   4
#define EXIT_CMD_POLL_ERROR 5
#define EXIT_EXEC_FAILED    6

#define SOCAT_PATH_BUFF_SIZE 256

static char **envdata;
static int control;
static char* bytes;
static int fds[2];
static int accept_fd;
static int connect_fd;
static char socat_path[SOCAT_PATH_BUFF_SIZE];
static const char* response_fmt = "resp %d\0";
static const char* terminated_fmt = "terminated %d\0";
static sigset_t signal_block_set;

//TODO: kill all children processes on terminate

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

static void sigchld_handler(int sig) {
	int status;
	pid_t pid;

	char buff[32];
	memset(buff, 0, sizeof(buff));

	pid = waitpid(-1, &status, WNOHANG);

	if(pid > 0) {
		if(WIFEXITED(status)) {
			log_android(ANDROID_LOG_DEBUG, LOG_TAG, "Child exited with error code %d", WEXITSTATUS(status));
		} else if(WIFSIGNALED(status)) {
			log_android(ANDROID_LOG_DEBUG, LOG_TAG, "Child exited by signal %d", WTERMSIG(status));
		} else {
			log_android(ANDROID_LOG_DEBUG, LOG_TAG, "Child exited by unknown reason");
		}

		snprintf(buff, sizeof(buff), terminated_fmt, pid);
		send_cmd(control, buff, strlen(buff));
	}

}

static void usage() {
	log_print(ANDROID_LOG_INFO, LOG_TAG, "usage: usernat <ctrl_unix_socket>");
}

static int find_cmd(const char* cmd, char* buff, int len) {
	int cmd_len = strlen(cmd);
	if(len < cmd_len) {
		return -1;
	}

	if(strncmp(buff, cmd, cmd_len) == 0 && (buff[cmd_len] == '\0' || buff[cmd_len] == ' ')) {
		return cmd_len;
	}

	return -1;
}

static void process_socat_path(char* cmd, int len) {
	char buff[18];
	memset(buff, 0, sizeof(buff));

	cmd++; //cmd pointed to <space> after a command before
	len--;

	if(len > SOCAT_PATH_BUFF_SIZE - 1) {
		snprintf(buff, sizeof(buff), response_fmt, -1);
		send_cmd(control, buff, strlen(buff));
		return;
	}

	memset(socat_path, 0, SOCAT_PATH_BUFF_SIZE);
	memcpy(socat_path, cmd, len);

	snprintf(buff, sizeof(buff), response_fmt, 0);
	send_cmd(control, buff, strlen(buff));
}

static void process_accept_fd() {
	char buff[18];

	accept_fd = fds[0];

	memset(buff, 0, sizeof(buff));
	snprintf(buff, sizeof(buff), response_fmt, 0);
	send_cmd(control, buff, strlen(buff));
}

static void process_connect_fd() {
	char buff[18];

	connect_fd = fds[0];

	memset(buff, 0, sizeof(buff));
	snprintf(buff, sizeof(buff), response_fmt, 0);
	send_cmd(control, buff, strlen(buff));
}

static void process_socat(char* cmd, int len) {
	char buff[18];
	memset(buff, 0, sizeof(buff));

	cmd++; //cmd pointed to <space> after a command before
	len--;

	pid_t child = fork();

	if(child == -1) {
		//fork error
		snprintf(buff, sizeof(buff), response_fmt, child);
		log_android(ANDROID_LOG_ERROR, LOG_TAG, "Error while fork() %d: %s", errno, strerror(errno));
		send_cmd(control, buff, strlen(buff));

		shutdown(accept_fd, SHUT_RDWR);
		shutdown(connect_fd, SHUT_RDWR);

		accept_fd = -1;
		connect_fd = -1;
		return;
	} else if(child > 0) {
		//parent
		snprintf(buff, sizeof(buff), response_fmt, child);
		send_cmd(control, buff, strlen(buff));

		close(accept_fd);
		close(connect_fd);
		accept_fd = -1;
		connect_fd = -1;
		return;
	}
	//child == 0, it's a child
	char buff_from[64];
	char buff_to[64];
	memset(buff_from, 0, sizeof(buff_from));
	memset(buff_to, 0, sizeof(buff_to));
	//"-d", "-d", "-d", "-d",
	char* params[] = {"socat", buff_from, buff_to};

	snprintf(buff_from, sizeof(buff_from), "SOCKET-FD-ACCEPT:%d", accept_fd);
	snprintf(buff_to, sizeof(buff_to), "SOCKET-FD-CONNECT:%d:%s", connect_fd, cmd);
	log_android(ANDROID_LOG_ERROR, LOG_TAG, "preparing to execute %s %s %s", socat_path, buff_from, buff_to);

	struct sockaddr_in sa;
	int sa_len = sizeof(sa);
	int accepted_sock = accept(accept_fd, (struct sockaddr*) &sa, &sa_len);
	if(accepted_sock == -1) {
		log_android(ANDROID_LOG_ERROR, LOG_TAG, "can't accept() %d: %s", errno, strerror(errno));
		shutdown(accept_fd, SHUT_RDWR);
		shutdown(connect_fd, SHUT_RDWR);
		exit(EXIT_EXEC_FAILED);
	}
	log_android(ANDROID_LOG_DEBUG, LOG_TAG, "accept() returned %d", accepted_sock);
	close(accept_fd);

	struct pollfd* pollfds = malloc(sizeof(struct pollfd) * 2);
	if(pollfds == NULL) {
		log_android(ANDROID_LOG_ERROR, LOG_TAG, "can't malloc() space for poll fds");
		exit(EXIT_EXEC_FAILED);
	}

	uint8_t* bytebuff = malloc(1500);
	if(bytebuff == NULL) {
		log_android(ANDROID_LOG_ERROR, LOG_TAG, "can't malloc() space for byte buffer");
		exit(EXIT_EXEC_FAILED);
	}

	pollfds[0].events = POLLIN | POLLPRI;
	pollfds[0].revents = 0;
	pollfds[0].fd = accepted_sock;
	pollfds[1].events = POLLIN | POLLPRI;
	pollfds[1].revents = 0;
	pollfds[1].fd = connect_fd;

	while(true) {
		int res = poll(pollfds, 2, 500);
		if(res > 0) {
			if((pollfds[0].revents & POLLIN) != 0) {
				int size = read(pollfds[0].fd, bytebuff, 1500);
				if(size < 0) {
					log_android(ANDROID_LOG_ERROR, LOG_TAG, "error while read(%d) %d: %s",
							pollfds[0].fd, errno, strerror(errno));
					close(pollfds[0].fd);
					close(pollfds[1].fd);
					exit(EXIT_EXEC_FAILED);
				} else if(size > 0) {
					log_android(ANDROID_LOG_DEBUG, LOG_TAG, "read(%d) returned %d", pollfds[0].fd, size);
					size = write(pollfds[1].fd, bytebuff, size);
					if(size < 0) {
						log_android(ANDROID_LOG_ERROR, LOG_TAG, "error while write(%d) %d: %s",
								pollfds[0].fd, errno, strerror(errno));
						close(pollfds[0].fd);
						close(pollfds[1].fd);
						exit(EXIT_EXEC_FAILED);
					}
					log_android(ANDROID_LOG_DEBUG, LOG_TAG, "write(%d) returned %d", pollfds[1].fd, size);
				}
			}

			if((pollfds[1].revents & POLLIN) != 0) {
				int size = read(pollfds[1].fd, bytebuff, 1500);
				if(size < 0) {
					log_android(ANDROID_LOG_ERROR, LOG_TAG, "error while read(%d) %d: %s",
							pollfds[0].fd, errno, strerror(errno));
					close(pollfds[0].fd);
					close(pollfds[1].fd);
					exit(EXIT_EXEC_FAILED);
				} else if(size > 0) {
					log_android(ANDROID_LOG_DEBUG, LOG_TAG, "read(%d) returned %d", pollfds[1].fd, size);
					size = write(pollfds[0].fd, bytebuff, size);
					if(size < 0) {
						log_android(ANDROID_LOG_ERROR, LOG_TAG, "error while write(%d) %d: %s",
								pollfds[0].fd, errno, strerror(errno));
						close(pollfds[0].fd);
						close(pollfds[1].fd);
						exit(EXIT_EXEC_FAILED);
					}
					log_android(ANDROID_LOG_DEBUG, LOG_TAG, "write(%d) returned %d", pollfds[0].fd, size);
				}
			}

			if((pollfds[0].revents & POLLHUP) != 0) {
				log_android(ANDROID_LOG_DEBUG, LOG_TAG, "POLLHUP from %d (accepted)", pollfds[0].fd);
				close(pollfds[0].fd);
				close(pollfds[1].fd);
				exit(EXIT_SUCCESS);
			}

			if((pollfds[1].revents & POLLHUP) != 0) {
				log_android(ANDROID_LOG_DEBUG, LOG_TAG, "POLLHUP from %d (connected)", pollfds[1].fd);
				close(pollfds[0].fd);
				close(pollfds[1].fd);
				exit(EXIT_SUCCESS);
			}
		} else if(res < 0) {
			log_android(ANDROID_LOG_ERROR, LOG_TAG, "poll() returned error %d: %s", errno, strerror(errno));
			break;
		}

		//TODO: processing closed connections
	}

	log_android(ANDROID_LOG_ERROR, LOG_TAG, "Error while crossovering sockets %d: %s", errno, strerror(errno));

	shutdown(pollfds[0].fd, SHUT_RDWR);
	shutdown(pollfds[1].fd, SHUT_RDWR);
	exit(EXIT_EXEC_FAILED);
}

static void parse_resp(char* cmd, int len) {
	//log_remote(ANDROID_LOG_INFO, LOG_TAG, "Received response (%d bytes): %s", len, cmd);
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

	param_offs = find_cmd("socat_path", cmd, len);
	if(param_offs != -1) {
		process_socat_path(cmd + param_offs, len - param_offs);
		return;
	}

	param_offs = find_cmd("accept_fd", cmd, len);
	if(param_offs != -1) {
		process_accept_fd();
		return;
	}

	param_offs = find_cmd("connect_fd", cmd, len);
	if(param_offs != -1) {
		process_connect_fd();
		return;
	}

	param_offs = find_cmd("socat", cmd, len);
	if(param_offs != -1) {
		process_socat(cmd + param_offs, len - param_offs);
		return;
	}



	if(strncmp(cmd, "halt\0", strlen("halt") + 1) == 0) {
		log_remote(ANDROID_LOG_INFO, LOG_TAG, "Received 'halt'. Exiting with error code 0.");
		exit(EXIT_SUCCESS);
	}

	log_remote(ANDROID_LOG_INFO, LOG_TAG, "no cmd was found");
	log_android(ANDROID_LOG_INFO, LOG_TAG, "no cmd was found");
}

static int socket_process_cmsg(struct msghdr* pMsg) {
    struct cmsghdr *cmsgptr;

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
                exit(EXIT_CMD_PEER_ERROR);
            }

            int i;
            for(i = 0; i < count; i++) {
            	if(count >= 2) {
            		log_android(ANDROID_LOG_WARN, LOG_TAG, "skipping fd=%d due to insufficient storage", pDescriptors[i]);
            		continue;
            	}

            	fds[i] = pDescriptors[i];
            }
        }
    }

    return 0;
}

static ssize_t rcvd_cmd_with_fds(int fd, void *buffer, size_t len) {
    ssize_t ret;
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

    sigset_t old_sigset;
    sigprocmask(SIG_BLOCK, &signal_block_set, &old_sigset);
    do {
        ret = recvmsg(fd, &msg, MSG_NOSIGNAL);
    } while (ret < 0 && errno == EINTR);
    sigprocmask(SIG_SETMASK, &old_sigset, NULL);

    if (ret < 0 && errno == EPIPE) {
    	log_android(ANDROID_LOG_ERROR, LOG_TAG, "End of stream %d: %s", errno, strerror(errno));
    	log_remote(ANDROID_LOG_ERROR, LOG_TAG, "End of stream %d: %s", errno, strerror(errno));
    	exit(EXIT_CMD_PEER_HUP);
    }

    if (ret < 0) {
    	log_android(ANDROID_LOG_ERROR, LOG_TAG, "Error %d: %s", errno, strerror(errno));
    	log_remote(ANDROID_LOG_ERROR, LOG_TAG, "Error %d: %s", errno, strerror(errno));
        exit(EXIT_CMD_PEER_ERROR);
    }

    if ((msg.msg_flags & (MSG_CTRUNC | MSG_OOB | MSG_ERRQUEUE)) != 0) {
        log_android(ANDROID_LOG_ERROR, LOG_TAG, "Unexpected error or truncation during recvmsg() %d: %s", errno, strerror(errno));
		log_remote(ANDROID_LOG_ERROR, LOG_TAG, "Unexpected error or truncation during recvmsg() %d: %s", errno, strerror(errno));
		exit(EXIT_CMD_PEER_ERROR);
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

		sigset_t old_sigset;
		sigprocmask(SIG_BLOCK, &signal_block_set, &old_sigset);
		send(sock, buff, total_len + 2, 0);
		sigprocmask(SIG_SETMASK, &old_sigset, NULL);

		free(buff);
	}
}

int main(int argc, char **argv, char *envp[]) {
	envdata = envp;

	errno = 0;
	int priority = getpriority(PRIO_PROCESS, 0);
	if(priority == -1 && errno != 0) {
		log_android(ANDROID_LOG_WARN, LOG_TAG, "can't getpriority() %d: %s", errno, strerror(errno));
	} else {
		if(priority + 10 <= PRIO_MAX) {
			priority += 10;
		}
		if(setpriority(PRIO_PROCESS, 0, priority) == -1) {
			log_android(ANDROID_LOG_WARN, LOG_TAG, "can't setpriority() %d: %s", errno, strerror(errno));
		}
	}

	log_print = log_android;
	accept_fd = -1;
	connect_fd = -1;
	memset(socat_path, 0, SOCAT_PATH_BUFF_SIZE);

	if(argc != 2) {
		usage();
		exit(EXIT_SUCCESS);
	}

	sigaddset(&signal_block_set, SIGCHLD); //we'll block SIGCHLD while performing send/receive actions

	struct sigaction sigact;
	sigemptyset(&sigact.sa_mask);
	sigact.sa_flags = 0;
	sigact.sa_handler = sigchld_handler;
	sigaction(SIGCHLD, &sigact, NULL);

	//"magic" parameter test performs test run
	if(strncmp("--test", argv[1], strlen(argv[1])) == 0) {
		printf("%s started in test mode\n", argv[0]);
		fprintf(stdout, "stdout test");
		fprintf(stderr, "stderr test");
		log_print = log_android;
		memset(socat_path, 0, SOCAT_PATH_BUFF_SIZE);
		strcpy(socat_path, "/data/data/no.infoss.confprofile/cache/ocpasocat");

		char buff_from[64];
		char buff_to[64];
		memset(buff_from, 0, sizeof(buff_from));
		memset(buff_to, 0, sizeof(buff_to));
		char* params[] = {"socat", "-d", "-d", "-d", "-d", buff_from, buff_to};

		struct sockaddr_in test_sa;
		accept_fd = socket(AF_INET, SOCK_STREAM, 0);
		if(accept_fd < 0) {
			printf("accept_fd = socket() failed with code %d: %s\n", errno, strerror(errno));
		}

		connect_fd = socket(AF_INET, SOCK_STREAM, 0);
		if(connect_fd < 0) {
			printf("connect_fd = socket() failed with code %d: %s\n", errno, strerror(errno));
		}

		test_sa.sin_family = AF_INET;
		test_sa.sin_addr.s_addr = INADDR_ANY;
		test_sa.sin_port = 0;
		if(bind(accept_fd, (struct sockaddr*) &test_sa, sizeof(test_sa)) < 0) {
			printf("bind(accept_fd) failed with code %d: %s\n", errno, strerror(errno));
		}

		snprintf(buff_from, sizeof(buff_from), "SOCKET-FD-ACCEPT:%d", accept_fd);
		snprintf(buff_to, sizeof(buff_to), "SOCKET-FD-CONNECT:%d:192.0.43.9:80", connect_fd);
		printf("preparing to execute %s %s %s\n", socat_path, buff_from, buff_to);

		execve(socat_path, params, envdata);

		printf("execve() failed with code %d: %s\n", errno, strerror(errno));

		//normally unreachable
		return EXIT_EXEC_FAILED;
	}

	struct sockaddr_un remote;
	int addr_len;

	remote.sun_family = AF_UNIX;
	strcpy(remote.sun_path, argv[1]);
	addr_len = strlen(remote.sun_path) + sizeof(remote.sun_family);

	if((control = socket(AF_UNIX, SOCK_STREAM, 0)) == -1) {
		exit(EXIT_CMD_SOCK_CONN);
	}

	/*
	if(fcntl(control, F_SETFL, O_NONBLOCK) < 0) {
		log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Failed to set O_NONBLOCK to socket with code %d: %s", errno, strerror(errno));
	}
	*/

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
		exit(EXIT_CMD_SOCK_CONN);
	}

	if(fcntl(control, F_SETFD, FD_CLOEXEC) < 0) {
		log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Failed to set FD_CLOEXEC to socket with code %d: %s", errno, strerror(errno));
	}

	log_print(ANDROID_LOG_DEBUG, LOG_TAG, "test");
	log_remote(ANDROID_LOG_DEBUG, LOG_TAG, "test");
	//log_print = log_remote;

	bytes = malloc(sizeof(char) * 65538);
	if(bytes == NULL) {
		log_print(ANDROID_LOG_DEBUG, LOG_TAG, "insufficient memory");
		exit(EXIT_NO_MEMORY);
	}

	int res = 0;
	struct pollfd poll_struct;
	poll_struct.fd = control;

	while(true) {
		poll_struct.events = POLLIN | POLLPRI; //POLLERR | POLLHUP will be received automatically
		poll_struct.revents = 0;
		res = poll(&poll_struct, 1, 1000);

		if(res > 0) {
			log_print(ANDROID_LOG_DEBUG,
					LOG_TAG,
					"Start receiving data events=%08x, revents=%08x",
					poll_struct.events,
					poll_struct.revents);

			if((poll_struct.revents & POLLHUP) != 0) {
				log_print(ANDROID_LOG_WARN, LOG_TAG, "Remote peer closed connection");
				exit(EXIT_CMD_PEER_HUP);
			}

			if((poll_struct.revents & POLLERR) != 0) {
				log_print(ANDROID_LOG_ERROR, LOG_TAG, "Remote peer error");
				exit(EXIT_CMD_PEER_ERROR);
			}

			//receive length first
			fds[0] = -1;
			fds[1] = -1;

			int length = rcvd_cmd_with_fds(control, bytes, 2);
			log_print(ANDROID_LOG_DEBUG, LOG_TAG, "rcvd_cmd_with_fds(): res=%d", length);
			if(length == 0) {
				continue;
			}
			length = bytes[0] << 8 | bytes[1];
			if(length == 0xFFFF) {
				log_print(ANDROID_LOG_DEBUG, LOG_TAG, "length is 0xffff, this is a legal way to halt");
				exit(EXIT_SUCCESS);
			}
			//receive body
			rcvd_cmd_with_fds(control, bytes + 2, length);
			bytes[length + 2] = 0;
			parse_cmd(bytes + 2, length);
		} else if(res == 0) {
			send_cmd(control, "ping", strlen("ping"));
		} else {
			if(errno == EINTR) {
				//we were interrupted probably by SIGCHLD, just continue
				continue;
			}

			log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Error code %d: %s", errno, strerror(errno));
			exit(EXIT_CMD_POLL_ERROR);
		}
	}

	free(bytes);
	return EXIT_SUCCESS;
}
