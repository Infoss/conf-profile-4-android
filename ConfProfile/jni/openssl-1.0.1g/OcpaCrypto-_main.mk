include $(CLEAR_VARS)

common_cflags := \
  -DNO_WINDOWS_BRAINDEATH \

common_src_files := \
  crypto/bf/bf_cfb64.c \
  crypto/bf/bf_ecb.c \
  crypto/bf/bf_enc.c \
  crypto/bf/bf_ofb64.c \
  crypto/bf/bf_skey.c \
  crypto/bio/b_dump.c \
  crypto/bio/b_print.c \
  crypto/bio/b_sock.c \
  crypto/bio/bf_buff.c \
  crypto/bio/bf_nbio.c \
  crypto/bio/bf_null.c \
  crypto/bio/bio_cb.c \
  crypto/bio/bio_err.c \
  crypto/bio/bio_lib.c \
  crypto/bio/bss_acpt.c \
  crypto/bio/bss_bio.c \
  crypto/bio/bss_conn.c \
  crypto/bio/bss_dgram.c \
  crypto/bio/bss_fd.c \
  crypto/bio/bss_file.c \
  crypto/bio/bss_log.c \
  crypto/bio/bss_mem.c \
  crypto/bio/bss_null.c \
  crypto/bio/bss_sock.c \
  crypto/bn/bn_add.c \
  crypto/bn/bn_asm.c \
  crypto/bn/bn_blind.c \
  crypto/bn/bn_const.c \
  crypto/bn/bn_ctx.c \
  crypto/bn/bn_div.c \
  crypto/bn/bn_err.c \
  crypto/bn/bn_exp.c \
  crypto/bn/bn_exp2.c \
  crypto/bn/bn_gcd.c \
  crypto/bn/bn_gf2m.c \
  crypto/bn/bn_kron.c \
  crypto/bn/bn_lib.c \
  crypto/bn/bn_mod.c \
  crypto/bn/bn_mont.c \
  crypto/bn/bn_mpi.c \
  crypto/bn/bn_mul.c \
  crypto/bn/bn_nist.c \
  crypto/bn/bn_prime.c \
  crypto/bn/bn_print.c \
  crypto/bn/bn_rand.c \
  crypto/bn/bn_recp.c \
  crypto/bn/bn_shift.c \
  crypto/bn/bn_sqr.c \
  crypto/bn/bn_sqrt.c \
  crypto/bn/bn_word.c \
  crypto/buffer/buf_err.c \
  crypto/buffer/buf_str.c \
  crypto/buffer/buffer.c \
  crypto/cmac/cm_ameth.c \
  crypto/cmac/cm_pmeth.c \
  crypto/cmac/cmac.c \
  crypto/cms/cms_asn1.c \
  crypto/cms/cms_att.c \
  crypto/cms/cms_cd.c \
  crypto/cms/cms_dd.c \
  crypto/cms/cms_enc.c \
  crypto/cms/cms_env.c \
  crypto/cms/cms_err.c \
  crypto/cms/cms_ess.c \
  crypto/cms/cms_io.c \
  crypto/cms/cms_lib.c \
  crypto/cms/cms_pwri.c \
  crypto/cms/cms_sd.c \
  crypto/cms/cms_smime.c \
  crypto/comp/c_rle.c \
  crypto/comp/c_zlib.c \
  crypto/comp/comp_err.c \
  crypto/comp/comp_lib.c \
  crypto/conf/conf_api.c \
  crypto/conf/conf_def.c \
  crypto/conf/conf_err.c \
  crypto/conf/conf_lib.c \
  crypto/conf/conf_mall.c \
  crypto/conf/conf_mod.c \
  crypto/conf/conf_sap.c \
  crypto/cpt_err.c \
  crypto/cryptlib.c \
  crypto/cversion.c \
  crypto/des/cbc_cksm.c \
  crypto/des/cbc_enc.c \
  crypto/des/cfb64ede.c \
  crypto/des/cfb64enc.c \
  crypto/des/cfb_enc.c \
  crypto/des/des_enc.c \
  crypto/des/des_old.c \
  crypto/des/des_old2.c \
  crypto/des/ecb3_enc.c \
  crypto/des/ecb_enc.c \
  crypto/des/ede_cbcm_enc.c \
  crypto/des/enc_read.c \
  crypto/des/enc_writ.c \
  crypto/des/fcrypt.c \
  crypto/des/fcrypt_b.c \
  crypto/des/ofb64ede.c \
  crypto/des/ofb64enc.c \
  crypto/des/ofb_enc.c \
  crypto/des/pcbc_enc.c \
  crypto/des/qud_cksm.c \
  crypto/des/rand_key.c \
  crypto/des/read2pwd.c \
  crypto/des/rpc_enc.c \
  crypto/des/set_key.c \
  crypto/des/str2key.c \
  crypto/des/xcbc_enc.c \
  crypto/dh/dh_ameth.c \
  crypto/dh/dh_asn1.c \
  crypto/dh/dh_check.c \
  crypto/dh/dh_depr.c \
  crypto/dh/dh_err.c \
  crypto/dh/dh_gen.c \
  crypto/dh/dh_key.c \
  crypto/dh/dh_lib.c \
  crypto/dh/dh_pmeth.c \
  crypto/dsa/dsa_ameth.c \
  crypto/dsa/dsa_asn1.c \
  crypto/dsa/dsa_depr.c \
  crypto/dsa/dsa_err.c \
  crypto/dsa/dsa_gen.c \
  crypto/dsa/dsa_key.c \
  crypto/dsa/dsa_lib.c \
  crypto/dsa/dsa_ossl.c \
  crypto/dsa/dsa_pmeth.c \
  crypto/dsa/dsa_prn.c \
  crypto/dsa/dsa_sign.c \
  crypto/dsa/dsa_vrf.c \
  crypto/dso/dso_dl.c \
  crypto/dso/dso_dlfcn.c \
  crypto/dso/dso_err.c \
  crypto/dso/dso_lib.c \
  crypto/dso/dso_null.c \
  crypto/dso/dso_openssl.c \
  crypto/ebcdic.c \
  crypto/ec/ec2_mult.c \
  crypto/ec/ec2_oct.c \
  crypto/ec/ec2_smpl.c \
  crypto/ec/ec_ameth.c \
  crypto/ec/ec_asn1.c \
  crypto/ec/ec_check.c \
  crypto/ec/ec_curve.c \
  crypto/ec/ec_cvt.c \
  crypto/ec/ec_err.c \
  crypto/ec/ec_key.c \
  crypto/ec/ec_lib.c \
  crypto/ec/ec_mult.c \
  crypto/ec/ec_oct.c \
  crypto/ec/ec_pmeth.c \
  crypto/ec/ec_print.c \
  crypto/ec/eck_prn.c \
  crypto/ec/ecp_mont.c \
  crypto/ec/ecp_nist.c \
  crypto/ec/ecp_oct.c \
  crypto/ec/ecp_smpl.c \
  crypto/ecdh/ech_err.c \
  crypto/ecdh/ech_key.c \
  crypto/ecdh/ech_lib.c \
  crypto/ecdh/ech_ossl.c \
  crypto/ecdsa/ecs_asn1.c \
  crypto/ecdsa/ecs_err.c \
  crypto/ecdsa/ecs_lib.c \
  crypto/ecdsa/ecs_ossl.c \
  crypto/ecdsa/ecs_sign.c \
  crypto/ecdsa/ecs_vrf.c \
  crypto/engine/eng_all.c \
  crypto/engine/eng_cnf.c \
  crypto/engine/eng_ctrl.c \
  crypto/engine/eng_dyn.c \
  crypto/engine/eng_err.c \
  crypto/engine/eng_fat.c \
  crypto/engine/eng_init.c \
  crypto/engine/eng_lib.c \
  crypto/engine/eng_list.c \
  crypto/engine/eng_pkey.c \
  crypto/engine/eng_table.c \
  crypto/engine/tb_asnmth.c \
  crypto/engine/tb_cipher.c \
  crypto/engine/tb_dh.c \
  crypto/engine/tb_digest.c \
  crypto/engine/tb_dsa.c \
  crypto/engine/tb_ecdh.c \
  crypto/engine/tb_ecdsa.c \
  crypto/engine/tb_pkmeth.c \
  crypto/engine/tb_rand.c \
  crypto/engine/tb_rsa.c \
  crypto/engine/tb_store.c \
  crypto/err/err.c \
  crypto/err/err_all.c \
  crypto/err/err_prn.c \
  crypto/ex_data.c \
  crypto/hmac/hm_ameth.c \
  crypto/hmac/hm_pmeth.c \
  crypto/hmac/hmac.c \
  crypto/krb5/krb5_asn.c \
  crypto/lhash/lh_stats.c \
  crypto/lhash/lhash.c \
  crypto/md4/md4_dgst.c \
  crypto/md4/md4_one.c \
  crypto/md5/md5_dgst.c \
  crypto/md5/md5_one.c \
  crypto/mem.c \
  crypto/mem_clr.c \
  crypto/mem_dbg.c \
  crypto/modes/cbc128.c \
  crypto/modes/ccm128.c \
  crypto/modes/cfb128.c \
  crypto/modes/ctr128.c \
  crypto/modes/gcm128.c \
  crypto/modes/ofb128.c \
  crypto/modes/xts128.c \
  crypto/o_dir.c \
  crypto/o_init.c \
  crypto/o_str.c \
  crypto/o_time.c \
  crypto/objects/o_names.c \
  crypto/objects/obj_dat.c \
  crypto/objects/obj_err.c \
  crypto/objects/obj_lib.c \
  crypto/objects/obj_xref.c \
  crypto/ocsp/ocsp_asn.c \
  crypto/ocsp/ocsp_cl.c \
  crypto/ocsp/ocsp_err.c \
  crypto/ocsp/ocsp_ext.c \
  crypto/ocsp/ocsp_ht.c \
  crypto/ocsp/ocsp_lib.c \
  crypto/ocsp/ocsp_prn.c \
  crypto/ocsp/ocsp_srv.c \
  crypto/ocsp/ocsp_vfy.c \
  crypto/pem/pem_all.c \
  crypto/pem/pem_err.c \
  crypto/pem/pem_info.c \
  crypto/pem/pem_lib.c \
  crypto/pem/pem_oth.c \
  crypto/pem/pem_pk8.c \
  crypto/pem/pem_pkey.c \
  crypto/pem/pem_seal.c \
  crypto/pem/pem_sign.c \
  crypto/pem/pem_x509.c \
  crypto/pem/pem_xaux.c \
  crypto/pem/pvkfmt.c \
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
  crypto/pqueue/pqueue.c \
  crypto/rand/md_rand.c \
  crypto/rand/rand_egd.c \
  crypto/rand/rand_err.c \
  crypto/rand/rand_lib.c \
  crypto/rand/rand_unix.c \
  crypto/rand/rand_win.c \
  crypto/rand/randfile.c \
  crypto/rc2/rc2_cbc.c \
  crypto/rc2/rc2_ecb.c \
  crypto/rc2/rc2_skey.c \
  crypto/rc2/rc2cfb64.c \
  crypto/rc2/rc2ofb64.c \
  crypto/rc4/rc4_enc.c \
  crypto/rc4/rc4_skey.c \
  crypto/rc4/rc4_utl.c \
  crypto/ripemd/rmd_dgst.c \
  crypto/ripemd/rmd_one.c \
  crypto/rsa/rsa_ameth.c \
  crypto/rsa/rsa_asn1.c \
  crypto/rsa/rsa_chk.c \
  crypto/rsa/rsa_crpt.c \
  crypto/rsa/rsa_eay.c \
  crypto/rsa/rsa_err.c \
  crypto/rsa/rsa_gen.c \
  crypto/rsa/rsa_lib.c \
  crypto/rsa/rsa_none.c \
  crypto/rsa/rsa_null.c \
  crypto/rsa/rsa_oaep.c \
  crypto/rsa/rsa_pk1.c \
  crypto/rsa/rsa_pmeth.c \
  crypto/rsa/rsa_prn.c \
  crypto/rsa/rsa_pss.c \
  crypto/rsa/rsa_saos.c \
  crypto/rsa/rsa_sign.c \
  crypto/rsa/rsa_ssl.c \
  crypto/rsa/rsa_x931.c \
  crypto/sha/sha1_one.c \
  crypto/sha/sha1dgst.c \
  crypto/sha/sha256.c \
  crypto/sha/sha512.c \
  crypto/sha/sha_dgst.c \
  crypto/srp/srp_lib.c \
  crypto/srp/srp_vfy.c \
  crypto/stack/stack.c \
  crypto/ts/ts_err.c \
  crypto/txt_db/txt_db.c \
  crypto/ui/ui_compat.c \
  crypto/ui/ui_err.c \
  crypto/ui/ui_lib.c \
  crypto/ui/ui_openssl.c \
  crypto/ui/ui_util.c \
  crypto/uid.c \
  crypto/x509v3/pcy_cache.c \
  crypto/x509v3/pcy_data.c \
  crypto/x509v3/pcy_lib.c \
  crypto/x509v3/pcy_map.c \
  crypto/x509v3/pcy_node.c \
  crypto/x509v3/pcy_tree.c \
  crypto/x509v3/v3_akey.c \
  crypto/x509v3/v3_akeya.c \
  crypto/x509v3/v3_alt.c \
  crypto/x509v3/v3_bcons.c \
  crypto/x509v3/v3_bitst.c \
  crypto/x509v3/v3_conf.c \
  crypto/x509v3/v3_cpols.c \
  crypto/x509v3/v3_crld.c \
  crypto/x509v3/v3_enum.c \
  crypto/x509v3/v3_extku.c \
  crypto/x509v3/v3_genn.c \
  crypto/x509v3/v3_ia5.c \
  crypto/x509v3/v3_info.c \
  crypto/x509v3/v3_int.c \
  crypto/x509v3/v3_lib.c \
  crypto/x509v3/v3_ncons.c \
  crypto/x509v3/v3_ocsp.c \
  crypto/x509v3/v3_pci.c \
  crypto/x509v3/v3_pcia.c \
  crypto/x509v3/v3_pcons.c \
  crypto/x509v3/v3_pku.c \
  crypto/x509v3/v3_pmaps.c \
  crypto/x509v3/v3_prn.c \
  crypto/x509v3/v3_purp.c \
  crypto/x509v3/v3_skey.c \
  crypto/x509v3/v3_sxnet.c \
  crypto/x509v3/v3_utl.c \
  crypto/x509v3/v3err.c \

