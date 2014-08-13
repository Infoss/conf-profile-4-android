package no.infoss.confprofile.model;

import android.view.View;
import android.widget.AdapterView;

public class SimpleListItemModel extends SimpleModel implements ListItemModel, AdapterView.OnItemClickListener {
	private String mMainText;
	private String mSubText;
	private int mMainTextViewId;
	private int mSubTextViewId;
	private boolean mPreferOnClickListener;
	private boolean mDeliverItemClickAsClick;
	private OnItemClickListener mOnItemClickListener;
	
	public SimpleListItemModel() {
		mMainText = "";
		mSubText = "";
		setMainTextViewId(android.R.id.text1);
		setSubTextViewId(android.R.id.text2);
		
		mPreferOnClickListener = false;
		mDeliverItemClickAsClick = true;
		mOnItemClickListener = null;
	}
	
	public SimpleListItemModel(String mainText, String subText) {
		this();
		
		setMainText(mainText);
		setSubText(subText);
	}
	
	@Override
	public String getMainText() {
		return mMainText;
	}
	
	@Override
	public void setMainText(String mainText) {
		this.mMainText = mainText;
	}
	
	@Override
	public String getSubText() {
		return mSubText;
	}
	
	@Override
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
	public void preferOnClickListener(boolean preferOnClick) {
		mPreferOnClickListener = preferOnClick;
	}
	
	@Override
	public void deliverItemClickAsClick(boolean deliverAsClick) {
		mDeliverItemClickAsClick = deliverAsClick;
	}
	
	@Override
	public void setOnItemClickListener(OnItemClickListener listener) {
		mOnItemClickListener = listener;
	}
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		if(mDeliverItemClickAsClick) {
			super.onClick(view);
			return;
		}
		
		if(view == getBoundView() && mOnItemClickListener != null) {
			mOnItemClickListener.onItemClick(this, parent, view, position, id);
		}
		
	}
	
	@Override
	protected void doBind(View view) {
		super.doBind(view);
		if(!mPreferOnClickListener) {
			view.setOnClickListener(null);
		}
	}
	
	@Override
	protected void doUnbind(View view) {
		super.doUnbind(view);
	}

	@Override
	protected void doApplyModel(View view) {
		super.doApplyModel(view);
		
		setTextToView(view, mMainTextViewId, mMainText);
		setTextToView(view, mSubTextViewId, mSubText);
	}

}
