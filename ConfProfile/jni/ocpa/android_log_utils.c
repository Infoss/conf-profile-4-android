/*
 * android_log_utils.c
 *
 *      Author: Dmitry Vorobiev
 */

#include <stdio.h>
#include <string.h>
#include "android_log_utils.h"

inline void log_dump_packet(const char *tag, uint8_t* buff, int len) {
	LOGD(tag, "Packet buffer dump (%d byte(s) at %p):", len, buff);
	uint8_t local[49];
	int i;
	int pos = 0;
	while(pos < len) {
		memset(&local, 0, sizeof(local));
		int cnt = 16;
		if(pos + 16 > len) {
			cnt = len - pos;
		}

		for(i = 0; i < cnt; i++) {
			sprintf((uint8_t*)(&local) + (i * 3), "%02x ", buff[pos]);
			pos++;
		}

		LOGD(tag, "%s", (char*) &local);
	}
}

/* (Got from AOSP libnativehelper)
 * Get a human-readable summary of an exception object.  The buffer will
 * be populated with the "binary" class name and, if present, the
 * exception message.
 */
static void getExceptionSummary(JNIEnv* env, jthrowable exception, char* buf, size_t bufLen) {
    int success = 0;
    /* get the name of the exception's class */
    jclass exceptionClazz = (*env)->GetObjectClass(env, exception); // can't fail
    jclass classClazz = (*env)->GetObjectClass(env, exceptionClazz); // java.lang.Class, can't fail
    jmethodID classGetNameMethod = (*env)->GetMethodID(
            env, classClazz, "getName", "()Ljava/lang/String;");
    jstring classNameStr = (*env)->CallObjectMethod(env, exceptionClazz, classGetNameMethod);
    if (classNameStr != NULL) {
        /* get printable string */
        const char* classNameChars = (*env)->GetStringUTFChars(env, classNameStr, NULL);
        if (classNameChars != NULL) {
            /* if the exception has a message string, get that */
            jmethodID throwableGetMessageMethod = (*env)->GetMethodID(
                    env, exceptionClazz, "getMessage", "()Ljava/lang/String;");
            jstring messageStr = (*env)->CallObjectMethod(
                    env, exception, throwableGetMessageMethod);
            if (messageStr != NULL) {
                const char* messageChars = (*env)->GetStringUTFChars(env, messageStr, NULL);
                if (messageChars != NULL) {
                    snprintf(buf, bufLen, "%s: %s", classNameChars, messageChars);
                    (*env)->ReleaseStringUTFChars(env, messageStr, messageChars);
                } else {
                    (*env)->ExceptionClear(env); // clear OOM
                    snprintf(buf, bufLen, "%s: <error getting message>", classNameChars);
                }
                (*env)->DeleteLocalRef(env, messageStr);
            } else {
                strncpy(buf, classNameChars, bufLen);
                buf[bufLen - 1] = '\0';
            }
            (*env)->ReleaseStringUTFChars(env, classNameStr, classNameChars);
            success = 1;
        }
        (*env)->DeleteLocalRef(env, classNameStr);
    }
    (*env)->DeleteLocalRef(env, classClazz);
    (*env)->DeleteLocalRef(env, exceptionClazz);
    if (! success) {
        (*env)->ExceptionClear(env);
        snprintf(buf, bufLen, "%s", "<error getting class name>");
    }
}

/* (Got from AOSP libnativehelper)
 * Formats an exception as a string with its stack trace.
 */
static void printStackTrace(JNIEnv* env, jthrowable exception, char* buf, size_t bufLen) {
    int success = 0;
    jclass stringWriterClazz = (*env)->FindClass(env, "java/io/StringWriter");
    if (stringWriterClazz != NULL) {
        jmethodID stringWriterCtor = (*env)->GetMethodID(env, stringWriterClazz,
                "<init>", "()V");
        jmethodID stringWriterToStringMethod = (*env)->GetMethodID(env, stringWriterClazz,
                "toString", "()Ljava/lang/String;");
        jclass printWriterClazz = (*env)->FindClass(env, "java/io/PrintWriter");
        if (printWriterClazz != NULL) {
            jmethodID printWriterCtor = (*env)->GetMethodID(env, printWriterClazz,
                    "<init>", "(Ljava/io/Writer;)V");
            jobject stringWriterObj = (*env)->NewObject(env, stringWriterClazz, stringWriterCtor);
            if (stringWriterObj != NULL) {
                jobject printWriterObj = (*env)->NewObject(env, printWriterClazz, printWriterCtor,
                        stringWriterObj);
                if (printWriterObj != NULL) {
                    jclass exceptionClazz = (*env)->GetObjectClass(env, exception); // can't fail
                    jmethodID printStackTraceMethod = (*env)->GetMethodID(
                            env, exceptionClazz, "printStackTrace", "(Ljava/io/PrintWriter;)V");
                    (*env)->CallVoidMethod(
                            env, exception, printStackTraceMethod, printWriterObj);
                    if (! (*env)->ExceptionCheck(env)) {
                        jstring messageStr = (*env)->CallObjectMethod(
                                env, stringWriterObj, stringWriterToStringMethod);
                        if (messageStr != NULL) {
                            jsize messageStrLength = (*env)->GetStringLength(env, messageStr);
                            if (messageStrLength >= (jsize) bufLen) {
                                messageStrLength = bufLen - 1;
                            }
                            (*env)->GetStringUTFRegion(env, messageStr, 0, messageStrLength, buf);
                            (*env)->DeleteLocalRef(env, messageStr);
                            buf[messageStrLength] = '\0';
                            success = 1;
                        }
                    }
                    (*env)->DeleteLocalRef(env, exceptionClazz);
                    (*env)->DeleteLocalRef(env, printWriterObj);
                }
                (*env)->DeleteLocalRef(env, stringWriterObj);
            }
            (*env)->DeleteLocalRef(env, printWriterClazz);
        }
        (*env)->DeleteLocalRef(env, stringWriterClazz);
    }
    if (! success) {
        (*env)->ExceptionClear(env);
        getExceptionSummary(env, exception, buf, bufLen);
    }
}

/* (Got from AOSP libnativehelper)
 * Log an exception.
 * If exception is NULL, logs the current exception in the JNI environment, if any.
 */
void jniLogException(JNIEnv* env, int priority, const char* tag, jthrowable exception) {
    int currentException = 0;
    if (exception == NULL) {
        exception = (*env)->ExceptionOccurred(env);
        if (exception == NULL) {
            return;
        }
        (*env)->ExceptionClear(env);
        currentException = 1;
    }
    char buffer[1024];
    printStackTrace(env, exception, buffer, sizeof(buffer));
    __android_log_write(priority, tag, buffer);
    if (currentException) {
        (*env)->Throw(env, exception); // rethrow
        (*env)->DeleteLocalRef(env, exception);
    }
}
