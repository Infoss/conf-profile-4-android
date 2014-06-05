package no.infoss.confprofile.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.os.Build;
import android.util.Log;

public class MiscUtils {
	public static final String TAG = MiscUtils.class.getSimpleName();
	
	public static String genLibraryPath(Context ctx, ProcessBuilder pb) {	
		String[] paths = new String[] {
			ctx.getApplicationInfo().nativeLibraryDir, 
			pb.environment().get("LD_LIBRARY_PATH")
		};
	
		return StringUtils.join(paths, ":", true);
	}
	
	public static boolean writeExecutableToCache(Context context, String filename) {
		File dstFile = new File(context.getCacheDir(), filename);
		if(dstFile.exists() && dstFile.canExecute())
		return true;
	
		try {
			InputStream is = null;
			
			try {
				is = context.getAssets().open(filename.concat(".").concat(Build.CPU_ABI));
			} catch (IOException e) {
				Log.i(TAG, "Failed getting assets for archicture ".concat(Build.CPU_ABI), e);
				if(Build.CPU_ABI2 == null || Build.CPU_ABI2.isEmpty()) {
					throw e;
				}
				is = context.getAssets().open(filename.concat(".").concat(Build.CPU_ABI2));
			}
			
			FileOutputStream fos = new FileOutputStream(dstFile);
			try {
				byte buff[]= new byte[4096];
				int readbytes = 0; ;
				while((readbytes = is.read(buff)) != -1) {
					fos.write(buff, 0, readbytes);
					readbytes = is.read(buff);
				}
			} finally {
				try {
					fos.flush();
				} catch(Exception ex) {
					//ignore this
				}
				
				try {
					fos.close();
				} catch(Exception ex) {
					//ignore this
				}
			}
			
			if(!dstFile.setExecutable(true)) {
				Log.e(TAG, String.format("Failed to make ".concat(filename).concat(" executable")));
				return false;
			}
				
		} catch (IOException e) {
			Log.e(TAG, "Can't write executable to cache", e);
			return false;
		}
		return true;
	}
}
