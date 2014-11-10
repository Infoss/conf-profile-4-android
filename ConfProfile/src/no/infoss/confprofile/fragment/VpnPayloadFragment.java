package no.infoss.confprofile.fragment;

import java.util.ArrayList;
import java.util.List;

import no.infoss.confprofile.R;
import no.infoss.confprofile.model.common.ListItemModel;
import no.infoss.confprofile.model.vpndata.CertificateModel;
import no.infoss.confprofile.model.vpndata.IpsecGroupNameModel;
import no.infoss.confprofile.model.vpndata.LoginModel;
import no.infoss.confprofile.model.vpndata.OverridePrimaryModel;
import no.infoss.confprofile.model.vpndata.PasswordModel;
import no.infoss.confprofile.model.vpndata.PptpEncryptionModel;
import no.infoss.confprofile.model.vpndata.RemoteServerModel;
import no.infoss.confprofile.model.vpndata.RsaSecurIdModel;
import no.infoss.confprofile.model.vpndata.SharedSecretModel;
import no.infoss.confprofile.profile.BaseQueryCursorLoader;
import no.infoss.confprofile.profile.DbOpenHelper;
import no.infoss.confprofile.profile.VpnDataCursorLoader;
import no.infoss.confprofile.profile.data.VpnData;
import no.infoss.confprofile.util.ListItemMapper;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.litecoding.classkit.view.HeaderObjectAdapter;

public class VpnPayloadFragment extends Fragment implements LoaderCallbacks<Cursor>  {
	public static final String TAG = VpnPayloadFragment.class.getSimpleName();
	
	public static final String TAB_L2TP = "l2tp";
	public static final String TAB_PPTP = "pptp";
	public static final String TAB_IPSEC = "ipsec";
	public static final String TAB_OPENVPN = "openvpn";
	
	private static final List<String> SINGLE_EMPTY_HEADER_LIST = new ArrayList<String>(1);
	static {
		SINGLE_EMPTY_HEADER_LIST.clear();
		SINGLE_EMPTY_HEADER_LIST.add("");
	}
	
	private final List<List<ListItemModel<?>>> mItems = new ArrayList<List<ListItemModel<?>>>();
	private final List<ListItemModel<?>> mPptpItems = new ArrayList<ListItemModel<?>>(6);
	private final List<ListItemModel<?>> mL2tpItems = new ArrayList<ListItemModel<?>>(6);
	private final List<ListItemModel<?>> mIpsecItems = new ArrayList<ListItemModel<?>>(6);
	private final List<ListItemModel<?>> mOpenvpnItems = new ArrayList<ListItemModel<?>>(6);
	private HeaderObjectAdapter<ListItemModel<?>, String> mAdapter;
	
	private DbOpenHelper mDbHelper;
	private String mPayloadUuid;
	private VpnData mData = null;
	
	
	public VpnPayloadFragment() {
		super();
		resetFields();
	}
	
	public VpnPayloadFragment(DbOpenHelper dbHelper) {
		this();
		mDbHelper = dbHelper;
	}

	private void resetFields() {
	}

	public void setPayloadUuid(String payloadUuid) {
		mPayloadUuid = payloadUuid;
	}
	
