#include <stdio.h>
#include "android_log_utils.h"
#include "ocpa.h"
#include "tun_openvpn.h"

#define LOG_TAG "ocpa.c"

void free_vpn_service_ctx(vpn_service_ctx_t* ctx) {
	if(ctx == NULL) {
		return;
	}

	ctx->routerloop_object = NULL;
	ctx->routerloop_protect = NULL;
	ctx->free_vpn_service_ctx = NULL;
	ctx->bypass_socket = NULL;
	free(ctx);
}

/**
 * Bypass a single socket
 */
bool bypass_socket(vpn_service_ctx_t* ctx, int fd) {
	JNIEnv *env;
	jmethodID method_id;

	androidjni_attach_thread(&env);

	if (!ctx->routerloop_protect) {
		goto failed;
	}

	if (!(*env)->CallBooleanMethod(env, ctx->routerloop_object, ctx->routerloop_protect, fd)) {
		//DBG2(DBG_KNL, "VpnService.protect() failed");
		goto failed;
	}
	androidjni_detach_thread();
	return true;

failed:
	androidjni_exception_occurred(env);
	androidjni_detach_thread();
	return false;
}

JNI_METHOD(RouterLoop, initIpRouter, jlong) {
	return (jlong) (intptr_t) router_init();
}

JNI_METHOD(RouterLoop, deinitIpRouter, void, jlong jrouterctx) {
	router_deinit((router_ctx_t*) (intptr_t) jrouterctx);
}

JNI_METHOD(RouterLoop, createVpnServiceContext, jlong) {
	vpn_service_ctx_t* ctx = (vpn_service_ctx_t*) malloc(sizeof(vpn_service_ctx_t));
	if(ctx == NULL) {
		return 0;
	}

	ctx->routerloop_object = this;
	ctx->routerloop_protect = (*env)->GetMethodID(env, android_routerloop_class, "protect", "(I)Z");
	ctx->free_vpn_service_ctx = free_vpn_service_ctx;
	ctx->bypass_socket = bypass_socket;

	return (jlong) (intptr_t) ctx;
}

JNI_METHOD(RouterLoop, freeVpnServiceContext, void, jlong jvpnservicectx) {
	free_vpn_service_ctx((vpn_service_ctx_t*) (intptr_t) jvpnservicectx);
}

JNI_METHOD(RouterLoop, routerLoop, jint, jlong jrouterctx, jobject jbuilder) {
	jmethodID method_id;
	int fd;

	method_id = (*env)->GetMethodID(env, android_ocpavpnservice_builder_class, "establish", "()I");

	if (!method_id) {
		__android_log_write(ANDROID_LOG_ERROR, "ocpa.c", "Unknown method: establish()I");
		goto failed;
	}

	fd = (*env)->CallIntMethod(env, jbuilder, method_id);
	if (fd == -1) {
		__android_log_write(ANDROID_LOG_ERROR, "ocpa.c", "Builder returned invalid fd");
		goto failed;
	}

	//START LOOP
	router_ctx_t* ctx = (router_ctx_t*) (intptr_t) jrouterctx;
	if(ctx == NULL) {
		__android_log_write(ANDROID_LOG_ERROR, "ocpa.c", "Router context is null");
		goto failed;
	}

	ctx->dev_fd = fd;
	rebuild_poll_struct(ctx);

	int timeout_msecs = 500;
	int ip_pkt_buff_size = 1500;
	uint8_t* ip_pkt_buff = malloc(sizeof(uint8_t) * ip_pkt_buff_size);

	struct pollfd poll_dev_fd;
	poll_dev_fd.fd = ctx->dev_fd;
	poll_dev_fd.events = POLLIN | POLLHUP;
	poll_dev_fd.revents = 0;

	int i;
	int res = 0;
	while(true) {
		res = poll(&poll_dev_fd, 1, timeout_msecs);

		if(res > 0 && (poll_dev_fd.revents & POLLIN) != 0) {
			LOGD(LOG_TAG, "Reading a packet from /dev/tun fd (poll res = %d, poll_dev_fd.revents = %d)", res, poll_dev_fd.revents);
			res = read_ip_packet(ctx->dev_fd, ip_pkt_buff, ip_pkt_buff_size);
			if(res < 0) {
				//TODO: error handling
				LOGE(LOG_TAG, "Can't read IP packet from /dev/tun (%d - %s)", res, strerror(res));
				log_dump_packet(LOG_TAG, ip_pkt_buff, ip_pkt_buff_size);
				goto failed;
			}

			log_dump_packet(LOG_TAG, ip_pkt_buff, res);

			LOGD(LOG_TAG, "Sending received packet");
			ipsend(ctx, ip_pkt_buff, res);
		}

		poll_dev_fd.revents = 0;
		/*
		res = poll(ctx->poll_fds, ctx->poll_fds_count, timeout_msecs);
		if(res > 0) {
			for(i = 0; i < ctx->poll_fds_count; i++) {
				if((ctx->poll_fds[i].revents && POLLIN) != 0) {
					res = read_ip_packet(ctx->poll_ctxs[i]->local_fd, ip_pkt_buff, ip_pkt_buff_size);
					if(res < 0) {
						//TODO: error handling
						LOGE(LOG_TAG, "Can't read IP packet from tun sockets");
						goto failed;
					}

					tun_recv_func_ptr recv_func = ctx->poll_ctxs[i]->recv_func;
					if(recv_func != NULL) {
						recv_func((intptr_t) ctx->poll_ctxs[i], ip_pkt_buff, res);
					}
				}

				ctx->poll_fds[i].revents = 0;
			}
		} else if(res < 0) {
			LOGD(LOG_TAG, "Polling tun fds finished with error %d %s", errno, strerror(errno));
		}*/
	}
	//END LOOP

	return 0;

failed:

	return -1;
}

