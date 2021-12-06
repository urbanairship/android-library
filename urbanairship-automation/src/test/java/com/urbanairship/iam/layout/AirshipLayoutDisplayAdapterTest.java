package com.urbanairship.iam.layout;

import android.content.Context;

import com.urbanairship.android.layout.Thomas;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;
import com.urbanairship.iam.assets.Assets;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AirshipLayoutDisplayAdapter} tests.
 */
@RunWith(AndroidJUnit4.class)
public class AirshipLayoutDisplayAdapterTest {

    private AirshipLayoutDisplayAdapter adapter;
    private AirshipLayoutDisplayContent displayContent;

    private AirshipLayoutDisplayAdapter.PrepareDisplayCallback mockCallback = Mockito.mock(AirshipLayoutDisplayAdapter.PrepareDisplayCallback.class);
    private Context context = mock(Context.class);
    private Assets assets = mock(Assets.class);

    @Before
    public void setup() throws JsonException {
        String payloadString = "{\n" +
                "    \"layout\": {\n" +
                "        \"version\": 1,\n" +
                "        \"presentation\": {\n" +
                "          \"type\": \"modal\",\n" +
                "          \"default_placement\": {\n" +
                "            \"size\": {\n" +
                "              \"width\": \"100%\",\n" +
                "              \"height\": \"100%\"\n" +
                "            },\n" +
                "            \"position\": { \n" +
                "                \"horizontal\": \"center\",\n" +
                "                \"vertical\": \"center\" \n" +
                "            },\n" +
                "            \"shade_color\": {\n" +
                "              \"default\": { \n" +
                "                  \"type\": \"hex\", \n" +
                "                  \"hex\": \"#000000\", \n" +
                "                  \"alpha\": 0.2 }\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        \"view\": {\n" +
                "            \"type\": \"empty_view\"\n" +
                "        }\n" +
                "    }\n" +
                "}";

        JsonValue payload = JsonValue.parseString(payloadString);
        displayContent = AirshipLayoutDisplayContent.fromJson(payload);
        InAppMessage message = InAppMessage.newBuilder()
                                           .setDisplayContent(displayContent)
                                           .setName("layout name")
                                           .build();

        adapter = new AirshipLayoutDisplayAdapter(message, displayContent, mockCallback);
    }

    @Test
    public void testPrepareDisplayException() throws JsonException, Thomas.DisplayException {
        Mockito.doThrow(new Thomas.DisplayException("nope")).when(mockCallback).prepareDisplay(Mockito.any());
        assertEquals(InAppMessageAdapter.CANCEL, adapter.onPrepare(context, assets));
    }

    @Test
    public void testDisplay() throws Thomas.DisplayException {
        DisplayHandler displayHandler = mock(DisplayHandler.class);

        Thomas.PendingDisplay pendingDisplay = mock(Thomas.PendingDisplay.class);
        when(pendingDisplay.setEventListener(any())).thenReturn(pendingDisplay);
        when(mockCallback.prepareDisplay(displayContent.getPayload())).thenReturn(pendingDisplay);

        assertEquals(InAppMessageAdapter.OK, adapter.onPrepare(context, assets));
        adapter.onDisplay(context, displayHandler);

        verify(pendingDisplay).setEventListener(any());
        verify(pendingDisplay).display(context);
    }
}
