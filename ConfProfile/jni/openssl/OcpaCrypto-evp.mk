include $(CLEAR_VARS)

common_cflags := \
  -DNO_WINDOWS_BRAINDEATH \

common_src_files := \
  crypto/evp/bio_b64.c \
  crypto/evp/bio_enc.c \
  crypto/evp/bio_md.c \
  crypto/evp/bio_ok.c \
  crypto/evp/c_all.c \
  crypto/evp/c_allc.c \
  crypto/evp/c_alld.c \
  crypto/evp/digest.c \
  crypto/evp/e_aes.c \
  crypto/evp/e_aes_cbc_hmac_sha1.c \
  crypto/evp/e_bf.c \
  crypto/evp/e_des.c \
  crypto/evp/e_des3.c \
  crypto/evp/e_null.c \
  crypto/evp/e_old.c \
  crypto/evp/e_rc2.c \
  crypto/evp/e_rc4.c \
  crypto/evp/e_rc4_hmac_md5.c \
  crypto/evp/e_rc5.c \
  crypto/evp/e_xcbc_d.c \
  crypto/evp/encode.c \
  crypto/evp/evp_acnf.c \
  crypto/evp/evp_cnf.c \
  crypto/evp/evp_enc.c \
  crypto/evp/evp_err.c \
  crypto/evp/evp_key.c \
  crypto/evp/evp_lib.c \
  crypto/evp/evp_pbe.c \
  crypto/evp/evp_pkey.c \
  crypto/evp/m_dss.c \
  crypto/evp/m_dss1.c \
  crypto/evp/m_ecdsa.c \
  crypto/evp/m_md4.c \
  crypto/evp/m_md5.c \
  crypto/evp/m_mdc2.c \
  crypto/evp/m_null.c \
  crypto/evp/m_ripemd.c \
  crypto/evp/m_sha1.c \
  crypto/evp/m_sigver.c \
  crypto/evp/m_wp.c \
  crypto/evp/names.c \
  crypto/evp/p5_crpt.c \
  crypto/evp/p5_crpt2.c \
  crypto/evp/p_dec.c \
  crypto/evp/p_enc.c \
  crypto/evp/p_lib.c \
  crypto/evp/p_open.c \
  crypto/evp/p_seal.c \
  crypto/evp/p_sign.c \
  crypto/evp/p_verify.c \
  crypto/evp/pmeth_fn.c \
  crypto/evp/pmeth_gn.c \
  crypto/evp/pmeth_lib.c \

common_c_includes := \
  jni/openssl/. \
  jni/openssl/crypto \
  jni/openssl/crypto/asn1 \
  jni/openssl/crypto/evp \
  jni/openssl/crypto/modes \
  jni/openssl/include \
  jni/openssl/include/openssl \

arm_cflags := \
  -DAES_ASM \
  -DBSAES_ASM \
  -DGHASH_ASM \
  -DOPENSSL_BN_ASM_GF2m \
  -DOPENSSL_BN_ASM_MONT \
  -DOPENSSL_CPUID_OBJ \
  -DSHA1_ASM \
  -DSHA256_ASM \
  -DSHA512_ASM \

arm_src_files := \

arm_exclude_files := \

arm64_cflags := \
  -DOPENSSL_NO_ASM \

arm64_src_files :=

arm64_exclude_files :=

x86_cflags := \
  -DAES_ASM \
  -DDES_PTR \
  -DDES_RISC1 \
  -DDES_UNROLL \
  -DGHASH_ASM \
  -DMD5_ASM \
  -DOPENSSL_BN_ASM_GF2m \
  -DOPENSSL_BN_ASM_MONT \
  -DOPENSSL_BN_ASM_PART_WORDS \
  -DOPENSSL_CPUID_OBJ \
  -DOPENSSL_IA32_SSE2 \
  -DSHA1_ASM \
  -DSHA256_ASM \
  -DSHA512_ASM \
  -DVPAES_ASM \

x86_src_files := \

x86_exclude_files := \

x86_64_cflags := \
  -DAES_ASM \
  -DBSAES_ASM \
  -DDES_PTR \
  -DDES_RISC1 \
  -DDES_UNROLL \
  -DGHASH_ASM \
  -DMD5_ASM \
  -DOPENSSL_BN_ASM_GF2m \
  -DOPENSSL_BN_ASM_MONT \
  -DOPENSSL_BN_ASM_MONT5 \
  -DOPENSSL_CPUID_OBJ \
  -DSHA1_ASM \
  -DSHA256_ASM \
  -DSHA512_ASM \
  -DVPAES_ASM \

x86_64_src_files := \

x86_64_exclude_files := \

mips_cflags := \
  -DAES_ASM \
  -DOPENSSL_BN_ASM_MONT \
  -DSHA1_ASM \
  -DSHA256_ASM \

mips_src_files := \

mips_exclude_files := \


LOCAL_CFLAGS += $(common_cflags)
LOCAL_C_INCLUDES += $(common_c_includes)

LOCAL_SRC_FILES_arm := $(filter-out $(arm_exclude_files),$(common_src_files) $(arm_src_files))
LOCAL_CFLAGS_arm := $(arm_cflags)

LOCAL_SRC_FILES_arm64 := $(filter-out $(arm64_exclude_files),$(common_src_files) $(arm64_src_files))
LOCAL_CFLAGS_arm64 := $(arm64_cflags)

LOCAL_SRC_FILES_x86 := $(filter-out $(x86_exclude_files),$(common_src_files) $(x86_src_files))
LOCAL_CFLAGS_x86 := $(x86_cflags)

LOCAL_SRC_FILES_x86_64 := $(filter-out $(x86_64_exclude_files),$(common_src_files) $(x86_64_src_files))
LOCAL_CFLAGS_x86_64 := $(x86_64_cflags)

LOCAL_SRC_FILES_mips := $(filter-out $(mips_exclude_files),$(common_src_files) $(mips_src_files))
LOCAL_CFLAGS_mips := $(mips_cflags)

include $(LOCAL_PATH)/android-config.mk

LOCAL_MODULE:= libcrypto_part_evp

# Replace cflags with static-specific cflags so we dont build in libdl deps
LOCAL_CFLAGS_32 := $(openssl_cflags_static_32)
LOCAL_CFLAGS_64 := $(openssl_cflags_static_64)

LOCAL_SRC_FILES += $(LOCAL_SRC_FILES_$(TARGET_ARCH))
LOCAL_CFLAGS += $(LOCAL_CFLAGS_$(TARGET_ARCH)) $(LOCAL_CFLAGS_32)

include $(BUILD_STATIC_LIBRARY)