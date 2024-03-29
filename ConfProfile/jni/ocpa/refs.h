/*
 * refs.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef REFS_H_
#define REFS_H_

#include <stdbool.h>
#include <library.h>
#include <signal.h>

#include "android_log_utils.h"
#include "debug.h"

#define refs_link(instance) (ref_get(&instance->ref) > 0 ? instance : NULL)
#define refs_unlink(instance) (ref_put(&instance->ref) ? instance : NULL)

#define REFS_DECLARE_METHODS(type) \
	type* (*ref_get)(type*); \
	type* (*ref_put)(type*);

#define REFS_DECLARE_METHODS_BODIES(type, exttype) \
	static type* ref_get_##type(type* instance) {\
		refcount_t res = ref_get(&((exttype*)instance)->__ref_count);\
		refcount_t gen = ref_get(&((exttype*)instance)->__ref_generation);\
		LOGDIF(REFS_DEBUG, "refs", "Get a " #type " at %p (#%d), gen=0x%x (#%d)", instance, res, gen, gen);\
		return (res > 0 ? instance : NULL);\
	}\
	\
	static type* ref_put_##type(type* instance) {\
		if(!ref_put(&((exttype*)instance)->__ref_count)) {\
			refcount_t gen = ref_get(&((exttype*)instance)->__ref_generation);\
			LOGDIF(REFS_DEBUG, "refs", "Put a " #type " at %p, gen=0x%x (#%d)", instance, gen, gen);\
			return instance;\
		}\
		refcount_t gen = ref_get(&((exttype*)instance)->__ref_generation);\
		LOGDIF(REFS_DEBUG, "refs", "Put last " #type " at %p, gen=0x%x (#%d)", instance, gen, gen);\
		instance->destroy(instance);\
		return NULL;\
	}

#define REFS_INIT(type, instance) \
	instance->__ref_count = 0;\
	instance->__ref_generation = 0;\
	instance->public.ref_get = ref_get_##type;\
	instance->public.ref_put = ref_put_##type;

#endif /* REFS_H_ */
