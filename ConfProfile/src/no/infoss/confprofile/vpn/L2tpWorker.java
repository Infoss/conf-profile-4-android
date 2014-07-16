package no.infoss.confprofile.vpn;

import java.util.List;
import java.util.Map;

public class L2tpWorker extends AbstractWorker {
	public static final String TAG = L2tpWorker.class.getSimpleName();
	public static final String MINIVPN = "ocpamtpd";

	public L2tpWorker(L2tpTunnel tunnel, List<String> args, Map<String, String> env) {
		super(tunnel, args, env);
	}

}
