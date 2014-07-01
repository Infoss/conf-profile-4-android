/*
 * jni_RouterLoop.c
 *
 *      Author: Dmitry Vorobiev
 */

#include "android_log_utils.h"
#include "android_jni.h"
#include <stdio.h>
#include <errno.h>
#include "ocpa.h"
#include "router.h"

#define LOG_TAG "jni_RouterLoop.c"

JNI_METHOD(RouterLoop, initIpRouter, jlong) {
	return (jlong) (intptr_t) router_init();
}

JNI_METHOD(RouterLoop, deinitIpRouter, void, jlong jrouterctx) {
	router_deinit((router_ctx_t*) (intptr_t) jrouterctx);
}

JNI_METHOD(RouterLoop, routerLoop, jint, jlong jrouterctx, jobject jbuilder) {
	jint ret_val = 0;
	jmethodID method_id;
	int fd;

	method_id = (*env)->GetMethodID(env, android_ocpavpnservice_builder_class, "establish", "()I");

	if (!method_id) {
		LOGE(LOG_TAG, "Unknown method: establish()I");
		goto failed;
	}

	fd = (*env)->CallIntMethod(env, jbuilder, method_id);
	if (fd == -1) {
		LOGE(LOG_TAG, "Builder returned invalid fd");
		goto failed;
	}

	//START LOOP
	router_ctx_t* ctx = (router_ctx_t*) (intptr_t) jrouterctx;
	if(ctx == NULL) {
		LOGE(LOG_TAG, "Router context is null");
		goto failed;
	}

	ctx->dev_tun_ctx.local_fd = fd;
	ctx->dev_tun_ctx.remote_fd = fd;

	int timeout_msecs = 500;
	int router_sleep_msecs = 250;
	int ip_pkt_buff_size = 1500;
	uint8_t* ip_pkt_buff = malloc(sizeof(uint8_t) * ip_pkt_buff_size);

	poll_helper_struct_t* poll_struct = malloc(sizeof(poll_helper_struct_t));
	if(poll_struct == NULL) {
		LOGE(LOG_TAG, "Can't allocate poll helper struct");
		goto failed;
	}
	poll_struct->poll_ctxs = NULL;
	poll_struct->poll_fds = NULL;
	poll_struct->poll_fds_count = 0;
	rebuild_poll_struct(ctx, poll_struct);

	LOGD(LOG_TAG, "Starting router loop");
	int i;
	int res = 0;
	while(true) {

		while(router_is_paused(ctx)) {
			usleep(router_sleep_msecs);
		}

		if(ctx->routes_updated) {
			rebuild_poll_struct(ctx, poll_struct);
		}

		res = poll(poll_struct->poll_fds, poll_struct->poll_fds_count, timeout_msecs);

		if(res > 0) {
			for(i = 0; i < poll_struct->poll_fds_count; i++) {
				if((poll_struct->poll_fds[i].revents && POLLIN) != 0) {
					LOGD(LOG_TAG, "Read from fd=%d", poll_struct->poll_fds[i].fd);
					res = 0;
					tun_recv_func_ptr recv_func = poll_struct->poll_ctxs[i]->recv_func;
					if(recv_func != NULL) {
						res = recv_func((intptr_t) poll_struct->poll_ctxs[i], ip_pkt_buff, ip_pkt_buff_size);
						if(res == 0) {
							LOGD(LOG_TAG, "Read from fd=%d returned 0", poll_struct->poll_fds[i].fd);
						}
					} else {
						LOGE(LOG_TAG, "Tunnel is not ready, dropping a packet");
						log_dump_packet(LOG_TAG, ip_pkt_buff, ip_pkt_buff_size);
					}

					if(res < 0) {
						//TODO: error handling
						LOGE(LOG_TAG, "Can't read IP packet from tun socket (tun_ctx=%p)", poll_struct->poll_ctxs[i]);
						LOGE(LOG_TAG, "R/W on tun fds finished with error %d (returned %d) %s", errno, res, strerror(errno));
						log_dump_packet(LOG_TAG, ip_pkt_buff, ip_pkt_buff_size);
						goto failed;
					}


				} else if((poll_struct->poll_fds[i].revents && POLLHUP) != 0) {
					LOGE(LOG_TAG, "HUP for fd %d", poll_struct->poll_fds[i].fd);
				} else {
					LOGD(LOG_TAG, "no action for fd %d (events=%d, revents=%d)", poll_struct->poll_fds[i].fd, poll_struct->poll_fds[i].events, poll_struct->poll_fds[i].revents);
				}

				poll_struct->poll_fds[i].revents = 0;
			}
		} else if(res < 0) {
			LOGD(LOG_TAG, "Polling tun fds finished with error %d %s", errno, strerror(errno));
		}

		if(ctx->terminate) {
			LOGD(LOG_TAG, "Terminating router loop");
			break;
		}
	}
	//END LOOP
	if(poll_struct != NULL) {
		if(poll_struct->poll_ctxs != NULL) {
			free(poll_struct->poll_ctxs);
			poll_struct->poll_ctxs = NULL;
		}
		if(poll_struct->poll_fds != NULL) {
			free(poll_struct->poll_fds);
			poll_struct->poll_fds = NULL;
		}
		poll_struct->poll_fds_count = 0;
		free(poll_struct);
	}
	LOGD(LOG_TAG, "Router loop was gracefully terminated");
	ret_val = 0;
	goto exit;

failed:
	if(poll_struct != NULL) {
		if(poll_struct->poll_ctxs != NULL) {
			free(poll_struct->poll_ctxs);
			poll_struct->poll_ctxs = NULL;
		}
		if(poll_struct->poll_fds != NULL) {
			free(poll_struct->poll_fds);
			poll_struct->poll_fds = NULL;
		}
		poll_struct->poll_fds_count = 0;
		free(poll_struct);
	}
	LOGD(LOG_TAG, "Router loop was terminated due to error");
	ret_val = -1;
	goto exit;

exit:
	return ret_val;
}

