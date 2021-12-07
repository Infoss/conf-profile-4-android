/*
 * Copyright (C) 2009 Martin Willi
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

/**
 * @defgroup pgp pgp
 * @ingroup plugins
 *
 * @defgroup pgp_plugin pgp_plugin
 * @{ @ingroup pgp
 */

#ifndef PGP_PLUGIN_H_
#define PGP_PLUGIN_H_

#include <plugins/plugin.h>

typedef struct pgp_plugin_t pgp_plugin_t;

/**
 * Plugin providing PKCS#1 private/public key decoding functions
 */
struct pgp_plugin_t {

	/**
	 * implements plugin interface
	 */
	plugin_t plugin;
};

#endif /** PGP_PLUGIN_H_ @}*/
