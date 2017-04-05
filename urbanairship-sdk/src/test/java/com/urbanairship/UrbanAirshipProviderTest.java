/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;

public class UrbanAirshipProviderTest extends BaseTestCase {

    private ContentResolver resolver;
    private Uri preferenceUri;
    private Uri richPushUri;

    @Before
    public void setup() {
        resolver = RuntimeEnvironment.application.getContentResolver();
        preferenceUri = UrbanAirshipProvider.getPreferencesContentUri(TestApplication.getApplication());
        richPushUri = UrbanAirshipProvider.getRichPushContentUri(TestApplication.getApplication());
    }

    @Test
    @Config(shadows = { CustomShadowContentResolver.class })
    public void testGetType() {
        Uri messagesUri = this.richPushUri;
        assertEquals(UrbanAirshipProvider.RICH_PUSH_CONTENT_TYPE, this.resolver.getType(messagesUri));

        Uri messageUri = Uri.withAppendedPath(this.richPushUri, "this.should.work");
        assertEquals(UrbanAirshipProvider.RICH_PUSH_CONTENT_ITEM_TYPE, this.resolver.getType(messageUri));

        Uri failureUri = Uri.parse("content://com.urbanairship/garbage");
        assertNull(this.resolver.getType(failureUri));
    }


    @Test
    public void testInsertRow() {
        ContentValues values = new ContentValues();
        values.put(PreferencesDataManager.COLUMN_NAME_KEY, "key");
        values.put(PreferencesDataManager.COLUMN_NAME_VALUE, "value");

        Uri newUri = this.resolver.insert(this.preferenceUri, values);
        assertFalse(this.preferenceUri.equals(newUri));

        Cursor cursor = this.resolver.query(newUri, null, null, null, null);
        assertEquals(1, cursor.getCount());

        cursor.moveToFirst();

        assertEquals("key", cursor.getString(cursor.getColumnIndex(PreferencesDataManager.COLUMN_NAME_KEY)));
        assertEquals("value", cursor.getString(cursor.getColumnIndex(PreferencesDataManager.COLUMN_NAME_VALUE)));

        cursor.close();
    }

    @Test
    public void testReplaceRow() {
        ContentValues values = new ContentValues();
        values.put(PreferencesDataManager.COLUMN_NAME_KEY, "key");
        values.put(PreferencesDataManager.COLUMN_NAME_VALUE, "value");

        Uri newUri = this.resolver.insert(this.preferenceUri, values);

        values.put(PreferencesDataManager.COLUMN_NAME_VALUE, "new value");

        Uri replaceUri = this.resolver.insert(this.preferenceUri, values);
        assertEquals(newUri, replaceUri);

        Cursor cursor = this.resolver.query(replaceUri, null, null, null, null);
        assertEquals(1, cursor.getCount());

        cursor.moveToFirst();
        assertEquals("key", cursor.getString(cursor.getColumnIndex(PreferencesDataManager.COLUMN_NAME_KEY)));
        assertEquals("new value", cursor.getString(cursor.getColumnIndex(PreferencesDataManager.COLUMN_NAME_VALUE)));

        cursor.close();
    }


    @Test
    public void testUpdateAllData() {
        ContentValues values = new ContentValues();
        values.put(PreferencesDataManager.COLUMN_NAME_KEY, "key");
        values.put(PreferencesDataManager.COLUMN_NAME_VALUE, "value");
        resolver.insert(this.preferenceUri, values);

        ContentValues anotherValue = new ContentValues();
        anotherValue.put(PreferencesDataManager.COLUMN_NAME_KEY, "another key");
        anotherValue.put(PreferencesDataManager.COLUMN_NAME_VALUE, "another value");
        resolver.insert(this.preferenceUri, anotherValue);

        ContentValues updateValue = new ContentValues();
        updateValue.put(PreferencesDataManager.COLUMN_NAME_VALUE, "new value");

        int updated = this.resolver.update(this.preferenceUri, updateValue, null, null);
        assertEquals(2, updated);

        Cursor cursor = resolver.query(this.preferenceUri, null, null, null, null);
        assertEquals(2, cursor.getCount());

        cursor.moveToFirst();
        assertEquals("key", cursor.getString(cursor.getColumnIndex(PreferencesDataManager.COLUMN_NAME_KEY)));
        assertEquals("new value", cursor.getString(cursor.getColumnIndex(PreferencesDataManager.COLUMN_NAME_VALUE)));

        cursor.moveToLast();
        assertEquals("another key", cursor.getString(cursor.getColumnIndex(PreferencesDataManager.COLUMN_NAME_KEY)));
        assertEquals("new value", cursor.getString(cursor.getColumnIndex(PreferencesDataManager.COLUMN_NAME_VALUE)));

        cursor.close();
    }

