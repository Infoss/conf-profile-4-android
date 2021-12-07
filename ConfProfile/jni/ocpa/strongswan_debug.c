/*
 * strongswan_debug.c
 *
 *      Author: Dmitry Vorobiev
 */

#include "strongswan_debug.h"
#include "android_log_utils.h"

typedef struct {
	mem_cred_t public_if;

	void *lock;
	linked_list_t *trusted;
	linked_list_t *untrusted;
	linked_list_t *keys;
	linked_list_t *shared;
	linked_list_t *cdps;
} __printer_mem_cred_t;

typedef struct {
	shared_key_t public_if;
	enum shared_key_type_t type;
	chunk_t key;
	refcount_t ref;
} __printer_shared_key_t;

typedef struct {
	identification_t public_if;
	chunk_t encoded;
	enum id_type_t type;
} __printer_identification_t;

typedef struct {
	/* shared key */
	shared_key_t *shared;
	/* list of owners, identification_t */
	linked_list_t *owners;
} __printer_shared_entry_t;

inline void __print_linked_list_t(void* data, const char* name, __printer_func_t print_child) {
	if(!data) {
		LOGD("__printer", "%s (linked_list_t) -> NULL", name);
		return;
	}
	linked_list_t* list = (linked_list_t*) data;

	LOGD("__printer", "begin %s (linked_list_t) -> %p (%d item(s))", name, list, list->get_count(list));
	enumerator_t* e = list->create_enumerator(list);
	void* child_data = NULL;
	if(e) {
		char buff[10];
		int i = 0;
		while(e->enumerate(e, &child_data)) {
			snprintf(buff, 10, "[%d]\0", i);
			if(print_child) {
				print_child(child_data, buff, NULL);
			} else {
				LOGD("__printer", "[%d] -> %p", i, child_data);
			}
			i++;
		}
		e->destroy(e);
	}
	LOGD("__printer", "end linked_list_t -> %p", list);
}

inline void __print_shared_key_t(void* data, const char* name, __printer_func_t print_child) {
	if(!data) {
		LOGD("__printer", "%s (shared_key_t) -> NULL", name);
		return;
	}
	__printer_shared_key_t* key = (__printer_shared_key_t*) data;

	LOGD("__printer", "begin %s (shared_key_t) -> %p", name, key);
	LOGD("__printer", "get_type() -> %p", key->public_if.get_type);
	LOGD("__printer", "get_key() -> %p", key->public_if.get_key);
	LOGD("__printer", "get_ref() -> %p", key->public_if.get_ref);
	LOGD("__printer", "destroy() -> %p", key->public_if.destroy);
	LOGD("__printer", "type = %d", key->type);
	LOGD("__printer", "key = [ptr = %p, len = %d]", key->key.ptr, key->key.len);
	LOGD("__printer", "ref = %d", key->ref);
	LOGD("__printer", "end %s (shared_key_t) -> %p", name, key);
}

inline void __print_identification_t(void* data, const char* name, __printer_func_t print_child) {
	if(!data) {
		LOGD("__printer", "%s (identification_t) -> NULL", name);
		return;
	}
	__printer_identification_t* id = (__printer_identification_t*) data;

	LOGD("__printer", "begin %s (identification_t) -> %p", name, id);
	LOGD("__printer", "get_encoding() -> %p", id->public_if.get_encoding);
	LOGD("__printer", "get_type() -> %p", id->public_if.get_type);
	LOGD("__printer", "equals() -> %p", id->public_if.equals);
	LOGD("__printer", "matches() -> %p", id->public_if.matches);
	LOGD("__printer", "contains_wildcards() -> %p", id->public_if.contains_wildcards);
	LOGD("__printer", "create_part_enumerator() -> %p", id->public_if.create_part_enumerator);
	LOGD("__printer", "clone() -> %p", id->public_if.clone);
	LOGD("__printer", "destroy() -> %p", id->public_if.destroy);


	LOGD("__printer", "encoded = [ptr = %p, len = %d]", id->encoded.ptr, id->encoded.len);
	LOGD("__printer", "type = %d", id->type);

	LOGD("__printer", "end %s (identification_t) -> %p", name, id);
}

inline void __print_shared_entry_t(void* data, const char* name, __printer_func_t print_child) {
	if(!data) {
		LOGD("__printer", "%s (shared_entry_t) -> NULL", name);
		return;
	}
	__printer_shared_entry_t* entry = (__printer_shared_entry_t*) data;

	LOGD("__printer", "begin %s (shared_entry_t) -> %p", name, entry);
	__print_shared_key_t(entry->shared, "shared", NULL);
	__print_linked_list_t(entry->owners, "owners", __print_identification_t);
	LOGD("__printer", "end %s (shared_entry_t) -> %p", name, entry);
}

inline void __print_mem_cred_t(void* data, const char* name, __printer_func_t print_child) {
	if(!data) {
		LOGD("__printer", "%s (mem_cred_t) -> NULL", name);
		return;
	}
	__printer_mem_cred_t* mem_cred = (__printer_mem_cred_t*) data;

	LOGD("__printer", "begin %s (mem_cred_t) -> %p", name, mem_cred);
	LOGD("__printer", "set [SKIPPED]");
	LOGD("__printer", "add_cert() -> %p", mem_cred->public_if.add_cert);
	LOGD("__printer", "add_cert_ref() -> %p", mem_cred->public_if.add_cert_ref);
	LOGD("__printer", "add_crl() -> %p", mem_cred->public_if.add_crl);
	LOGD("__printer", "add_key() -> %p", mem_cred->public_if.add_key);
	LOGD("__printer", "add_shared() -> %p", mem_cred->public_if.add_shared);
	LOGD("__printer", "add_shared_list() -> %p", mem_cred->public_if.add_shared_list);
	LOGD("__printer", "add_cdp() -> %p", mem_cred->public_if.add_cdp);
	LOGD("__printer", "replace_secrets() -> %p", mem_cred->public_if.replace_secrets);
	LOGD("__printer", "clear_secrets() -> %p", mem_cred->public_if.clear_secrets);
	LOGD("__printer", "destroy() -> %p", mem_cred->public_if.destroy);

	LOGD("__printer", "lock -> %p", mem_cred->lock);
	__print_linked_list_t(mem_cred->trusted, "trusted", NULL);
	__print_linked_list_t(mem_cred->untrusted, "untrusted", NULL);
	__print_linked_list_t(mem_cred->keys, "keys", NULL);
	__print_linked_list_t(mem_cred->shared, "shared", __print_shared_entry_t);
	__print_linked_list_t(mem_cred->cdps, "cdps", NULL);

	LOGD("__printer", "end %s (mem_cred_t) -> %p", name, mem_cred);
}


