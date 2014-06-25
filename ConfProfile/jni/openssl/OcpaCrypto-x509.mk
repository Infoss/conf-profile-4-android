include $(CLEAR_VARS)

common_cflags := \
  -DNO_WINDOWS_BRAINDEATH \

common_src_files := \
  crypto/x509/by_dir.c \
  crypto/x509/by_file.c \
  crypto/x509/x509_att.c \
  crypto/x509/x509_cmp.c \
  crypto/x509/x509_d2.c \
  crypto/x509/x509_def.c \
  crypto/x509/x509_err.c \
  crypto/x509/x509_ext.c \
  crypto/x509/x509_lu.c \
  crypto/x509/x509_obj.c \
  crypto/x509/x509_r2x.c \
  crypto/x509/x509_req.c \
  crypto/x509/x509_set.c \
  crypto/x509/x509_trs.c \
  crypto/x509/x509_txt.c \
  crypto/x509/x509_v3.c \
  crypto/x509/x509_vfy.c \
  crypto/x509/x509_vpm.c \
  crypto/x509/x509cset.c \
  crypto/x509/x509name.c \
  crypto/x509/x509rset.c \
  crypto/x509/x509spki.c \
  crypto/x509/x509type.c \
  crypto/x509/x_all.c \

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

LOCAL_MODULE:= libcrypto_part_x509

# Replace cflags with static-specific cflags so we dont build in libdl deps
LOCAL_CFLAGS_32 := $(openssl_cflags_static_32)
LOCAL_CFLAGS_64 := $(openssl_cflags_static_64)

LOCAL_SRC_FILES += $(LOCAL_SRC_FILES_$(TARGET_ARCH))
LOCAL_CFLAGS += $(LOCAL_CFLAGS_$(TARGET_ARCH)) $(LOCAL_CFLAGS_32)

include $(BUILD_STATIC_LIBRARY)
