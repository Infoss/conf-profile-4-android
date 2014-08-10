package no.infoss.confprofile.db;

import java.util.ArrayList;
import java.util.Arrays;

import no.infoss.confprofile.db.Expressions.Expression;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

/**
 * Class for performing Update request, partially compatible with 
 * corresponding class from android-db-commons.
 * @author Dmitry Vorobiev
 *
 */
public class Update extends RequestWithAffectedRows {
	private String mSql = null;
	private ContentValues mContentValues = null;
	private Expression mWhereExpression;
	private ArrayList<String> mSelectionArgs = new ArrayList<String>();
	
	private Update() {
		
	}
	
	public Update table(String tableName) {
		setTableName(tableName);
		return this;
	}
	
	public Update values(ContentValues values) {
		mContentValues = values;
		return this;
	}
	
	public Update where(Expression exp, Object... selectionArgs) {
		mWhereExpression = exp;;
		mSelectionArgs.addAll(Arrays.asList(exp.getSelectionArgs()));
		//TODO: fill selectionArgs
		//mSelectionArgs.addAll(Arrays.asList(selectionArgs));
		return this;
	}

	@Override
	public Update perform(SQLiteDatabase db) {
		if(mIsAlreadyPerformed) {
			throw new IllegalStateException("This request was already performed");
		}
		
		buildQuery();
		mRowsCount = db.update(mTableName, mContentValues, mSql, mSelectionArgs.toArray(new String[mSelectionArgs.size()]));
		return this;
	}
	
	private void buildQuery() {
		if(mWhereExpression == null) {
			mSql = null;
		} else {
			mSql = mWhereExpression.getLiteral();
		}
	}
	
	public static final Update update() {
		return new Update();
	}
}
