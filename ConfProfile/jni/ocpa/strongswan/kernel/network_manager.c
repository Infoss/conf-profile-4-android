/*
 * Copyright (C) 2012-2013 Tobias Brunner
 * Hochschule fuer Technik Rapperswil
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.  See <http://www.fsf.org/copyleft/gpl.txt>.  *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 */

#include "network_manager.h"

#include "../strongswan.h"
#include <utils/debug.h>
#include <threading/mutex.h>

typedef struct private_network_manager_t private_network_manager_t;

struct private_network_manager_t {

	/**
	 * Public interface
	 */
	network_manager_t public;

	/**
	 * Registered callback
	 */
	struct {
		connectivity_cb_t cb;
		void *data;
	} connectivity_cb;

	/**
	 * Mutex to access callback
	 */
	mutex_t *mutex;
};

METHOD(network_manager_t, add_connectivity_cb, void,
	private_network_manager_t *this, connectivity_cb_t cb, void *data)
{
	this->mutex->lock(this->mutex);
	if (!this->connectivity_cb.cb)
	{
		this->connectivity_cb.cb = cb;
		this->connectivity_cb.data = data;
	}
	this->mutex->unlock(this->mutex);
}

METHOD(network_manager_t, remove_connectivity_cb, void,
	private_network_manager_t *this, connectivity_cb_t cb)
{
	this->mutex->lock(this->mutex);
	if (this->connectivity_cb.cb == cb)
	{
		this->connectivity_cb.cb = NULL;
	}
	this->mutex->unlock(this->mutex);
}

METHOD(network_manager_t, destroy, void,
	private_network_manager_t *this)
{
	this->mutex->lock(this->mutex);
	if (this->connectivity_cb.cb)
	{
		this->connectivity_cb.cb = NULL;
	}
	this->mutex->unlock(this->mutex);

	this->mutex->destroy(this->mutex);
	free(this);
}

METHOD(network_manager_t, networkChanged, void,
	private_network_manager_t *this, bool disconnected)
{
	this->mutex->lock(this->mutex);
	if (this->connectivity_cb.cb)
	{
		this->connectivity_cb.cb(this->connectivity_cb.data, disconnected);
	}
	this->mutex->unlock(this->mutex);
}

/*
 * Described in header.
 */
network_manager_t *network_manager_create()
{
	private_network_manager_t *this;

	INIT(this,
		.public = {
			.add_connectivity_cb = _add_connectivity_cb,
			.remove_connectivity_cb = _remove_connectivity_cb,
			.destroy = _destroy,
			.networkChanged = _networkChanged,
		},
		.mutex = mutex_create(MUTEX_TYPE_DEFAULT),
	);

	return &this->public;
};
