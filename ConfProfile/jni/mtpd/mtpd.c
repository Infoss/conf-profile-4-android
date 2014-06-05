/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/poll.h>
#include <sys/wait.h>
#include <netdb.h>
#include <signal.h>
#include <unistd.h>
#include <fcntl.h>
#include <time.h>

#ifdef ANDROID_CHANGES
#include <android/log.h>
#include <cutils/sockets.h>
#endif

#ifdef OCPA_CHANGES
#include <sys/un.h>
#include <android/log.h>
#define PPPD "ocpapppd"
#endif

#ifndef PPPD
#define PPPD "pppd"
#endif

#include "mtpd.h"

int the_socket = -1;

extern struct protocol l2tp;
extern struct protocol pptp;
static struct protocol *protocols[] = {&l2tp, &pptp, NULL};
static struct protocol *the_protocol;

static char *interface;
static int pppd_argc;
static char **pppd_argv;
static pid_t pppd_pid;

#if defined(ANDROID_CHANGES) || defined(OCPA_CHANGES)
static char* mtpd_log_tag;
#endif

/* We redirect signals to a pipe in order to prevent race conditions. */
static int signals[2];

static void interrupt(int signal)
{
    write(signals[1], &signal, sizeof(int));
}

static int initialize(int argc, char **argv)
{
    int i;

    for (i = 0; protocols[i]; ++i) {
        struct protocol *p = protocols[i];
        if (argc - 3 >= p->arguments && !strcmp(argv[2], p->name)) {
            log_print(INFO, "Using protocol %s", p->name);
            the_protocol = p;
            break;
        }
    }

    if (!the_protocol) {
        printf("Usages:\n");
        for (i = 0; protocols[i]; ++i) {
            struct protocol *p = protocols[i];
            printf("  %s interface %s %s pppd-arguments\n",
                    argv[0], p->name, p->usage);
        }
        exit(0);
    }

    interface = argv[1];
    pppd_argc = argc - 3 - the_protocol->arguments;
    pppd_argv = &argv[3 + the_protocol->arguments];
    return the_protocol->connect(&argv[3]);
}

static void stop_pppd()
{
    if (pppd_pid) {
        int status;
        log_print(INFO, "Sending signal to pppd (pid = %d)", pppd_pid);
        kill(pppd_pid, SIGTERM);
        waitpid(pppd_pid, &status, 0);
        pppd_pid = 0;
    }
}

#ifdef ANDROID_CHANGES

static int android_get_control_and_arguments(int *argc, char ***argv)
{
    static char *args[32];
    int control;
    int i;

    if ((i = android_get_control_socket("mtpd")) == -1) {
        return -1;
    }
    log_print(DEBUG, "Waiting for control socket");
    if (listen(i, 1) == -1 || (control = accept(i, NULL, 0)) == -1) {
        log_print(FATAL, "Cannot get control socket");
        exit(SYSTEM_ERROR);
    }
    close(i);
    fcntl(control, F_SETFD, FD_CLOEXEC);

    args[0] = (*argv)[0];
    for (i = 1; i < 32; ++i) {
        unsigned char bytes[2];
        if (recv(control, &bytes[0], 1, 0) != 1 ||
                recv(control, &bytes[1], 1, 0) != 1) {
            log_print(FATAL, "Cannot get argument length");
            exit(SYSTEM_ERROR);
        } else {
            int length = bytes[0] << 8 | bytes[1];
            int offset = 0;

            if (length == 0xFFFF) {
                break;
            }
            args[i] = malloc(length + 1);
            while (offset < length) {
                int n = recv(control, &args[i][offset], length - offset, 0);
                if (n > 0) {
                    offset += n;
                } else {
                    log_print(FATAL, "Cannot get argument value");
                    exit(SYSTEM_ERROR);
                }
            }
            args[i][length] = 0;
        }
    }
    log_print(DEBUG, "Received %d arguments", i - 1);

    *argc = i;
    *argv = args;
    return control;
}

#endif

#ifdef OCPA_CHANGES

