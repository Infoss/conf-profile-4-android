package no.infoss.confprofile.model.common;

import android.view.View;
import android.widget.AdapterView;

public class SimpleListItemModel<T> extends SimpleModel<T> implements ListItemModel<T>, AdapterView.OnItemClickListener {
	private String mMainText;
	private int mMainTextId;
	private String mSubText;
	private int mSubTextId;
	private int mMainTextViewId;
	private int mSubTextViewId;
	private boolean mPreferOnClickListener;
	private boolean mDeliverItemClickAsClick;
	private OnItemClickListener mOnItemClickListener;
	
	private boolean mStaticMainTextMode;
	private boolean mStaticSubTextMode;
	
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
		this.mMainTextId = 0;
	}
	
	@Override
	public void setMainText(int mainText) {
		this.mMainText = null;
		this.mMainTextId = mainText;
	}
	
	@Override
	public String getSubText() {
		return mSubText;
	}
	
	@Override
	public void setSubText(String subText) {
		this.mSubText = subText;
		this.mSubTextId = 0;
	}
	
	@Override
	public void setSubText(int subText) {
		this.mSubText = null;
		this.mSubTextId = subText;
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
		if(!isEnabled()) {
			return;
		}
		
		if(mDeliverItemClickAsClick) {
			super.onClick(view);
			return;
		}
		
		if(view == getBoundView() && mOnItemClickListener != null) {
			mOnItemClickListener.onItemClick(this, parent, view, position, id);
		}
		
	}
	
	protected boolean isStaticMainTextMode() {
		return mStaticMainTextMode;
	}
	
	protected void setStaticMainTextMode(boolean mode) {
		mStaticMainTextMode = true;
	}
	
	protected boolean isStaticSubTextMode() {
		return mStaticSubTextMode;
	}
	
	protected void setStaticSubTextMode(boolean mode) {
		mStaticSubTextMode = mode;
	}
	
	@Override
	protected void doBind(View view) {
		super.doBind(view);
		if(!mPreferOnClickListener) {
			view.setOnClickListener(null);
			view.setClickable(false);
		}
	}
	
	@Override
	protected void doUnbind(View view) {
		super.doUnbind(view);
	}

	@Override
	protected void doApplyModel(T data, View view) {
		super.doApplyModel(data, view);
		
		if(mStaticMainTextMode || data == null) {
			if(mMainText == null && mMainTextId != 0) {
				setTextToView(view, mMainTextViewId, mMainTextId);
			} else {
				setTextToView(view, mMainTextViewId, mMainText);
			}
		}
		
		if(mStaticSubTextMode || data == null) {
			if(mSubText == null && mSubTextId != 0) {
				setTextToView(view, mSubTextViewId, mSubTextId);
			} else {
				setTextToView(view, mSubTextViewId, mSubText);
			}
		}
	}

}
