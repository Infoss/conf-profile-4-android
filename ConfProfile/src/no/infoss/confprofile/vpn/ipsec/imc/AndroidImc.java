/*
 * Copyright (C) 2013 Tobias Brunner
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

package no.infoss.confprofile.vpn.ipsec.imc;

import no.infoss.confprofile.vpn.ipsec.imc.attributes.Attribute;
import no.infoss.confprofile.vpn.ipsec.imc.attributes.AttributeType;
import no.infoss.confprofile.vpn.ipsec.imc.collectors.Collector;
import no.infoss.confprofile.vpn.ipsec.imc.collectors.DeviceIdCollector;
import no.infoss.confprofile.vpn.ipsec.imc.collectors.InstalledPackagesCollector;
import no.infoss.confprofile.vpn.ipsec.imc.collectors.PortFilterCollector;
import no.infoss.confprofile.vpn.ipsec.imc.collectors.ProductInformationCollector;
import no.infoss.confprofile.vpn.ipsec.imc.collectors.SettingsCollector;
import no.infoss.confprofile.vpn.ipsec.imc.collectors.StringVersionCollector;
import android.content.Context;

public class AndroidImc
{
	private final Context mContext;

	public AndroidImc(Context context)
	{
		mContext = context;
	}

	/**
	 * Get a measurement (the binary encoding of the requested attribute) for
	 * the given vendor specific attribute type.
	 *
	 * @param vendor vendor ID
	 * @param type vendor specific attribute type
	 * @return encoded attribute, or null if not available or failed
	 */
	public byte[] getMeasurement(int vendor, int type)
	{
		return getMeasurement(vendor, type, null);
	}

	/**
	 * Get a measurement (the binary encoding of the requested attribute) for
	 * the given vendor specific attribute type.
	 *
	 * @param vendor vendor ID
	 * @param type vendor specific attribute type
	 * @param args optional arguments for a measurement
	 * @return encoded attribute, or null if not available or failed
	 */
	public byte[] getMeasurement(int vendor, int type, String[] args)
	{
		AttributeType attributeType = AttributeType.fromValues(vendor, type);
		Collector collector = null;

		switch (attributeType)
		{
			case IETF_PRODUCT_INFORMATION:
				collector = new ProductInformationCollector();
				break;
			case IETF_STRING_VERSION:
				collector = new StringVersionCollector();
				break;
			case IETF_PORT_FILTER:
				collector = new PortFilterCollector();
				break;
			case IETF_INSTALLED_PACKAGES:
				collector = new InstalledPackagesCollector(mContext);
				break;
			case ITA_SETTINGS:
				collector = new SettingsCollector(mContext, args);
				break;
			case ITA_DEVICE_ID:
				collector = new DeviceIdCollector(mContext);
				break;
			default:
				break;
		}
		if (collector != null)
		{
			Attribute attribute = collector.getMeasurement();
			if (attribute != null)
			{
				return attribute.getEncoding();
			}
		}
		return null;
	}
}
