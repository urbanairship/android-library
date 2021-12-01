package com.urbanairship.android.layout;

import com.urbanairship.android.layout.model.ContainerLayoutModel;
import com.urbanairship.android.layout.property.HorizontalPosition;
import com.urbanairship.android.layout.property.Position;
import com.urbanairship.android.layout.property.VerticalPosition;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(RobolectricTestRunner.class)
public class ThomasTest {

    @Test
    public void parsesSampleModal() throws JsonException {
        JsonMap json = readJsonMapResource("modal.json");
        BasePayload payload = BasePayload.fromJson(json);
        assertNotNull(payload);

        ModalPresentation presentation = (ModalPresentation) payload.getPresentation();
        assertNotNull(presentation);

        Position position = presentation.getDefaultPlacement().getPosition();
        assertNotNull(position);
        assertEquals(HorizontalPosition.CENTER, position.getHorizontal());
        assertEquals(VerticalPosition.CENTER, position.getVertical());

        ContainerLayoutModel view = (ContainerLayoutModel) payload.getView();
        assertNotNull(view);
        assertTrue(view.getItems().size() > 0);

        for(ContainerLayoutModel.Item item : view.getItems()) {
            assertNotSame(item.getView().getType(), ViewType.UNKNOWN);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private JsonMap readJsonMapResource(String path) {
        ClassLoader classLoader = getClass().getClassLoader();
        assert classLoader != null;

        String json = "";
        try(BufferedReader br = new BufferedReader(new InputStreamReader(classLoader.getResourceAsStream(path)))) {
            StringBuilder builder = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                builder.append(line);
                line = br.readLine();
            }
            json = builder.toString();
        } catch (IOException e) {
            e.printStackTrace();
            fail("Failed to read json from file: " + path);
        }
        try {
            return JsonValue.parseString(json).optMap();
        } catch (JsonException e) {
            fail("Unable to parse json from file: " + path);
        }
        return JsonMap.EMPTY_MAP;
    }
}
