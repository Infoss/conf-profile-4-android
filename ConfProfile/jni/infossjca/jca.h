/*
 * jca.h
 *
 *      Author: Dmitry
 */

#ifndef JCA_H_
#define JCA_H_

#include <openssl/rsa.h>

#include <android/log.h>

#define LOGV(LOG_TAG, ...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGD(LOG_TAG, ...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(LOG_TAG, ...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(LOG_TAG, ...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(LOG_TAG, ...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGF(LOG_TAG, ...) __android_log_print(ANDROID_LOG_FATAL, LOG_TAG, __VA_ARGS__)

#define CRYPTO_CALL(fname) (crypto_lib_table-> fname)

typedef struct jca_crypto_lib_table_t jca_crypto_lib_table_t;
struct jca_crypto_lib_table_t {
	void* libcrypto_handle;
	int (*RSA_size)(const RSA *r);
	int (*RSA_check_key)(RSA *rsa);
	int (*RSA_private_encrypt)(int flen, const unsigned char *from, unsigned char *to, RSA *rsa, int padding);

	RSA* (*d2i_RSAPrivateKey)(RSA **a, const unsigned char **pp, long length);
};

extern jca_crypto_lib_table_t* crypto_lib_table;


#endif /* JCA_H_ */
