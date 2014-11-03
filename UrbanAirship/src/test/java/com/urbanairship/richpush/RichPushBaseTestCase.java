package com.urbanairship.richpush;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;

import com.urbanairship.RichPushTable;
import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.UrbanAirshipProvider;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;

@RunWith(RobolectricGradleTestRunner.class)
public abstract class RichPushBaseTestCase {

    public RichPushResolver richPushResolver;
    public ContentResolver resolver;
    public Application app;

    @Before
    public void setUp() {
        this.app = Robolectric.application;
        this.resolver = app.getApplicationContext().getContentResolver();
        this.richPushResolver = new RichPushResolver(app.getApplicationContext());
    }

    protected RichPushMessage createRichPushMessage(JSONObject richPushMessageJson) {
        this.insertRichPushRows(1, richPushMessageJson);

        Cursor cursor = this.richPushResolver.getMessage("1_message_id");
        cursor.moveToFirst();

        RichPushMessage message = null;
        try {
            message = RichPushMessage.messageFromCursor(cursor);
        } catch (JSONException e) {
            Assert.fail(e.getMessage());
        } finally {
            cursor.close();
        }
        return message;
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
