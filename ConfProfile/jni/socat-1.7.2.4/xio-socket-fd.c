/*
* This file is part of Profile provisioning for Android
* Copyright (C) 2014 Infoss AS, https://infoss.no, info@infoss.no
* Copyright Gerhard Rieger 2001-2008
*
* This program is free software; you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation; version 2 of the License.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License along
* with this program; if not, write to the Free Software Foundation, Inc.,
* 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
*/
/*
 * xio-socket-fd.c
 *
 *      Author: Dmitry Vorobiev
 *
 * This file contains SOCKET-*-FD extension for socat.
 *
 * Socket provided to SOCKET-ACCEPT-FD should be at least bound
 * (can be in the listening state, if appliable).
 * Socket provided to SOCKET-CONNECT-FD should be initialized (can be bound).
 * Since this is experimental extension, codestyle is non-socat-like.
 *
 * This extension is useful for software developers who need to create
 * pseudopipe with customized transport. For instance, OCPA uses it for
 * connecting by already protected (in the VpnService terms) sockets on Android.
 *
 * Main usage steps:
 * 1. Create sockets (check absence of O_CLOEXEC flag)
 * 2. fork()
 * 3. In the child process: exec() socat SOCKET-FD-ACCEPT:<fd1> SOCKET-FD-CONNECT:<fd2>:<ip>:<port>
 * 4. Close sockets in the parent process
 *
 * License notes:
 * This file is licensed under GPLv2.
 * Since socat is used as standalone executable in Profile provisioning for Android, license of
 * this file and/or socat doesn't affect the license of the rest parts of OCPA
 * (Profile provisioning for Android).
 */

#include "xiosysincludes.h"
#include "xioopen.h"

#include "xio-socket-fd.h"
#include "xio-socket.h"

#if WITH_GENERICSOCKET

static int xioopen_socket_fd_accept(int argc, const char *argv[], struct opt *opts,
		int xioflags, xiofile_t *xxfd, unsigned groups,
		int dummy1, int dummy2, int dummy3);

static int xioopen_socket_fd_connect(int argc, const char *argv[], struct opt *opts,
		int xioflags, xiofile_t *xxfd, unsigned groups,
		int dummy1, int dummy2, int dummy3);

/* generic socket addresses */
const struct addrdesc xioaddr_socket_fd_connect = { "socket-fd-connect",     3, xioopen_socket_fd_connect,  GROUP_FD|GROUP_SOCKET|GROUP_CHILD|GROUP_RETRY, 0, 0, 0 HELP(":<fd>[:<remote-address>:<remote-port>]") };
#if WITH_LISTEN
const struct addrdesc xioaddr_socket_fd_accept  = { "socket-fd-accept",      3, xioopen_socket_fd_accept,   GROUP_FD|GROUP_SOCKET|GROUP_LISTEN|GROUP_RANGE|GROUP_CHILD|GROUP_RETRY, 0, 0, 0 HELP(":<fd>") };
#endif /* WITH_LISTEN */

struct connection_ctx {
	//addresses and sizes for a...
	//...listening socket
	union sockaddr_union sock;
	size_t sock_len;
	//...local socket
	union sockaddr_union local;
	size_t local_len;
	//...remote socket
	union sockaddr_union peer;
	size_t peer_len;

	//connection options
	int so_type;
	int so_protocol;
	int so_acceptconn;

	//master options
	struct opt *opts;

	//parsed options (quick access)
	bool dofork;
	int backlog;
	int maxchildren;
	int log_level;
};

static struct connection_ctx* __ocpa_create_connection_ctx() {
	Debug("inside __ocpa_create_connection_ctx()");
	struct connection_ctx* ctx = (struct connection_ctx*) malloc(sizeof(struct connection_ctx));
	if(ctx != NULL) {
		Debug("connection_ctx was successfully created");
		memset(ctx, 0, sizeof(struct connection_ctx));
		ctx->sock_len = sizeof(ctx->sock);
		ctx->local_len = sizeof(ctx->local);
		ctx->peer_len = sizeof(ctx->peer);

		ctx->backlog = 5;	/* why? 1 seems to cause problems under some load */
		ctx->maxchildren = 0;
	}
	return ctx;
}

