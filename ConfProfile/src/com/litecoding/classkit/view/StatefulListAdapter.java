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

import android.widget.ListAdapter;

/**
 * Interface 
 * @author Dmitry S. Vorobiev
 *
 */
public interface StatefulListAdapter extends ListAdapter {
	/**
	 * Notify observer that data set is changed.
	 * 
	 * This method should be called manually when data set is changed 
	 * (as usual when list w/ objects was modified). If it is changed by 
	 * adapter methods, this method is called automatically.
	 */
	public void notifyDataSetChanged();
	
	/**
	 * Notify observer that data set is invalidated.
	 * 
	 * This method should be called manually when data set is invalidated. 
	 * If it is changed by adapter methods, this method is called automatically.
	 */
	public void notifyDataSetInvalidated();
}
