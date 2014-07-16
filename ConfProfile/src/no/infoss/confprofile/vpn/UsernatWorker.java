package no.infoss.confprofile.vpn;

import java.util.List;
import java.util.Map;

public class UsernatWorker extends AbstractWorker {
	public static final String TAG = UsernatWorker.class.getSimpleName();
	public static final String USERNAT = "usernat";
	public static final String SOCAT = "ocpasocat";

	public UsernatWorker(UsernatTunnel tunnel, List<String> args, Map<String, String> processEnv) {
		super(tunnel, args, processEnv);
	}

}
