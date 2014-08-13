package no.infoss.confprofile.model;

import android.view.View;
import android.widget.AdapterView;

public interface ListItemModel extends Model {
	
	public String getMainText();
	public void setMainText(String mainText);
	
	public String getSubText();
	public void setSubText(String subText);
	
	public void preferOnClickListener(boolean preferOnClick);
	public void deliverItemClickAsClick(boolean deliverAsClick);
	public void setOnItemClickListener(OnItemClickListener listener);
	
	public interface OnItemClickListener {
		void onItemClick(Model model, AdapterView<?> parent, View view, int position, long id);
	}
	
}
