package no.infoss.confprofile.model;

import no.infoss.confprofile.R;
import no.infoss.confprofile.model.common.CompositeListItemModel;
import no.infoss.confprofile.model.common.ImageViewModel;
import no.infoss.confprofile.vpn.VpnManagerInterface;
import android.util.Log;
import android.view.View;

public class VpnStatusModel extends CompositeListItemModel<Void> {
	public static final String TAG = VpnStatusModel.class.getSimpleName();
	
	private ImageViewModel mImageModel;
	
	public VpnStatusModel() {
		mImageModel = new ImageViewModel(android.R.id.icon);
		mImageModel.setImageResourceId(R.drawable.arrow);
		addMapping(mImageModel);
		setLayoutId(R.layout.simple_list_item_2_image);
		setRootViewId(R.id.simple_list_item_2_image);
	}
	
	public void setTunnelState(int state) {
		switch(state) {
		case VpnManagerInterface.TUNNEL_STATE_TERMINATED:
		case VpnManagerInterface.TUNNEL_STATE_DISCONNECTED: {
			setEnabled(false);
			setSubText(R.string.main_item_status_disconnected_label);
			mImageModel.setVisible(View.INVISIBLE);
			break;
		}
		case VpnManagerInterface.TUNNEL_STATE_CONNECTING: {
			setEnabled(false);
			setSubText(R.string.main_item_status_connecting_label);
			mImageModel.setVisible(View.INVISIBLE);
			break;
		}
		case VpnManagerInterface.TUNNEL_STATE_CONNECTED: {
			setEnabled(true);
			setSubText(R.string.main_item_status_connected_label);
			mImageModel.setVisible(View.VISIBLE);
			break;
		}
		case VpnManagerInterface.TUNNEL_STATE_DISCONNECTING: {
			setEnabled(false);
			setSubText(R.string.main_item_status_disconnecting_label);
			mImageModel.setVisible(View.INVISIBLE);
			break;
		}
		default: {
			Log.e(TAG, "Received unexpected tunnel state");
			break;
		}
		}
	}
}