common_c_includes := \
  jni/openssl-1.0.1g/. \
  jni/openssl-1.0.1g/crypto \
  jni/openssl-1.0.1g/crypto/asn1 \
  jni/openssl-1.0.1g/crypto/evp \
  jni/openssl-1.0.1g/crypto/modes \
  jni/openssl-1.0.1g/include \
  jni/openssl-1.0.1g/include/openssl \

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
  crypto/armcap.c \
  crypto/armv4cpuid.S \
  crypto/bn/asm/armv4-gf2m.S \
  crypto/bn/asm/armv4-mont.S \
  crypto/modes/asm/ghash-armv4.S \
  crypto/sha/asm/sha1-armv4-large.S \
  crypto/sha/asm/sha256-armv4.S \
  crypto/sha/asm/sha512-armv4.S \

arm_exclude_files := \
  crypto/mem_clr.c \

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
  crypto/bf/asm/bf-586.S \
  crypto/bn/asm/bn-586.S \
  crypto/bn/asm/co-586.S \
  crypto/bn/asm/x86-gf2m.S \
  crypto/bn/asm/x86-mont.S \
  crypto/des/asm/crypt586.S \
  crypto/des/asm/des-586.S \
  crypto/md5/asm/md5-586.S \
  crypto/modes/asm/ghash-x86.S \
  crypto/sha/asm/sha1-586.S \
  crypto/sha/asm/sha256-586.S \
  crypto/sha/asm/sha512-586.S \
  crypto/x86cpuid.S \

