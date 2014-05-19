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

	androidjni_attach_thread(&env);

	method_id = (*env)->GetMethodID(env, android_ocpavpnservice_builder_class, "establish", "()I");

	if (!method_id) {
		goto failed;
	}

	fd = (*env)->CallIntMethod(env, jbuilder, method_id);
	if (fd == -1) {
		goto failed;
	}

	androidjni_detach_thread();

	//START LOOP
	router_ctx_t* ctx = (router_ctx_t*) (intptr_t) jrouterctx;
	if(ctx == NULL) {
		goto failed;
	}

	ctx->dev_fd = fd;

	int res = 0;
	while(true) {
		res = read_ip_packet(ctx->dev_fd, ctx->ip4_pkt_buff, ctx->ip4_pkt_buff_size);
		if(res < 0) {
			return res;
		}

		ipsend(ctx, ctx->ip4_pkt_buff, res);
	}
	//END LOOP

	return 0;

failed:
	androidjni_exception_occurred(env);
	androidjni_detach_thread();
	return -1;
}

int common_tun_send(intptr_t tun_ctx, uint8_t* buff, int len) {
	if(tun_ctx == (intptr_t) NULL) {
		return EBADF;
	}

	common_tun_ctx_t* ctx = (common_tun_ctx_t*) tun_ctx;
	return write(ctx->remote_fd, buff, len);
}

int common_tun_recv(intptr_t tun_ctx, uint8_t* buff, int len) {
	if(tun_ctx == (intptr_t) NULL) {
		return EBADF;
	}

	common_tun_ctx_t* ctx = (common_tun_ctx_t*) tun_ctx;

	int res = read_ip_packet(ctx->local_fd, buff, len);
	if(res < 0) {
		return res;
	}

	res = write(ctx->router_ctx->dev_fd, buff, res);

	return res;
}