static int __ocpa_getsockname(int fd, struct connection_ctx* ctx) {
	int result = getsockname(fd, &ctx->sock.soa, &ctx->sock_len);
	if(result < 0) {
		result = errno;
		Error2("getsockname() returned error code %d (%s)", result, strerror(result));
		return STAT_NORETRY;
	}
	return STAT_OK;
}

static int __ocpa_getpeername(int fd, struct connection_ctx* ctx) {
	int result = getpeername(fd, &ctx->peer.soa, &ctx->peer_len);
	if(result < 0) {
		result = errno;
		if(result == ENOTCONN) {
			//TODO: check here is sockaddr filled
			return STAT_OK;
		} else {
			Error2("getpeername() returned error code %d (%s)", result, strerror(result));
			return STAT_NORETRY;
		}
	}
	return STAT_OK;
}

static int __ocpa_get_so_type(int fd, struct connection_ctx* ctx) {
	int tmpreadsize = sizeof(int);
	int result = getsockopt(fd, SOL_SOCKET, SO_TYPE, &ctx->so_type, &tmpreadsize);
	Debug4("__ocpa_get_so_type(%d, %p): getsockopt(%d, SOL_SOCKET, SO_TYPE) -> %d", fd, ctx, fd, result);
	if(result < 0) {
		result = errno;
		Error3("getsockopt(%d, SOL_SOCKET, SO_TYPE) returned error code %d (%s)",
				fd,
				result,
				strerror(result));
		return STAT_NORETRY;
	}
	Debug1("ctx->so_type == %d", ctx->so_type);
	return STAT_OK;
}

static int __ocpa_get_so_protocol(int fd, struct connection_ctx* ctx) {
	int tmpreadsize = sizeof(int);
	int result = getsockopt(fd, SOL_SOCKET, SO_PROTOCOL, &ctx->so_protocol, &tmpreadsize);
	Debug4("__ocpa_get_so_protocol(%d, %p): getsockopt(%d, SOL_SOCKET, SO_TYPE) -> %d", fd, ctx, fd, result);
	if(result < 0) {
		result = errno;
		Warn3("getsockopt(%d, SOL_SOCKET, SO_PROTOCOL) returned error code %d (%s)",
				fd,
				result,
				strerror(result));
		return STAT_NORETRY;
	}
	Debug1("ctx->so_protocol == %d", ctx->so_protocol);
	return STAT_OK;
}

static int __ocpa_get_so_acceptconn(int fd, struct connection_ctx* ctx) {
	int tmpreadsize = sizeof(int);
	int result = getsockopt(fd, SOL_SOCKET, SO_ACCEPTCONN, &ctx->so_acceptconn, &tmpreadsize);
	Debug4("__ocpa_get_so_protocol(%d, %p): getsockopt(%d, SOL_SOCKET, SO_ACCEPTCONN) -> %d", fd, ctx, fd, result);
	if(result < 0) {
		result = errno;
		Warn2("getsockopt(SOL_SOCKET, SO_ACCEPTCONN) returned error code %d (%s)",
				result,
				strerror(result));
		return STAT_NORETRY;
	}
	Debug1("ctx->so_acceptconn == %d", ctx->so_acceptconn);
	return STAT_OK;
}

static int __ocpa_check_sockaddr(struct sockaddr* sa) {
	char infobuff[256];
	Debug1("__ocpa_check_sockaddr(%p)", sa);
	Debug1("sockaddr info: %s",
		   sockaddr_info(sa, sizeof(struct sockaddr), infobuff, sizeof(infobuff)));
	union sockaddr_union* us = (union sockaddr_union*) sa;
	if(us->soa.sa_family == AF_INET && us->ip4.sin_addr.s_addr == 0) {
		//socket is not bound
		Warn("AF_INET socket is not bound");
		return STAT_NORETRY;
	} else if(us->soa.sa_family == AF_INET6 &&
		us->ip6.sin6_addr.s6_addr32[0] == 0 &&
		us->ip6.sin6_addr.s6_addr32[1] == 0 &&
		us->ip6.sin6_addr.s6_addr32[2] == 0 &&
		us->ip6.sin6_addr.s6_addr32[3] == 0) {
		//socket is not bound
		Warn("AF_INET6 socket is not bound");
		return STAT_NORETRY;
	}
	return STAT_OK;
}

