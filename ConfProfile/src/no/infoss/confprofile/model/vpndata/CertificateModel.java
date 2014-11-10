package no.infoss.confprofile.model.vpndata;

import no.infoss.confprofile.R;
import no.infoss.confprofile.model.common.CompositeListItemModel;
import no.infoss.confprofile.model.common.SwitchModel;
import no.infoss.confprofile.profile.data.VpnData;
import android.view.View;

public class CertificateModel extends CompositeListItemModel<VpnData> {
	
	private SwitchModel mSwitchModel;
	
	public CertificateModel(VpnData data) {
		mSwitchModel = new SwitchModel(R.id.switchWidget);
		addMapping(mSwitchModel);
		setLayoutId(R.layout.simple_list_item_2_switch);
		setRootViewId(R.id.simple_list_item_2_switch);
		
		setMainText(R.string.fragment_vpn_payload_certificate_label);
		setStaticMainTextMode(true);
		
		setData(data);
		
		setEnabled(false);
		mSwitchModel.setEnabled(false);
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
	
	public void setSwitchOnClickListener(OnClickListener listener) {
		mSwitchModel.setOnClickListener(listener);
	}
	
	@Override
	protected void doApplyModel(VpnData data, View view) {
		mSwitchModel.setChecked(data.getCertificate() != null);
		
		super.doApplyModel(data, view);
	}
}
