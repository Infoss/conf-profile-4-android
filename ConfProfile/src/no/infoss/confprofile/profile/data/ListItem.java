package no.infoss.confprofile.profile.data;

public class ListItem {
	private String mMainText;
	private String mSubText;
	private int mLayoutId;
	private int mRootViewId;
	
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

	public int getLayoutId() {
		return mLayoutId;
	}

	/**
	 * Set layout id which is used when new view should be instantiated.
	 * @param layoutId
	 */
	public void setLayoutId(int layoutId) {
		this.mLayoutId = layoutId;
	}

	public int getRootViewId() {
		return mRootViewId;
	}

	/**
	 * Set root view id as hint for checking is provided view can be converted into current item view. 
	 * @param rootViewId
	 */
	public void setRootViewId(int rootViewId) {
		this.mRootViewId = rootViewId;
	}
}
