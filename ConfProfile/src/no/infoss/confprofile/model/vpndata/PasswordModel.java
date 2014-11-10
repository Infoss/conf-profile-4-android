package no.infoss.confprofile.model.vpndata;

import no.infoss.confprofile.R;
import no.infoss.confprofile.model.common.SimpleListItemModel;
import no.infoss.confprofile.profile.data.VpnData;
import android.view.View;

public class PasswordModel extends SimpleListItemModel<VpnData> {
	
	public PasswordModel(VpnData data) {
		super();
		setData(data);
		
		setLayoutId(R.layout.simple_list_item_2);
		
		setMainText(R.string.fragment_vpn_payload_password_label);
		setStaticMainTextMode(true);
		
		setSubText("********");
		setStaticSubTextMode(true);
		
		setEnabled(false);
	}

	@Override
	protected void doApplyModel(VpnData data, View view) {
		super.doApplyModel(data, view);
	}
	
}
