package no.infoss.confprofile.task;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

public class BackupTask extends AsyncTask<Void, Void, Void> {
	public static final String TAG = BackupTask.class.getSimpleName();
	
	private Context mCtx;

	public BackupTask(Context ctx) {
		mCtx = ctx;
	}
	
	@Override
	protected Void doInBackground(Void... params) {
		String state = Environment.getExternalStorageState();
		if(!Environment.MEDIA_MOUNTED.equals(state)) {
			if(Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
				//error: ro
				return null;
			} else {
				//error: no storage
				return null;
			}
		}
		
		File externalFilesDir = mCtx.getExternalFilesDir(null);
		if(externalFilesDir == null) {
			//error: storage error
			return null;
		}
		
		String backupName = String.format("ocpa-backup-%d.zip", System.currentTimeMillis());
		ZipOutputStream zos = null;
		InputStream is = null;
		try {
			OutputStream os = new FileOutputStream(new File(externalFilesDir, backupName));
			zos = new ZipOutputStream(new BufferedOutputStream(os));
			
			ZipEntry entry;
			int readbytes;
			byte buff[] = new byte[4096];
			
			//saving database
			entry = new ZipEntry("profiles.sqlite");
	        zos.putNextEntry(entry);
	        
	        is = new FileInputStream(mCtx.getDatabasePath("profiles.sqlite"));
	        while((readbytes = is.read(buff)) != -1) {
	        	zos.write(buff, 0, readbytes);
	        }
	        
	        try {
	        	is.close();
	        } catch(Exception e) {
	        	Log.w(TAG, "Backup error while closing", e);
	        }
	        is = null;
	        
	        zos.closeEntry();
	         
			//saving keystore
	        entry = new ZipEntry("storage.jks");
	        zos.putNextEntry(entry);
	        
	        is = mCtx.openFileInput("storage.jks");
	        while((readbytes = is.read(buff)) != -1) {
	        	zos.write(buff, 0, readbytes);
	        }
	        
	        try {
	        	is.close();
	        } catch(Exception e) {
	        	Log.w(TAG, "Backup error while closing", e);
	        }
	        is = null;
	        
	        zos.closeEntry();
	        
			//saving preferences
	        entry = new ZipEntry("confprofile.pref");
	        zos.putNextEntry(entry);
	        
	        is = mCtx.openFileInput("confprofile.pref");
	        while((readbytes = is.read(buff)) != -1) {
	        	zos.write(buff, 0, readbytes);
	        }
	        
	        try {
	        	is.close();
	        } catch(Exception e) {
	        	Log.w(TAG, "Backup error while closing", e);
	        }
	        is = null;
	        
	        zos.closeEntry();
		} catch (Exception e) {
			Log.e(TAG, "Backup error", e);
			return null;
		} finally {
			if(zos != null) {
				try {
					zos.flush();
				} catch(Exception e) {
					Log.w(TAG, "Backup error while flushing", e);
				}
				
				try {
					zos.close();
				} catch(Exception e) {
					Log.w(TAG, "Backup error while closing", e);
				}
			}
			
			if(is != null) {
				try {
					is.close();
				} catch(Exception e) {
					Log.w(TAG, "Backup error while closing", e);
				}
			}
		}
		
		mCtx = null;
		return null;
	}

}
