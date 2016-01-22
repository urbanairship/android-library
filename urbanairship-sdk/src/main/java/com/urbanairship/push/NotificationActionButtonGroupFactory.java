/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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

import com.urbanairship.R;
import com.urbanairship.push.notifications.NotificationActionButton;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;

import java.util.HashMap;
import java.util.Map;

class NotificationActionButtonGroupFactory {

    static Map<String, NotificationActionButtonGroup> createUrbanAirshipGroups() {
        Map<String, NotificationActionButtonGroup> groups = new HashMap<>();

        groups.put("ua_yes_no_foreground", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("yes")
                        .setLabel(R.string.ua_notification_button_yes)
                        .setIcon(R.drawable.ua_ic_notification_button_accept)
                        .setPerformsInForeground(true)
                        .build())
                .addNotificationActionButton(new NotificationActionButton.Builder("no")
                        .setLabel(R.string.ua_notification_button_no)
                        .setIcon(R.drawable.ua_ic_notification_button_decline)
                        .setPerformsInForeground(false)
                        .build())
                .build());

        groups.put("ua_yes_no_background", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("yes")
                        .setLabel(R.string.ua_notification_button_yes)
                        .setIcon(R.drawable.ua_ic_notification_button_accept)
                        .setPerformsInForeground(false)
                        .build())
                .addNotificationActionButton(new NotificationActionButton.Builder("no")
                        .setLabel(R.string.ua_notification_button_no)
                        .setIcon(R.drawable.ua_ic_notification_button_decline)
                        .setPerformsInForeground(false)
                        .build())
                .build());

        groups.put("ua_accept_decline_foreground", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("accept")
                        .setLabel(R.string.ua_notification_button_accept)
                        .setIcon(R.drawable.ua_ic_notification_button_accept)
                        .setPerformsInForeground(true)
                        .build())
                .addNotificationActionButton(new NotificationActionButton.Builder("decline")
                        .setLabel(R.string.ua_notification_button_decline)
                        .setIcon(R.drawable.ua_ic_notification_button_decline)
                        .setPerformsInForeground(false)
                        .build())
                .build());

        groups.put("ua_accept_decline_background", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("accept")
                        .setLabel(R.string.ua_notification_button_accept)
                        .setIcon(R.drawable.ua_ic_notification_button_accept)
                        .setPerformsInForeground(false)
                        .build())
                .addNotificationActionButton(new NotificationActionButton.Builder("decline")
                        .setLabel(R.string.ua_notification_button_decline)
                        .setIcon(R.drawable.ua_ic_notification_button_decline)
                        .setPerformsInForeground(false)
                        .build())
                .build());

        groups.put("ua_download_share", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("download")
                        .setLabel(R.string.ua_notification_button_download)
                        .setIcon(R.drawable.ua_ic_notification_button_download)
                        .setPerformsInForeground(true)
                        .build())
                .addNotificationActionButton(new NotificationActionButton.Builder("share")
                        .setLabel(R.string.ua_notification_button_share)
                        .setIcon(R.drawable.ua_ic_notification_button_share)
                        .setPerformsInForeground(true)
                        .build())
                .build());

        groups.put("ua_remind_share", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("remind")
                        .setLabel(R.string.ua_notification_button_remind)
                        .setIcon(R.drawable.ua_ic_notification_button_remind)
                        .setPerformsInForeground(false)
                        .build())
                .addNotificationActionButton(new NotificationActionButton.Builder("share")
                        .setLabel(R.string.ua_notification_button_share)
                        .setIcon(R.drawable.ua_ic_notification_button_share)
                        .setPerformsInForeground(true)
                        .build())
                .build());

        groups.put("ua_opt_in_share", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("opt_in")
                        .setLabel(R.string.ua_notification_button_opt_in)
                        .setIcon(R.drawable.ua_ic_notification_button_follow)
                        .setPerformsInForeground(false)
                        .build())
                .addNotificationActionButton(new NotificationActionButton.Builder("share")
                        .setLabel(R.string.ua_notification_button_share)
                        .setIcon(R.drawable.ua_ic_notification_button_share)
                        .setPerformsInForeground(true)
                        .build())
                .build());

        groups.put("ua_opt_out_share", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("opt_out")
                        .setLabel(R.string.ua_notification_button_opt_out)
                        .setIcon(R.drawable.ua_ic_notification_button_unfollow)
                        .setPerformsInForeground(false)
                        .build())
                .addNotificationActionButton(new NotificationActionButton.Builder("share")
                        .setLabel(R.string.ua_notification_button_share)
                        .setIcon(R.drawable.ua_ic_notification_button_share)
                        .setPerformsInForeground(true)
                        .build())
                .build());

        groups.put("ua_follow_share", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("follow")
                        .setLabel(R.string.ua_notification_button_follow)
                        .setIcon(R.drawable.ua_ic_notification_button_follow)
                        .setPerformsInForeground(false)
                        .build())
                .addNotificationActionButton(new NotificationActionButton.Builder("share")
                        .setLabel(R.string.ua_notification_button_share)
                        .setIcon(R.drawable.ua_ic_notification_button_share)
                        .setPerformsInForeground(true)
                        .build())
                .build());

        groups.put("ua_unfollow_share", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("unfollow")
                        .setLabel(R.string.ua_notification_button_unfollow)
                        .setPerformsInForeground(false)
                        .setIcon(R.drawable.ua_ic_notification_button_unfollow)
                        .build())
                .addNotificationActionButton(new NotificationActionButton.Builder("share")
                        .setLabel(R.string.ua_notification_button_share)
                        .setIcon(R.drawable.ua_ic_notification_button_share)
                        .setPerformsInForeground(true)
                        .build())
                .build());

        groups.put("ua_shop_now_share", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("shop_now")
                        .setLabel(R.string.ua_notification_button_shop_now)
                        .setPerformsInForeground(true)
                        .setIcon(R.drawable.ua_ic_notification_button_cart)
                        .build())
                .addNotificationActionButton(new NotificationActionButton.Builder("share")
                        .setLabel(R.string.ua_notification_button_share)
                        .setIcon(R.drawable.ua_ic_notification_button_share)
                        .setPerformsInForeground(true)
                        .build())
                .build());

        groups.put("ua_buy_now_share", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("buy_now")
                        .setLabel(R.string.ua_notification_button_buy_now)
                        .setPerformsInForeground(true)
                        .setIcon(R.drawable.ua_ic_notification_button_cart)
                        .build())
                .addNotificationActionButton(new NotificationActionButton.Builder("share")
                        .setLabel(R.string.ua_notification_button_share)
                        .setIcon(R.drawable.ua_ic_notification_button_share)
                        .setPerformsInForeground(true)
                        .build())
                .build());

        groups.put("ua_more_like_less_like", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("more_like")
                        .setLabel(R.string.ua_notification_button_more_like)
                        .setIcon(R.drawable.ua_ic_notification_button_thumbs_up)
                        .setPerformsInForeground(false)
                        .build())
                .addNotificationActionButton(new NotificationActionButton.Builder("less_like")
                        .setLabel(R.string.ua_notification_button_less_like)
                        .setIcon(R.drawable.ua_ic_notification_button_thumbs_down)
                        .setPerformsInForeground(false)
                        .build())
                .build());

        groups.put("ua_like_dislike", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("like")
                        .setLabel(R.string.ua_notification_button_like)
                        .setIcon(R.drawable.ua_ic_notification_button_thumbs_up)
                        .setPerformsInForeground(false)
                        .build())
                .addNotificationActionButton(new NotificationActionButton.Builder("dislike")
                        .setLabel(R.string.ua_notification_button_dislike)
                        .setIcon(R.drawable.ua_ic_notification_button_thumbs_down)
                        .setPerformsInForeground(false)
                        .build())
                .build());

        groups.put("ua_like_share", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("like")
                        .setLabel(R.string.ua_notification_button_like)
                        .setIcon(R.drawable.ua_ic_notification_button_thumbs_up)
                        .setPerformsInForeground(false)
                        .build())
                .addNotificationActionButton(new NotificationActionButton.Builder("share")
                        .setLabel(R.string.ua_notification_button_share)
                        .setIcon(R.drawable.ua_ic_notification_button_share)
                        .setPerformsInForeground(true)
                        .build())
                .build());


        groups.put("ua_shop_now", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("shop_now")
                        .setLabel(R.string.ua_notification_button_shop_now)
                        .setIcon(R.drawable.ua_ic_notification_button_cart)
                        .setPerformsInForeground(true)
                        .build())
                .build());

        groups.put("ua_buy_now", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("buy_now")
                        .setLabel(R.string.ua_notification_button_buy_now)
                        .setIcon(R.drawable.ua_ic_notification_button_cart)
                        .setPerformsInForeground(true)
                        .build())
                .build());

        groups.put("ua_follow", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("follow")
                        .setLabel(R.string.ua_notification_button_follow)
                        .setIcon(R.drawable.ua_ic_notification_button_follow)
                        .setPerformsInForeground(false)
                        .build())
                .build());

        groups.put("ua_unfollow", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("unfollow")
                        .setLabel(R.string.ua_notification_button_unfollow)
                        .setIcon(R.drawable.ua_ic_notification_button_unfollow)
                        .setPerformsInForeground(false)
                        .build())
                .build());


        groups.put("ua_opt_in", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("opt_in")
                        .setLabel(R.string.ua_notification_button_opt_in)
                        .setIcon(R.drawable.ua_ic_notification_button_follow)
                        .setPerformsInForeground(false)
                        .build())
                .build());

        groups.put("ua_opt_out", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("opt_out")
                        .setLabel(R.string.ua_notification_button_opt_out)
                        .setIcon(R.drawable.ua_ic_notification_button_unfollow)
                        .setPerformsInForeground(false)
                        .build())
                .build());

        groups.put("ua_remind_me_later", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("remind")
                        .setLabel(R.string.ua_notification_button_remind)
                        .setIcon(R.drawable.ua_ic_notification_button_remind)
                        .setPerformsInForeground(false)
                        .build())
                .build());

        groups.put("ua_share", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("share")
                        .setLabel(R.string.ua_notification_button_share)
                        .setIcon(R.drawable.ua_ic_notification_button_share)
                        .setPerformsInForeground(true)
                        .build())
                .build());

        groups.put("ua_download", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("download")
                        .setLabel(R.string.ua_notification_button_download)
                        .setIcon(R.drawable.ua_ic_notification_button_download)
                        .setPerformsInForeground(true)
                        .build())
                .build());

        groups.put("ua_like", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("like")
                        .setLabel(R.string.ua_notification_button_like)
                        .setIcon(R.drawable.ua_ic_notification_button_thumbs_up)
                        .setPerformsInForeground(false)
                        .build())
                .build());

        groups.put("ua_icons_up_down", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("up")
                        .setIcon(R.drawable.ua_ic_notification_button_thumbs_up)
                        .setDescription("thumbs up icon")
                        .setPerformsInForeground(false)
                        .build())
                .addNotificationActionButton(new NotificationActionButton.Builder("down")
                        .setIcon(R.drawable.ua_ic_notification_button_thumbs_down)
                        .setDescription("thumbs down icon")
                        .setPerformsInForeground(false)
                        .build())
                .build());

        groups.put("ua_icons_happy_sad", new NotificationActionButtonGroup.Builder()
                .addNotificationActionButton(new NotificationActionButton.Builder("happy")
                        .setIcon(R.drawable.ua_ic_notification_button_happy)
                        .setDescription("happy icon")
                        .setPerformsInForeground(false)
                        .build())
                .addNotificationActionButton(new NotificationActionButton.Builder("sad")
                        .setIcon(R.drawable.ua_ic_notification_button_sad)
                        .setDescription("sad icon")
                        .setPerformsInForeground(false)
                        .build())
                .build());

        return groups;
    }
}
