package no.infoss.confprofile.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import no.infoss.confprofile.BuildConfig;

public class PcapOutputStream extends BufferedOutputStream {
	public static final String TAG = PcapOutputStream.class.getSimpleName();
	
	public static final int LINKTYPE_RAW = 101; //raw IPv4 & IPv6 packets
	
	public static final int MAGIC = 0xa1b2c3d4;
	public static final short VER_MAJOR = 2;
	public static final short VER_MINOR = 4;
	
	public PcapOutputStream(String filename, int mtu, int linkType) throws IOException {
		this(new File(filename), mtu, linkType);
	}
	
	public PcapOutputStream(File file, int mtu, int linkType) throws IOException {
		super(new FileOutputStream(file));
		writePcapHeader(mtu, linkType);
	}
	
	@Override
	public synchronized void close() throws IOException {
		super.close();
	}
	
	public synchronized void writePacket(byte[] buff, int offs, int len) 
			throws IOException {
		writePacket(buff, offs, len, len);
	}
	
	public synchronized void writePacket(byte[] buff, int offs, int len, int origLen) 
			throws IOException {
		long ts = System.currentTimeMillis();
		int ts_sec = (int) (ts / 1000L);
		int ts_usec = (int) (ts % 1000L);
		writePacket(buff, offs, len, origLen, ts_sec, ts_usec);
	}
	
	public synchronized void writePacket(byte[] buff, int offs, int len, int origLen, int ts_sec, int ts_usec) 
			throws IOException {
		
		byte[] hdr = valuesAsByteArray(ts_sec, ts_usec, len, origLen);
		write(hdr);
		write(buff, offs, len);
		
		if(BuildConfig.DEBUG) {
			flush();
		}
	}
	
	private synchronized void writePcapHeader(int mtu, int linkType) throws IOException {
		byte[] hdr = valuesAsByteArray(MAGIC, VER_MAJOR, VER_MINOR, (Integer) 0, (Integer) 0, mtu, linkType);
		write(hdr);
	}
	
	private byte[] valuesAsByteArray(Object... values) {
		int size = 0;
		for(Object value : values) {
			if(value instanceof Long) {
				size += 8;
			} else if(value instanceof Integer) {
				size += 4;
			} else if(value instanceof Short) {
				size += 2;
			} else if(value instanceof Byte) {
				size++;
			} else if(value instanceof String) {
				try {
					size += ((String) value).getBytes("UTF-8").length;
				} catch (UnsupportedEncodingException e) {
					throw new IllegalArgumentException("String can't be encoded into UTF-8", e);
				}
			} else if(value instanceof byte[]) {
				size += ((byte[]) value).length;
			} else {
				throw new IllegalArgumentException("Type " + value.getClass().getName() + " doesn't supported");
			}
		}
		
		ByteBuffer buffer = ByteBuffer.allocate(size);
		for(Object value : values) {
			if(value instanceof Long) {
				buffer.putLong((Long) value);
			} else if(value instanceof Integer) {
				buffer.putInt((Integer) value);
			} else if(value instanceof Short) {
				buffer.putShort((Short) value);
			} else if(value instanceof Byte) {
				buffer.put((Byte) value);
			} else if(value instanceof String) {
				try {
					buffer.put(((String) value).getBytes("UTF-8"));
				} catch (UnsupportedEncodingException e) {
					throw new IllegalArgumentException("String can't be encoded into UTF-8", e);
				}
			} else if(value instanceof byte[]) {
				buffer.put((byte[]) value);
			}
		}
		
		return buffer.array();
	}
}