static int __ocpa_check_bound(struct connection_ctx* ctx) {
	Debug1("__ocpa_check_bound(%p)", ctx);
	int result = __ocpa_check_sockaddr(&ctx->sock.soa);
	Debug2("__ocpa_check_bound(%p) -> %d", ctx, result);
	return result;
}

static int __ocpa_check_connected(struct connection_ctx* ctx) {
	Debug1("__ocpa_check_connected(%p)", ctx);
	int result = __ocpa_check_sockaddr(&ctx->peer.soa);
	Debug2("__ocpa_check_connected(%p) -> %d", ctx, result);
	return result;
}

static int __ocpa_check_af_inet(struct connection_ctx* ctx) {
	Debug1("__ocpa_check_af_inet(%p)", ctx);
	if(ctx->sock.soa.sa_family != AF_INET && ctx->sock.soa.sa_family != AF_INET6) {
		Error1("address family %d doesn't supported", ctx->sock.soa.sa_family);
		return STAT_NORETRY;
	}

#if !WITH_IP6
	if(ctx->sock.soa.sa_family == AF_INET6) {
		Error("AF_INET6 address family requires enabled IPv6 feature");
		return STAT_NORETRY;
	}
#endif

	return STAT_OK;
}

#if WITH_LISTEN

static int __xioopen_socket_fd_listen_loop(struct single *xfd, struct connection_ctx* ctx) {
	int result;
	int backlog;
	struct opt* opts = copyopts(ctx->opts, GROUP_ALL);

	while(true) {
		/* loop over failed attempts */
		applyopts(xfd->fd, opts, PH_PRELISTEN);
		retropt_int(opts, OPT_BACKLOG, &ctx->backlog);
		if(Listen(xfd->fd, ctx->backlog) < 0) {
			Debug3("listen(%d, %d): %s", xfd->fd, ctx->backlog, strerror(errno));
			result = STAT_RETRYLATER;
		}
		/*! not sure if we should try again on retry/forever */

		switch(result) {
		case STAT_OK: break;
#if WITH_RETRY
		case STAT_RETRYLATER:
		case STAT_RETRYNOW: {
			if(xfd->forever || xfd->retry) {
				dropopts(opts, PH_ALL);
				free(opts);
				opts = copyopts(ctx->opts, GROUP_ALL);
				if(result == STAT_RETRYLATER) {
					Nanosleep(&xfd->intervall, NULL);
				}
				dropopts(opts, PH_ALL);
				free(opts);
				opts = copyopts(ctx->opts, GROUP_ALL);
				--xfd->retry;
				continue;
			}
			return STAT_NORETRY;
		}
#endif /* WITH_RETRY */
		default: {
			return result;
		}
		} /* switch */

		break;
	}	/* drop out on success */

	return result;
}