x86_exclude_files := \
  crypto/bf/bf_enc.c \
  crypto/bn/bn_asm.c \
  crypto/des/des_enc.c \
  crypto/des/fcrypt_b.c \
  crypto/mem_clr.c \

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
  crypto/bn/asm/modexp512-x86_64.S \
  crypto/bn/asm/x86_64-gcc.c \
  crypto/bn/asm/x86_64-gf2m.S \
  crypto/bn/asm/x86_64-mont.S \
  crypto/bn/asm/x86_64-mont5.S \
  crypto/md5/asm/md5-x86_64.S \
  crypto/modes/asm/ghash-x86_64.S \
  crypto/rc4/asm/rc4-md5-x86_64.S \
  crypto/rc4/asm/rc4-x86_64.S \
  crypto/sha/asm/sha1-x86_64.S \
  crypto/sha/asm/sha256-x86_64.S \
  crypto/sha/asm/sha512-x86_64.S \
  crypto/x86_64cpuid.S \

x86_64_exclude_files := \
  crypto/bn/bn_asm.c \
  crypto/mem_clr.c \
  crypto/rc4/rc4_enc.c \
  crypto/rc4/rc4_skey.c \

mips_cflags := \
  -DAES_ASM \
  -DOPENSSL_BN_ASM_MONT \
  -DSHA1_ASM \
  -DSHA256_ASM \

mips_src_files := \
  crypto/bn/asm/bn-mips.S \
  crypto/bn/asm/mips-mont.S \
  crypto/sha/asm/sha1-mips.S \
  crypto/sha/asm/sha256-mips.S \

mips_exclude_files := \
  crypto/bn/bn_asm.c \


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

LOCAL_MODULE:= libcrypto_part__main

# Replace cflags with static-specific cflags so we dont build in libdl deps
LOCAL_CFLAGS_32 := $(openssl_cflags_static_32)
LOCAL_CFLAGS_64 := $(openssl_cflags_static_64)

LOCAL_SRC_FILES += $(LOCAL_SRC_FILES_$(TARGET_ARCH))
LOCAL_CFLAGS += $(LOCAL_CFLAGS_$(TARGET_ARCH)) $(LOCAL_CFLAGS_32)

include $(BUILD_STATIC_LIBRARY)
