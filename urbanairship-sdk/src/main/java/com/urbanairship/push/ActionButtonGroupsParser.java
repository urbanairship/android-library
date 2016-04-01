/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.push;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.support.annotation.XmlRes;
import android.util.AttributeSet;
import android.util.Xml;

import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.push.notifications.NotificationActionButton;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;
import com.urbanairship.util.UAStringUtil;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to parse {@link NotificationActionButtonGroup}.
 */
class ActionButtonGroupsParser {

    private static final String BUTTON_GROUP_TAG = "UrbanAirshipActionButtonGroup";
    private static final String BUTTON_TAG = "UrbanAirshipActionButton";

    private static final String ID_ATTRIBUTE = "id";
    private static final String DESCRIPTION_ATTRIBUTE = "description";
    private static final String FOREGROUND_ATTRIBUTE = "foreground";

    /**
     * Generates a map of NotificationActionButtonGroups from an xml resource file.
     *
     * @param context The application context.
     * @param resource The xml resource.
     * @return A map of NotificationActionButtonGroups.
     */
    public static Map<String, NotificationActionButtonGroup> fromXml(Context context, @XmlRes int resource) {

        XmlResourceParser parser;
        try {
            parser = context.getResources().getXml(resource);
            return parseGroups(context, parser);
        } catch (IOException | XmlPullParserException | Resources.NotFoundException e) {
            Logger.error("Failed to parse NotificationActionButtonGroups:" + e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Parses NotificationActionButtonGroups from xml.
     *
     * @param context The context.
     * @param parser The xml parser.
     * @return A map of NotificationActionButtonGroups.
     * @throws IOException
     * @throws XmlPullParserException
     */
    private static Map<String, NotificationActionButtonGroup> parseGroups(Context context, XmlResourceParser parser) throws IOException, XmlPullParserException {
        Map<String, NotificationActionButtonGroup> groups = new HashMap<>();

        String groupId = null;
        NotificationActionButtonGroup.Builder groupBuilder = null;

        while (parser.next() != XmlPullParser.END_DOCUMENT) {

            int tagType = parser.getEventType();
            String tagName = parser.getName();

            // Start group
            if (tagType == XmlPullParser.START_TAG && BUTTON_GROUP_TAG.equals(tagName)) {
                String id = parser.getAttributeValue(null, ID_ATTRIBUTE);
                if (UAStringUtil.isEmpty(id)) {
                    Logger.error(BUTTON_GROUP_TAG + " missing id.");
                    continue;
                }

                groupId = id;
                groupBuilder = new NotificationActionButtonGroup.Builder();

                continue;
            }

            if (UAStringUtil.isEmpty(groupId)) {
                continue;
            }

            // Inner Buttons
            if (tagType == XmlPullParser.START_TAG && BUTTON_TAG.equals(tagName)) {
                String buttonId = parser.getAttributeValue(null, ID_ATTRIBUTE);
                if (UAStringUtil.isEmpty(buttonId)) {
                    Logger.error(BUTTON_TAG + " missing id.");
                    continue;
                }

                AttributeSet attributeSet = Xml.asAttributeSet(parser);
                TypedArray typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.UrbanAirshipActionButton);

                NotificationActionButton button = new NotificationActionButton.Builder(buttonId)
                        .setPerformsInForeground(parser.getAttributeBooleanValue(null, FOREGROUND_ATTRIBUTE, true))
                        .setIcon(typedArray.getResourceId(R.styleable.UrbanAirshipActionButton_android_icon, 0))
                        .setLabel(typedArray.getResourceId(R.styleable.UrbanAirshipActionButton_android_label, 0))
                        .setDescription(parser.getAttributeValue(null, DESCRIPTION_ATTRIBUTE))
                        .build();

                groupBuilder.addNotificationActionButton(button);

                typedArray.recycle();

                continue;
            }

            // End Group
            if (tagType == XmlPullParser.END_TAG && BUTTON_GROUP_TAG.equals(tagName)) {
                NotificationActionButtonGroup group = groupBuilder.build();
                if (group.getNotificationActionButtons().isEmpty()) {
                    Logger.error(BUTTON_GROUP_TAG + " " + groupId + " missing action buttons.");
                    continue;
                }

                groups.put(groupId, group);
            }
        }
        return groups;
    }

}
