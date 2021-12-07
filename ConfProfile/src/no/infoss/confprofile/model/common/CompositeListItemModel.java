package no.infoss.confprofile.model.common;

import android.util.SparseArray;
import android.view.View;

public class CompositeListItemModel<T> extends SimpleListItemModel<T> {
	private SparseArray<Model<?>> mMappings;
	
	public CompositeListItemModel() {
		mMappings = new SparseArray<Model<?>>();
	}
	
	public void addMapping(Model<?> model) {
		mMappings.put(model.getRootViewId(), model);
	}
	
	public void addMapping(int viewId, Model<?> model) {
		mMappings.put(viewId, model);
	}
	
	public Model<?> getMapping(int viewId) {
		return mMappings.get(viewId);
	}
	
	public void clearMappings() {
		unbind();
		mMappings.clear();
	}
	
	@Override
	protected void doBind(View view) {
		super.doBind(view);
		
		int count = mMappings.size();
		for(int i = 0; i < count; i++) {
			int viewId = mMappings.keyAt(i);
			Model<?> entry = mMappings.valueAt(i);
			if(entry != null) {
				View subView = view.findViewById(viewId);
				entry.bind(subView);
			}
		}
	}
	
	@Override
	protected void doUnbind(View view) {
		super.doUnbind(view);
		
		int count = mMappings.size();
		for(int i = 0; i < count; i++) {
			Model<?> entry = mMappings.valueAt(i);
			if(entry != null) {
				entry.unbind();
			}
		}
	}
	
	@Override
	protected void doApplyModel(T data, View view) {
		super.doApplyModel(data, view);
		
		int count = mMappings.size();
		for(int i = 0; i < count; i++) {
			Model<?> entry = mMappings.valueAt(i);
			if(entry != null) {
				entry.applyModel();
			}
		}
	}
}
