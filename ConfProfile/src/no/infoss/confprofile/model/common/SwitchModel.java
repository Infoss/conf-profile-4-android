package no.infoss.confprofile.model.common;

import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;

public class SwitchModel extends SimpleModel implements CompoundButton.OnCheckedChangeListener {
	private OnCheckedChangeListener mOnCheckedChangeListener;
	private boolean mChecked;
	
	public SwitchModel() {
		super();
		mOnCheckedChangeListener = null;
	}
	
	public SwitchModel(int viewId) {
		this();
		
		setRootViewId(viewId);
	}
	
	public boolean isChecked() {
		return mChecked;
	}

	public void setChecked(boolean checked) {
		this.mChecked = checked;
	}

	public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
		mOnCheckedChangeListener = listener;
	}
	
	@Override
	protected void doBind(View view) {
		super.doBind(view);
		((Switch) view).setOnCheckedChangeListener(this);
	}
	
	@Override
	protected void doUnbind(View view) {
		super.doUnbind(view);
		((Switch) view).setOnCheckedChangeListener(null);	
	}
	
	@Override
	protected void doApplyModel(View view) {
		super.doApplyModel(view);
		setCheckedToView(view, getRootViewId(), mChecked);
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		if(buttonView == getBoundView() && mOnCheckedChangeListener != null) {
			setChecked(isChecked);
			mOnCheckedChangeListener.onCheckedChanged(this, (Switch) buttonView, isChecked);
		}
	}
	
	public interface OnCheckedChangeListener {
		public void onCheckedChanged(SwitchModel model, Switch buttonView, boolean isChecked);
	}
}
