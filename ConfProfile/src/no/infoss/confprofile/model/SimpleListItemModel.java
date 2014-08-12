package no.infoss.confprofile.model;

import android.view.View;

public class SimpleListItemModel extends SimpleModel implements ListItemModel {
	private String mMainText;
	private String mSubText;
	private int mMainTextViewId;
	private int mSubTextViewId;
	
	public SimpleListItemModel() {
		mMainText = "";
		mSubText = "";
		setMainTextViewId(android.R.id.text1);
		setSubTextViewId(android.R.id.text2);
	}
	
	public SimpleListItemModel(String mainText, String subText) {
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

	public int getMainTextViewId() {
		return mMainTextViewId;
	}

	public void setMainTextViewId(int mainTextViewId) {
		this.mMainTextViewId = mainTextViewId;
	}

	public int getSubTextViewId() {
		return mSubTextViewId;
	}

	public void setSubTextViewId(int subTextViewId) {
		this.mSubTextViewId = subTextViewId;
	}

	@Override
	protected void doApplyModel(View view) {
		super.doApplyModel(view);
		
		setTextToView(view, mMainTextViewId, mMainText);
		setTextToView(view, mSubTextViewId, mSubText);
	}
}
