/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

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

package com.urbanairship.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A class containing utility methods related to JSON data.
 */
public class JSONUtils {

    /**
     * Converts a JSONObject to a Map
     *
     * @param jsonObject JSONObject to convert
     * @return A map representing the JSONObject
     */
    public static Map<String, Object> convertToMap(JSONObject jsonObject) {
        Map<String, Object> map = new HashMap<String, Object>();

        if (jsonObject == null || jsonObject.length() == 0) {
            return map;
        }

        Iterator iterator = jsonObject.keys();
        while (iterator.hasNext()) {
            String key = (String) iterator.next();

            if (jsonObject.isNull(key)) {
                map.put(key, null);
                continue;
            }

            JSONArray array = jsonObject.optJSONArray(key);
            if (array != null) {
                map.put(key, convertToList(array));
                continue;
            }

            JSONObject childObject = jsonObject.optJSONObject(key);
            if (childObject != null) {
                map.put(key, convertToMap(childObject));
                continue;
            }

            map.put(key, jsonObject.opt(key));
        }

        return map;
    }

    /**
     * Converts a JSONArray to a List of objects
     *
     * @param jsonArray JSONArray to convert
     * @return A List of objects from the JSONArray
     */
    public static List<Object> convertToList(JSONArray jsonArray) {

        List<Object> objects = new ArrayList<Object>(jsonArray.length());

        for (int i = 0; i < jsonArray.length(); i++) {
            if (jsonArray.isNull(i)) {
                objects.add(null);
                continue;
            }

            JSONArray childArray = jsonArray.optJSONArray(i);
            if (childArray != null) {
                objects.add(convertToList(childArray));
                continue;
            }

            JSONObject childObject = jsonArray.optJSONObject(i);
            if (childObject != null) {
                objects.add(convertToMap(childObject));
                continue;
            }

            objects.add(jsonArray.opt(i));
        }

        return objects;
    }
}
