package no.infoss.confprofile.profile.data;

import no.infoss.confprofile.model.ListItemModel;

public class VpnData extends ListItem {
	private String mPayloadUuid;
	private String mUserDefinedName;
	private boolean mOverridePrimary;
	private boolean mOnDemandEnabled;
	private boolean mOnDemandEnabledByUser;
	private String mOnDemandRules;
	private String mOnDemandCredentials;
	
	public String getPayloadUuid() {
		return mPayloadUuid;
	}
	
	public void setPayloadUuid(String payloadUuid) {
		this.mPayloadUuid = payloadUuid;
	}
	
	public String getUserDefinedName() {
		return mUserDefinedName;
	}
	
	public void setUserDefinedName(String userDefinedName) {
		this.mUserDefinedName = userDefinedName;
		applyData();
	}
	
	public boolean isOverridePrimary() {
		return mOverridePrimary;
	}
	
	public void setOverridePrimary(boolean overridePrimary) {
		this.mOverridePrimary = overridePrimary;
	}
	
	public boolean isOnDemandEnabled() {
		return mOnDemandEnabled;
	}
	
	public void setOnDemandEnabled(boolean onDemandEnabled) {
		this.mOnDemandEnabled = onDemandEnabled;
	}
	
	public boolean isOnDemandEnabledByUser() {
		return mOnDemandEnabledByUser;
	}
	
	public void setOnDemandEnabledByUser(boolean onDemandEnabledByUser) {
		this.mOnDemandEnabledByUser = onDemandEnabledByUser;
	}
	
	public String getOnDemandRules() {
		return mOnDemandRules;
	}

	public void setOnDemandRules(String onDemandRules) {
		this.mOnDemandRules = onDemandRules;
	}

	public String getOnDemandCredentials() {
		return mOnDemandCredentials;
	}

	public void setOnDemandCredentials(String onDemandCredentials) {
		this.mOnDemandCredentials = onDemandCredentials;
	}

	@Override
	public void applyData() {
		ListItemModel model = getModel();
		if(model != null) {
			model.setMainText(mUserDefinedName);
			model.applyModel();
		}
	}
}
