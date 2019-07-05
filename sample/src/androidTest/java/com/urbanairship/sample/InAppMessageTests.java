/* Copyright Airship and Contributors */

package com.urbanairship.sample;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;

import com.urbanairship.UAirship;
import com.urbanairship.automation.Triggers;
import com.urbanairship.iam.ButtonInfo;
import com.urbanairship.iam.DisplayContent;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;
import com.urbanairship.iam.InAppMessageListener;
import com.urbanairship.iam.InAppMessageSchedule;
import com.urbanairship.iam.InAppMessageScheduleInfo;
import com.urbanairship.iam.ResolutionInfo;
import com.urbanairship.iam.TextInfo;
import com.urbanairship.iam.banner.BannerAdapter;
import com.urbanairship.iam.banner.BannerDisplayContent;
import com.urbanairship.iam.banner.BannerView;
import com.urbanairship.iam.fullscreen.FullScreenDisplayContent;
import com.urbanairship.iam.modal.ModalDisplayContent;
import com.urbanairship.json.JsonValue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.idling.CountingIdlingResource;

import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class InAppMessageTests {

    static final String TEST_MESSAGE_ID = "TEST_MESSAGE_ID";

    @Rule
    public IntentsTestRule<MainActivity> activityRule = new IntentsTestRule<>(MainActivity.class);

    private CountingIdlingResource expectedDisplays;

    @Before
    public void setup() {
        expectedDisplays = new CountingIdlingResource("Expected IAA displays");
        UAirship.shared().getInAppMessagingManager().setAdapterFactory(InAppMessage.TYPE_BANNER, new InAppMessageAdapter.Factory() {
            @NonNull
            @Override
            public InAppMessageAdapter createAdapter(@NonNull InAppMessage message) {
                return new BannerAdapter(message, message.getDisplayContent()) {

                    @NonNull
                    @Override
                    protected BannerView onCreateView(@NonNull Activity activity, ViewGroup viewGroup) {
                        BannerView bannerView = super.onCreateView(activity, viewGroup);
                        bannerView.setAnimations(0, 0);
                        return bannerView;
                    }
                };
            }
        });

        IdlingRegistry.getInstance().register(expectedDisplays);
    }

    @After
    public void cleanup() {
        UAirship.shared().getInAppMessagingManager().cancelAll();
        IdlingRegistry.getInstance().unregister(expectedDisplays);
    }

    private Context getContext() {
        return activityRule.getActivity();
    }

    @Test
    public void testBanner() throws Exception {

        // Create the actions map
        Map<String, JsonValue> actions = new HashMap<>();
        actions.put("add_tags_action", JsonValue.wrap("clicked_banner"));

        // Create the banner in-app message
        BannerDisplayContent displayContent = BannerDisplayContent.newBuilder()
                                                                  .setHeading(TextInfo.newBuilder()
                                                                                      .setText("Banner Heading")
                                                                                      .addStyle(TextInfo.STYLE_BOLD)
                                                                                      .setColor(Color.BLUE)
                                                                                      .setDrawable(getContext(), R.drawable.ua_ic_add)
                                                                                      .build())
                                                                  .setBody(TextInfo.newBuilder()
                                                                                   .setText("Banner body text is so cool its italic")
                                                                                   .addStyle(TextInfo.STYLE_ITALIC)
                                                                                   .setColor(Color.GREEN)
                                                                                   .build())
                                                                  .setButtonLayout(DisplayContent.BUTTON_LAYOUT_JOINED)
                                                                  .addButton(ButtonInfo.newBuilder()
                                                                                       .setId("button-one")
                                                                                       .setBackgroundColor(Color.BLUE)
                                                                                       .setId("button id 1")
                                                                                       .setBorderRadius(10)
                                                                                       .setBorderColor(Color.MAGENTA)
                                                                                       .setLabel(TextInfo.newBuilder()
                                                                                                         .setText("Button One")
                                                                                                         .build())
                                                                                       .addAction("add_tags_action", JsonValue.wrap("button_one"))
                                                                                       .build())
                                                                  .addButton(ButtonInfo.newBuilder()
                                                                                       .setBackgroundColor(Color.GRAY)
                                                                                       .setBorderRadius(2)
                                                                                       .setId("button-two")
                                                                                       .setLabel(TextInfo.newBuilder()
                                                                                                         .setText("Button Two")
                                                                                                         .setAlignment(TextInfo.ALIGNMENT_RIGHT)
                                                                                                         .build())
                                                                                       .addAction("add_tags_action", JsonValue.wrap("button_two"))
                                                                                       .build())
                                                                  .setDuration(10, TimeUnit.SECONDS)
                                                                  .setDismissButtonColor(Color.RED)
                                                                  .setActions(actions)
                                                                  .build();

        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(displayContent)
                                           .setId(TEST_MESSAGE_ID)
                                           .build();

        displayMessage(message);
        onView(withText(containsString("Banner Heading"))).perform(click());

        displayMessage(message);
        onView(withText(containsString("Button One"))).perform(click());

        displayMessage(message);
        onView(withText(containsString("Button Two"))).perform(click());

        // Verify all the tags where added
        Set<String> tags = UAirship.shared().getPushManager().getTags();
        assertTrue(tags.containsAll(Arrays.asList("clicked_banner", "button_one", "button_two")));
    }

    /**
     * Test a fullscreen in-app message V2 with media image and five buttons.
     */
    @Test
    public void testFullScreen() throws ExecutionException, InterruptedException {

        // Create the fullscreen in-app message
        FullScreenDisplayContent displayContent = FullScreenDisplayContent.newBuilder()
                                                                          .setHeading(TextInfo.newBuilder()
                                                                                              .setText("Fullscreen heading is bold")
                                                                                              .addStyle(TextInfo.STYLE_BOLD)
                                                                                              .setColor(Color.BLUE)
                                                                                              .setDrawable(getContext(), R.drawable.ic_add)
                                                                                              .build())
                                                                          .setBody(TextInfo.newBuilder()
                                                                                           .setText("What's in a name? that which we call a rose By any other name would smell as sweet; So Romeo would, were he not Romeo call'd, Retain that dear perfection which he owes Without that title. Romeo, doff thy name, And for that name which is no part of thee Take all myself.")
                                                                                           .setAlignment(TextInfo.ALIGNMENT_CENTER)
                                                                                           .addStyle(TextInfo.STYLE_ITALIC)
                                                                                           .setColor(Color.GREEN)
                                                                                           .addFontFamily("cursive")
                                                                                           .setFontSize(10)
                                                                                           .build())
                                                                          .setFooter(ButtonInfo.newBuilder()
                                                                                               .setLabel(TextInfo.newBuilder()
                                                                                                                 .setText("Footer is lukewarm.")
                                                                                                                 .setColor(Color.RED)
                                                                                                                 .addStyle(TextInfo.STYLE_UNDERLINE)
                                                                                                                 .build())
                                                                                               .setId("footerId")
                                                                                               .build())
                                                                          .setButtonLayout(DisplayContent.BUTTON_LAYOUT_SEPARATE)
                                                                          .addButton(ButtonInfo.newBuilder()
                                                                                               .setId("button-one")
                                                                                               .setBackgroundColor(Color.BLUE)
                                                                                               .setBorderRadius(2)
                                                                                               .setBehavior(ButtonInfo.BEHAVIOR_DISMISS)
                                                                                               .setBorderRadius(10)
                                                                                               .setBorderColor(Color.MAGENTA)
                                                                                               .setLabel(TextInfo.newBuilder()
                                                                                                                 .setText("Button One!")
                                                                                                                 .build())
                                                                                               .addAction("add_tags_action", JsonValue.wrap("button_one!"))
                                                                                               .build())
                                                                          .addButton(ButtonInfo.newBuilder()
                                                                                               .setBackgroundColor(Color.GRAY)
                                                                                               .setBorderRadius(2)
                                                                                               .setId("button-two")
                                                                                               .setLabel(TextInfo.newBuilder()
                                                                                                                 .setText("Button Two!")
                                                                                                                 .setAlignment(TextInfo.ALIGNMENT_RIGHT)
                                                                                                                 .build())
                                                                                               .addAction("add_tags_action", JsonValue.wrap("button_two!"))
                                                                                               .build())
                                                                          .addButton(ButtonInfo.newBuilder()
                                                                                               .setId("button-three")
                                                                                               .setBackgroundColor(Color.BLUE)
                                                                                               .setBorderRadius(2)
                                                                                               .setBehavior(ButtonInfo.BEHAVIOR_DISMISS)
                                                                                               .setBorderRadius(10)
                                                                                               .setBorderColor(Color.MAGENTA)
                                                                                               .setLabel(TextInfo.newBuilder()
                                                                                                                 .setText("Button Three!")
                                                                                                                 .setAlignment(TextInfo.ALIGNMENT_CENTER)
                                                                                                                 .build())
                                                                                               .addAction("add_tags_action", JsonValue.wrap("button_three!"))
                                                                                               .build())
                                                                          .addButton(ButtonInfo.newBuilder()
                                                                                               .setBackgroundColor(Color.GRAY)
                                                                                               .setBorderRadius(2)
                                                                                               .setId("button-four")
                                                                                               .setLabel(TextInfo.newBuilder()
                                                                                                                 .setText("Button Four!")
                                                                                                                 .setAlignment(TextInfo.ALIGNMENT_CENTER)
                                                                                                                 .build())
                                                                                               .addAction("add_tags_action", JsonValue.wrap("button_four!"))
                                                                                               .build())
                                                                          .addButton(ButtonInfo.newBuilder()
                                                                                               .setBackgroundColor(Color.GRAY)
                                                                                               .setBorderRadius(2)
                                                                                               .setId("button-five")
                                                                                               .setLabel(TextInfo.newBuilder()
                                                                                                                 .setText("Button Five!")
                                                                                                                 .setAlignment(TextInfo.ALIGNMENT_CENTER)
                                                                                                                 .build())
                                                                                               .addAction("add_tags_action", JsonValue.wrap("button_five!"))
                                                                                               .build())
                                                                          .setDismissButtonColor(Color.GREEN)
                                                                          .setTemplate(FullScreenDisplayContent.TEMPLATE_MEDIA_HEADER_BODY)
                                                                          .setBackgroundColor(Color.YELLOW)
                                                                          .build();

        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(displayContent)
                                           .setId(TEST_MESSAGE_ID)
                                           .build();

        List<String> buttons = Arrays.asList("Button One!", "Button Two!", "Button Three!", "Button Four!", "Button Five!");
        for (String button : buttons) {
            displayMessage(message);

            onView(withText(containsString(button))).perform(ViewActions.scrollTo());
            onView(withText(containsString(button))).perform(click());
        }

        // Verify all the tags where added
        Set<String> tags = UAirship.shared().getPushManager().getTags();
        assertTrue(tags.containsAll(Arrays.asList("button_one!", "button_two!", "button_three!", "button_four!", "button_five!")));
    }

    /**
     * Test a modal in-app message V2 with two buttons.
     */
    @Test
    public void testModal() throws Exception {

        // Create the modal in-app message
        ModalDisplayContent displayContent = ModalDisplayContent.newBuilder()
                                                                .setHeading(TextInfo.newBuilder()
                                                                                    .setText("Blue Heading aligned left")
                                                                                    .addFontFamily("arizonia")
                                                                                    .setAlignment(TextInfo.ALIGNMENT_LEFT)
                                                                                    .setColor(Color.BLUE)
                                                                                    .setFontSize(15)
                                                                                    .setDrawable(R.drawable.ic_add)
                                                                                    .build())
                                                                .setBackgroundColor(Color.YELLOW)
                                                                .setBody(TextInfo.newBuilder()
                                                                                 .setText("What's in a name? that which we call a rose By any other name would smell as sweet; So Romeo would, were he not Romeo call'd, Retain that dear perfection which he owes Without that title. Romeo, doff thy name, And for that name which is no part of thee Take all myself.")
                                                                                 .setColor(Color.GREEN)
                                                                                 .addFontFamily("cursive")
                                                                                 .setFontSize(11)
                                                                                 .build())
                                                                .setBorderRadius(20)
                                                                .setDismissButtonColor(Color.MAGENTA)
                                                                .setFooter(ButtonInfo.newBuilder()
                                                                                     .setLabel(TextInfo.newBuilder()
                                                                                                       .setText("Footer is lukewarm.")
                                                                                                       .setColor(Color.RED)
                                                                                                       .addStyle(TextInfo.STYLE_UNDERLINE)
                                                                                                       .setAlignment(TextInfo.ALIGNMENT_RIGHT)
                                                                                                       .build())
                                                                                     .addAction("share_action", JsonValue.wrap("Sharing this awesome app!"))
                                                                                     .setId("footerId")
                                                                                     .build())
                                                                .setTemplate(ModalDisplayContent.TEMPLATE_HEADER_BODY_MEDIA)
                                                                .setButtonLayout(DisplayContent.BUTTON_LAYOUT_STACKED)
                                                                .addButton(ButtonInfo.newBuilder()
                                                                                     .setId("button-one")
                                                                                     .setBackgroundColor(Color.BLUE)
                                                                                     .setBorderRadius(2)
                                                                                     .setId("button id 1")
                                                                                     .setBehavior(ButtonInfo.BEHAVIOR_DISMISS)
                                                                                     .setBorderRadius(10)
                                                                                     .setBorderColor(Color.MAGENTA)
                                                                                     .setLabel(TextInfo.newBuilder()
                                                                                                       .setText("Modal One!")
                                                                                                       .setAlignment(TextInfo.ALIGNMENT_LEFT)
                                                                                                       .setColor(Color.WHITE)
                                                                                                       .build())
                                                                                     .addAction("add_tags_action", JsonValue.wrap("modal_one"))
                                                                                     .build())
                                                                .addButton(ButtonInfo.newBuilder()
                                                                                     .setBackgroundColor(Color.GRAY)
                                                                                     .setBorderRadius(2)
                                                                                     .setId("button-two")
                                                                                     .setLabel(TextInfo.newBuilder()
                                                                                                       .setText("Modal Two!")
                                                                                                       .setAlignment(TextInfo.ALIGNMENT_RIGHT)
                                                                                                       .setColor(Color.BLUE)
                                                                                                       .build())
                                                                                     .addAction("add_tags_action", JsonValue.wrap("modal_two"))
                                                                                     .build())
                                                                .build();

        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(displayContent)
                                           .setId(TEST_MESSAGE_ID)
                                           .build();

        // Schedule modal in-app message
        displayMessage(message);
        onView(withText(containsString("Modal One!"))).perform(click());

        // Schedule modal in-app message
        displayMessage(message);
        onView(withText(containsString("Modal Two!"))).perform(click());

        assertTrue(UAirship.shared().getPushManager().getTags().contains("modal_one"));
        assertTrue(UAirship.shared().getPushManager().getTags().contains("modal_two"));
    }


    /**
     * Displays a message.
     *
     * @param message The message.
     */
    private void displayMessage(InAppMessage message) throws ExecutionException, InterruptedException {
        expectedDisplays.increment();


        InAppMessageScheduleInfo scheduleInfo = InAppMessageScheduleInfo.newBuilder()
                                                                        .setMessage(message)
                                                                        .addTrigger(Triggers.newActiveSessionTriggerBuilder()
                                                                                            .setGoal(1)
                                                                                            .build())
                                                                        .build();

        final InAppMessageSchedule schedule = UAirship.shared().getInAppMessagingManager().scheduleMessage(scheduleInfo).get();

        UAirship.shared().getInAppMessagingManager().addListener(new InAppMessageListener() {
            @Override
            public void onMessageDisplayed(@NonNull String scheduleId, @NonNull InAppMessage message) {
                if (scheduleId.equals(schedule.getId())) {
                    expectedDisplays.decrement();
                    UAirship.shared().getInAppMessagingManager().removeListener(this);
                }
            }

            @Override
            public void onMessageFinished(@NonNull String scheduleId, @NonNull InAppMessage message, @NonNull ResolutionInfo resolutionInfo) {

            }
        });
    }
}
