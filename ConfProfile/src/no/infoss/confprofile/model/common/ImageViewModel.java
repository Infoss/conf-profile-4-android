package no.infoss.confprofile.model.common;

import android.view.View;

public class ImageViewModel extends SimpleModel<Void> {
	private int mImageResourceId;
	
	public ImageViewModel() {
		super();
	}
	
	public ImageViewModel(int viewId) {
		this();
		
		setRootViewId(viewId);
	}
	
	public int getImageResourceId() {
		return mImageResourceId;
	}

	public void setImageResourceId(int imageResourceId) {
		this.mImageResourceId = imageResourceId;
	}
	
	@Override
	protected void doApplyModel(Void data, View view) {
		super.doApplyModel(data, view);
		
		setImageToView(getBoundView(), getRootViewId(), mImageResourceId);
	}
	
}
