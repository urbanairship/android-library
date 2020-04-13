package com.urbanairship.accengage.common.persistence;

import android.content.Context;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.WorkerThread;

/**
 * Accengage Settings Loader.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AccengageSettingsLoader {

    /**
     * Loads settings from disk.
     *
     * @param context The context.
     * @param filename The filename.
     * @return The settings as a JsonMap.
     */
    @WorkerThread
    @NonNull
    public JsonMap load(@NonNull Context context, @NonNull String filename) {
        try {
            // read config file
            FileInputStream input = context.openFileInput(filename);

            if (input == null) {
                return JsonMap.EMPTY_MAP;
            }

            // WE READ JSON archive as UTF8!!!
            String readLine;
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(input, Charset.forName("UTF-8")));
            StringBuilder fileContent = new StringBuilder();
            while ((readLine = bufferedReader.readLine()) != null) {
                fileContent.append(readLine);
            }
            bufferedReader.close();
            return JsonValue.parseString(fileContent.toString()).optMap();

        } catch (FileNotFoundException e) {
            Logger.debug("JSONArchive - Unable to open file (reading) : %s", filename);
        } catch (IOException e) {
            Logger.debug(e, "JSONArchive - Error while closing file (reading) : %s", filename);
        } catch (JsonException e) {
            Logger.debug(e, "JSONArchive - Error while converting file to JSONObject (reading) : %s", filename);
        }

        return JsonMap.EMPTY_MAP;
    }

}
