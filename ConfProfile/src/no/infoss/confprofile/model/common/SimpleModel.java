package no.infoss.confprofile.model.common;

import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

public class SimpleModel implements Model, View.OnClickListener {
	private View mBoundView;
	private boolean mEnabled;
	private int mVisible;
	private int mLayoutId;
	private int mRootViewId;
	private OnClickListener mOnClickListener;
	
	public SimpleModel() {
		mBoundView = null;
		mEnabled = true;
		mVisible = View.VISIBLE;
		mLayoutId = 0;
		mRootViewId = 0;
		mOnClickListener = null;
	}
	
	@Override
	public final void bind(View view) {
		if(mBoundView != null & mBoundView != view) {
			doUnbind(mBoundView);
		}
		
		mBoundView = view;
		
		if(view != null) {
			doBind(view);
			applyModel();
		}	
	}
	
	@Override
	public final void unbind() {
		if(mBoundView != null) {
			doUnbind(mBoundView);
		}
		
		mBoundView = null;
	}
	
	@Override
	public View getBoundView() {
		return mBoundView;
	}
	
	@Override
	public final void applyModel() {
		if(mBoundView != null) {
			doApplyModel(mBoundView);
		}
	}
	
	@Override
	public boolean isEnabled() {
		return mEnabled;
	}
	
	@Override
	public void setEnabled(boolean enabled) {
		mEnabled = enabled;
	}
	
	@Override
	public int getVisible() {
		return mVisible;
	}
	
	@Override
	public void setVisible(int visible) {
		mVisible = visible;
	}
	
	@Override
	public int getLayoutId() {
		return mLayoutId;
	}

	@Override
	public void setLayoutId(int layoutId) {
		this.mLayoutId = layoutId;
	}

	@Override
	public int getRootViewId() {
		return mRootViewId;
	}

	@Override
	public void setRootViewId(int rootViewId) {
		this.mRootViewId = rootViewId;
	}
	
	@Override
	public void setOnClickListener(OnClickListener listener) {
		mOnClickListener = listener;
	}

	@Override
	public void onClick(View v) {
		if(v == mBoundView && mOnClickListener != null) {
			mOnClickListener.onClick(this, v);
		}
	}
	
	protected final TextView setTextToView(View rootView, int id, String text) {
		TextView result = null;
		
		View testView = rootView.findViewById(id);
		if(testView != null && testView instanceof TextView) {
			result = (TextView) testView;
			result.setText(text);
		}
		
		return result;
	}
	
	protected final TextView setTextToView(View rootView, int id, int textId) {
		TextView result = null;
		
		View testView = rootView.findViewById(id);
		if(testView != null && testView instanceof TextView) {
			result = (TextView) testView;
			result.setText(textId);
		}
		
		return result;
	}
	
	protected final ImageView setImageToView(View rootView, int id, int resourceId) {
		ImageView result = null;
		
		View testView = rootView.findViewById(id);
		if(testView != null && testView instanceof ImageView) {
			result = (ImageView) testView;
			result.setImageResource(resourceId);
		}
		
		return result;
	}
	
	protected final CompoundButton setCheckedToView(View rootView, int id, boolean checked) {
		CompoundButton result = null;
		
		View testView = rootView.findViewById(id);
		if(testView != null && testView instanceof CompoundButton) {
			result = (CompoundButton) testView;
			result.setChecked(checked);
		}
		
		return result;
	}
	
	protected void doBind(View view) {
		view.setOnClickListener(this);
	}
	
	protected void doUnbind(View view) {
		view.setOnClickListener(null);
		view.setClickable(false);
	}
	
	protected void doApplyModel(View view) {
		view.setEnabled(mEnabled);
		view.setVisibility(mVisible);
	}
}
