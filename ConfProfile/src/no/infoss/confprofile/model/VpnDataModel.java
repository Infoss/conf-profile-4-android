package no.infoss.confprofile.model;

import no.infoss.confprofile.R;
import no.infoss.confprofile.model.common.CompositeListItemModel;
import no.infoss.confprofile.model.common.ImageViewModel;
import no.infoss.confprofile.profile.data.VpnData;
import android.view.View;

public class VpnDataModel extends CompositeListItemModel<VpnData> {
	
	private ImageViewModel mCheckModel;
	private ImageViewModel mArrowModel;
	private boolean mChecked;
	private boolean mEditable;

	public VpnDataModel() {
		mCheckModel = new ImageViewModel(android.R.id.icon1);
		mCheckModel.setImageResourceId(R.drawable.check);
		addMapping(mCheckModel);
		mChecked = false;
		
		mArrowModel = new ImageViewModel(android.R.id.icon2);
		mArrowModel.setImageResourceId(R.drawable.arrow);
		addMapping(mArrowModel);
		
		mEditable = false;
	}
	
	public VpnDataModel(VpnData data) {
		this();
		setData(data);
	}
	
	public boolean isChecked() {
		return mChecked;
	}
	
	public void setChecked(boolean checked) {
		mChecked = checked;
		applyModel();
	}
	
	public boolean isEditable() {
		return mEditable;
	}
	
	public void setEditable(boolean editable) {
		mEditable = editable;
		applyModel();
	}
	
	public void setArrowOnClickListener(OnClickListener listener) {
		mArrowModel.setOnClickListener(listener);
	}
	
	@Override
	protected void doApplyModel(VpnData data, View view) {
		if(mChecked) {
			mCheckModel.setVisible(View.VISIBLE);
		} else {
			mCheckModel.setVisible(View.INVISIBLE);
		}
		
		if(mEditable) {
			mArrowModel.setVisible(View.VISIBLE);
		} else {
			mArrowModel.setVisible(View.INVISIBLE);
		}
		
		super.doApplyModel(data, view);
		
		if(data != null) {
			setTextToView(view, getMainTextViewId(), data.getUserDefinedName());
			setTextToView(view, getSubTextViewId(), "");	
		}
	}
}