static int __xioopen_socket_fd_accept_loop(struct single *xfd, struct connection_ctx* ctx) {
	int ps;		/* peer socket */
	char infobuff[256];

	struct opt* opts = ctx->opts; //This is okay since we don't modify opts

	int result;

	while(true) {
		/* but we only loop if fork option is set */
		do {
			/*? int level = E_ERROR;*/
			Notice1("listening on %s",
					sockaddr_info(&ctx->sock.soa, ctx->sock_len, infobuff, sizeof(infobuff)));
			ps = Accept(xfd->fd, &ctx->peer.soa, &ctx->peer_len);
			if(ps >= 0) {
				/*0 Info4("accept(%d, %p, {"F_Zu"}) -> %d", xfd->fd, &sa, salen, ps);*/
				break;	/* success, break out of loop */
			}

			if(errno == EINTR) {
				continue;
			}

			if(errno == ECONNABORTED) {
				Notice4("accept(%d, %p, {"F_socklen"}): %s",
						xfd->fd,
						&ctx->peer.soa,
						ctx->peer_len,
						strerror(errno));
				continue;
			}

			Msg4(ctx->log_level, "accept(%d, %p, {"F_socklen"}): %s",
					xfd->fd,
					&ctx->peer.soa,
					ctx->peer_len,
					strerror(errno));
			return STAT_RETRYLATER;
		} while (true);

		applyopts_cloexec(ps, opts);

		if(Getsockname(ps, &ctx->local.soa, &ctx->local_len) < 0) {
			Warn4("getsockname(%d, %p, {"F_socklen"}): %s",
					ps,
					&ctx->local.soa,
					ctx->local_len,
					strerror(errno));
		}
		Notice2("accepting connection from %s on %s",
			sockaddr_info(&ctx->peer.soa, ctx->peer_len, infobuff, sizeof(infobuff)),
			sockaddr_info(&ctx->local.soa, ctx->local_len, infobuff, sizeof(infobuff)));

		if(xiocheckpeer(xfd, &ctx->peer, &ctx->local) < 0) {
			if(Shutdown(ps, 2) < 0) {
				Info2("shutdown(%d, 2): %s", ps, strerror(errno));
			}
			Close(ps);
			continue;
		}

		Info1("permitting connection from %s",
			sockaddr_info(&ctx->peer.soa,
					ctx->peer_len,
					infobuff,
					sizeof(infobuff)));

		if(ctx->dofork) {
			pid_t pid;	/* mostly int; only used with fork */
			sigset_t mask_sigchld;

			/* we must prevent that the current packet triggers another fork;
			therefore we wait for a signal from the recent child: USR1
			indicates that is has consumed the last packet; CHLD means it has
			terminated */
			/* block SIGCHLD and SIGUSR1 until parent is ready to react */
			sigemptyset(&mask_sigchld);
			sigaddset(&mask_sigchld, SIGCHLD);
			Sigprocmask(SIG_BLOCK, &mask_sigchld, NULL);

			if((pid = xio_fork(false, ctx->log_level == E_ERROR ? ctx->log_level : E_WARN)) < 0) {
				Sigprocmask(SIG_UNBLOCK, &mask_sigchld, NULL);
				return STAT_RETRYLATER;
			}

			if(pid == 0) {
				/* child */
				pid_t cpid = Getpid();
				Sigprocmask(SIG_UNBLOCK, &mask_sigchld, NULL);

				Info1("just born: child process "F_pid, cpid);
				xiosetenvulong("PID", cpid, 1);

				if(Close(xfd->fd) < 0) {
					Info2("close(%d): %s", xfd->fd, strerror(errno));
				}
				xfd->fd = ps;

#if WITH_RETRY
/* !? */
				xfd->forever = false;  xfd->retry = 0;
				ctx->log_level = E_ERROR;
#endif /* WITH_RETRY */

				break;
			}

			/* server: continue loop with listen */
			/* shutdown() closes the socket even for the child process, but
			close() does what we want */
			if(Close(ps) < 0) {
				Info2("close(%d): %s", ps, strerror(errno));
			}

			/* now we are ready to handle signals */
			Sigprocmask(SIG_UNBLOCK, &mask_sigchld, NULL);

			while(ctx->maxchildren) {
				if(num_child < ctx->maxchildren) {
					break;
				}

				Notice("maxchildren are active, waiting");
				while(!Sleep(UINT_MAX)) ;	/* any signal lets us continue */
			}

			Info("still listening");
		} else /* if(dofork) */ {
			if(Close(xfd->fd) < 0) {
				Info2("close(%d): %s", xfd->fd, strerror(errno));
			}
			xfd->fd = ps;
			break;
		}
	} /* while(true) */

	applyopts(xfd->fd, opts, PH_FD);
	applyopts(xfd->fd, opts, PH_PASTSOCKET);
	applyopts(xfd->fd, opts, PH_CONNECTED);
	if((result = _xio_openlate(xfd, opts)) < 0) {
		return result;
	}

	/* set the env vars describing the local and remote sockets */
	xiosetsockaddrenv("SOCK", &ctx->local, ctx->local_len, ctx->so_protocol);
	xiosetsockaddrenv("PEER", &ctx->peer, ctx->peer_len, ctx->so_protocol);

	return STAT_OK;
}

