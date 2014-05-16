#include "android_jni.h"
#include "ocpa.h"
#include "router.h"

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
	return (jlong) (intptr_t) router_init(NULL);
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


