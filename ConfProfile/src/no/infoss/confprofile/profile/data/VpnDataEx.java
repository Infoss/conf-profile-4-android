package no.infoss.confprofile.profile.data;

public class VpnDataEx extends VpnData {
	private VpnOnDemandConfig[] mOnDemandConfiguration;
	
	public VpnDataEx() {
		super();
	}
	
	public VpnDataEx(VpnData data) {
		super(data);
	}
	
	public VpnDataEx(VpnDataEx data) {
		this((VpnData) data);
		
		//TODO: deep copy here
		mOnDemandConfiguration = data.mOnDemandConfiguration;
	}

	public VpnOnDemandConfig[] getOnDemandConfiguration() {
		return mOnDemandConfiguration;
	}

	public void setOnDemandConfiguration(VpnOnDemandConfig[] onDemandConfiguration) {
		this.mOnDemandConfiguration = onDemandConfiguration;
	}
	
}
