package no.infoss.confprofile.profile;

import no.infoss.confprofile.R;
import no.infoss.confprofile.db.Expressions;
import no.infoss.confprofile.db.Expressions.Expression;
import no.infoss.confprofile.db.Insert;
import no.infoss.confprofile.db.QueryBuilder;
import no.infoss.confprofile.db.Select;
import no.infoss.confprofile.db.Transaction;
import no.infoss.confprofile.db.Update;
import no.infoss.confprofile.model.CompositeListItemModel;
import no.infoss.confprofile.model.ImageViewModel;
import no.infoss.confprofile.profile.data.VpnData;
import no.infoss.confprofile.util.SqliteRequestThread;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import com.litecoding.classkit.view.LazyCursorList.CursorMapper;

public class VpnDataCursorLoader extends BaseQueryCursorLoader {
	public static final String TAG = VpnDataCursorLoader.class.getSimpleName();
	
	public static final String TABLE = "vpn_data";
	public static final String COL_PAYLOAD_UUID = "payload_uuid";
	public static final String COL_USER_DEFINED_NAME = "user_defined_name";
	public static final String COL_OVERRIDE_PRIMARY = "override_primary";
	public static final String COL_ON_DEMAND_ENABLED = "on_demand_enabled";
	public static final String COL_ON_DEMAND_ENABLED_BY_USER = "on_demand_enabled_by_user";
	
	public static final String P_PREFIX = PREFIX.concat(TABLE).concat(".");
	public static final String P_PAYLOAD_UUID = P_PREFIX.concat("P_PAYLOAD_UUID");
	public static final String P_USER_DEFINED_NAME = P_PREFIX.concat("P_USER_DEFINED_NAME");
	public static final String P_OVERRIDE_PRIMARY = P_PREFIX.concat("P_OVERRIDE_PRIMARY");
	public static final String P_ON_DEMAND_ENABLED = P_PREFIX.concat("P_ON_DEMAND_ENABLED");
	public static final String P_ON_DEMAND_ENABLED_BY_USER = P_PREFIX.concat("P_ON_DEMAND_ENABLED_BY_USER");
	
	public static final CursorMapper<VpnData> VPN_DATA_CURSOR_MAPPER = new CursorMapper<VpnData>() {

		@Override
		public VpnData mapRowToObject(Cursor cursor) {
			ImageViewModel iconModel;
			CompositeListItemModel model = new CompositeListItemModel();
			iconModel = new ImageViewModel(android.R.id.icon1);
			iconModel.setImageResourceId(0);
			model.addMapping(iconModel);
			
			iconModel = new ImageViewModel(android.R.id.icon2);
			iconModel.setImageResourceId(R.drawable.arrow);
			model.addMapping(iconModel);
			
			VpnData data = new VpnData();
			data.setPayloadUuid(cursor.getString(0));
			data.setUserDefinedName(cursor.getString(1));
			data.setOverridePrimary(cursor.getInt(2) != 0);
			data.setOnDemandEnabled(cursor.getInt(3) != 0);
			data.setOnDemandEnabledByUser(cursor.getInt(4) != 0);
			data.setModel(model);
			return data;
		}
	};
	
	public VpnDataCursorLoader(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
		super(context, create(context, id, params, dbHelper));
	}
	
	public static VpnDataPerformance create(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
		return new VpnDataPerformance(context, id, params, dbHelper);
	}
	
	public static class VpnDataPerformance extends LoaderQueryPerformance {
		private String mNewPayloadUuid[] = null;
		private String mNewUserDefinedName[] = null;
		private boolean mNewOverridePrimary[] = null;
		private boolean mNewOnDemandEnabled[] = null;
		private boolean mNewOnDemandEnabledByUser[] = null;
		private String mSelectBy = null; 
		private String mSelectValue = null;
		
