package no.infoss.confprofile.entity;

import no.infoss.confprofile.format.ConfigurationProfile.Payload;
import android.content.ContentValues;
import android.database.Cursor;

public abstract class AppEntity {
	public abstract void mapCursor(Cursor cursor);
	public abstract void mapPayload(Payload payload);
	public abstract ContentValues asContentValues();
}
