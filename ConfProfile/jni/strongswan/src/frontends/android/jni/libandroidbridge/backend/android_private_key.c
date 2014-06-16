/*
 * Copyright (C) 2012 Tobias Brunner
 * Hochschule fuer Technik Rapperswil
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.  See <http://www.fsf.org/copyleft/gpl.txt>.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 */

#include "android_private_key.h"

#include "../android_jni.h"
#include <utils/debug.h>

typedef struct private_private_key_t private_private_key_t;

/**
 * Private data of a android_private_key_t object.
 */
struct private_private_key_t {

	/**
	 * Public interface
	 */
	private_key_t public;

	/**
	 * reference to the Java PrivateKey object
	 */
	jobject key;

	/**
	 * Java class used to build signatures
	 */
	jclass signature_class;

	/**
	 * public key that belongs to this private key
	 */
	public_key_t *pubkey;

	/**
	 * reference count
	 */
	refcount_t ref;
};

METHOD(private_key_t, sign, bool,
	private_private_key_t *this, signature_scheme_t scheme,
	chunk_t data, chunk_t *signature)
{
	JNIEnv *env;
	jmethodID method_id;
	const char *method;
	jstring jmethod;
	jobject jsignature;
	jbyteArray jdata, jsigarray;

	switch (scheme)
	{
		case SIGN_RSA_EMSA_PKCS1_MD5:
			method = "MD5withRSA";
			break;
		case SIGN_RSA_EMSA_PKCS1_SHA1:
			method = "SHA1withRSA";
			break;
		case SIGN_RSA_EMSA_PKCS1_SHA224:
			method = "SHA224withRSA";
			break;
		case SIGN_RSA_EMSA_PKCS1_SHA256:
			method = "SHA256withRSA";
			break;
		case SIGN_RSA_EMSA_PKCS1_SHA384:
			method = "SHA384withRSA";
			break;
		case SIGN_RSA_EMSA_PKCS1_SHA512:
			method = "SHA512withRSA";
			break;
		default:
			DBG1(DBG_LIB, "signature scheme %N not supported via JNI",
				 signature_scheme_names, scheme);
			return FALSE;
	}

	androidjni_attach_thread(&env);
	/* we use java.security.Signature to create the signature without requiring
	 * access to the actual private key */
	method_id = (*env)->GetStaticMethodID(env, this->signature_class,
				"getInstance", "(Ljava/lang/String;)Ljava/security/Signature;");
	if (!method_id)
	{
		goto failed;
	}
	jmethod = (*env)->NewStringUTF(env, method);
	if (!jmethod)
	{
		goto failed;
	}
	jsignature = (*env)->CallStaticObjectMethod(env, this->signature_class,
												method_id, jmethod);
	if (!jsignature)
	{
		goto failed;
	}
	method_id = (*env)->GetMethodID(env, this->signature_class, "initSign",
									"(Ljava/security/PrivateKey;)V");
	if (!method_id)
	{
		goto failed;
	}
	(*env)->CallVoidMethod(env, jsignature, method_id, this->key);
	if (androidjni_exception_occurred(env))
	{
		goto failed;
	}
	method_id = (*env)->GetMethodID(env, this->signature_class, "update",
									"([B)V");
	if (!method_id)
	{
		goto failed;
	}
	jdata = byte_array_from_chunk(env, data);
	(*env)->CallVoidMethod(env, jsignature, method_id, jdata);
	if (androidjni_exception_occurred(env))
	{
		goto failed;
	}
	method_id = (*env)->GetMethodID(env, this->signature_class, "sign",
									"()[B");
	if (!method_id)
	{
		goto failed;
	}
	jsigarray = (*env)->CallObjectMethod(env, jsignature, method_id);
	if (!jsigarray)
	{
		goto failed;
	}
	*signature = chunk_from_byte_array(env, jsigarray);
	androidjni_detach_thread();
	return TRUE;

failed:
	DBG1(DBG_LIB, "failed to build %N signature via JNI",
		 signature_scheme_names, scheme);
	androidjni_exception_occurred(env);
	androidjni_detach_thread();
	return FALSE;
}

METHOD(private_key_t, get_type, key_type_t,
	private_private_key_t *this)
{
	return KEY_RSA;
}

METHOD(private_key_t, decrypt, bool,
	private_private_key_t *this, encryption_scheme_t scheme,
	chunk_t crypto, chunk_t *plain)
{
	DBG1(DBG_LIB, "private key decryption is currently not supported via JNI");
	return FALSE;
}

METHOD(private_key_t, get_keysize, int,
	private_private_key_t *this)
{
	return this->pubkey->get_keysize(this->pubkey);
}

METHOD(private_key_t, get_public_key, public_key_t*,
	private_private_key_t *this)
{
	return this->pubkey->get_ref(this->pubkey);
}

METHOD(private_key_t, get_encoding, bool,
	private_private_key_t *this, cred_encoding_type_t type,
	chunk_t *encoding)
{
	return FALSE;
}

METHOD(private_key_t, get_fingerprint, bool,
	private_private_key_t *this, cred_encoding_type_t type, chunk_t *fp)
{
	return this->pubkey->get_fingerprint(this->pubkey, type, fp);
}

METHOD(private_key_t, get_ref, private_key_t*,
	private_private_key_t *this)
{
	ref_get(&this->ref);
	return &this->public;
}

METHOD(private_key_t, destroy, void,
	private_private_key_t *this)
{
	if (ref_put(&this->ref))
	{
		JNIEnv *env;

		androidjni_attach_thread(&env);
		if (android_sdk_version == ANDROID_JELLY_BEAN)
		{	/* there is a bug in JB that causes a SIGSEGV if the key object is
			 * garbage collected so we intentionally leak the reference to it */
			DBG1(DBG_LIB, "intentionally leaking private key reference due to "
				 "a bug in the framework");
		}
		else
		{
			(*env)->DeleteGlobalRef(env, this->key);
		}
		(*env)->DeleteGlobalRef(env, this->signature_class);
		androidjni_detach_thread();
		this->pubkey->destroy(this->pubkey);
		free(this);
	}
}

/*
 * See header
 */
private_key_t *android_private_key_create(jobject key, public_key_t *pubkey)
{
	JNIEnv *env;
	private_private_key_t *this;

	INIT(this,
		.public = {
			.get_type = _get_type,
			.sign = _sign,
			.decrypt = _decrypt,
			.get_keysize = _get_keysize,
			.get_public_key = _get_public_key,
			.belongs_to = private_key_belongs_to,
			.equals = private_key_equals,
			.get_fingerprint = _get_fingerprint,
			.has_fingerprint = private_key_has_fingerprint,
			.get_encoding = _get_encoding,
			.get_ref = _get_ref,
			.destroy = _destroy,
		},
		.ref = 1,
		.pubkey = pubkey,
	);

	if (!pubkey)
	{
		free(this);
		return NULL;
	}

	/* in ICS we could simply call getEncoded and use the PKCS#8/DER encoded
	 * private key, since JB that's not possible as there is no direct access
	 * to private keys anymore (as these could now be hardware backed) */
	androidjni_attach_thread(&env);
	this->key = (*env)->NewGlobalRef(env, key);
	this->signature_class = (*env)->NewGlobalRef(env, (*env)->FindClass(env,
													"java/security/Signature"));
	androidjni_detach_thread();
	return &this->public;
}
