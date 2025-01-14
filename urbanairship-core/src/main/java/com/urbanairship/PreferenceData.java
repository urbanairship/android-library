/* Copyright Airship and Contributors */

package com.urbanairship;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/** @hide */
@Entity(tableName = "preferences")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PreferenceData {

    public PreferenceData(@NonNull String _id, String value) {
        this._id = _id;
        this.value = value;
    }

    @PrimaryKey
    @ColumnInfo(name = "_id")
    @NonNull
    protected String _id;

    @ColumnInfo(name = "value")
    protected String value;

    @Ignore
    public String getKey() {
        return _id;
    }

    @Ignore
    public String getValue() {
        return value;
    }

}