static int xioopen_socket_fd_accept(int argc, const char *argv[], struct opt *opts,
		int xioflags, xiofile_t *xxfd, unsigned groups,
		int dummy1, int dummy2, int dummy3) {
	struct single *xfd = &xxfd->stream;
	const char *fromfdname = argv[1];
	char *garbage;
	int fromfd;
	int pf;
	int result;
	char* rangename;
	struct connection_ctx* ctx;

	if(argc != 2) {
		Error2("%s: wrong number of parameters (%d instead of 1)", argv[0], argc-1);
		return STAT_NORETRY;
	}

	fromfd = strtoul(fromfdname, &garbage, 0);
	if(*garbage != '\0') {
		Warn1("garbage in parameter: \"%s\"", garbage);
	}

	Debug("pre __ocpa_create_connection_ctx()");
	ctx = __ocpa_create_connection_ctx();
	if(ctx == NULL) {
		Error("Failed to allocate connection context");
		return STAT_NORETRY;
	}
	ctx->opts = opts;

	retropt_bool(opts, OPT_FORK, &ctx->dofork);

	if(ctx->dofork) {
		if (!(xioflags & XIO_MAYFORK)) {
			Error("option fork not allowed here");
			free(ctx);
			return STAT_NORETRY;
		}

		xfd->flags |= XIO_DOESFORK;
	}

	retropt_int(opts, OPT_MAX_CHILDREN, &ctx->maxchildren);

	if(!ctx->dofork && ctx->maxchildren) {
		Error("option max-children not allowed without option fork");
		free(ctx);
		return STAT_NORETRY;
	}

	if(ctx->dofork) {
		xiosetchilddied();	/* set SIGCHLD handler */
	}

	Debug("pre __ocpa_getsockname()");
	result = __ocpa_getsockname(fromfd, ctx);
	if(result < 0) {
		free(ctx);
		return result;
	}

	//check address family/protocol family
	Debug("pre __ocpa_check_af_inet()");
	result = __ocpa_check_af_inet(ctx);
	if(result < 0) {
		free(ctx);
		return result;
	}

	//check is socket bound
	Debug("pre __ocpa_check_bound()");
	result = __ocpa_check_bound(ctx);
	if(result < 0) {
		free(ctx);
		return result;
	}

	Debug("pre __ocpa_get_so_type()");
	result = __ocpa_get_so_type(fromfd, ctx);
	if(result < 0) {
		free(ctx);
		return result;
	}

	//check is socket in listening mode
	Debug("pre __ocpa_get_so_acceptconn()");
	result = __ocpa_get_so_acceptconn(fromfd, ctx);
	if(result < 0) {
		free(ctx);
		return result;
	}

	if(applyopts_single(xfd, opts, PH_INIT) < 0) {
		free(ctx);
		return -1;
	}
	applyopts(-1, opts, PH_INIT);
	applyopts(-1, opts, PH_EARLY);

	xfd->fd = fromfd;
	xfd->howtoend = END_SHUTDOWN;
	applyopts_cloexec(xfd->fd, opts);

#if WITH_IP4 /*|| WITH_IP6*/
	if(retropt_string(opts, OPT_RANGE, &rangename) >= 0) {
		if(xioparserange(rangename, pf, &xfd->para.socket.range) < 0) {
			free(rangename);
			free(ctx);
			return STAT_NORETRY;
		}
		free(rangename);
		xfd->para.socket.dorange = true;
	}
#endif

#if (WITH_TCP || WITH_UDP) && WITH_LIBWRAP
	xio_retropt_tcpwrap(xfd, opts);
#endif /* && (WITH_TCP || WITH_UDP) && WITH_LIBWRAP */

#if WITH_TCP || WITH_UDP
	if(retropt_ushort(opts, OPT_SOURCEPORT, &xfd->para.socket.ip.sourceport) >= 0) {
		xfd->para.socket.ip.dosourceport = true;
	}
#endif /* WITH_TCP || WITH_UDP */

	ctx->log_level = E_ERROR;
#if WITH_RETRY
	if(xfd->forever || xfd->retry) {
		ctx->log_level = E_INFO;
	}
#endif /* WITH_RETRY */

	//check is socket listening
	if(ctx->so_acceptconn == 0 && ctx->so_type == SOCK_STREAM) {
		//force listening
		Debug("pre __xioopen_socket_fd_listen_loop()");
		result = __xioopen_socket_fd_listen_loop(xfd, ctx);
		Debug1("__xioopen_socket_fd_listen_loop() -> %d", result);
		if(result != STAT_OK) {
			free(ctx);
			return result;
		}
	}

	if (xioopts.logopt == 'm') {
		Info("starting accept loop, switching to syslog");
		diag_set('y', xioopts.syslogfac);  xioopts.logopt = 'y';
	} else {
		Info("starting accept loop");
	}

	result = __xioopen_socket_fd_accept_loop(xfd, ctx);
	Debug1("__xioopen_socket_fd_listen_loop() -> %d", result);
	free(ctx);
	return result;
}
#endif /* WITH_LISTEN */

