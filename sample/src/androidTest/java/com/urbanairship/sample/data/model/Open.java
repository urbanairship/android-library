/* Copyright Airship and Contributors */

package com.urbanairship.sample.data.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Open {

    @SerializedName("type")
    @Expose
    private String type;

    @SerializedName("content")
    @Expose
    private Content content;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        this.content = content;
    }

}
