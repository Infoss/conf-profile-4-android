include $(CLEAR_VARS)

common_cflags := \
  -DNO_WINDOWS_BRAINDEATH \

common_src_files := \
  crypto/pkcs12/p12_add.c \
  crypto/pkcs12/p12_asn.c \
  crypto/pkcs12/p12_attr.c \
  crypto/pkcs12/p12_crpt.c \
  crypto/pkcs12/p12_crt.c \
  crypto/pkcs12/p12_decr.c \
  crypto/pkcs12/p12_init.c \
  crypto/pkcs12/p12_key.c \
  crypto/pkcs12/p12_kiss.c \
  crypto/pkcs12/p12_mutl.c \
  crypto/pkcs12/p12_npas.c \
  crypto/pkcs12/p12_p8d.c \
  crypto/pkcs12/p12_p8e.c \
  crypto/pkcs12/p12_utl.c \
  crypto/pkcs12/pk12err.c \
  crypto/pkcs7/pk7_asn1.c \
  crypto/pkcs7/pk7_attr.c \
  crypto/pkcs7/pk7_doit.c \
  crypto/pkcs7/pk7_lib.c \
  crypto/pkcs7/pk7_mime.c \
  crypto/pkcs7/pk7_smime.c \
  crypto/pkcs7/pkcs7err.c \

common_c_includes := \
  jni/openssl/. \
  jni/openssl/crypto \
  jni/openssl/crypto/asn1 \
  jni/openssl/crypto/evp \
  jni/openssl/crypto/modes \
  jni/openssl/include \
  jni/openssl/include/openssl \

arm_clang_asflags := \
  -no-integrated-as \

arm_cflags := \
  -DAES_ASM \
  -DBSAES_ASM \
  -DDES_UNROLL \
  -DGHASH_ASM \
  -DOPENSSL_BN_ASM_GF2m \
  -DOPENSSL_BN_ASM_MONT \
  -DOPENSSL_CPUID_OBJ \
  -DSHA1_ASM \
  -DSHA256_ASM \
  -DSHA512_ASM \

arm_src_files := \

arm_exclude_files := \


arm64_clang_asflags := \
  -no-integrated-as \

arm64_cflags := \
  -DDES_UNROLL \
  -DOPENSSL_CPUID_OBJ \
  -DSHA1_ASM \
  -DSHA256_ASM \
  -DSHA512_ASM \

arm64_src_files := \

arm64_exclude_files :=

x86_clang_asflags :=

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
  -DRC4_INDEX \
  -DRMD160_ASM \
  -DSHA1_ASM \
  -DSHA256_ASM \
  -DSHA512_ASM \
  -DVPAES_ASM \

x86_src_files := \

x86_exclude_files := \

x86_64_clang_asflags :=

x86_64_cflags := \
  -DAES_ASM \
  -DBSAES_ASM \
  -DDES_UNROLL \
  -DGHASH_ASM \
  -DMD5_ASM \
  -DOPENSSL_BN_ASM_GF2m \
  -DOPENSSL_BN_ASM_MONT \
  -DOPENSSL_BN_ASM_MONT5 \
  -DOPENSSL_CPUID_OBJ \
  -DOPENSSL_IA32_SSE2 \
  -DSHA1_ASM \
  -DSHA256_ASM \
  -DSHA512_ASM \
  -DVPAES_ASM \

x86_64_src_files := \

x86_64_exclude_files := \

mips_clang_asflags :=

mips_cflags := \
  -DAES_ASM \
  -DOPENSSL_BN_ASM_MONT \
  -DSHA1_ASM \
  -DSHA256_ASM \

mips_src_files := \

mips_exclude_files := \


mips64_clang_asflags :=

mips64_cflags := \
  -DOPENSSL_NO_ASM \

mips64_src_files :=

mips64_exclude_files :=

mips32r6_clang_asflags :=

mips32r6_cflags := \
  -DOPENSSL_NO_ASM \

mips32r6_src_files :=

mips32r6_exclude_files :=


# "Temporary" hack until this can be fixed in openssl.config
x86_64_cflags += -DRC4_INT="unsigned int"

LOCAL_SRC_FILES_$(TARGET_ARCH) :=
LOCAL_CFLAGS_$(TARGET_ARCH) :=
LOCAL_CLANG_ASFLAGS_$(TARGET_ARCH) :=

LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/include

ifdef ARCH_MIPS_REV6
mips_cflags := $(mips32r6_cflags)
mips_src_files := $(mips32r6_src_files)
mips_exclude_files := $(mips32r6_exclude_files)
endif

LOCAL_CFLAGS += $(common_cflags)
LOCAL_C_INCLUDES += $(common_c_includes)

LOCAL_SRC_FILES_arm += $(filter-out $(arm_exclude_files),$(common_src_files) $(arm_src_files))
LOCAL_CFLAGS_arm += $(arm_cflags)
LOCAL_CLANG_ASFLAGS_arm += $(arm_clang_asflags)

LOCAL_SRC_FILES_arm64 += $(filter-out $(arm64_exclude_files),$(common_src_files) $(arm64_src_files))
LOCAL_CFLAGS_arm64 += $(arm64_cflags)
LOCAL_CLANG_ASFLAGS_arm64 += $(arm64_clang_asflags)

LOCAL_SRC_FILES_x86 += $(filter-out $(x86_exclude_files),$(common_src_files) $(x86_src_files))
LOCAL_CFLAGS_x86 += $(x86_cflags)
LOCAL_CLANG_ASFLAGS_x86 += $(x86_clang_asflags)

LOCAL_SRC_FILES_x86_64 += $(filter-out $(x86_64_exclude_files),$(common_src_files) $(x86_64_src_files))
LOCAL_CFLAGS_x86_64 += $(x86_64_cflags)
LOCAL_CLANG_ASFLAGS_x86_64 += $(x86_64_clang_asflags)

LOCAL_SRC_FILES_mips += $(filter-out $(mips_exclude_files),$(common_src_files) $(mips_src_files))
LOCAL_CFLAGS_mips += $(mips_cflags)
LOCAL_CLANG_ASFLAGS_mips += $(mips_clang_asflags)

LOCAL_SRC_FILES_mips64 += $(filter-out $(mips64_exclude_files),$(common_src_files) $(mips64_src_files))
LOCAL_CFLAGS_mips64 += $(mips64_cflags)
LOCAL_CLANG_ASFLAGS_mips64 += $(mips64_clang_asflags)

include $(LOCAL_PATH)/android-config.mk

LOCAL_MODULE:= libcrypto_part_pkcs

# Replace cflags with static-specific cflags so we dont build in libdl deps
LOCAL_CFLAGS_32 := $(openssl_cflags_static_32)
LOCAL_CFLAGS_64 := $(openssl_cflags_static_64)

LOCAL_SRC_FILES += $(LOCAL_SRC_FILES_$(TARGET_ARCH))
LOCAL_CFLAGS += $(LOCAL_CFLAGS_$(TARGET_ARCH)) $(LOCAL_CFLAGS_32)

include $(BUILD_STATIC_LIBRARY)
