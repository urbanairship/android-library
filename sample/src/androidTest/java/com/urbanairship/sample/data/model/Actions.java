/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.sample.data.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Actions {

    @SerializedName("add_tag")
    @Expose
    private String addTag;

    @SerializedName("open")
    @Expose
    private Open open;

    public String getAddTag() {
        return addTag;
    }

    public void setAddTag(String addTag) {
        this.addTag = addTag;
    }

    public Open getOpen() {
        return open;
    }

    public void setOpen(Open open) {
        this.open = open;
    }

}
