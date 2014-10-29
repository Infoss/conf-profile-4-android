package no.infoss.confprofile.vpn.delegates;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.app.NotificationCompat;
import no.infoss.confprofile.Main;
import no.infoss.confprofile.R;
import no.infoss.confprofile.vpn.VpnManagerService;

public class NotificationDelegate extends VpnManagerDelegate {
	
	private NotificationManager mNtfMgr;
	private int[] mVpnManagerIcons;
	private int[] mVpnManagerErrorIcons;

	public NotificationDelegate(VpnManagerService vpnMgr) {
		super(vpnMgr);
		
		mNtfMgr = (NotificationManager) vpnMgr.getSystemService(Context.NOTIFICATION_SERVICE);
		initIcons();
	}
	
	@Override
	protected void doReleaseResources() {
		super.doReleaseResources();
		
		cancelAllNotifications();
		mNtfMgr = null;
	}
	
	public void cancelNotification() {
		mNtfMgr.cancel(R.string.app_name);
	}
	
	public void cancelAllNotifications() {
		mNtfMgr.cancelAll();
	}
	
	public void notifyPreparing() {
		String title = getResources().getString(R.string.notification_title_preparing);
		String text = getResources().getString(R.string.notification_text_preparing);
		Notification notification = buildNotification(
				mVpnManagerIcons[0], 
				mVpnManagerIcons[0], 
				title, 
				text);
		mNtfMgr.notify(R.string.app_name, notification);
	}
	
	public void notifyRevoked() {
		String title = getResources().getString(R.string.notification_title_error_revoked);
		String text = getResources().getString(R.string.notification_text_error_revoked);
		Notification notification = buildNotification(
				mVpnManagerErrorIcons[2], 
				mVpnManagerErrorIcons[2], 
				title, 
				text);
		mNtfMgr.notify(R.string.app_name, notification);
	}
	
	public void notifyConnecting() {
		String title;
		String text;
		int smallIconId;
		int largeIconId;
		
		title = getResources().getString(R.string.notification_title_connecting);
		text = getResources().getString(R.string.notification_text_connecting);
		
		smallIconId = mVpnManagerIcons[1];
		largeIconId = mVpnManagerIcons[1];
		
		Notification notification = buildNotification(
				smallIconId, 
				largeIconId, 
				title, 
				text);
		mNtfMgr.notify(R.string.app_name, notification);
	}
	
	public void notifyConnected() {
		String title;
		String text;
		int smallIconId;
		int largeIconId;
		
		title = getResources().getString(R.string.notification_title_connected);
		text = getResources().getString(R.string.notification_text_connected);
		
		smallIconId = mVpnManagerIcons[3];
		largeIconId = mVpnManagerIcons[3];
		
		Notification notification = buildNotification(
				smallIconId, 
				largeIconId, 
				title, 
				text);
		mNtfMgr.notify(R.string.app_name, notification);
	}
	
	public void notifyDisconnected() {
		String title;
		String text;
		int smallIconId;
		int largeIconId;
		
		title = getResources().getString(R.string.notification_title_disconnected);
		text = getResources().getString(R.string.notification_text_disconnected);
		
		smallIconId = mVpnManagerIcons[4];
		largeIconId = mVpnManagerIcons[4];
		
		Notification notification = buildNotification(
				smallIconId, 
				largeIconId, 
				title, 
				text);
		mNtfMgr.notify(R.string.app_name, notification);
	}
	
	public void notifyLockedBySystem() {
		String title = getResources().getString(R.string.notification_title_error_always_on);
		String text = getResources().getString(R.string.notification_text_error_always_on);
		Notification notification = buildNotification(
				mVpnManagerErrorIcons[1], 
				mVpnManagerErrorIcons[1], 
				title, 
				text);
		mNtfMgr.notify(R.string.app_name, notification);
	}
	
	public void notifyUnsupported() {
		String title = getResources().getString(R.string.notification_title_error_unsupported);
		String text = getResources().getString(R.string.notification_text_error_unsupported);
		Notification notification = buildNotification(
				mVpnManagerErrorIcons[0], 
				mVpnManagerErrorIcons[0], 
				title, 
				text);
		mNtfMgr.notify(R.string.app_name, notification);
	}
	
	private Resources getResources() {
		return getVpnManager().getResources();
	}
	
	private void initIcons() {
		TypedArray a;
		a = getResources().obtainTypedArray(R.array.vpn_manager_icons);
		mVpnManagerIcons = new int[a.length()];
		for(int i = 0; i < a.length(); i++) {
			mVpnManagerIcons[i] = a.getResourceId(i, 0);
		}
		a.recycle();
		
		a = getResources().obtainTypedArray(R.array.vpn_manager_error_icons);
		mVpnManagerErrorIcons = new int[a.length()];
		for(int i = 0; i < a.length(); i++) {
			mVpnManagerErrorIcons[i] = a.getResourceId(i, 0);
		}
		a.recycle();
	}
	
	private Notification buildNotification(int smallIconId, int largeIconId, String title, String text) {
		Context appCtx = getVpnManager().getApplicationContext();
		NotificationCompat.Builder compatBuilder = new NotificationCompat.Builder(appCtx);
		if(smallIconId > 0) {
			compatBuilder.setSmallIcon(smallIconId);
		}
		
		if(largeIconId > 0) {
			Drawable d = getResources().getDrawable(largeIconId);
			if(d instanceof BitmapDrawable) {
				compatBuilder.setLargeIcon(((BitmapDrawable) d).getBitmap());
			}
		}
		
		compatBuilder.setContentTitle(title);
		compatBuilder.setContentText(text);
		compatBuilder.setOngoing(true);
		
		Intent intent = new Intent(appCtx, Main.class);
		intent.setAction(Intent.ACTION_MAIN);
		PendingIntent pendingIntent = PendingIntent.getActivity(appCtx, 0, intent, 0);
		compatBuilder.setContentIntent(pendingIntent);
		
		return compatBuilder.build();
	}

}
