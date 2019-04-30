/* Copyright Airship and Contributors */

package com.urbanairship.sample.data.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Audience {

    @SerializedName("android_channel")
    @Expose
    private String androidChannel;

    @SerializedName("named_user")
    @Expose
    private String namedUser;

    @SerializedName("tag")
    @Expose
    private String tag;

    public String getAndroidChannel() {
        return androidChannel;
    }

    public void setAndroidChannel(String androidChannel) {
        this.androidChannel = androidChannel;
    }

    public String getNamedUser() {
        return namedUser;
    }

    public void setNamedUser(String namedUser) {
        this.namedUser = namedUser;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

}
