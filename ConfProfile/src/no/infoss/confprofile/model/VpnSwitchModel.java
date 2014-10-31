package no.infoss.confprofile.model;

import no.infoss.confprofile.R;
import no.infoss.confprofile.model.common.CompositeListItemModel;
import no.infoss.confprofile.model.common.SwitchModel;
import no.infoss.confprofile.vpn.VpnManagerInterface;
import android.util.Log;
import android.view.View;

public class VpnSwitchModel extends CompositeListItemModel{
	private static final String TAG = VpnSwitchModel.class.getSimpleName();
	
	private SwitchModel mSwitchModel;
	
	public VpnSwitchModel() {
		mSwitchModel = new SwitchModel(R.id.switchWidget);
		addMapping(mSwitchModel);
		setLayoutId(R.layout.simple_list_item_2_switch);
		setRootViewId(R.id.simple_list_item_2_switch);
	}
	
	public void setVpnState(int state) {
		switch(state) {
		case VpnManagerInterface.SERVICE_STATE_STARTED: {
			setEnabled(true);
			setSubText(R.string.main_item_vpn_enabled_label);
			setSwitchEnabled(true);
			setSwitchVisible(View.VISIBLE);
			break;
		}
		case VpnManagerInterface.SERVICE_STATE_REVOKED: {
			setEnabled(true);
			setSubText(R.string.main_item_vpn_disabled_label);
			setSwitchChecked(false);
			setSwitchEnabled(true);
			setSwitchVisible(View.GONE);
			break;
		}
		case VpnManagerInterface.SERVICE_STATE_LOCKED: {
			setEnabled(false);
			setSubText(R.string.main_item_vpn_locked_label);
			setSwitchChecked(false);
			setSwitchEnabled(false);
			setSwitchVisible(View.GONE);
			break;
		}
		case VpnManagerInterface.SERVICE_STATE_UNSUPPORTED: {
			setEnabled(false);
			setSubText(R.string.main_item_vpn_unsupported_label);
			setSwitchChecked(false);
			setSwitchEnabled(false);
			setSwitchVisible(View.GONE);
			break;
		}
		default: {
			Log.e(TAG, "Received unexpected service state");
			break;
		}
		}
	}
	
	public void setTunnelState(int state) {
		switch(state) {
		case VpnManagerInterface.TUNNEL_STATE_TERMINATED:
		case VpnManagerInterface.TUNNEL_STATE_DISCONNECTED: {
			setSwitchChecked(false);
			break;
		}
		case VpnManagerInterface.TUNNEL_STATE_CONNECTING:
		case VpnManagerInterface.TUNNEL_STATE_CONNECTED: {
			setSwitchChecked(true);
			break;
		}
		case VpnManagerInterface.TUNNEL_STATE_DISCONNECTING: {
			//nothing to do here
			break;
		}
		default: {
			Log.e(TAG, "Received unexpected tunnel state");
			break;
		}
		}
	}
	
	public SwitchModel getSwitchModel() {
		return mSwitchModel;
	}
	
	public boolean isSwitchChecked() {
		return mSwitchModel.isChecked();
	}

	public void setSwitchChecked(boolean checked) {
		mSwitchModel.setChecked(checked);
	}
	
	public boolean isSwitchEnabled() {
		return mSwitchModel.isEnabled();
	}
	
	public void setSwitchEnabled(boolean enabled) {
		mSwitchModel.setEnabled(enabled);
	}
	
	public int getSwitchVisible() {
		return mSwitchModel.getVisible();
	}
	
	public void setSwitchVisible(int visible) {
		mSwitchModel.setVisible(visible);
	}
	
	public void setSwitchOnClickListener(OnClickListener listener) {
		mSwitchModel.setOnClickListener(listener);
	}
	
}