JNI_METHOD(RouterLoop, addRoute4, void, jlong jrouterctx, jint jip4, jlong jtunctx) {
	router_ctx_t* router_ctx = (router_ctx_t*) (intptr_t) jrouterctx;
	common_tun_ctx_t* tun_ctx = (common_tun_ctx_t*) (intptr_t) jtunctx;
	if(router_ctx == NULL || tun_ctx == NULL) {
		return;
	}

	if(tun_ctx->router_ctx != NULL && tun_ctx->router_ctx != router_ctx) {
		LOGE(LOG_TAG,
				"Memory leak! TUN context was reassigned from %p to %p",
				tun_ctx->router_ctx,
				router_ctx);
	}

	tun_ctx->router_ctx = router_ctx;

	route4(router_ctx, jip4, tun_ctx);
}

JNI_METHOD(RouterLoop, defaultRoute4, void, jlong jrouterctx, jlong jtunctx) {
	router_ctx_t* router_ctx = (router_ctx_t*) (intptr_t) jrouterctx;
	common_tun_ctx_t* tun_ctx = (common_tun_ctx_t*) (intptr_t) jtunctx;
	if(router_ctx == NULL || tun_ctx == NULL) {
		return;
	}

	if(tun_ctx->router_ctx != NULL && tun_ctx->router_ctx != router_ctx) {
		LOGE(LOG_TAG,
				"Memory leak! TUN context was reassigned from %p to %p",
				tun_ctx->router_ctx,
				router_ctx);
	}

	tun_ctx->router_ctx = router_ctx;

	default4(router_ctx, tun_ctx);
}

JNI_METHOD(RouterLoop, removeRoute4, void, jlong jrouterctx, jint jip4) {
	router_ctx_t* router_ctx = (router_ctx_t*) (intptr_t) jrouterctx;
	if(router_ctx == NULL) {
		return;
	}

	unroute4(router_ctx, jip4);
}

JNI_METHOD(RouterLoop, getRoutes4, jobject, jlong jrouterctx) {
	router_ctx_t* router_ctx = (router_ctx_t*) (intptr_t) jrouterctx;
	if(router_ctx == NULL) {
		return NULL;
	}

	jclass array_list_class = (*env)->FindClass(env, "java/util/ArrayList");
	if(array_list_class == NULL) {
		LOGE(LOG_TAG, "Class not found: java/util/ArrayList");
		return NULL;
	}

	jmethodID array_list_init = (*env)->GetMethodID(env, array_list_class, "<init>", "(I)V");
	if(array_list_init == NULL) {
		LOGE(LOG_TAG, "Constructor not found: <init>(I)V for java/util/ArrayList");
		return NULL;
	}

	jmethodID array_list_add = (*env)->GetMethodID(env, array_list_class, "add", "(Ljava/lang/Object;)Z");
	if(array_list_add == NULL) {
		LOGE(LOG_TAG, "Method not found: add(Ljava/lang/Object;)Z for java/util/ArrayList");
		return NULL;
	}

	jclass route4_class = (*env)->FindClass(env, JNI_PACKAGE_STRING "/RouterLoop$Route4");
	if(route4_class == NULL) {
		LOGE(LOG_TAG, "Class not found: " JNI_PACKAGE_STRING "/RouterLoop$Route4");
		return NULL;
	}

	jmethodID route4_init = (*env)->GetMethodID(env, route4_class, "<init>", "(JII)V");
	if(route4_init == NULL) {
		LOGE(LOG_TAG, "Method not found: <init>(JII)V for " JNI_PACKAGE_STRING "/RouterLoop$Route4");
		return NULL;
	}

	pthread_rwlock_rdlock(router_ctx->rwlock4);
	jobject list = (*env)->NewObject(env, array_list_class, array_list_init, router_ctx->ip4_routes_count + 1);
	jobject item = (*env)->NewObject(env, route4_class, route4_init, (jlong) (intptr_t) router_ctx->ip4_default_tun_ctx, 0, 0);
	if((*env)->CallBooleanMethod(env, list, item) != JNI_TRUE) {
		LOGW(LOG_TAG, "Can't add object %p to list", item);
	}

	route4_link_t* link4 = router_ctx->ip4_routes;
	while(link4 != NULL) {
		//TODO: fix subnets
		item = (*env)->NewObject(env, route4_class, route4_init, (jlong) (intptr_t) link4->tun_ctx, link4->ip4, 32);
		if((*env)->CallBooleanMethod(env, list, item) != JNI_TRUE) {
			LOGW(LOG_TAG, "Can't add object %p to list", item);
		}
		link4 = link4->next;
	}

	pthread_rwlock_unlock(router_ctx->rwlock4);
	return list;
}

JNI_METHOD(OpenVpnTunnel, initOpenVpnTun, jlong) {
	return (jlong) (intptr_t) openvpn_tun_init();
}

JNI_METHOD(OpenVpnTunnel, deinitOpenVpnTun, void, jlong jtunctx) {
	openvpn_tun_deinit((openvpn_tun_ctx_t*) (intptr_t) jtunctx);
}

JNI_METHOD(OpenVpnTunnel, getLocalFd, jint, jlong jtunctx) {
	if(((openvpn_tun_ctx_t*) (intptr_t) jtunctx) == NULL) {
		return -1;
	}

	return ((openvpn_tun_ctx_t*) (intptr_t) jtunctx)->common.local_fd;
}

JNI_METHOD(OpenVpnTunnel, getRemoteFd, jint, jlong jtunctx) {
	if(((openvpn_tun_ctx_t*) (intptr_t) jtunctx) == NULL) {
		return -1;
	}

	return ((openvpn_tun_ctx_t*) (intptr_t) jtunctx)->common.remote_fd;
}
