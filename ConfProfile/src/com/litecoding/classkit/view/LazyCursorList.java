/* 
* Copyright 2014 Dmitry S. Vorobiev
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

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import android.database.Cursor;
import android.database.DataSetObservable;
import android.util.Log;

public class LazyCursorList<E> extends DataSetObservable implements List<E> {
	public static final String TAG = LazyCursorList.class.getSimpleName();
	private static final int ALL_ROWS = -1;
	
	private Cursor mCursor = null;
	private int mCursorPos;
	private CursorMapper<E> mMapper;
	private List<E> mWrappedList;
	private boolean mIsAllProcessed;
	private boolean mOwnCursor = false;
	private int mInitialSize = 0;
	
	public LazyCursorList(CursorMapper<E> mapper) {
		this(null, mapper);
	}
	
	public LazyCursorList(Cursor cursor, CursorMapper<E> mapper) {
		this(cursor, mapper, null);
	}
	
	public LazyCursorList(Cursor cursor, CursorMapper<E> mapper, List<E> wrappedList) {
		this(cursor, mapper, wrappedList, false);
	}
	
	public LazyCursorList(Cursor cursor, CursorMapper<E> mapper, List<E> wrappedList, boolean ownCursor) {
		Log.d(TAG, "Create with params(".
				concat(String.valueOf(cursor)).
				concat(", ").
				concat(String.valueOf(mapper)).
				concat(", ").
				concat(String.valueOf(wrappedList)).
				concat(")"));
		mMapper = mapper;
		
		if(wrappedList != null) {
			mWrappedList = wrappedList;
		} else {
			mWrappedList = new LinkedList<E>();
		}
		
		populateFrom(cursor, true, ownCursor);
	}
	
	public void populateFrom(Cursor cursor, boolean clearBefore) {
		populateFrom(cursor, clearBefore, false);
	}
	
	public void populateFrom(Cursor cursor, boolean clearBefore, boolean ownCursor) {
		Log.d(TAG, "populateFrom(".
				concat(String.valueOf(cursor)).
				concat(", ").
				concat(String.valueOf(clearBefore)).
				concat(", ").
				concat(String.valueOf(ownCursor)).
				concat(")"));
		
		if(!clearBefore) {
			processRows(ALL_ROWS);
		} else {
			mWrappedList.clear();
		}
		
		if(mOwnCursor && mCursor != null && !mCursor.isClosed()) {
			mCursor.close();
		}
		
		mInitialSize = mWrappedList.size();
		mCursor = cursor;
		mOwnCursor = ownCursor;
		mCursorPos = 0;
		
		if(mCursor == null) {
			mIsAllProcessed = true;
		} else {
			mIsAllProcessed = false;
		}
		
		notifyChanged();
	}
	
	public List<E> getWrappedList() {
		Log.d(TAG, "getWrappedList()");
		return mWrappedList;
	}
	
	@Override
	public void add(int location, E object) {
		processRows(ALL_ROWS);
		mWrappedList.add(location, object);
		notifyChanged();
	}

	@Override
	public boolean add(E object) {
		Log.d(TAG, "add(".
				concat(String.valueOf(object)).
				concat(")"));
		processRows(ALL_ROWS);
		boolean result = mWrappedList.add(object);
		if(result) {
			notifyChanged();
		}
		Log.d(TAG, "Returning ".concat(String.valueOf(result)));
		return result;
	}

	@Override
	public boolean addAll(int location, Collection<? extends E> collection) {
		processRows(ALL_ROWS);
		boolean result = mWrappedList.addAll(location, collection);
		if(result) {
			notifyChanged();
		}
		return result;
	}

	@Override
	public boolean addAll(Collection<? extends E> collection) {
		processRows(ALL_ROWS);
		boolean result = mWrappedList.addAll(collection);
		if(result) {
			notifyChanged();
		}
		return result;
	}

	@Override
	public void clear() {
		mIsAllProcessed = true;
		if(mCursor != null && !mCursor.isClosed()) {
			mCursor.close();
		}
		
		boolean wasEmpty = mWrappedList.isEmpty();
		mWrappedList.clear();
		if(!wasEmpty) {
			notifyChanged();
		}
	}

	@Override
	public boolean contains(Object object) {
		boolean result = mWrappedList.contains(object);
		if(!result && !mIsAllProcessed) {
			processRows(ALL_ROWS);
			result = mWrappedList.contains(object);
		}
		return result;
	}

	@Override
	public boolean containsAll(Collection<?> collection) {
		processRows(ALL_ROWS);
		return mWrappedList.containsAll(collection);
	}

	@Override
	public E get(int location) {
		Log.d(TAG, "get(".
				concat(String.valueOf(location)).
				concat(")"));
		processTo(location);
		Log.d(TAG, "Current wrapped list size is ".
				concat(String.valueOf(mWrappedList.size())));
		return mWrappedList.get(location);
	}

	@Override
	public int indexOf(Object object) {
		int result = mWrappedList.indexOf(object);
		if(result == -1 && !mIsAllProcessed) {
			processRows(ALL_ROWS);
			result = mWrappedList.indexOf(object);
		}
		
		return result;
	}

	@Override
	public boolean isEmpty() {
		if(mIsAllProcessed || mCursor == null || mCursor.isClosed()) {
			return mWrappedList.isEmpty();
		}
		
		return mCursor.getCount() == 0;
	}

	@Override
	public Iterator<E> iterator() {
		processRows(ALL_ROWS);
		return mWrappedList.iterator();
	}

	@Override
	public int lastIndexOf(Object object) {
		processRows(ALL_ROWS);
		return mWrappedList.lastIndexOf(object);
	}

	@Override
	public ListIterator<E> listIterator() {
		processRows(ALL_ROWS);
		return mWrappedList.listIterator();
	}

	@Override
	public ListIterator<E> listIterator(int location) {
		processRows(ALL_ROWS);
		return mWrappedList.listIterator(location);
	}

	@Override
	public E remove(int location) {
		processRows(ALL_ROWS);
		E result = mWrappedList.remove(location);
		notifyChanged();
		return result;
	}

	@Override
	public boolean remove(Object object) {
		processRows(ALL_ROWS);
		boolean result = mWrappedList.remove(object);
		if(result) {
			notifyChanged();
		}
		return result;
	}

	@Override
	public boolean removeAll(Collection<?> collection) {
		processRows(ALL_ROWS);
		boolean result = mWrappedList.removeAll(collection);
		if(result) {
			notifyChanged();
		}
		return result;
	}

	@Override
	public boolean retainAll(Collection<?> collection) {
		processRows(ALL_ROWS);
		boolean result = mWrappedList.retainAll(mWrappedList);
		if(result) {
			notifyChanged();
		}
		return result;
	}

	@Override
	public E set(int location, E object) {
		processTo(location);
		E result = mWrappedList.set(location, object);
		notifyChanged();
		return result;
	}

	@Override
	public int size() {
		Log.d(TAG, "size()");
		int result = 0;
		if(!mIsAllProcessed && mCursor != null) {
			Log.d(TAG, "...from cursor");
			result = mInitialSize + mCursor.getCount();
		} else {
			Log.d(TAG, "...from list");
			result = mWrappedList.size();
		}
		Log.d(TAG, "Returning ".concat(String.valueOf(result)));
		return result;
	}

	@Override
	public List<E> subList(int start, int end) {
		processTo(end);
		return mWrappedList.subList(start, end);
	}

	@Override
	public Object[] toArray() {
		processRows(ALL_ROWS);
		return mWrappedList.toArray();
	}

	@Override
	public <T> T[] toArray(T[] array) {
		processRows(ALL_ROWS);
		return mWrappedList.toArray(array);
	}
	
	private int processTo(int rowNum) {
		Log.d(TAG, "processTo(".
				concat(String.valueOf(rowNum)).
				concat(")"));
		
		if(rowNum < 0) {
			return 0;
		}
		
		int currSize = mWrappedList.size();
		int cnt = rowNum - currSize + 1;
		
		if(cnt <= 0) {
			return 0;
		}
		
		return processRows(cnt);
	}
	
	private int processRows(int rowsCount) {
		Log.d(TAG, "processRows(".
				concat(String.valueOf(rowsCount)).
				concat(")"));
		
		int rowsProcessed = 0;
		
		if(mCursor.isClosed()) {
			mCursor = null;
		}
		
		if(mIsAllProcessed || mCursor == null) {
			Log.d(TAG, "Returning ".
					concat(String.valueOf(rowsProcessed)).
					concat(" (mIsAllProcessed=").
					concat(String.valueOf(mIsAllProcessed)).
					concat(", mCursor=").
					concat(String.valueOf(mCursor)).
					concat(")"));
			return rowsProcessed;
		}
		
		while(mCursor.moveToPosition(mCursorPos) && 
			(rowsCount == ALL_ROWS || rowsProcessed < rowsCount)) {
			
			mWrappedList.add(mMapper.mapRowToObject(mCursor));
			
			Log.d(TAG, "Adding to list. Size of wrapped list is ".
					concat(String.valueOf(mWrappedList.size())));
			
			rowsProcessed++;
			mCursorPos++;
		}
		
		if(!mCursor.isClosed() && 
			mCursor.isAfterLast()) {
			mIsAllProcessed = true;
			
			if(mOwnCursor) {
				mCursor.close();
			}
			
			mCursor = null;
			Log.d(TAG, "Closing (?) & nulling cursor");
		}
		
		Log.d(TAG, "Returning ".
				concat(String.valueOf(rowsProcessed)));
		
		return rowsProcessed;
	}

	public static interface CursorMapper<E> {
		public E mapRowToObject(Cursor cursor);
	}
}
