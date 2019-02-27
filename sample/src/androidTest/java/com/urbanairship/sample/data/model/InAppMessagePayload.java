/* Copyright Urban Airship and Contributors */

package com.urbanairship.sample.data.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class InAppMessagePayload {

    @SerializedName("display_type")
    @Expose
    private String displayType;

    @SerializedName("interactive")
    @Expose
    private Interactive interactive;

    @SerializedName("alert")
    @Expose
    private String alert;

    @SerializedName("actions")
    @Expose
    private Actions actions;

    public String getDisplayType() {
        return displayType;
    }

    public void setDisplayType(String displayType) {
        this.displayType = displayType;
    }

    public Interactive getInteractive() {
        return interactive;
    }

    public void setInteractive(Interactive interactive) {
        this.interactive = interactive;
    }

    public String getAlert() {
        return alert;
    }

    public void setAlert(String alert) {
        this.alert = alert;
    }

    public Actions getActions() {
        return actions;
    }

    public void setActions(Actions actions) {
        this.actions = actions;
    }

}