static int __xioopen_socket_fd_bind_loop(struct single* xfd, struct connection_ctx* ctx) {
	char infobuff[256];
	struct opt* opts;
	int result;

	do {
		/* loop over retries and forks */
		ctx->log_level = E_ERROR;
#if WITH_RETRY
		if (xfd->forever || xfd->retry) {
			ctx->log_level = E_INFO;
		}
#endif /* WITH_RETRY */

		result = STAT_OK;
		if(Bind(xfd->fd, &ctx->sock.soa, ctx->sock_len) < 0) {
			Msg4(ctx->log_level, "bind(%d, {%s}, "F_Zd"): %s",
					xfd->fd, sockaddr_info(&ctx->sock.soa, ctx->sock_len, infobuff, sizeof(infobuff)),
					ctx->sock_len, strerror(errno));
			result = STAT_RETRYLATER;
		}

		switch(result) {
		case STAT_OK: {
			break;
		}
#if WITH_RETRY
		case STAT_RETRYLATER: {
			if(xfd->forever || xfd->retry) {
				--xfd->retry;
				if(result == STAT_RETRYLATER) {
					Nanosleep(&xfd->intervall, NULL);
				}
				dropopts(opts, PH_ALL);
				free(opts);
				opts = copyopts(ctx->opts, GROUP_ALL);
				continue;
			}
			return STAT_NORETRY;
		}
#endif /* WITH_RETRY */
		default: {
			return result;
		}
		} /* switch */

		if(ctx->dofork) {
			xiosetchilddied();	/* set SIGCHLD handler */
		}

#if WITH_RETRY
		if(ctx->dofork) {
			pid_t pid;
			int level = E_ERROR;
			if(xfd->forever || xfd->retry) {
				ctx->log_level = E_WARN;
				/* most users won't expect a problem here, so Notice is too weak */
			}

			while((pid = xio_fork(false, level)) < 0) {
				--xfd->retry;
				if(xfd->forever || xfd->retry) {
					dropopts(opts, PH_ALL);
					free(opts);
					opts = copyopts(ctx->opts, GROUP_ALL);
					Nanosleep(&xfd->intervall, NULL); continue;
				}
				return STAT_RETRYLATER;
			}

			if(pid == 0) {
				/* child process */
				break;
			}

			/* parent process */
			Close(xfd->fd);
			/* with and without retry */
			Nanosleep(&xfd->intervall, NULL);
			dropopts(opts, PH_ALL);
			free(opts);
			opts = copyopts(ctx->opts, GROUP_ALL);
			continue;	/* with next socket() bind() connect() */
		} else
#endif /* WITH_RETRY */
		{
			break;
		}
#if 0
		if((result = _xio_openlate(fd, opts)) < 0) {
			return result;
		}
#endif
	} while (true);

	return STAT_OK;
}

