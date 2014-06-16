#ifndef STRONGSWAN_H_
#define STRONGSWAN_H_

#include "android_jni.h"
#include "router.h"

#include "kernel/network_manager.h"

#include <library.h>
#include <collections/linked_list.h>

typedef enum android_vpn_state_t android_vpn_state_t;
typedef enum android_imc_state_t android_imc_state_t;
typedef struct charonservice_t charonservice_t;

/**
 * VPN status codes. As defined in CharonVpnService.java
 */
enum android_vpn_state_t {
	CHARONSERVICE_CHILD_STATE_UP = 1,
	CHARONSERVICE_CHILD_STATE_DOWN,
	CHARONSERVICE_AUTH_ERROR,
	CHARONSERVICE_PEER_AUTH_ERROR,
	CHARONSERVICE_LOOKUP_ERROR,
	CHARONSERVICE_UNREACHABLE_ERROR,
	CHARONSERVICE_GENERIC_ERROR,
};

/**
 * Final IMC state as defined in ImcState.java
 */
enum android_imc_state_t {
	ANDROID_IMC_STATE_UNKNOWN = 0,
	ANDROID_IMC_STATE_ALLOW = 1,
	ANDROID_IMC_STATE_BLOCK = 2,
	ANDROID_IMC_STATE_ISOLATE = 3,
};

/**
 * Public interface of charonservice.
 *
 * Used to communicate with CharonVpnService via JNI
 */
struct charonservice_t {

	/**
	 * Update the status in the Java domain (UI)
	 *
	 * @param code			status code
	 * @return				TRUE on success
	 */
	bool (*update_status)(charonservice_t *this, android_vpn_state_t code);

	/**
	 * Update final IMC state in the Java domain (UI)
	 *
	 * @param state			IMC state
	 * @return				TRUE on success
	 */
	bool (*update_imc_state)(charonservice_t *this, android_imc_state_t state);

	/**
	 * Add a remediation instruction via JNI
	 *
	 * @param instr			remediation instruction
	 * @return				TRUE on success
	 */
	bool (*add_remediation_instr)(charonservice_t *this, char *instr);

	/**
	 * Install a bypass policy for the given socket using the protect() Method
	 * of the Android VpnService interface.
	 *
	 * Use -1 as fd to re-bypass previously bypassed sockets.
	 *
	 * @param fd			socket file descriptor
	 * @param family		socket protocol family
	 * @return				TRUE if operation successful
	 */
	bool (*bypass_socket)(charonservice_t *this, int fd, int family);

	/**
	 * Get a list of trusted certificates via JNI
	 *
	 * @return				list of DER encoded certificates (as chunk_t*),
	 *						NULL on failure
	 */
	linked_list_t *(*get_trusted_certificates)(charonservice_t *this);

	/**
	 * Get the configured user certificate chain via JNI
	 *
	 * The first item in the returned list is the  user certificate followed
	 * by any remaining elements of the certificate chain.
	 *
	 * @return				list of DER encoded certificates (as chunk_t*),
	 *						NULL on failure
	 */
	linked_list_t *(*get_user_certificate)(charonservice_t *this);

	/**
	 * Get the configured private key via JNI
	 *
	 * @param pubkey		the public key as extracted from the certificate
	 * @return				PrivateKey object, NULL on failure
	 */
	private_key_t *(*get_user_key)(charonservice_t *this, public_key_t *pubkey);

	/**
	 * Get the current network_manager_t object
	 *
	 * @return				NetworkManager instance
	 */
	network_manager_t *(*get_network_manager)(charonservice_t *this);

	int (*get_fd)(charonservice_t *this);

	bool (*add_address)(charonservice_t *this, host_t* vip);

	bool (*add_route)(charonservice_t *this, host_t *net, u_int8_t prefix);

	char* (*get_xauth_identity)(charonservice_t *this);

	char* (*get_xauth_key)(charonservice_t *this);
};

typedef struct ipsec_tun_ctx_t ipsec_tun_ctx_t;

struct ipsec_tun_ctx_t {
	common_tun_ctx_t common;
};

/**
 * The single instance of charonservice_t.
 *
 * Set between JNI calls to initializeCharon() and deinitializeCharon().
 */
extern charonservice_t *charonservice;

bool initialize_library(JNIEnv *env, jobject this, char *logfile, bool byod);
void deinitialize_library();
void initialize_tunnel(char *type, char *gateway, char *username, char *password);
void notify_library(bool disconnected);

#endif /** STRONGSWAN_H_ @}*/
