package no.infoss.confprofile.profile.data;

import no.infoss.confprofile.model.ListItemModel;

public class VpnData extends ListItem {
	private String mPayloadUuid;
	private String mUserDefinedName;
	private boolean mOverridePrimary;
	private boolean mOnDemandEnabled;
	private boolean mOnDemandEnabledByUser;
	
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
	
	@Override
	public void applyData() {
		ListItemModel model = getModel();
		if(model != null) {
			model.setMainText(mUserDefinedName);
			model.applyModel();
		}
	}
}
