package no.infoss.confprofile.util;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import no.infoss.confprofile.BuildConfig;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

public class MiscUtils {
	public static final String PREFERENCE_FILE = "confprofile.pref";
	public static final String TAG = MiscUtils.class.getSimpleName();
	public static final String HEX = "0123456789abcdef";
	
	private static Method M_FD_SET;
	private static Method M_FD_GET;
	
	static {
		try {
			M_FD_SET = FileDescriptor.class.getDeclaredMethod("setInt$", int.class);
		} catch (NoSuchMethodException e) {
			Log.e(TAG, "Can't find method FileDescriptor.setInt$()", e);
			M_FD_SET = null;
		}
		
		try {
			M_FD_GET = FileDescriptor.class.getDeclaredMethod("getInt$");
		} catch (NoSuchMethodException e) {
			Log.e(TAG, "Can't find method FileDescriptor.getInt$()", e);
			M_FD_GET = null;
		}
	}
	
	@Deprecated
	public static String genLibraryPath(Context ctx, ProcessBuilder pb) {	
		String[] paths = new String[] {
			ctx.getApplicationInfo().nativeLibraryDir, 
			pb.environment().get("LD_LIBRARY_PATH")
		};
	
		return StringUtils.join(paths, ":", true);
	}
	
	public static String genLibraryPath(Context ctx, String oldLibraryPath) {	
		String[] paths = new String[] {
			ctx.getApplicationInfo().nativeLibraryDir, 
			oldLibraryPath
		};
	
		return StringUtils.join(paths, ":", true);
	}
	
	/**
	 * Finds specified file in assets and writes it into application cache folder
	 * @param context
	 * @param filename name of asset file without ABI-specific extension
	 * @return destination file or null, if error occurred
	 */
	public static File writeExecutableToCache(Context context, String filename) {
		File dstFile = new File(context.getCacheDir(), filename);
		if(dstFile.exists() && dstFile.canExecute()) {
			if(!BuildConfig.DEBUG) {
				return dstFile;
			} else {
				dstFile.delete();
			}
		}
	
		try {
			InputStream is = null;
			
			try {
				is = context.getAssets().open(filename.concat(".").concat(Build.CPU_ABI));
			} catch (IOException e) {
				Log.i(TAG, "Failed getting assets for archicture ".concat(Build.CPU_ABI), e);
				
				try {
					is = context.getAssets().open(filename.concat(".").concat(Build.CPU_ABI2));
				} catch(Exception ex) {
					Log.i(TAG, "Failed getting assets for archicture ".concat(Build.CPU_ABI2), ex);
					is = context.getAssets().open(filename);
				}
			}
			
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(dstFile);
				byte buff[]= new byte[4096];
				int readbytes = 0;
				while((readbytes = is.read(buff)) != -1) {
					fos.write(buff, 0, readbytes);
					readbytes = is.read(buff);
				}
			} finally {
				if(fos != null) {
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
			}
			
			if(!dstFile.setExecutable(true, !BuildConfig.DEBUG)) {
				Log.e(TAG, String.format("Failed to make ".concat(filename).concat(" executable")));
				return null;
			}
				
		} catch (IOException e) {
			Log.e(TAG, "Can't write executable to cache", e);
			return null;
		}
		return dstFile;
	}
	
	public static boolean writeStringToFile(File dstFile, String str) {
		boolean isOk = false;
		FileOutputStream fos = null;
		
		try {
			fos = new FileOutputStream(dstFile);
			fos.write(str.getBytes("UTF-8"));
			isOk = true;
		} catch(Exception e) {
			Log.e(TAG, "Error while saving string to a file", e);
		} finally {
			if(fos != null) {
				try {
					fos.flush();
				} catch(Exception e) {
					Log.w(TAG, e);
				}
				
				try {
					fos.close();
				} catch(Exception e) {
					Log.w(TAG, e);
				}
			}
		}
		
		return isOk;
	}
	
	public static int hexToIntDigit(char ch) {
		int result = HEX.indexOf(ch, 0);
		if(result < 0) {
			throw new IllegalArgumentException("Invalid hex character: ".concat(String.valueOf(ch)));
		}
		return result;
	}
	
	public static boolean isExternalStorageWriteable() {
		String state = Environment.getExternalStorageState();
		if(!Environment.MEDIA_MOUNTED.equals(state)) {
			if(Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
				//error: ro
				return false;
			} else {
				//error: no storage
				return false;
			}
		}
		return true;
	}
	
	public static Process startProcess(Context ctx, List<String> args, Map<String, String> env) 
			throws IOException {
		ProcessBuilder pb = new ProcessBuilder(args);
		
		if(env != null) {
			if(env.containsKey("redirectErrorStream")) {
				pb.redirectErrorStream(Boolean.parseBoolean(env.get("redirectErrorStream")));
				env.remove("redirectErrorStream");
			}
			
			for(Entry<String, String> entry : env.entrySet()) {
				pb.environment().put(entry.getKey(), entry.getValue());
			}
		}
		
		String ldLibraryPath = genLibraryPath(ctx, pb.environment().get("LD_LIBRARY_PATH"));
		pb.environment().put("LD_LIBRARY_PATH", ldLibraryPath);
		
		return pb.start();
	}
	
	public static FileDescriptor intToFileDescriptor(int val) {
		FileDescriptor fd = new FileDescriptor();
		try {
			M_FD_SET.invoke(fd, val);
		} catch(Exception e) {
			Log.e(TAG, "Can't set fd to FileDescriptor instance", e);
			fd = null;
		}
		
		return fd;
	}
	
	public static int fileDescriptorToInt(FileDescriptor fd) {
		int val = -1;
		try {
			val = (Integer) M_FD_GET.invoke(fd);
		} catch(Exception e) {
			Log.e(TAG, "Can't get fd from FileDescriptor instance", e);
			val = -1;
		}
		
		return val;
	}
}
