/*
 * strongswan_debug.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef OCPA_STRONGSWAN_DEBUG_H_
#define OCPA_STRONGSWAN_DEBUG_H_

#include <collections/linked_list.h>
#include <credentials/credential_set.h>
#include <credentials/sets/mem_cred.h>

typedef void(*__printer_func_t)(void*, const char*, void*);

inline void __print_linked_list_t(void* data, const char* name, __printer_func_t print_child);
inline void __print_shared_key_t(void* data, const char* name, __printer_func_t print_child);
inline void __print_identification_t(void* data, const char* name, __printer_func_t print_child);
inline void __print_shared_entry_t(void* data, const char* name, __printer_func_t print_child);
inline void __print_mem_cred_t(void* data, const char* name, __printer_func_t print_child);


#endif /* OCPA_STRONGSWAN_DEBUG_H_ */
