package no.infoss.confprofile.db;

public abstract class RequestWithAffectedRows extends Request {
	protected int mRowsCount = -1;
	
	public final int getRowsCount() {
		return mRowsCount;
	}
}
