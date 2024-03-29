package no.infoss.confprofile.model.common;

import android.view.View;
import android.widget.AdapterView;

public interface ListItemModel<T> extends Model<T> {
	
	public String getMainText();
	public void setMainText(String mainText);
	public void setMainText(int mainText);
	
	public String getSubText();
	public void setSubText(String subText);
	public void setSubText(int subText);
	
	public void preferOnClickListener(boolean preferOnClick);
	public void deliverItemClickAsClick(boolean deliverAsClick);
	public void setOnItemClickListener(OnItemClickListener listener);
	
	public interface OnItemClickListener {
		void onItemClick(Model<?> model, AdapterView<?> parent, View view, int position, long id);
	}
	
}
