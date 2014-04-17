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
