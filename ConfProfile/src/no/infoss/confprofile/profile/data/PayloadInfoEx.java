package no.infoss.confprofile.profile.data;

import no.infoss.confprofile.format.ConfigurationProfile.Payload;

public class PayloadInfoEx extends PayloadInfo {
	public Payload payload;
	
	public PayloadInfoEx(PayloadInfo info) {
		this.profileId = info.profileId;
		this.payloadUuid = info.payloadUuid;
		this.payloadType = info.payloadType;
		this.data = info.data;
	}
}
