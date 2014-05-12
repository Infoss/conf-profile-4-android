package no.infoss.confprofile.task;

public interface TaskError {
	public static final int SUCCESS = 0;
	public static final int INTERNAL = 1;
	public static final int NETWORK_FAILED = 2;
	public static final int HTTP_FAILED = 3;
	public static final int INVALID_DATA = 4;
	
	public static final int SCEP_FAILED = 5;
	public static final int SCEP_TIMEOUT = 6;
	public static final int MISSING_SCEP_PAYLOAD = 7;
}
