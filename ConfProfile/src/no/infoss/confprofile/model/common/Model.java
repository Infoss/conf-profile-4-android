package no.infoss.confprofile.model.common;

import android.view.View;

public interface Model {
	
	/**
	 * Bind model to a provided view (useful when views are reusable)
	 * @param view
	 */
	public void bind(View view);
	public void unbind();
	public View getBoundView();
	
	/**
	 * Apply model data to a bound view
	 */
	public void applyModel();
	
	public boolean isEnabled();
	public void setEnabled(boolean enabled);
	
	public int getVisible();
	public void setVisible(int visible);
	
	public int getLayoutId();

	/**
	 * Set layout id which is used when new view should be instantiated.
	 * @param layoutId
	 */
	public void setLayoutId(int layoutId);

	public int getRootViewId();

	/**
	 * Set root view id as hint for checking is provided view can be converted into current item view. 
	 * @param rootViewId
	 */
	public void setRootViewId(int rootViewId);
	
	public void setOnClickListener(OnClickListener listener);
	
	public interface OnClickListener {
		void onClick(Model model, View v);
	}
}
