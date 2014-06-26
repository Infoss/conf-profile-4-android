package no.infoss.confprofile.db;

import java.util.ArrayList;
import java.util.Arrays;

import no.infoss.confprofile.db.Expressions.Expression;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class Select extends Request {
	private boolean mSelectAll = false;
	private Cursor mResult = null;
	
	private String mSql;
	private Expression mWhereExpression;
	private ArrayList<String> mSelectionArgs = new ArrayList<String>();
	
	private Select() {
		
	}
	
	public Select all() {
		mSelectAll = true;
		return this;
	}
	
	public Select from(String tableName) {
		setTableName(tableName);
		return this;
	}
	
	public Select where(Expression exp, Object... selectionArgs) {
		mWhereExpression = exp;;
		mSelectionArgs.addAll(Arrays.asList(exp.getSelectionArgs()));
		//TODO: fill selectionArgs
		//mSelectionArgs.addAll(Arrays.asList(selectionArgs));
		return this;
	}

	@Override
	public Select perform(SQLiteDatabase db) {
		if(mIsAlreadyPerformed) {
			throw new IllegalStateException("This request was already performed");
		}
		
		buildQuery();
		mResult = db.rawQuery(mSql, mSelectionArgs.toArray(new String[mSelectionArgs.size()]));
		
		return this;
	}
	
	public Cursor getResult() {
		return mResult;
	}
	
	private void buildQuery() {
		String sqlFmt = "SELECT % FROM % WHERE %";
		String fields;
		String where;
		if(mSelectAll) {
			fields = "*";
		} else {
			fields = "<FILLME>";
		}
		
		if(mWhereExpression == null) {
			where = "TRUE";
		} else {
			where = mWhereExpression.getLiteral();
		}
		
		mSql = String.format(sqlFmt, fields, mTableName, where);
	}

	public static final Select select() {
		return new Select();
	}
}
