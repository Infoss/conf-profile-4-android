#include <android/log.h>
#include "ocpa.h"

void free_vpn_service_ctx(vpn_service_ctx_t* ctx) {
	if(ctx == NULL) {
		return;
	}

	ctx->workflow_object = NULL;
	ctx->workflow_protect = NULL;
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

	if (!ctx->workflow_protect) {
		goto failed;
	}

	if (!(*env)->CallBooleanMethod(env, ctx->workflow_object, ctx->workflow_protect, fd)) {
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

JNI_METHOD(OcpaVpnWorkflow, initIpRouter, jlong) {
	return (jlong) (intptr_t) router_init();
}

JNI_METHOD(OcpaVpnWorkflow, deinitIpRouter, void, jlong jrouterctx) {
	router_deinit((router_ctx_t*) (intptr_t) jrouterctx);
}

JNI_METHOD(OcpaVpnWorkflow, createVpnServiceContext, jlong) {
	vpn_service_ctx_t* ctx = (vpn_service_ctx_t*) malloc(sizeof(vpn_service_ctx_t));
	if(ctx == NULL) {
		return 0;
	}

	ctx->workflow_object = this;
	ctx->workflow_protect = (*env)->GetMethodID(env, android_ocpavpnworkflow_class, "protect", "(I)Z");
	ctx->free_vpn_service_ctx = free_vpn_service_ctx;
	ctx->bypass_socket = bypass_socket;

	return (jlong) (intptr_t) ctx;
}

JNI_METHOD(OcpaVpnWorkflow, freeVpnServiceContext, void, jlong jvpnservicectx) {
	free_vpn_service_ctx((vpn_service_ctx_t*) (intptr_t) jvpnservicectx);
}

JNI_METHOD(OcpaVpnWorkflow, routerLoop, jint, jlong jrouterctx, jobject jbuilder) {
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

	int ip_pkt_buff_size = 1500;
	uint8_t* ip_pkt_buff = malloc(sizeof(uint8_t) * ip_pkt_buff_size);

	struct pollfd poll_dev_fd;
	poll_dev_fd.fd = ctx->dev_fd;
	poll_dev_fd.events = POLLIN | POLLHUP;
	poll_dev_fd.revents = 0;

	int i;
	int res = 0;
	while(true) {
		res = poll(&poll_dev_fd, ctx->dev_fd + 1, 0);

		if(res == 0 && (poll_dev_fd.revents & POLLIN) != 0) {
			res = read_ip_packet(ctx->dev_fd, ip_pkt_buff, ip_pkt_buff_size);
			if(res < 0) {
				//TODO: error handling
				__android_log_write(ANDROID_LOG_ERROR, "ocpa.c", "Can't read IP packet from /dev/tun");
				goto failed;
			}

			ipsend(ctx, ip_pkt_buff, res);
		}

		poll_dev_fd.revents = 0;

		res = poll(ctx->poll_fds, ctx->poll_fds_nfds, 0);
		if(res == 0) {
			for(i = 0; i < ctx->poll_fds_count; i++) {
				if((ctx->poll_fds[i].revents && POLLIN) != 0) {
					res = read_ip_packet(ctx->poll_ctxs[i]->local_fd, ip_pkt_buff, ip_pkt_buff_size);
					if(res < 0) {
						//TODO: error handling
						__android_log_write(ANDROID_LOG_ERROR, "ocpa.c", "Can't read IP packet from tun sockets");
						goto failed;
					}

					tun_recv_func_ptr recv_func = ctx->poll_ctxs[i]->recv_func;
					if(recv_func != NULL) {
						recv_func((intptr_t) ctx->poll_ctxs[i], ip_pkt_buff, res);
					}
				}

				ctx->poll_fds[i].revents = 0;
			}
		}
	}
	//END LOOP

	return 0;

failed:

	return -1;
}

JNI_METHOD(OcpaVpnWorkflow, addRoute4, void, jlong jrouterctx, jint jip4, jlong jtunctx) {
	router_ctx_t* router_ctx = (router_ctx_t*) (intptr_t) jrouterctx;
	common_tun_ctx_t* tun_ctx = (common_tun_ctx_t*) (intptr_t) jtunctx;
	if(router_ctx == NULL || tun_ctx == NULL) {
		return;
	}

	if(tun_ctx->router_ctx != NULL && tun_ctx->router_ctx != router_ctx) {
			__android_log_write(ANDROID_LOG_ERROR, "ocpa.c", "Memory leak! TUN context was reassigned");
	}

	tun_ctx->router_ctx = router_ctx;

	route4(router_ctx, jip4, tun_ctx);
}

JNI_METHOD(OcpaVpnWorkflow, defaultRoute4, void, jlong jrouterctx, jlong jtunctx) {
	router_ctx_t* router_ctx = (router_ctx_t*) (intptr_t) jrouterctx;
	common_tun_ctx_t* tun_ctx = (common_tun_ctx_t*) (intptr_t) jtunctx;
	if(router_ctx == NULL || tun_ctx == NULL) {
		return;
	}

	if(tun_ctx->router_ctx != NULL && tun_ctx->router_ctx != router_ctx) {
		__android_log_write(ANDROID_LOG_ERROR, "ocpa.c", "Memory leak! TUN context was reassigned");
	}

	tun_ctx->router_ctx = router_ctx;

	default4(router_ctx, tun_ctx);
}

JNI_METHOD(OcpaVpnWorkflow, removeRoute4, void, jlong jrouterctx, jint jip4) {
	router_ctx_t* router_ctx = (router_ctx_t*) (intptr_t) jrouterctx;
	if(router_ctx == NULL) {
		return;
	}

	unroute4(router_ctx, jip4);
}
