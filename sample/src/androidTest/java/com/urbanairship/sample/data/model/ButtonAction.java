/* Copyright Urban Airship and Contributors */

package com.urbanairship.sample.data.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ButtonAction {

    @SerializedName("add_tag")
    @Expose
    private List<String> addTag = null;

    public List<String> getAddTag() {
        return addTag;
    }

    public void setAddTag(List<String> addTag) {
        this.addTag = addTag;
    }

}
