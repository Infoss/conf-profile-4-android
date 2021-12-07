/*
 * Copyright (C) 2012-2013 Tobias Brunner
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

package org.strongswan.android.data;

public enum VpnType
{
	/* the order here must match the items in R.array.vpn_types */
	IKEV2_EAP("ikev2-eap", true, false),
	IKEV2_CERT("ikev2-cert", false, true),
	IKEV2_CERT_EAP("ikev2-cert-eap", true, true),
	IKEV2_BYOD_EAP("ikev2-byod-eap", true, false, true);

	private String mIdentifier;
	private boolean mCertificate;
	private boolean mUsernamePassword;
	private boolean mBYOD;

	/**
	 * Enum which provides additional information about the supported VPN types.
	 *
	 * @param id identifier used to store and transmit this specific type
	 * @param userpass true if username and password are required
	 * @param certificate true if a client certificate is required
	 */
	VpnType(String id, boolean userpass, boolean certificate)
	{
		this(id, userpass, certificate, false);
	}

	/**
	 * Enum which provides additional information about the supported VPN types.
	 *
	 * @param id identifier used to store and transmit this specific type
	 * @param userpass true if username and password are required
	 * @param certificate true if a client certificate is required
	 * @param byod true to enable BYOD features
	 */
	VpnType(String id, boolean userpass, boolean certificate, boolean byod)
	{
		mIdentifier = id;
		mUsernamePassword = userpass;
		mCertificate = certificate;
		mBYOD = byod;
	}

	/**
	 * The identifier used to store this value in the database
	 * @return identifier
	 */
	public String getIdentifier()
	{
		return mIdentifier;
	}

	/**
	 * Whether username and password are required for this type of VPN.
	 *
	 * @return true if username and password are required
	 */
	public boolean getRequiresUsernamePassword()
	{
		return mUsernamePassword;
	}

	/**
	 * Whether a certificate is required for this type of VPN.
	 *
	 * @return true if a certificate is required
	 */
	public boolean getRequiresCertificate()
	{
		return mCertificate;
	}

	/**
	 * Whether BYOD features should be enabled.
	 *
	 * @return true if BYOD features are to be enabled
	 */
	public boolean getEnableBYOD()
	{
		return mBYOD;
	}

	/**
	 * Get the enum entry with the given identifier.
	 *
	 * @param identifier get the enum entry with this identifier
	 * @return the enum entry, or the default if not found
	 */
	public static VpnType fromIdentifier(String identifier)
	{
		for (VpnType type : VpnType.values())
		{
			if (identifier.equals(type.mIdentifier))
			{
				return type;
			}
		}
		return VpnType.IKEV2_EAP;
	}
}
