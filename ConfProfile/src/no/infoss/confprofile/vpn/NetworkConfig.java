package no.infoss.confprofile.vpn;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class NetworkConfig {
	public static final String IF_UNSUPPORTED = "Unsupported";
	public static final String IF_ETHER = "Ethernet";
	public static final String IF_WIFI  = "WiFi";
	public static final String IF_CELL  = "Cellular";
	
	private boolean mIsStrict;
	private boolean mIsActive;
	private String mInterfaceType; //null == any
	private String mSsid; //null == any
	private List<DomainName> mDnsDomains;
	private List<DomainAddress> mDnsAddresses;
	
	public NetworkConfig() {
		setDnsDomains((List<String>) null);
		setDnsAddresses((List<String>) null);
		mIsStrict = false;
		mIsActive = false;
	}
	
	public NetworkConfig(boolean isStrict) {
		this();
		mIsStrict = isStrict;
	}
	
	public String getInterfaceType() {
		if(mIsStrict && mInterfaceType == null) {
			throw new IllegalStateException("Interface type should be defined in strict mode");
		}
		return mInterfaceType;
	}
	
	public void setInterfaceType(String type) {
		if(mIsStrict && type == null) {
			throw new IllegalArgumentException("Interface type can't be null in strict mode");
		}
		mInterfaceType = type;
	}
	
	public String getSsid() {
		return mSsid;
	}
	
	public void setSsid(String ssid) {
		mSsid = ssid;
	}
	
	public List<String> getDnsDomains() {
		return DomainName.unwrap(mDnsDomains);
	}
	
	public void setDnsDomains(List<String> domains) {
		if(domains == null) {
			domains = new LinkedList<String>();
		}
		
		List<DomainName> domainList = DomainName.wrap(domains);
		if(mIsStrict) {
			for(DomainName name : domainList) {
				if(!name.isStrict()) {
					throw new IllegalArgumentException("Dns domain can't contain a wildcard in strict mode");
				}
			}
		}
		
		mDnsDomains = Collections.unmodifiableList(domainList);
	}
	
	public void setDnsDomains(String[] domains) {
		if(domains == null) {
			setDnsDomains((List<String>) null);
		} else {
			setDnsDomains(Arrays.asList(domains));
		}
	}
	
	public List<String> getDnsAddresses() {
		return DomainAddress.unwrap(mDnsAddresses);
	}
	
	public void setDnsAddresses(List<String> addrs) {
		if(addrs == null) {
			addrs = new LinkedList<String>();
		}
		
		List<DomainAddress> addrList = DomainAddress.wrap(addrs);
		if(mIsStrict) {
			for(DomainAddress addr : addrList) {
				if(!addr.isStrict()) {
					throw new IllegalArgumentException("Dns address can't contain a wildcard in strict mode");
				}
			}
		}
		
		mDnsAddresses = Collections.unmodifiableList(addrList);
	}
	
	public void setDnsAddresses(String[] addrs) {
		if(addrs == null) {
			setDnsAddresses((List<String>)null);
		} else {
			setDnsAddresses(Arrays.asList(addrs));
		}
	}
	
	public boolean isActive() {
		return mIsActive;
	}
	
	public void setActive(boolean isActive) {
		mIsActive = false;
	}
	
	public boolean isStrict() {
		return mIsStrict;
	}
	
	public boolean match(NetworkConfig cfg) {
		boolean result = true;
		
		if(cfg == null) {
			return false;
		}
		
		//If both configurations aren't strict, it can't be checked for match
		if(!mIsStrict && !cfg.mIsStrict) {
			return false;
		}
		
		if(!mIsStrict) {
			return cfg.match(this);
		}
		
		//We're inside a strict config now and going on
		if(!mIsActive) {
			return false;
		}
		
		//Checking interface type
		if(cfg.getInterfaceType() != null && 
				!getInterfaceType().equals(cfg.getInterfaceType())) {
			return false;
		}
		
		//BREAKING NEWS: Interface types are equal or remote value is null		
		
		if(mSsid != null && 
				cfg.getSsid() != null && 
				!mSsid.equals(cfg.mSsid)) {
			return false;
		}
		
		//BREAKING NEWS: SSIDs are equal or one of them is null or both
		
		String resultSsid = mSsid;
		if(resultSsid == null && cfg.mSsid != null) {
			resultSsid = cfg.mSsid;
		}
		
		//Returning false if SSID is defined against non-WiFi interface type
		if(resultSsid != null && !IF_WIFI.equals(mInterfaceType)) {
			return false;
		}
		
		if(cfg.mDnsDomains.size() > 0) {
			result = false;
			for(DomainName name : mDnsDomains) {
				for(DomainName mask : cfg.mDnsDomains) {
					if(name.match(mask)) {
						result = true;
						break;
					}
				}
				
				if(result) {
					break;
				}
			}
			
			if(!result) {
				return false;
			}
		}
		
		if(cfg.mDnsAddresses.size() > 0) {
			result = false;
			for(DomainAddress addr : mDnsAddresses) {
				for(DomainAddress mask : cfg.mDnsAddresses) {
					if(addr.match(mask)) {
						result = true;
						break;
					}
				}
				
				if(result) {
					break;
				}
			}
			
			if(!result) {
				return false;
			}
		}
		
		return result;
	}
	
	public static class DomainName {
		boolean mHasPrefix = false;
		String mName;
		
		public DomainName(String domain) {
			if(domain == null) {
				domain = "";
			}
			
			int idx = 0;
			if(domain.startsWith("*")) {
				mHasPrefix = true;
				idx++;
			}
			
			mName = domain.substring(idx);
		}
		
		public boolean hasPrefix() {
			return mHasPrefix;
		}
		
		public String getName() {
			return mName;
		}
		
		public boolean isStrict() {
			return !mHasPrefix;
		}
		
		public boolean match(DomainName name) {
			if(name == null) {
				return false;
			}
			
			if(!isStrict() && !name.isStrict()) {
				return false;
			}
			
			if(!isStrict()) {
				return name.match(this);
			}
			
			String remoteName = name.getName();
			
			int pos = mName.lastIndexOf(remoteName);
			if(pos == -1) {
				return false;
			}
			
			if(pos < mName.length() - remoteName.length()) {
				return false;
			}
			
			if(remoteName.length() != mName.length() && name.isStrict()) {
				return false;
			}
			
			return true;
		}
		
		@Override
		public String toString() {
			String prefix = "";
			if(hasPrefix()) {
				prefix = prefix.concat("*");
			}
			
			return prefix.concat(mName);
		}
		
		public static List<DomainName> wrap(List<String> domains) {
			LinkedList<DomainName> domainList = new LinkedList<DomainName>();
			
			for(String domain : domains) {
				domainList.add(new DomainName(domain));
			}
			
			return domainList;
		}
		
		public static List<String> unwrap(List<DomainName> domains) {
			LinkedList<String> domainList = new LinkedList<String>();
			
			for(DomainName domain : domains) {
				domainList.add(domain.toString());
			}
			
			return domainList;
		}
	}
	
	public static class DomainAddress {
		boolean mHasSuffix = false;
		String mAddr;
		
		public DomainAddress(String domain) {
			if(domain == null) {
				domain = "";
			}
			
			int lastIdx = domain.length();
			if(domain.endsWith("*")) {
				mHasSuffix = true;
				lastIdx = domain.lastIndexOf("*");
			}
			
			mAddr = domain.substring(0, lastIdx);
		}
		
		public boolean hasSuffix() {
			return mHasSuffix;
		}
		
		public String getAddress() {
			return mAddr;
		}
		
		public boolean isStrict() {
			return !mHasSuffix;
		}
		
		public boolean match(DomainAddress addr) {
			if(addr == null) {
				return false;
			}
			
			if(!isStrict() && !addr.isStrict()) {
				return false;
			}
			
			if(!isStrict()) {
				return addr.match(this);
			}
			
			String remoteAddress = addr.getAddress();
			
			int pos = mAddr.indexOf(remoteAddress);
			if(pos != 0) {
				return false;
			}
			
			if(remoteAddress.length() != mAddr.length() && addr.isStrict()) {
				return false;
			}
			
			return true;
		}
		
		@Override
		public String toString() {
			String result = mAddr;
			if(hasSuffix()) {
				result = result.concat("*");
			}
			
			return result;
		}
		
		public static List<DomainAddress> wrap(List<String> addrs) {
			LinkedList<DomainAddress> addrList = new LinkedList<DomainAddress>();
			
			for(String addr : addrs) {
				addrList.add(new DomainAddress(addr));
			}
			
			return addrList;
		}
		
		public static List<String> unwrap(List<DomainAddress> addrs) {
			LinkedList<String> addrList = new LinkedList<String>();
			
			for(DomainAddress addr : addrs) {
				addrList.add(addr.toString());
			}
			
			return addrList;
		}
	}
}