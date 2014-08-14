package no.infoss.confprofile.db;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import android.database.sqlite.SQLiteDatabase;

public class Transaction extends Request {
	protected List<Request> mRequests = new LinkedList<Request>();
	
	public void clearRequests() {
		mRequests.clear();
	}
	
	public void addRequest(Request request) {
		mRequests.add(request);
	}
	
	public List<Request> getRequests() {
		return Collections.unmodifiableList(mRequests);
	}

	@Override
	public Request perform(SQLiteDatabase db) {
		db.beginTransaction();
		try {
			for(Request request : mRequests) {
				request.perform(db);
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		
		return this;
	}

}
