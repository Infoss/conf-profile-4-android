
#include <errno.h>
#include <stdbool.h>
#include <dlfcn.h>

#include "android_jni.h"
#include "jca.h"

#define LOG_TAG "jca.c"

jca_crypto_lib_table_t* crypto_lib_table = NULL;

bool sign_none_with_rsa(RSA* rsa, char* src_buff, int src_len, char* dst_buff, int dst_len) {
	bool success = false;

	LOGD(LOG_TAG, "sign_none_with_rsa(rsa=%p, src_buff=%p, src_len=%d, dst_buff=%p, dst_len=%d)",
			rsa, src_buff, src_len, dst_buff, dst_len);

	if(CRYPTO_CALL(RSA_private_encrypt)(src_len,
			src_buff,
			dst_buff,
			rsa,
			RSA_PKCS1_PADDING) == dst_len) {
		LOGD(LOG_TAG, "CRYPTO_CALL(RSA_private_encrypt) succeed");
		success = true;
	}

	return success;
}

void verify_none_with_rsa() {

}

JNI_METHOD(InfossJcaProvider,
		nativeInitProvider,
		jboolean) {
	if(crypto_lib_table != NULL) {
		LOGD(LOG_TAG, "Provider already initialized");
		return JNI_TRUE;
	}

	crypto_lib_table = malloc(sizeof(jca_crypto_lib_table_t));
	if(crypto_lib_table == NULL) {
		return JNI_FALSE;
	}
	LOGD(LOG_TAG, "memory allocated");

	jmethodID method_id = (*env)->GetMethodID(env,
			android_infossjcaprovider_class,
			"getLibcryptoPath",
			"()Ljava/lang/String;");
	LOGD(LOG_TAG, "method_id = %p", method_id);

	if (!method_id) {
		free(crypto_lib_table);
		crypto_lib_table = NULL;
		return JNI_FALSE;
	}

	jstring ld_path_str = (*env)->CallObjectMethod(env, this, method_id);
	char* ld_path = androidjni_convert_jstring(env, ld_path_str);
	LOGD(LOG_TAG, "LD path is = %s", ld_path);

	crypto_lib_table->libcrypto_handle = dlopen(ld_path, RTLD_NOW);
	LOGD(LOG_TAG, "libcrypto_handle = %p", crypto_lib_table->libcrypto_handle);
	free(ld_path);
	if(crypto_lib_table->libcrypto_handle == NULL) {
		char* err_msg = dlerror();
		LOGE(LOG_TAG, "Error while loading a library: %s", err_msg);
		free(crypto_lib_table);
		crypto_lib_table = NULL;
		return JNI_FALSE;
	}

	crypto_lib_table->RSA_size = dlsym(crypto_lib_table->libcrypto_handle, "RSA_size");
	LOGD(LOG_TAG, "RSA_size = %p", crypto_lib_table->RSA_size);

	crypto_lib_table->RSA_check_key = dlsym(crypto_lib_table->libcrypto_handle, "RSA_check_key");
	LOGD(LOG_TAG, "RSA_check_key = %p", crypto_lib_table->RSA_check_key);

	crypto_lib_table->RSA_private_encrypt = dlsym(crypto_lib_table->libcrypto_handle, "RSA_private_encrypt");
	LOGD(LOG_TAG, "RSA_private_encrypt = %p", crypto_lib_table->RSA_private_encrypt);

	crypto_lib_table->d2i_RSAPrivateKey = dlsym(crypto_lib_table->libcrypto_handle, "d2i_RSAPrivateKey");
	LOGD(LOG_TAG, "d2i_RSAPrivateKey = %p", crypto_lib_table->d2i_RSAPrivateKey);

	return JNI_TRUE;
}

JNI_METHOD(NoneWithRsaSignatureSpi,
		nativeSignNoneWithRsa,
		jbyteArray,
		jbyteArray derKey,
		jbyteArray data) {
	jbyteArray result = NULL;

	int der_priv_key_len = (*env)->GetArrayLength(env, derKey);
	int data_to_sign_len = (*env)->GetArrayLength(env, data);
	uint8_t* der_priv_key = (*env)->GetByteArrayElements(env, derKey, JNI_FALSE);
	uint8_t* data_to_sign = (*env)->GetByteArrayElements(env, data, JNI_FALSE);

	LOGD(LOG_TAG, "der_priv_key=%p dep_priv_key_len=%d", der_priv_key, der_priv_key_len);

	RSA* rsa = CRYPTO_CALL(d2i_RSAPrivateKey)(NULL, (const unsigned char**)&der_priv_key, der_priv_key_len);
	if(rsa == NULL || CRYPTO_CALL(RSA_check_key)(rsa) != 1) {
		LOGE(LOG_TAG, "rsa is NULL (actually %p) or RSA_check_key returned not 1", rsa);
		goto error;
	}

	int sign_result_len = CRYPTO_CALL(RSA_size)(rsa);
	uint8_t* sign_result = malloc(sign_result_len);
	if(sign_result == NULL) {
		LOGE(LOG_TAG, "sign_result == NULL");
		goto error;
	}

	if(sign_none_with_rsa(rsa, data_to_sign, data_to_sign_len, sign_result, sign_result_len)) {
		result = (*env)->NewByteArray(env, sign_result_len);
		(*env)->SetByteArrayRegion(env, result, 0, sign_result_len, sign_result);
		LOGD(LOG_TAG, "Filling result");
	}
	goto exit;
error:
	LOGE(LOG_TAG, "Falling into error");
	goto exit;
exit:
//TODO: GC RSA
	(*env)->ReleaseByteArrayElements(env, derKey, der_priv_key, JNI_ABORT);
	(*env)->ReleaseByteArrayElements(env, data, data_to_sign, JNI_ABORT);
	return result;
}
