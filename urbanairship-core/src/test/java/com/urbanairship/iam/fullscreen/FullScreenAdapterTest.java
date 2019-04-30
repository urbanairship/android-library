/* Copyright Airship and Contributors */

package com.urbanairship.iam.fullscreen;

import android.content.Context;
import android.content.Intent;

import com.urbanairship.BaseTestCase;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.TextInfo;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link FullScreenAdapter} tests.
 */
public class FullScreenAdapterTest extends BaseTestCase {

    private FullScreenAdapter adapter;
    private InAppMessage message;
    private DisplayHandler displayHandler;

    @Before
    public void setup() {

        FullScreenDisplayContent displayContent = FullScreenDisplayContent.newBuilder()
                                                                          .setBody(TextInfo.newBuilder()
                                                                                           .setText("oh hi")
                                                                                           .build())
                                                                          .build();

        message = InAppMessage.newBuilder()
                              .setDisplayContent(displayContent)
                              .setId("id")
                              .build();

        displayHandler = new DisplayHandler("schedule ID");

        adapter = FullScreenAdapter.newAdapter(message);
    }

    @Test
    public void testDisplay() {
        Context context = mock(Context.class);
        adapter.onDisplay(context, displayHandler);

        verify(context).startActivity(Mockito.argThat(new ArgumentMatcher<Intent>() {
            @Override
            public boolean matches(Intent argument) {
                if (!displayHandler.equals(argument.getParcelableExtra(FullScreenActivity.DISPLAY_HANDLER_EXTRA_KEY))) {
                    return false;
                }

                if (!message.equals(argument.getParcelableExtra(FullScreenActivity.IN_APP_MESSAGE_KEY))) {
                    return false;
                }

                if (!argument.getComponent().getClassName().equals(FullScreenActivity.class.getName())) {
                    return false;
                }

                return true;
            }
        }));
    }

}