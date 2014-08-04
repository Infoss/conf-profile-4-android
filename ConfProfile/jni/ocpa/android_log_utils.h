/*
 * android_log_utils.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef ANDROID_LOG_UTILS_H_
#define ANDROID_LOG_UTILS_H_

#include <android/log.h>
#include <stdint.h>
#include <jni.h>

#define LOGV(LOG_TAG, ...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGD(LOG_TAG, ...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(LOG_TAG, ...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(LOG_TAG, ...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(LOG_TAG, ...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGF(LOG_TAG, ...) __android_log_print(ANDROID_LOG_FATAL, LOG_TAG, __VA_ARGS__)

inline void log_dump_packet(const char *tag, uint8_t* buff, int len);
void jniLogException(JNIEnv* env, int priority, const char* tag, jthrowable exception);

#endif /* ANDROID_LOG_UTILS_H_ */
