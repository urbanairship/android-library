/* Copyright Urban Airship and Contributors */

package com.urbanairship.sample.data.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;

public class Interactive {

    @SerializedName("button_actions")
    @Expose
    private Map<String, ButtonAction> buttonActions = new HashMap<>();

    @SerializedName("type")
    @Expose
    private String type;

    public Map<String, ButtonAction> getButtonActions() {
        return this.buttonActions;
    }

    public void setButtonActions(Map<String, ButtonAction> buttonActions) {
        this.buttonActions = buttonActions;
    }

    public void addButtonActions(String buttonId, ButtonAction buttonAction) {
        buttonActions.put(buttonId, buttonAction);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

}