    @Test
    public void testUpdateSomeData() {
        ContentValues values = new ContentValues();
        values.put(PreferencesDataManager.COLUMN_NAME_KEY, "key");
        values.put(PreferencesDataManager.COLUMN_NAME_VALUE, "value");
        resolver.insert(this.preferenceUri, values);

        ContentValues anotherValue = new ContentValues();
        anotherValue.put(PreferencesDataManager.COLUMN_NAME_KEY, "another key");
        anotherValue.put(PreferencesDataManager.COLUMN_NAME_VALUE, "another value");
        resolver.insert(this.preferenceUri, anotherValue);

        // Update the "another key" value
        ContentValues updateValue = new ContentValues();
        updateValue.put(PreferencesDataManager.COLUMN_NAME_VALUE, "new value");
        int updated = this.resolver.update(this.preferenceUri, updateValue,
                PreferencesDataManager.COLUMN_NAME_KEY + " IN (?)",
                new String[] { "another key" });

        assertEquals(1, updated);

        Cursor cursor = resolver.query(this.preferenceUri, null, null, null, null);
        assertEquals(2, cursor.getCount());

        cursor.moveToFirst();
        assertEquals("key", cursor.getString(cursor.getColumnIndex(PreferencesDataManager.COLUMN_NAME_KEY)));
        assertEquals("value", cursor.getString(cursor.getColumnIndex(PreferencesDataManager.COLUMN_NAME_VALUE)));

        cursor.moveToLast();
        assertEquals("another key", cursor.getString(cursor.getColumnIndex(PreferencesDataManager.COLUMN_NAME_KEY)));
        assertEquals("new value", cursor.getString(cursor.getColumnIndex(PreferencesDataManager.COLUMN_NAME_VALUE)));

        cursor.close();
    }

    @Test
    public void testDeleteAllData() {
        ContentValues values = new ContentValues();
        values.put(PreferencesDataManager.COLUMN_NAME_KEY, "key");
        values.put(PreferencesDataManager.COLUMN_NAME_VALUE, "value");
        resolver.insert(this.preferenceUri, values);

        ContentValues anotherValue = new ContentValues();
        anotherValue.put(PreferencesDataManager.COLUMN_NAME_KEY, "another key");
        anotherValue.put(PreferencesDataManager.COLUMN_NAME_VALUE, "another value");
        resolver.insert(this.preferenceUri, anotherValue);

        int deleted = this.resolver.delete(this.preferenceUri, null, null);
        assertEquals(2, deleted);
    }

    @Test
    public void testDeleteSomeData() {
        ContentValues values = new ContentValues();
        values.put(PreferencesDataManager.COLUMN_NAME_KEY, "key");
        values.put(PreferencesDataManager.COLUMN_NAME_VALUE, "value");
        resolver.insert(this.preferenceUri, values);

        ContentValues anotherValue = new ContentValues();
        anotherValue.put(PreferencesDataManager.COLUMN_NAME_KEY, "another key");
        anotherValue.put(PreferencesDataManager.COLUMN_NAME_VALUE, "another value");
        resolver.insert(this.preferenceUri, anotherValue);

        int deleted = this.resolver.delete(this.preferenceUri,
                PreferencesDataManager.COLUMN_NAME_KEY + " IN (?)",
                new String[] { "another key" });

        assertEquals(1, deleted);
    }

}