JNI_METHOD(RouterLoop, addRoute4, void, jlong jrouterctx, jint jip4, jint jmask, jlong jtunctx) {
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

	route4(router_ctx, jip4, jmask, tun_ctx);
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

JNI_METHOD(RouterLoop, removeRoute4, void, jlong jrouterctx, jint jip4, jint jmask) {
	router_ctx_t* router_ctx = (router_ctx_t*) (intptr_t) jrouterctx;
	if(router_ctx == NULL) {
		return;
	}

	unroute4(router_ctx, jip4, jmask);
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
	if((*env)->CallBooleanMethod(env, list, array_list_add, item) != JNI_TRUE) {
		LOGW(LOG_TAG, "Can't add object %p to list", item);
	}

	route4_link_t* link4 = router_ctx->ip4_routes;
	while(link4 != NULL) {
		//TODO: fix subnets
		item = (*env)->NewObject(env, route4_class, route4_init, (jlong) (intptr_t) link4->tun_ctx, link4->ip4, 32);
		if((*env)->CallBooleanMethod(env, list, array_list_add, item) != JNI_TRUE) {
			LOGW(LOG_TAG, "Can't add object %p to list", item);
		}
		link4 = link4->next;
	}

	pthread_rwlock_unlock(router_ctx->rwlock4);
	return list;
}

JNI_METHOD(RouterLoop, isPausedRouterLoop, jboolean, jlong jrouterctx) {
	router_ctx_t* router_ctx = (router_ctx_t*) (intptr_t) jrouterctx;
	if(router_ctx == NULL) {
		return false;
	}
	return router_is_paused(router_ctx);
}

JNI_METHOD(RouterLoop, pauseRouterLoop, jboolean, jlong jrouterctx, jboolean jpause) {
	router_ctx_t* router_ctx = (router_ctx_t*) (intptr_t) jrouterctx;
	if(router_ctx == NULL) {
		return false;
	}
	return router_pause(router_ctx, jpause);
}

JNI_METHOD(RouterLoop, terminateRouterLoop, void, jlong jrouterctx) {
	router_ctx_t* router_ctx = (router_ctx_t*) (intptr_t) jrouterctx;
	if(router_ctx == NULL) {
		return;
	}
	pthread_rwlock_wrlock(router_ctx->rwlock4);
	router_ctx->terminate = true;
	pthread_rwlock_unlock(router_ctx->rwlock4);
}

JNI_METHOD(RouterLoop, setMasqueradeIp4Mode, void, jlong jrouterctx, jboolean jison) {
	if(((router_ctx_t*) (intptr_t) jrouterctx) == NULL) {
			return;
		}

	router_ctx_t* ctx = (router_ctx_t*) (intptr_t) jrouterctx;

	ctx->dev_tun_ctx.use_masquerade4 = jison;
}

JNI_METHOD(RouterLoop, setMasqueradeIp4, void, jlong jrouterctx, jint jip) {
	if(((router_ctx_t*) (intptr_t) jrouterctx) == NULL) {
		return;
	}

	router_ctx_t* ctx = (router_ctx_t*) (intptr_t) jrouterctx;

	ctx->dev_tun_ctx.masquerade4 = jip;
}

JNI_METHOD(RouterLoop, setMasqueradeIp6Mode, void, jlong jrouterctx, jboolean jison) {
	if(((router_ctx_t*) (intptr_t) jrouterctx) == NULL) {
			return;
		}

	router_ctx_t* ctx = (router_ctx_t*) (intptr_t) jrouterctx;

	ctx->dev_tun_ctx.use_masquerade6 = jison;
}

JNI_METHOD(RouterLoop, setMasqueradeIp6, void, jlong jrouterctx, jbyteArray jip) {
	if(((router_ctx_t*) (intptr_t) jrouterctx) == NULL) {
		return;
	}

	router_ctx_t* ctx = (router_ctx_t*) (intptr_t) jrouterctx;
	uint8_t* ip6 = (*env)->GetByteArrayElements(env, jip, JNI_FALSE);
	memcpy(ctx->dev_tun_ctx.masquerade6, ip6, 16);
	(*env)->ReleaseByteArrayElements(env, jip, ip6, JNI_ABORT);
}

JNI_METHOD(RouterLoop, debugRestartPcap, void, jlong jrouterctx, jobject jos) {
	if(((router_ctx_t*) (intptr_t) jrouterctx) == NULL) {
		return;
	}

	router_ctx_t* ctx = (router_ctx_t*) (intptr_t) jrouterctx;
	if(ctx->dev_tun_ctx.pcap_output != NULL) {
		pcap_output_reset(ctx->dev_tun_ctx.pcap_output, jos);
	} else {
		ctx->dev_tun_ctx.pcap_output = pcap_output_init(jos);
	}

}

JNI_METHOD(RouterLoop, debugStopPcap, void, jlong jrouterctx) {
	if(((router_ctx_t*) (intptr_t) jrouterctx) == NULL) {
		return;
	}

	router_ctx_t* ctx = (router_ctx_t*) (intptr_t) jrouterctx;
	if(ctx->dev_tun_ctx.pcap_output != NULL) {
		pcap_output_flush(ctx->dev_tun_ctx.pcap_output);
		pcap_output_close(ctx->dev_tun_ctx.pcap_output);
		pcap_output_destroy(ctx->dev_tun_ctx.pcap_output);
		ctx->dev_tun_ctx.pcap_output = NULL;
	}
}