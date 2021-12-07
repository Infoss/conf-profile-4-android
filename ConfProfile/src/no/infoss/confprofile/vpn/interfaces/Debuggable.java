package no.infoss.confprofile.vpn.interfaces;

import no.infoss.confprofile.util.PcapOutputStream;

public interface Debuggable {
	public String generatePcapFilename(); 
	public boolean debugRestartPcap(PcapOutputStream pos);
	public boolean debugStopPcap();
}
