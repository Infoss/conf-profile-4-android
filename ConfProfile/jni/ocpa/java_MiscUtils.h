/*
 * java_MiscUtils.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef JAVA_MISCUTILS_H_
#define JAVA_MISCUTILS_H_

#include <stdint.h>
#include <jni.h>

typedef struct java_MiscUtils java_MiscUtils;

struct java_MiscUtils {
	jobject (*intToFileDescriptor)(java_MiscUtils* instance, int32_t fd);
	int32_t (*fileDescriptorToInt)(java_MiscUtils* instance, jobject fd);
};

java_MiscUtils* wrap_into_MiscUtils();
void destroy_MiscUtils(java_MiscUtils* instance);

#endif /* JAVA_MISCUTILS_H_ */
