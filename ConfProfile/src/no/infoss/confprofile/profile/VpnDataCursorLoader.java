package no.infoss.confprofile.profile;

import no.infoss.confprofile.db.Expressions;
import no.infoss.confprofile.db.Expressions.Expression;
import no.infoss.confprofile.db.Insert;
import no.infoss.confprofile.db.QueryBuilder;
import no.infoss.confprofile.db.Select;
import no.infoss.confprofile.db.Transaction;
import no.infoss.confprofile.db.Update;
import no.infoss.confprofile.model.VpnDataModel;
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
	public static final String COL_PROFILE_ID = "profile_id";
	public static final String COL_PAYLOAD_UUID = "payload_uuid";
	public static final String COL_USER_DEFINED_NAME = "user_defined_name";
	public static final String COL_OVERRIDE_PRIMARY = "override_primary";
	public static final String COL_ON_DEMAND_ENABLED = "on_demand_enabled";
	public static final String COL_ON_DEMAND_ENABLED_BY_USER = "on_demand_enabled_by_user";
	public static final String COL_ON_DEMAND_RULES = "on_demand_rules";
	public static final String COL_ON_DEMAND_CREDENTIALS = "on_demand_credentials";
	public static final String COL_REMOTE_SERVER = "remote_server";
	public static final String COL_LOGIN = "login";
	public static final String COL_PASSWORD = "password";
	public static final String COL_RSA_SECURID = "rsa_securid";
	public static final String COL_CERTIFICATE = "certificate";
	public static final String COL_SHARED_SECRET = "shared_secret";
	public static final String COL_PPTP_ENCRYPTION = "pptp_encryption";
	public static final String COL_IPSEC_GROUP_NAME = "ipsec_group_name";
	public static final String COL_VPN_TYPE = "vpn_type";
	
	public static final String P_PREFIX = PREFIX.concat(TABLE).concat(".");
	public static final String P_PROFILE_ID = P_PREFIX.concat("P_PROFILE_ID");
	public static final String P_PAYLOAD_UUID = P_PREFIX.concat("P_PAYLOAD_UUID");
	public static final String P_USER_DEFINED_NAME = P_PREFIX.concat("P_USER_DEFINED_NAME");
	public static final String P_OVERRIDE_PRIMARY = P_PREFIX.concat("P_OVERRIDE_PRIMARY");
	public static final String P_ON_DEMAND_ENABLED = P_PREFIX.concat("P_ON_DEMAND_ENABLED");
	public static final String P_ON_DEMAND_ENABLED_BY_USER = P_PREFIX.concat("P_ON_DEMAND_ENABLED_BY_USER");
	public static final String P_ON_DEMAND_RULES = P_PREFIX.concat("P_ON_DEMAND_RULES");
	public static final String P_ON_DEMAND_CREDENTIALS = P_PREFIX.concat("P_ON_DEMAND_CREDENTIALS");
	public static final String P_REMOTE_SERVER = P_PREFIX.concat("P_REMOTE_SERVER");
	public static final String P_LOGIN = P_PREFIX.concat("P_LOGIN");
	public static final String P_PASSWORD = P_PREFIX.concat("P_PASSWORD");
	public static final String P_RSA_SECURID = P_PREFIX.concat("P_RSA_SECURID");
	public static final String P_CERTIFICATE = P_PREFIX.concat("P_CERTIFICATE");
	public static final String P_SHARED_SECRET = P_PREFIX.concat("P_SHARED_SECRET");
	public static final String P_PPTP_ENCRYPTION = P_PREFIX.concat("P_PPTP_ENCRYPTION");
	public static final String P_IPSEC_GROUP_NAME = P_PREFIX.concat("P_IPSEC_GROUP_NAME");
	public static final String P_VPN_TYPE = P_PREFIX.concat("P_VPN_TYPE");
	
	public static final CursorMapper<VpnData> VPN_DATA_CURSOR_MAPPER = new CursorMapper<VpnData>() {

		@Override
		public VpnData mapRowToObject(Cursor cursor) {
			VpnData data = new VpnData();
			data.setProfileId(cursor.getString(0));
			data.setPayloadUuid(cursor.getString(1));
			data.setUserDefinedName(cursor.getString(2));
			data.setOverridePrimary(cursor.getInt(3) != 0);
			data.setOnDemandEnabled(cursor.getInt(4) != 0);
			data.setOnDemandEnabledByUser(cursor.getInt(5) != 0);
			data.setOnDemandRules(cursor.getString(6));
			data.setOnDemandCredentials(cursor.getString(7));
			data.setRemoteServer(cursor.getString(8));
			data.setLogin(cursor.getString(9));
			data.setPassword(cursor.getString(10));
			data.setRsaSecurid(cursor.getInt(11) != 0);
			data.setCertificate(cursor.getString(12));
			data.setSharedSecret(cursor.getString(13));
			data.setPptpEncryption(cursor.getString(14));
			data.setIpsecGroupName(cursor.getString(15));
			data.setVpnType(cursor.getString(16));;
			return data;
		}
	};
	
	public static final CursorMapper<VpnDataModel> VPN_DATA_MODEL_CURSOR_MAPPER = new CursorMapper<VpnDataModel>() {

		@Override
		public VpnDataModel mapRowToObject(Cursor cursor) {
			VpnDataModel model = new VpnDataModel();
			model.setData(VPN_DATA_CURSOR_MAPPER.mapRowToObject(cursor));
			return model;
		}
	};
	
	public VpnDataCursorLoader(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
		super(context, create(context, id, params, dbHelper));
	}
	
	public static VpnDataPerformance create(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
		return new VpnDataPerformance(context, id, params, dbHelper);
	}
	
	public static class VpnDataPerformance extends LoaderQueryPerformance {
		private String mNewProfileId[] = null;
		private String mNewPayloadUuid[] = null;
		private String mNewUserDefinedName[] = null;
		private boolean mNewOverridePrimary[] = null;
		private boolean mNewOnDemandEnabled[] = null;
		private boolean mNewOnDemandEnabledByUser[] = null;
		private String mNewOnDemandRules[] = null;
		private String mNewOnDemandCredentials[] = null;
		private String mNewRemoteServer[] = null;
		private String mNewLogin[] = null;
		private String mNewPassword[] = null;
		private boolean mNewRsaSecurid[] = null;
		private String mNewCertificate[] = null;
		private String mNewSharedSecret[] = null;
		private String mNewPptpEncryption[] = null;
		private String mNewIpsecGroupName[] = null;
		private String mNewVpnType[] = null;
		private String mSelectBy = null; 
		private String mSelectValue = null;
		
		public VpnDataPerformance(Context context, int id, Bundle params, DbOpenHelper dbHelper) {
			super(context, id, params, dbHelper);
			
			if(params != null) {
				if(params.containsKey(P_BATCH_MODE)) {
					mNewProfileId = params.getStringArray(P_PROFILE_ID);
					mNewPayloadUuid = params.getStringArray(P_PAYLOAD_UUID);
					mNewUserDefinedName = params.getStringArray(P_USER_DEFINED_NAME);
					mNewOverridePrimary = params.getBooleanArray(P_OVERRIDE_PRIMARY);
					mNewOnDemandEnabled = params.getBooleanArray(P_ON_DEMAND_ENABLED);
					mNewOnDemandEnabledByUser = params.getBooleanArray(P_ON_DEMAND_ENABLED_BY_USER);
					mNewOnDemandRules = params.getStringArray(P_ON_DEMAND_RULES);
					mNewOnDemandCredentials = params.getStringArray(P_ON_DEMAND_CREDENTIALS);
					mNewRemoteServer = params.getStringArray(P_REMOTE_SERVER);
					mNewLogin = params.getStringArray(P_LOGIN);
					mNewPassword = params.getStringArray(P_PASSWORD);
					mNewRsaSecurid = params.getBooleanArray(P_RSA_SECURID);
					mNewCertificate = params.getStringArray(P_CERTIFICATE);
					mNewSharedSecret = params.getStringArray(P_SHARED_SECRET);
					mNewPptpEncryption = params.getStringArray(P_PPTP_ENCRYPTION);
					mNewIpsecGroupName = params.getStringArray(P_IPSEC_GROUP_NAME);
					mNewVpnType = params.getStringArray(P_VPN_TYPE);
				} else {
					mNewProfileId = new String[] { params.getString(P_PROFILE_ID) };
					mNewPayloadUuid = new String[] { params.getString(P_PAYLOAD_UUID) };
					mNewUserDefinedName = new String[] { params.getString(P_USER_DEFINED_NAME) };
					mNewOverridePrimary = new boolean[] { params.getBoolean(P_OVERRIDE_PRIMARY) };
					mNewOnDemandEnabled = new boolean[] { params.getBoolean(P_ON_DEMAND_ENABLED) };
					mNewOnDemandEnabledByUser = new boolean[] { params.getBoolean(P_ON_DEMAND_ENABLED_BY_USER) };
					mNewOnDemandRules = new String[] { params.getString(P_ON_DEMAND_RULES) };
					mNewOnDemandCredentials = new String[] { params.getString(P_ON_DEMAND_CREDENTIALS) };
					mNewRemoteServer = new String[] { params.getString(P_REMOTE_SERVER) };
					mNewLogin = new String[] { params.getString(P_LOGIN) };
					mNewPassword = new String[] { params.getString(P_PASSWORD) };
					mNewRsaSecurid = new boolean[] { params.getBoolean(P_RSA_SECURID) };
					mNewCertificate = new String[] { params.getString(P_CERTIFICATE) };
					mNewSharedSecret = new String[] { params.getString(P_SHARED_SECRET) };
					mNewPptpEncryption = new String[] { params.getString(P_PPTP_ENCRYPTION) };
					mNewIpsecGroupName = new String[] { params.getString(P_IPSEC_GROUP_NAME) };
					mNewVpnType = new String[] { params.getString(P_VPN_TYPE) };
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
			if(mNewProfileId != null && 
					mNewPayloadUuid != null && 
					mNewUserDefinedName != null && 
					mNewOverridePrimary != null && 
					mNewOnDemandEnabled != null && 
					mNewOnDemandEnabledByUser != null &&
					mNewOnDemandRules != null &&
					mNewOnDemandCredentials != null && 
					mNewRemoteServer != null &&
					mNewLogin != null &&
					mNewPassword != null &&
					mNewRsaSecurid != null &&
					mNewCertificate != null &&
					mNewSharedSecret != null &&
					mNewPptpEncryption != null &&
					mNewIpsecGroupName != null &&
					mNewVpnType != null) {	
				Transaction transaction = new Transaction();
				
				for(int i = 0; i < mNewPayloadUuid.length; i++) {
					ContentValues values = new ContentValues();
					values.put(COL_PROFILE_ID, mNewProfileId[i]);
					values.put(COL_PAYLOAD_UUID, mNewPayloadUuid[i]);
					values.put(COL_USER_DEFINED_NAME, mNewUserDefinedName[i]);
					values.put(COL_OVERRIDE_PRIMARY, mNewOverridePrimary[i] ? 1 : 0);
					values.put(COL_ON_DEMAND_ENABLED, mNewOnDemandEnabled[i] ? 1 : 0);
					values.put(COL_ON_DEMAND_ENABLED_BY_USER, mNewOnDemandEnabledByUser[i] ? 1 : 0);
					values.put(COL_ON_DEMAND_RULES, mNewOnDemandRules[i]);
					values.put(COL_ON_DEMAND_CREDENTIALS, mNewOnDemandCredentials[i]);
					values.put(COL_REMOTE_SERVER, mNewRemoteServer[i]);
					values.put(COL_LOGIN, mNewLogin[i]);
					values.put(COL_PASSWORD, mNewPassword[i]);
					values.put(COL_RSA_SECURID, mNewRsaSecurid[i]);
					values.put(COL_CERTIFICATE, mNewCertificate[i]);
					values.put(COL_SHARED_SECRET, mNewSharedSecret[i]);
					values.put(COL_PPTP_ENCRYPTION, mNewPptpEncryption[i]);
					values.put(COL_IPSEC_GROUP_NAME, mNewIpsecGroupName[i]);
					values.put(COL_VPN_TYPE, mNewVpnType[i]);
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