		public VpnDataPerformance(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
			super(context, id, params, dbHelper);
			
			if(params != null) {
				if(params.containsKey(P_BATCH_MODE)) {
					mNewPayloadUuid = params.getStringArray(P_PAYLOAD_UUID);
					mNewUserDefinedName = params.getStringArray(P_USER_DEFINED_NAME);
					mNewOverridePrimary = params.getBooleanArray(P_OVERRIDE_PRIMARY);
					mNewOnDemandEnabled = params.getBooleanArray(P_ON_DEMAND_ENABLED);
					mNewOnDemandEnabledByUser = params.getBooleanArray(P_ON_DEMAND_ENABLED_BY_USER);
				} else {
					mNewPayloadUuid = new String[] { params.getString(P_PAYLOAD_UUID) };
					mNewUserDefinedName = new String[] { params.getString(P_USER_DEFINED_NAME) };
					mNewOverridePrimary = new boolean[] { params.getBoolean(P_OVERRIDE_PRIMARY) };
					mNewOnDemandEnabled = new boolean[] { params.getBoolean(P_ON_DEMAND_ENABLED) };
					mNewOnDemandEnabledByUser = new boolean[] { params.getBoolean(P_ON_DEMAND_ENABLED_BY_USER) };
				}
				
				if(params.containsKey(P_SELECT_BY)) {
					mSelectBy = params.getString(P_SELECT_BY);
				}
				
				if(params.containsKey(P_SELECT_VALUE)) {
					mSelectValue = params.getString(P_SELECT_VALUE);
				}
			}
		}
		
		@Override
	    public Cursor perform() {		
			Cursor result;
			SQLiteDatabase db = mDbHelper.getWritableDatabase();
			
			if(mQueryType == STMT_INSERT) {
				insert();
			} else if(mQueryType == STMT_UPDATE) {
				update();
			}
			
			Select select = QueryBuilder.select().from(TABLE);
			if(mSelectBy == null) {
				select = select.all();
			} else {
				Expression expr = Expressions.column(mSelectBy).eq(Expressions.literal(mSelectValue));
				select.where(expr, new Object[0]);
			}
			
			//finally always do select to show changes in the UI
			result = select.perform(db).getCursor();
			
			return result;
		}
		
		private void insert() {
			if(mNewPayloadUuid != null && 
					mNewUserDefinedName != null && 
					mNewOverridePrimary != null && 
					mNewOnDemandEnabled != null && 
					mNewOnDemandEnabledByUser != null) {	
				Transaction transaction = new Transaction();
				
				for(int i = 0; i < mNewPayloadUuid.length; i++) {
					ContentValues values = new ContentValues();
					values.put(COL_PAYLOAD_UUID, mNewPayloadUuid[i]);
					values.put(COL_USER_DEFINED_NAME, mNewUserDefinedName[i]);
					values.put(COL_OVERRIDE_PRIMARY, mNewOverridePrimary[i] ? 1 : 0);
					values.put(COL_ON_DEMAND_ENABLED, mNewOnDemandEnabled[i] ? 1 : 0);
					values.put(COL_ON_DEMAND_ENABLED_BY_USER, mNewOnDemandEnabledByUser[i] ? 1 : 0);
					transaction.addRequest(Insert.insert().into(TABLE).values(values));
				}
				
				SqliteRequestThread.getInstance().request(transaction);
			}
		}
		
		private void update() {
			if(mNewOnDemandEnabledByUser != null && 
					mNewOnDemandEnabledByUser.length == 1) {
				ContentValues values = new ContentValues();
				values.put(COL_ON_DEMAND_ENABLED_BY_USER, mNewOnDemandEnabledByUser[0] ? 1 : 0);
				Update request = Update.update().table(TABLE).values(values);
				if(mSelectBy != null) {
					Expression expr = Expressions.column(mSelectBy).eq(Expressions.literal(mSelectValue));
					request.where(expr, new Object[0]);
				}
				
				Transaction transaction = new Transaction();
				transaction.addRequest(request);
				SqliteRequestThread.getInstance().request(transaction);
			}
		}
	}
}