	public void createTabs(ActionBar actionBar) {
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		ActionBar.Tab tab = null;
		
		ActionBar.TabListener listener = new ActionBar.TabListener() {
			
			@Override
			public void onTabUnselected(Tab tab, FragmentTransaction ft) {
				//nothing to do here
			}
			
			@Override
			public void onTabSelected(Tab tab, FragmentTransaction ft) {
				VpnPayloadFragment.this.onTabSelected(tab.getTag());
			}
			
			@Override
			public void onTabReselected(Tab tab, FragmentTransaction ft) {
				//nothing to do here
			}
		};
		
		tab = actionBar.newTab();
		tab.setText(R.string.fragment_vpn_payload_l2tp_tab).setTabListener(listener);
		tab.setTag(VpnPayloadFragment.TAB_L2TP);
		actionBar.addTab(tab);
		
		tab = actionBar.newTab();
		tab.setText(R.string.fragment_vpn_payload_pptp_tab).setTabListener(listener);
		tab.setTag(VpnPayloadFragment.TAB_PPTP);
		actionBar.addTab(tab);
		
		tab = actionBar.newTab();
		tab.setText(R.string.fragment_vpn_payload_ipsec_tab).setTabListener(listener);
		tab.setTag(VpnPayloadFragment.TAB_IPSEC);
		actionBar.addTab(tab);
		
		tab = actionBar.newTab();
		tab.setText(R.string.fragment_vpn_payload_openvpn_tab).setTabListener(listener);
		tab.setTag(VpnPayloadFragment.TAB_OPENVPN);
		actionBar.addTab(tab);
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		if(mDbHelper == null) {
			mDbHelper = DbOpenHelper.getInstance(activity);
		}
		
		mAdapter = new HeaderObjectAdapter<ListItemModel<?>, String>(activity.getLayoutInflater(), 
				SINGLE_EMPTY_HEADER_LIST, 
				R.layout.simple_list_item_1_header, 
				mItems, 
				R.layout.payload_item,
				new ListItemMapper(activity));
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		View view = inflater.inflate(R.layout.fragment_vpn_payload, container, false);
		ListView list = (ListView) view.findViewById(android.R.id.list);
		if(list != null) {
			list.setAdapter(mAdapter);
		}
		
		return view;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		Bundle request = null;
		
		request = new Bundle();
		request.putInt(BaseQueryCursorLoader.STMT_TYPE, BaseQueryCursorLoader.STMT_SELECT);
		request.putString(BaseQueryCursorLoader.P_SELECT_BY, VpnDataCursorLoader.COL_PAYLOAD_UUID);
		request.putString(BaseQueryCursorLoader.P_SELECT_VALUE, mPayloadUuid);
		getLoaderManager().initLoader(mPayloadUuid.hashCode(), request, this);
	}
	
	@Override
	public void onDetach() {
		super.onDetach();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle params) {
		return new VpnDataCursorLoader(getActivity(), id, params, mDbHelper);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		showPayloadsDetails(data);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		// nothing to do here
	}
	
	private void onTabSelected(Object tabTag) {
		if(TAB_PPTP.equals(tabTag)) {
			mItems.clear();
			mItems.add(mPptpItems);
		} else if(TAB_L2TP.equals(tabTag)) {
			mItems.clear();
			mItems.add(mL2tpItems);
		} else if(TAB_IPSEC.equals(tabTag)) {
			mItems.clear();
			mItems.add(mIpsecItems);
		} else if(TAB_OPENVPN.equals(tabTag)) {
			mItems.clear();
			mItems.add(mOpenvpnItems);
		}
		mAdapter.notifyDataSetChanged();
	}
	
	private void showPayloadsDetails(Cursor data) {
		if(data != null && data.getCount() > 0) {
			data.moveToFirst();
			mData = new VpnData();
			mData.mapCursor(data);
			
			rebuildLists();
			mAdapter.notifyDataSetChanged();
		}
	}
	
	private void rebuildLists() {
		ListItemModel<VpnData> remote = new RemoteServerModel(mData); 
		ListItemModel<VpnData> login = new LoginModel(mData);
		ListItemModel<VpnData> password = new PasswordModel(mData);
		ListItemModel<VpnData> rsa = new RsaSecurIdModel(mData);
		ListItemModel<VpnData> psk = new SharedSecretModel(mData);
		ListItemModel<VpnData> override = new OverridePrimaryModel(mData);
		ListItemModel<VpnData> cert = new CertificateModel(mData);
		ListItemModel<VpnData> encryption = new PptpEncryptionModel(mData);
		ListItemModel<VpnData> group = new IpsecGroupNameModel(mData);
		
		mPptpItems.clear();
		mPptpItems.add(remote);
		mPptpItems.add(login);
		mPptpItems.add(rsa);
		mPptpItems.add(password);
		mPptpItems.add(encryption);
		mPptpItems.add(override);
		
		mL2tpItems.clear();
		mL2tpItems.add(remote);
		mL2tpItems.add(login);
		mL2tpItems.add(rsa);
		mL2tpItems.add(password);
		mL2tpItems.add(psk);
		mL2tpItems.add(override);
		
		mIpsecItems.clear();
		mIpsecItems.add(remote);
		mIpsecItems.add(login);
		mIpsecItems.add(password);
		mIpsecItems.add(cert);
		mIpsecItems.add(group);
		mIpsecItems.add(psk);
	}
}
