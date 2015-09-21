package com.urbanairship.richpush;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;

import com.urbanairship.BaseTestCase;
import com.urbanairship.RichPushTable;
import com.urbanairship.UrbanAirshipProvider;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.robolectric.RuntimeEnvironment;

public abstract class RichPushBaseTestCase extends BaseTestCase {

    public RichPushResolver richPushResolver;
    public ContentResolver resolver;
    public Application app;

    @Before
    public void setUp() {
        this.app = RuntimeEnvironment.application;
        this.resolver = app.getApplicationContext().getContentResolver();
        this.richPushResolver = new RichPushResolver(app.getApplicationContext());
    }

    protected void insertRichPushRows(int numberOfRows, JSONObject richPushMessageJson) {
        ContentValues[] values = new ContentValues[numberOfRows];
        for (int i = 0; i < numberOfRows; ++i) {
            values[i] = this.createRichPushContentValues(String.valueOf(i + 1), richPushMessageJson);
        }
        this.resolver.bulkInsert(UrbanAirshipProvider.getRichPushContentUri(), values);
    }


    protected ContentValues createRichPushContentValues(String idSuffix,
                                                        JSONObject richPushMessageJson) {
        ContentValues values = new ContentValues();
        try {
            values.put(RichPushTable.COLUMN_NAME_MESSAGE_ID, idSuffix + "_message_id");
            values.put(RichPushTable.COLUMN_NAME_TIMESTAMP, richPushMessageJson.getString("message_sent"));
            values.put(RichPushTable.COLUMN_NAME_TITLE, richPushMessageJson.getString("title"));
            values.put(RichPushTable.COLUMN_NAME_EXTRA, richPushMessageJson.getJSONObject("extra").toString());
            values.put(RichPushTable.COLUMN_NAME_UNREAD, richPushMessageJson.getBoolean("unread"));
            values.put(RichPushTable.COLUMN_NAME_UNREAD_ORIG, richPushMessageJson.getBoolean("unread"));
            values.put(RichPushTable.COLUMN_NAME_DELETED, false);
        } catch (JSONException e) {
            Assert.fail(e.getMessage());
        }
        return values;
    }

}
