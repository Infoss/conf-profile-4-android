package no.infoss.confprofile.vpn.delegates;

import java.io.File;

import no.infoss.confprofile.BuildConfig;
import no.infoss.confprofile.util.MiscUtils;
import no.infoss.confprofile.util.PcapOutputStream;
import no.infoss.confprofile.vpn.VpnManagerService;
import no.infoss.confprofile.vpn.interfaces.Debuggable;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

public class DebugDelegate extends VpnManagerDelegate{
	public static final String TAG = DebugDelegate.class.getSimpleName();
	
	private static final String PREF_DEBUG_PCAP = "VpnManagerSevice_debugPcapEnabled";
	
	private boolean mDebugPcapEnabled = false;
	
	public DebugDelegate(VpnManagerService vpnMgr) {
		super(vpnMgr);
		
		if(BuildConfig.DEBUG) {
			SharedPreferences prefs = vpnMgr.getSharedPreferences(
					MiscUtils.PREFERENCE_FILE, 
					Context.MODE_PRIVATE);
		
		
			mDebugPcapEnabled = prefs.getBoolean(PREF_DEBUG_PCAP, false);
		}
	}
	
	public boolean isDebugPcapEnabled() {
		return mDebugPcapEnabled;
	}
	
	public boolean debugStartPcap(int mtu, 
			Debuggable routerLoop, 
			Debuggable usernatTunnel, 
			Debuggable currentTunnel) {
		
		if(BuildConfig.DEBUG) {
			if(!MiscUtils.isExternalStorageWriteable()) {
				mDebugPcapEnabled = false;
				storeDebugPcapEnabled(mDebugPcapEnabled);
				return false;
			}
			
			File externalFilesDir = getVpnManager().getExternalFilesDir(null);
			if(externalFilesDir == null) {
				//error: storage error
				mDebugPcapEnabled = false;
				storeDebugPcapEnabled(mDebugPcapEnabled);
				return false;
			}
			
			PcapOutputStream os = null;
			
			//capture from router loop
			if(routerLoop != null) {
				try {
					os = new PcapOutputStream(
							new File(externalFilesDir, routerLoop.generatePcapFilename()), 
							mtu, 
							PcapOutputStream.LINKTYPE_RAW);
					routerLoop.debugRestartPcap(os);
				} catch(Exception e) {
					Log.e(TAG, "Restart pcap error", e);
				}
			}
			
			//capture from tunnel
			if(currentTunnel != null) {
				debugStartTunnelPcap(mtu, currentTunnel);
			}
			
			if(usernatTunnel != null) {
				debugStartTunnelPcap(mtu, usernatTunnel);
			}
			
			mDebugPcapEnabled = true;
			storeDebugPcapEnabled(mDebugPcapEnabled);
			return true;
		}
		
		storeDebugPcapEnabled(false);
		return false;
	}
	
	public boolean debugStopPcap(Debuggable routerLoop, Debuggable usernatTunnel, Debuggable currentTunnel) {
		if(BuildConfig.DEBUG) {
			mDebugPcapEnabled = false;
			storeDebugPcapEnabled(mDebugPcapEnabled);
			
			if(routerLoop != null) {
				try {
					routerLoop.debugStopPcap();
				} catch(Exception e) {
					Log.e(TAG, "Stop pcap error", e);
				}
			}
			
			if(currentTunnel != null) {
				try {
					currentTunnel.debugStopPcap();
				} catch(Exception e) {
					Log.e(TAG, "Stop pcap error", e);
				}
			}
			
			if(usernatTunnel != null) {
				try {
					usernatTunnel.debugStopPcap();
				} catch(Exception e) {
					Log.e(TAG, "Stop pcap error", e);
				}
			}
			
			return true;
		}
		
		storeDebugPcapEnabled(false);
		return false;
	}
	
	public boolean debugStartTunnelPcap(int mtu, Debuggable tunnel) {
		if(BuildConfig.DEBUG) {
			if(tunnel == null) {
				return false;
			}
			
			if(!MiscUtils.isExternalStorageWriteable()) {
				return false;
			}
			
			File externalFilesDir = getVpnManager().getExternalFilesDir(null);
			if(externalFilesDir == null) {
				//error: storage error
				return false;
			}
			
			
			PcapOutputStream os = null;
			try {
				os = new PcapOutputStream(
						new File(externalFilesDir, tunnel.generatePcapFilename()), 
						mtu, 
						PcapOutputStream.LINKTYPE_RAW);
				tunnel.debugRestartPcap(os);
			} catch(Exception e) {
				Log.e(TAG, "Restart pcap error", e);
				if(os != null) {
					try {
						os.close();
					} catch(Exception ex) {
						//nothing to do here
					}
				}
				return false;
			}
			
			return true;
		}
		
		return false; //BuildConfig.DEBUG is false
	}
	
	private void storeDebugPcapEnabled(boolean enabled) {
		SharedPreferences prefs = getVpnManager().getSharedPreferences(
				MiscUtils.PREFERENCE_FILE, 
				Context.MODE_PRIVATE);
		Editor editor = prefs.edit();
		editor.putBoolean(PREF_DEBUG_PCAP, enabled);
		editor.commit();
	}
}
