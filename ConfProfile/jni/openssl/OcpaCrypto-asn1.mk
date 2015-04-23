include $(CLEAR_VARS)

common_cflags := \
  -DNO_WINDOWS_BRAINDEATH \

common_src_files := \
  crypto/asn1/a_bitstr.c \
  crypto/asn1/a_bool.c \
  crypto/asn1/a_bytes.c \
  crypto/asn1/a_d2i_fp.c \
  crypto/asn1/a_digest.c \
  crypto/asn1/a_dup.c \
  crypto/asn1/a_enum.c \
  crypto/asn1/a_gentm.c \
  crypto/asn1/a_i2d_fp.c \
  crypto/asn1/a_int.c \
  crypto/asn1/a_mbstr.c \
  crypto/asn1/a_object.c \
  crypto/asn1/a_octet.c \
  crypto/asn1/a_print.c \
  crypto/asn1/a_set.c \
  crypto/asn1/a_sign.c \
  crypto/asn1/a_strex.c \
  crypto/asn1/a_strnid.c \
  crypto/asn1/a_time.c \
  crypto/asn1/a_type.c \
  crypto/asn1/a_utctm.c \
  crypto/asn1/a_utf8.c \
  crypto/asn1/a_verify.c \
  crypto/asn1/ameth_lib.c \
  crypto/asn1/asn1_err.c \
  crypto/asn1/asn1_gen.c \
  crypto/asn1/asn1_lib.c \
  crypto/asn1/asn1_par.c \
  crypto/asn1/asn_mime.c \
  crypto/asn1/asn_moid.c \
  crypto/asn1/asn_pack.c \
  crypto/asn1/bio_asn1.c \
  crypto/asn1/bio_ndef.c \
  crypto/asn1/d2i_pr.c \
  crypto/asn1/d2i_pu.c \
  crypto/asn1/evp_asn1.c \
  crypto/asn1/f_enum.c \
  crypto/asn1/f_int.c \
  crypto/asn1/f_string.c \
  crypto/asn1/i2d_pr.c \
  crypto/asn1/i2d_pu.c \
  crypto/asn1/n_pkey.c \
  crypto/asn1/nsseq.c \
  crypto/asn1/p5_pbe.c \
  crypto/asn1/p5_pbev2.c \
  crypto/asn1/p8_pkey.c \
  crypto/asn1/t_bitst.c \
  crypto/asn1/t_crl.c \
  crypto/asn1/t_pkey.c \
  crypto/asn1/t_req.c \
  crypto/asn1/t_spki.c \
  crypto/asn1/t_x509.c \
  crypto/asn1/t_x509a.c \
  crypto/asn1/tasn_dec.c \
  crypto/asn1/tasn_enc.c \
  crypto/asn1/tasn_fre.c \
  crypto/asn1/tasn_new.c \
  crypto/asn1/tasn_prn.c \
  crypto/asn1/tasn_typ.c \
  crypto/asn1/tasn_utl.c \
  crypto/asn1/x_algor.c \
  crypto/asn1/x_attrib.c \
  crypto/asn1/x_bignum.c \
  crypto/asn1/x_crl.c \
  crypto/asn1/x_exten.c \
  crypto/asn1/x_info.c \
  crypto/asn1/x_long.c \
  crypto/asn1/x_name.c \
  crypto/asn1/x_nx509.c \
  crypto/asn1/x_pkey.c \
  crypto/asn1/x_pubkey.c \
  crypto/asn1/x_req.c \
  crypto/asn1/x_sig.c \
  crypto/asn1/x_spki.c \
  crypto/asn1/x_val.c \
  crypto/asn1/x_x509.c \
  crypto/asn1/x_x509a.c \

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

LOCAL_MODULE:= libcrypto_part_asn1

# Replace cflags with static-specific cflags so we dont build in libdl deps
LOCAL_CFLAGS_32 := $(openssl_cflags_static_32)
LOCAL_CFLAGS_64 := $(openssl_cflags_static_64)

LOCAL_SRC_FILES += $(LOCAL_SRC_FILES_$(TARGET_ARCH))
LOCAL_CFLAGS += $(LOCAL_CFLAGS_$(TARGET_ARCH)) $(LOCAL_CFLAGS_32)

include $(BUILD_STATIC_LIBRARY)
