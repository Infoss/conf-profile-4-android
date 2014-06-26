package no.infoss.confprofile.db;

import java.util.ArrayList;
import java.util.Arrays;

import no.infoss.confprofile.db.Expressions.Expression;
import android.database.sqlite.SQLiteDatabase;

public class Delete extends Request {
	private int mRowsCount;
	
	private Expression mWhereExpression;
	private ArrayList<String> mSelectionArgs = new ArrayList<String>();
	
	private Delete() {
		
	}
	
	public Delete from(String tableName) {
		setTableName(tableName);
		return this;
	}
	
	public Delete where(Expression exp, Object... selectionArgs) {
		mWhereExpression = exp;;
		mSelectionArgs.addAll(Arrays.asList(exp.getSelectionArgs()));
		//TODO: fill selectionArgs
		//mSelectionArgs.addAll(Arrays.asList(selectionArgs));
		return this;
	}

	@Override
	public Request perform(SQLiteDatabase db) {
		if(mIsAlreadyPerformed) {
			throw new IllegalStateException("This request was already performed");
		}
		
		String where;
		if(mWhereExpression == null) {
			where = "1";
		} else {
			where = mWhereExpression.getLiteral();
		}
		
		mRowsCount = db.delete(mTableName, where, mSelectionArgs.toArray(new String[mSelectionArgs.size()]));
		
		return this;
	}
	
	public int getRowsCount() {
		return mRowsCount;
	}
	
	public static final Delete delete() {
		return new Delete();
	}

}