static int ocpa_get_control_and_arguments(int *argc, char ***argv)
{
    static char *args[32];
    int control;
    int i;
    struct sockaddr_un remote;
    int addr_len;

	if(*argc < 2) {
		log_print(FATAL, "Missing control socket. Usage: %s <socket>", (*argv)[0]);
		exit(SYSTEM_ERROR);
	}

	remote.sun_family = AF_UNIX;
	strcpy(remote.sun_path, (*argv)[1]);
	addr_len = strlen(remote.sun_path) + sizeof(remote.sun_family);

	if((control = socket(AF_UNIX, SOCK_STREAM, 0)) == -1) {
		return -1;
	}
	log_print(DEBUG, "Connecting to control socket");
	int tries = 5;
	while(tries > 0) {
		if(connect(control, (struct sockaddr *) &remote, addr_len) == -1) {
			log_print(WARNING, "Cannot connect to control socket, retry after 500ms");
			usleep(500);
		}
		tries --;
	}

	if(tries == 0) {
		log_print(FATAL, "Cannot connect to control socket");
		exit(SYSTEM_ERROR);
	}
	fcntl(control, F_SETFD, FD_CLOEXEC);

    args[0] = (*argv)[0];
    for (i = 1; i < 32; ++i) {
        unsigned char bytes[2];
        if (recv(control, &bytes[0], 1, 0) != 1 ||
                recv(control, &bytes[1], 1, 0) != 1) {
            log_print(FATAL, "Cannot get argument length");
            exit(SYSTEM_ERROR);
        } else {
            int length = bytes[0] << 8 | bytes[1];
            int offset = 0;

            if (length == 0xFFFF) {
                break;
            }
            args[i] = malloc(length + 1);
            while (offset < length) {
                int n = recv(control, &args[i][offset], length - offset, 0);
                if (n > 0) {
                    offset += n;
                } else {
                    log_print(FATAL, "Cannot get argument value");
                    exit(SYSTEM_ERROR);
                }
            }
            args[i][length] = 0;
        }
    }
    log_print(DEBUG, "Received %d arguments", i - 1);

    *argc = i;
    *argv = args;
    return control;
}

static void ocpa_process(struct pollfd ctrl) {

}

#endif

int main(int argc, char **argv)
{
    struct pollfd pollfds[3];
    int control = -1;
    int timeout;
    int status;

#if defined(ANDROID_CHANGES) || defined(OCPA_CHANGES)
	mtpd_log_tag = argv[0];
#endif

#ifdef ANDROID_CHANGES
    control = android_get_control_and_arguments(&argc, &argv);
    shutdown(control, SHUT_WR);
#elif OCPA_CHANGES
    control = ocpa_get_control_and_arguments(&argc, &argv);
#endif

    srandom(time(NULL));

    if (pipe(signals) == -1) {
        log_print(FATAL, "Pipe() %s", strerror(errno));
        exit(SYSTEM_ERROR);
    }
    fcntl(signals[0], F_SETFD, FD_CLOEXEC);
    fcntl(signals[1], F_SETFD, FD_CLOEXEC);

    timeout = initialize(argc, argv);

    signal(SIGHUP, interrupt);
    signal(SIGINT, interrupt);
    signal(SIGTERM, interrupt);
    signal(SIGCHLD, interrupt);
    signal(SIGPIPE, SIG_IGN);
    atexit(stop_pppd);

    pollfds[0].fd = the_socket;
    pollfds[0].events = POLLIN;
    pollfds[1].fd = signals[0];
    pollfds[1].events = POLLIN;
    pollfds[2].fd = control;
    pollfds[2].events = 0;

    while (timeout >= 0) {
        if (poll(pollfds, 3, timeout ? timeout : -1) == -1 && errno != EINTR) {
            log_print(FATAL, "Poll() %s", strerror(errno));
            exit(SYSTEM_ERROR);
        }
        if (pollfds[1].revents) {
            break;
        }
        if (pollfds[2].revents) {
#ifdef OCPA_CHANGES
        	ocpa_process(pollfds[2]);
        	pollfds[2].revents = 0;
#else
            interrupt(SIGTERM);
#endif
        }
        timeout = pollfds[0].revents ?
                the_protocol->process() : the_protocol->timeout();
#ifdef ANDROID_CHANGES
        if (!access("/data/misc/vpn/abort", F_OK)) {
            interrupt(SIGTERM);
        }
        if (!timeout) {
            timeout = 5000;
        }
#endif
    }

    if (timeout < 0) {
        status = -timeout;
    } else {
        int signal;
        read(signals[0], &signal, sizeof(int));
        log_print(INFO, "Received signal %d", signal);
        if (signal == SIGCHLD && waitpid(pppd_pid, &status, WNOHANG) == pppd_pid
                && WIFEXITED(status)) {
            status = WEXITSTATUS(status);
            log_print(INFO, "Pppd is terminated (status = %d)", status);
            status += PPPD_EXITED;
            pppd_pid = 0;
        } else {
            status = USER_REQUESTED;
        }
    }

    stop_pppd();
    the_protocol->shutdown();
    log_print(INFO, "Mtpd is terminated (status = %d)", status);
    return status;
}

void log_print(int level, char *format, ...)
{
    if (level >= 0 && level <= LOG_MAX) {
#ifdef ANDROID_CHANGES
        static int levels[5] = {
            ANDROID_LOG_DEBUG, ANDROID_LOG_INFO, ANDROID_LOG_WARN,
            ANDROID_LOG_ERROR, ANDROID_LOG_FATAL
        };
        va_list ap;
        va_start(ap, format);
        __android_log_vprint(levels[level], "mtpd", format, ap);
        va_end(ap);
#else
        static char *levels = "DIWEF";
        va_list ap;
        fprintf(stderr, "%c: ", levels[level]);
        va_start(ap, format);
        vfprintf(stderr, format, ap);
        va_end(ap);
        fputc('\n', stderr);
#endif
    }
}