static int xioopen_socket_fd_connect(int argc, const char *argv[], struct opt *opts,
		int xioflags, xiofile_t *xxfd, unsigned groups,
		int dummy1, int dummy2, int dummy3) {
	struct single *xfd = &xxfd->stream;
	const char *tofdname = argv[1];
	const char *address = NULL;
	int port;
	char *garbage;
	int tofd;
	int pf;
	int needbind = 0;
	int result;
	struct connection_ctx* ctx;

	char infobuff[256];

	if(argc != 2 && argc != 4) {
		Error2("%s: wrong number of parameters (%d instead of 1 or 3)", argv[0], argc - 1);
		return STAT_NORETRY;
	}

	tofd = strtoul(tofdname, &garbage, 0);
	if(*garbage != '\0') {
		Warn1("garbage in parameter: \"%s\"", garbage);
	}

	if(argc == 4) {
		address = argv[2];
		port = strtoul(argv[3], &garbage, 0);
		if(*garbage != '\0') {
			Warn1("garbage in parameter: \"%s\"", garbage);
		}
	}

	ctx = __ocpa_create_connection_ctx();
	if(ctx == NULL) {
		Error("Failed to allocate connection context");
		return STAT_NORETRY;
	}
	ctx->opts = opts;

	retropt_socket_pf(opts, &pf);
	retropt_int(opts, OPT_SO_TYPE, &ctx->so_type);

	xfd->fd = tofd;
	xfd->howtoend = END_SHUTDOWN;

	if(applyopts_single(xfd, opts, PH_INIT) < 0) {
		free(ctx);
		return -1;
	}

	applyopts(-1, opts, PH_INIT);
	applyopts(-1, opts, PH_EARLY);

	xfd->dtype = XIOREAD_STREAM|XIOWRITE_STREAM;

	result = __ocpa_get_so_type(xfd->fd, ctx);
	if(result < 0) {
		free(ctx);
		return result;
	}

	result = __ocpa_get_so_protocol(xfd->fd, ctx);
	if(result < 0) {
		free(ctx);
		return result;
	}

	result = __ocpa_getsockname(xfd->fd, ctx);
	if(result < 0) {
		free(ctx);
		return result;
	}

	Debug1("post _ocpa_getsockname() sockaddr info: %s",
				   sockaddr_info(&ctx->sock.soa, sizeof(ctx->sock), infobuff, sizeof(infobuff)));

	pf = ctx->sock.soa.sa_family;

	//check is socket bound
	result = __ocpa_check_bound(ctx);
	Debug1("__ocpa_check_bound() -> %d", result);
	if(result < 0) {
		ctx->sock_len = sizeof(ctx->sock);
		result = retropt_bind(opts,
				0 /*pf*/,
				ctx->so_type,
				ctx->so_protocol,
				&ctx->sock.soa,
				(socklen_t*) &ctx->sock_len,
				3,
				0,
				0);
		Debug1("retopt_bind() -> %d", result);
		if(result != STAT_NOACTION) {
			needbind = true;
			ctx->sock.soa.sa_family = pf;
			//socket is not bound, but we have to bind it

			applyopts_offset(xfd, opts);
			applyopts(xfd->fd, opts, PH_PASTSOCKET);
			applyopts(xfd->fd, opts, PH_FD);

			result = __xioopen_socket_fd_bind_loop(xfd, ctx);
			if(result < 0) {
				free(ctx);
				return result;
			}
		}
	}

	applyopts_cloexec(xfd->fd, opts);

	ctx->peer.soa.sa_family = pf;
	Debug1("pre _ocpa_getpeername() sockaddr info: %s",
			   sockaddr_info(&ctx->peer.soa, sizeof(ctx->peer), infobuff, sizeof(infobuff)));
	result = __ocpa_getpeername(xfd->fd, ctx);
	if(result < 0) {
		free(ctx);
		return result;
	}

	if(ctx->peer_len == 0 || __ocpa_check_connected(ctx) != STAT_OK) {
		if(address == NULL) {
			Error("socket isn't connected while address unspecified");
			free(ctx);
			return STAT_NORETRY;
		}

		void* dst_addr = NULL;
		if(pf == AF_INET) {
			dst_addr = &ctx->peer.ip4.sin_addr.s_addr;
			ctx->peer.ip4.sin_port = port;
		} else if(pf == AF_INET6) {
			dst_addr = ctx->peer.ip6.sin6_addr.in6_u.u6_addr8;
			ctx->peer.ip6.sin6_port = port;
		} else {
			//SIGSEGV will be caught by socat handler
			Warn1("unsupported address family^ %d", pf);
		}

		result = inet_pton(pf, address, dst_addr);
		if(result == 0) {
			Error1("invalid address: \"%s\"", address);
			free(ctx);
			return STAT_NORETRY;
		} else if(result == -1) {
			Error3("invalid address family: %d (%d: %s)", pf, errno, strerror(errno));
			free(ctx);
			return STAT_NORETRY;
		}
	}

	if((result =
	xioopen_connect(xfd,
	needbind?&ctx->sock.soa:NULL, ctx->sock_len,
	&ctx->peer.soa, ctx->peer_len,
	opts, pf, ctx->so_type, ctx->so_protocol, false)) != 0) {
		free(ctx);
	return result;
	}

	if ((result = _xio_openlate(xfd, opts)) < 0) {
		free(ctx);
		return result;
	}

	free(ctx);
	return STAT_OK;
}

#endif /* WITH_GENERICSOCKET */
