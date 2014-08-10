package no.infoss.confprofile.profile.data;

public class ListItem {
	private String mMainText;
	private String mSubText;
	
	public ListItem() {
		
	}
	
	public ListItem(String mainText, String subText) {
		this();
		
		setMainText(mainText);
		setSubText(subText);
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
}
