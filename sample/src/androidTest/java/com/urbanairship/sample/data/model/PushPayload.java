/* Copyright Urban Airship and Contributors */

package com.urbanairship.sample.data.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;


public class PushPayload {

    @SerializedName("audience")
    @Expose
    private Audience audience;

    @SerializedName("device_types")
    @Expose
    private List<String> deviceTypes = null;

    @SerializedName("message")
    @Expose
    private Message message;

    @SerializedName("notification")
    @Expose
    private Notification notification;

    @SerializedName("in_app")
    @Expose
    private InAppMessagePayload inApp;

    @SerializedName("actions")
    @Expose
    private Actions actions;

    public Notification getNotification() {
        return notification;
    }

    public void setNotification(Notification notification) {
        this.notification = notification;
    }

    public Audience getAudience() {
        return audience;
    }

    public void setAudience(Audience audience) {
        this.audience = audience;
    }

    public List<String> getDeviceTypes() {
        return deviceTypes;
    }

    public void setDeviceTypes(List<String> deviceTypes) {
        this.deviceTypes = deviceTypes;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public Actions getActions() {
        return actions;
    }

    public void setActions(Actions actions) {
        this.actions = actions;
    }

    public InAppMessagePayload getInApp() {
        return inApp;
    }

    public void setInApp(InAppMessagePayload inAppMessagePayload) {
        this.inApp = inAppMessagePayload;
    }
}