#ifdef OCPA_CHANGES

void create_socket(int family, int type, char *server, char *port)
{
    struct addrinfo hints = {
        .ai_flags = AI_NUMERICSERV,
        .ai_family = family,
        .ai_socktype = type,
    };
    struct addrinfo *records;
    struct addrinfo *r;
    int error;

    log_print(INFO, "Connecting to %s port %s via %s", server, port, interface);

    error = getaddrinfo(server, port, &hints, &records);
    if (error) {
        log_print(FATAL, "Getaddrinfo() %s", (error == EAI_SYSTEM) ?
                strerror(errno) : gai_strerror(error));
        exit(NETWORK_ERROR);
    }

    for (r = records; r; r = r->ai_next) {
        int s = socket(r->ai_family, r->ai_socktype, r->ai_protocol);
        if (!setsockopt(s, SOL_SOCKET, SO_BINDTODEVICE, interface,
                strlen(interface)) && !connect(s, r->ai_addr, r->ai_addrlen)) {
            the_socket = s;
            break;
        }
        close(s);
    }

    freeaddrinfo(records);

    if (the_socket == -1) {
        log_print(FATAL, "Connect() %s", strerror(errno));
        exit(NETWORK_ERROR);
    }

    fcntl(the_socket, F_SETFD, FD_CLOEXEC);
    log_print(INFO, "Connection established (socket = %d)", the_socket);
}


#else

void create_socket(int family, int type, char *server, char *port)
{
    struct addrinfo hints = {
        .ai_flags = AI_NUMERICSERV,
        .ai_family = family,
        .ai_socktype = type,
    };
    struct addrinfo *records;
    struct addrinfo *r;
    int error;

    log_print(INFO, "Connecting to %s port %s via %s", server, port, interface);

    error = getaddrinfo(server, port, &hints, &records);
    if (error) {
        log_print(FATAL, "Getaddrinfo() %s", (error == EAI_SYSTEM) ?
                strerror(errno) : gai_strerror(error));
        exit(NETWORK_ERROR);
    }

    for (r = records; r; r = r->ai_next) {
        int s = socket(r->ai_family, r->ai_socktype, r->ai_protocol);
        if (!setsockopt(s, SOL_SOCKET, SO_BINDTODEVICE, interface,
                strlen(interface)) && !connect(s, r->ai_addr, r->ai_addrlen)) {
            the_socket = s;
            break;
        }
        close(s);
    }

    freeaddrinfo(records);

    if (the_socket == -1) {
        log_print(FATAL, "Connect() %s", strerror(errno));
        exit(NETWORK_ERROR);
    }

    fcntl(the_socket, F_SETFD, FD_CLOEXEC);
    log_print(INFO, "Connection established (socket = %d)", the_socket);
}

#endif

void start_pppd(int pppox)
{
    if (pppd_pid) {
        log_print(WARNING, "Pppd is already started (pid = %d)", pppd_pid);
        close(pppox);
        return;
    }

    log_print(INFO, "Starting pppd (pppox = %d)", pppox);

    pppd_pid = fork();
    if (pppd_pid < 0) {
        log_print(FATAL, "Fork() %s", strerror(errno));
        exit(SYSTEM_ERROR);
    }

    if (!pppd_pid) {
        char *args[pppd_argc + 5];
        char number[12];

        sprintf(number, "%d", pppox);
        args[0] = PPPD;
        args[1] = "nodetach";
        args[2] = "pppox";
        args[3] = number;
        memcpy(&args[4], pppd_argv, sizeof(char *) * pppd_argc);
        args[4 + pppd_argc] = NULL;

#if defined(ANDROID_CHANGES) || defined(OCPA_CHANGES)
        {
            char envargs[65536];
            char *tail = envargs;
            int i;
            /* Hex encode the arguments using [A-P] instead of [0-9A-F]. */
            for (i = 0; args[i]; ++i) {
                char *p = args[i];
                do {
                    *tail++ = 'A' + ((*p >> 4) & 0x0F);
                    *tail++ = 'A' + (*p & 0x0F);
                } while (*p++);
            }
            *tail = 0;
            setenv("envargs", envargs, 1);
            args[1] = NULL;
        }
#endif
        execvp(PPPD, args);
        log_print(FATAL, "Exec() %s", strerror(errno));
        exit(1); /* Pretending a fatal error in pppd. */
    }

    log_print(INFO, "Pppd started (pid = %d)", pppd_pid);
    close(pppox);
}
