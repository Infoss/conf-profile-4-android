package no.infoss.confprofile.model.vpndata;

import no.infoss.confprofile.R;
import no.infoss.confprofile.model.common.SimpleListItemModel;
import no.infoss.confprofile.profile.data.VpnData;
import android.view.View;

public class SharedSecretModel extends SimpleListItemModel<VpnData> {
	
	public SharedSecretModel(VpnData data) {
		super();
		setData(data);
		
		setLayoutId(R.layout.simple_list_item_2);
		
		setEnabled(false);
	}

	@Override
	protected void doApplyModel(VpnData data, View view) {
		super.doApplyModel(data, view);
		
		setTextToView(view, getMainTextViewId(), R.string.fragment_vpn_payload_psk_label);
		if(data.getSharedSecret() == null) {
			setTextToView(view, getSubTextViewId(), "");
		} else {
			setTextToView(view, getSubTextViewId(), "********");
		}
	}
	
}
