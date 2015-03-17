package no.infoss.confprofile.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * 
 * @author Dmitry Vorobiev
 *
 */
public abstract class AndroidProperties {
	private static final AndroidProperties INSTANCE = new AOSPProperties();
	
	public static final String BOGUS_DNS_ADDR = "49.0.0.0";
	
	public static final String PROP_NET_DNS1 = "net.dns1";
	public static final String PROP_NET_DNS2 = "net.dns2";
	public static final String PROP_NET_DNS3 = "net.dns3";
	public static final String PROP_NET_DNS4 = "net.dns4";
	
	public static final String PROP_WIFI_DNS1 = "net.wlan0.dns1";
	public static final String PROP_WIFI_DNS2 = "net.wlan0.dns2";
	public static final String PROP_WIFI_DNS3 = "net.wlan0.dns3";
	public static final String PROP_WIFI_DNS4 = "net.wlan0.dns4";
	
	public static final String PROP_WIFI_DHCP_DNS1 = "dhcp.wlan0.dns1";
	public static final String PROP_WIFI_DHCP_DNS2 = "dhcp.wlan0.dns2";
	public static final String PROP_WIFI_DHCP_DNS3 = "dhcp.wlan0.dns3";
	public static final String PROP_WIFI_DHCP_DNS4 = "dhcp.wlan0.dns4";
	
	public static final String PROP_MOBILE_DNS1 = "net.rmnet0.dns1";
	public static final String PROP_MOBILE_DNS2 = "net.rmnet0.dns2";
	public static final String PROP_MOBILE_DNS3 = "net.rmnet0.dns3";
	public static final String PROP_MOBILE_DNS4 = "net.rmnet0.dns4";
	
	public static final AndroidProperties getInstance() {
		return INSTANCE;
	}
	
	public abstract String get(String key);
	
	public final String[] getNetworkSpecificDnsAddrs(Context ctx) {
		ArrayList<String> list = new ArrayList<String>(4);
		
		ConnectivityManager mgr = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo info = mgr.getActiveNetworkInfo();
		
		if(info != null) {
			String dnsProp = null;
			
			switch(info.getType()) {
			case ConnectivityManager.TYPE_WIFI:
			case ConnectivityManager.TYPE_WIMAX: {
				
				dnsProp = get(PROP_WIFI_DHCP_DNS1);
				if(dnsProp != null) {
					list.add(dnsProp);
				}
				
				dnsProp = get(PROP_WIFI_DHCP_DNS2);
				if(dnsProp != null) {
					list.add(dnsProp);
				}
				
				dnsProp = get(PROP_WIFI_DHCP_DNS3);
				if(dnsProp != null) {
					list.add(dnsProp);
				}
				
				dnsProp = get(PROP_WIFI_DHCP_DNS4);
				if(dnsProp != null) {
					list.add(dnsProp);
				}
				
				if(list.isEmpty()) {
					dnsProp = get(PROP_WIFI_DNS1);
					if(dnsProp != null) {
						list.add(dnsProp);
					}
					
					dnsProp = get(PROP_WIFI_DNS2);
					if(dnsProp != null) {
						list.add(dnsProp);
					}
					
					dnsProp = get(PROP_WIFI_DNS3);
					if(dnsProp != null) {
						list.add(dnsProp);
					}
					
					dnsProp = get(PROP_WIFI_DNS4);
					if(dnsProp != null) {
						list.add(dnsProp);
					}
				}
				
				break;
			}
			case ConnectivityManager.TYPE_MOBILE: {
				dnsProp = get(PROP_MOBILE_DNS1);
				if(dnsProp != null) {
					list.add(dnsProp);
				}
				
				dnsProp = get(PROP_MOBILE_DNS2);
				if(dnsProp != null) {
					list.add(dnsProp);
				}
				
				dnsProp = get(PROP_MOBILE_DNS3);
				if(dnsProp != null) {
					list.add(dnsProp);
				}
				
				dnsProp = get(PROP_MOBILE_DNS4);
				if(dnsProp != null) {
					list.add(dnsProp);
				}
			}
			default: {
				break;
			}
			}
		}
		
		return list.toArray(new String[0]);
	}
	
	private static class AOSPProperties extends AndroidProperties {
		
		private static Method METHOD;
		
		static {
			try {
				Class<?> clazz = Class.forName("android.os.SystemProperties");
				METHOD = clazz.getMethod("get", new Class<?>[] { String.class });
			} catch(Exception e) {
			}
		}
		
		private BogusProperties mFallback = new BogusProperties();
		
		@Override
		public String get(String key) {
			String result = null;
			
			if(METHOD == null) {
				result = mFallback.get(key);
			} else {
				try {
					result = (String) METHOD.invoke(null, key);
				} catch(Exception e) {
					//fallback to BogusProperties
					result = mFallback.get(key);
				}
			}
			
			return result;
		}
	}
	
	private static class BogusProperties extends AndroidProperties {
		private Map<String, String> mValues = new HashMap<String, String>();
		
		public BogusProperties() {
			mValues.put(PROP_WIFI_DHCP_DNS1, "8.8.8.8");
			mValues.put(PROP_WIFI_DHCP_DNS2, "8.8.4.4");
			mValues.put(PROP_MOBILE_DNS1, "8.8.8.8");
			mValues.put(PROP_MOBILE_DNS2, "8.8.4.4");
			mValues.put(PROP_WIFI_DNS1, "8.8.8.8");
			mValues.put(PROP_WIFI_DNS2, "8.8.4.4");
			mValues.put(PROP_NET_DNS1, "8.8.8.8");
			mValues.put(PROP_NET_DNS2, "8.8.4.4");
		}
		
		@Override
		public String get(String key) {
			if(key == null) {
				return null;
			}
			
			return mValues.get(key);
		}
	}
}
