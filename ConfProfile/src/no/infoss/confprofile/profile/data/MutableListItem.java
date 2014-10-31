package no.infoss.confprofile.profile.data;

import no.infoss.confprofile.model.common.ListItemModel;

@Deprecated
public class MutableListItem extends ListItem {
	private String mMainText;
	private String mSubText;
	
	public MutableListItem() {
		this(null, null);
	}
	
	public MutableListItem(String mainText, String subText) {
		super(mainText, subText);
		
		mMainText = mainText;
		mSubText = subText;
	}
	
	public String getMainText() {
		return mMainText;
	}

	public void setMainText(String mainText) {
		this.mMainText = mainText;
	}

	public String getSubText() {
		return mSubText;
	}

	public void setSubText(String subText) {
		this.mSubText = subText;
	}

	@Override
	public void applyData() {
		ListItemModel model = getModel();
		if(model != null) {
			model.setMainText(mMainText);
			model.setSubText(mSubText);
			model.applyModel();
		}
	}

}
