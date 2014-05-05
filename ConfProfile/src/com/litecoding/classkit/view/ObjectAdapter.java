/* 
* Copyright 2011 Dmitry S. Vorobiev
*
*   Licensed under the Apache License, Version 2.0 (the "License");
*   you may not use this file except in compliance with the License.
*   You may obtain a copy of the License at
*
*       http://www.apache.org/licenses/LICENSE-2.0
*
*   Unless required by applicable law or agreed to in writing, software
*   distributed under the License is distributed on an "AS IS" BASIS,
*   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*   See the License for the specific language governing permissions and
*   limitations under the License.
*/

package com.litecoding.classkit.view;

import java.util.List;

import android.content.Context;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class ObjectAdapter<T> implements StatefulListAdapter {
	private DataSetObservable observable = new DataSetObservable();
	private AdapterDataSetObserver observer = new AdapterDataSetObserver();
	private LayoutInflater inflater;
	protected List<T> elements;
	private int resourceId;
	private ObjectMapper<T> mapper;
	
	public ObjectAdapter(LayoutInflater inflater, List<T> elements, int resourceId, ObjectMapper<T> mapper) {
		this.inflater = inflater;
		this.elements = elements;
		this.resourceId = resourceId;
		this.mapper = mapper;
		
		if(elements instanceof DataSetObservable) {
			((DataSetObservable) elements).registerObserver(observer);
		}
	}

	@Override
	public boolean areAllItemsEnabled() {
		return true;
	}

	@Override
	public boolean isEnabled(int position) {
		T element = elements.get(position);
		if(element instanceof Enablable)
			return ((Enablable)element).isEnabled();
		
		return true;
	}

	@Override
	public int getCount() {
		return elements.size();
	}

	@Override
	public Object getItem(int position) {		
		return elements.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public int getItemViewType(int position) {
		return 0;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View currentView = convertView;
		if(currentView == null) {
			LayoutInflater infl = null;
			
			if(inflater == null && parent != null) {
				infl = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			} else {
				infl = inflater;
			}
			currentView = infl.inflate(resourceId, parent, false);
		}
		
		mapper.mapData(position, currentView, elements.get(position));
		
		return currentView;
	}

	@Override
	public int getViewTypeCount() {
		return 1;
	}

	@Override
	public boolean hasStableIds() {
		return false;
	}

	@Override
	public boolean isEmpty() {
		return elements.isEmpty();
	}

	@Override
	public void registerDataSetObserver(DataSetObserver observer) {
		observable.registerObserver(observer);
	}

	@Override
	public void unregisterDataSetObserver(DataSetObserver observer) {
		observable.unregisterObserver(observer);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void notifyDataSetChanged() {
		observable.notifyChanged();	
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void notifyDataSetInvalidated() {
		observable.notifyInvalidated();	
	}
	
	private class AdapterDataSetObserver extends DataSetObserver {
		@Override
		public void onChanged() {
			ObjectAdapter.this.notifyDataSetChanged();
		}
		
		@Override
		public void onInvalidated() {
			ObjectAdapter.this.notifyDataSetInvalidated();
		}
	}
	
	/**
	 * 
	 * @author Dmitry Vorobiev
	 *
	 * @param <T>
	 */
	public interface ObjectMapper<T> {
		public void mapData(int position, View view, T data);
	}

}
