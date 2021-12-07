package no.infoss.confprofile.db;

public class Expressions {
	public static Expression column(String name) {
		return new ColumnExpression(name);
	}
	
	public static Expression literal(String literal) {
		return new Expression(literal);
	}
	
	public static class Expression {
		protected String mLiteral;
		
		private Expression(String literal) {
			mLiteral = literal;
		}
		
		public Expression eq(Expression exp) {
			if(exp instanceof ColumnExpression) {
				return exp.eq(this);
			}
			
			throw new UnsupportedOperationException();
		}
		
		public String getLiteral() {
			return mLiteral;
		}
		
		public String[] getSelectionArgs() {
			return new String[0];
		}
	}
	
	public static class ColumnExpression extends Expression {
		private Expression mRightExpression;
		private String mComparator;
		
		private ColumnExpression(String literal) {
			super(literal);
		}
		
		@Override
		public ColumnExpression eq(Expression exp) {
			mRightExpression = exp;
			mComparator = "=";
			return this;
		}
		
		@Override
		public String getLiteral() {
			if(mRightExpression instanceof ColumnExpression) {
				return String.format("%s %s %s", mLiteral, mComparator, mRightExpression.mLiteral);
			}
			return String.format("%s %s ?", mLiteral, mComparator);
		}
		
		@Override
		public String[] getSelectionArgs() {
			if(mRightExpression instanceof ColumnExpression) {
				return new String[0];
			}
			return new String[] { mRightExpression.getLiteral() };
		}
	}
}
