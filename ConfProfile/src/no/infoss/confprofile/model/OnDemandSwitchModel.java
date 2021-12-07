package no.infoss.confprofile.model;

import no.infoss.confprofile.R;
import no.infoss.confprofile.model.common.CompositeListItemModel;
import no.infoss.confprofile.model.common.SwitchModel;

public class OnDemandSwitchModel extends CompositeListItemModel<Void> {
	
	private SwitchModel mSwitchModel;
	
	public OnDemandSwitchModel() {
		mSwitchModel = new SwitchModel(R.id.switchWidget);
		addMapping(mSwitchModel);
		setLayoutId(R.layout.simple_list_item_2_switch);
		setRootViewId(R.id.simple_list_item_2_switch);
		
		setMainText(R.string.main_vpn_item_on_demand_label);
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
}
