package no.infoss.confprofile.profile.data;

import no.infoss.confprofile.model.common.ListItemModel;

public class ListItem {
	private final String mMainText;
	private final String mSubText;
	private ListItemModel mModel;
	
	public ListItem() {
		this(null, null);
	}
	
	public ListItem(String mainText, String subText) {
		mMainText = mainText;
		mSubText = subText;
	}

	public ListItemModel getModel() {
		return mModel;
	}

	public void setModel(ListItemModel mModel) {
		this.mModel = mModel;
		applyData();
	}
	
	/**
	 * Override this method to allow automatic pushing data to model after setModel() call. 
	 */
	public void applyData() {
		ListItemModel model = getModel();
		if(model != null) {
			model.setMainText(mMainText);
			model.setSubText(mSubText);
			model.applyModel();
		}
	}

}
