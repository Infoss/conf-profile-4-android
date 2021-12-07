/*
 * This file is part of Profile provisioning for Android
 * Copyright (C) 2014  Infoss AS, https://infoss.no, info@infoss.no
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package no.infoss.confprofile.format;

public class ConfigurationProfileException extends PlistFormatException {
	private static final long serialVersionUID = 8096677355337496599L;
	
	private String mField = null;
	private String mRequiredValue = null;

	public ConfigurationProfileException(String message) {
		super(message);
	}
	
	public ConfigurationProfileException(String message, Throwable e) {
		super(message, null, e);
	}
	
	/**
	 * Create exception object with "field is missing" report
	 * @param message
	 * @param field
	 */
	public ConfigurationProfileException(String message, String field) {
		super(makeMissingFieldCause(message, field));
		mField = field;
	}
	
	/**
	 * Create exception object with "invalid field value" report
	 * @param message
	 * @param field
	 * @param requiredValue
	 */
	public ConfigurationProfileException(String message, String field, String requiredValue) {
		super(makeInvalidFieldFormatCause(message, field, requiredValue));
		mField = field;
		mRequiredValue = requiredValue;
	}
	
	public String getField() {
		return mField;
	}
	
	public String getRequiredValue() {
		 return mRequiredValue;
	}
	
	private static String makeMissingFieldCause(String message, String field) {
		return String.format("%s (missing field %s)", message, field);
	}
	
	private static String makeInvalidFieldFormatCause(String message, String field, String required) {
		return String.format("%s (field %s has invalid format or value, should be %s)", 
				message, field, required);
	}

}
