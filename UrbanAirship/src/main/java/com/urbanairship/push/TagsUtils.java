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

import com.urbanairship.Logger;
import com.urbanairship.json.JsonValue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A class containing utility methods related to tags.
 */
class TagsUtils {

    private static final int MAX_TAG_LENGTH = 127;

    /**
     * Converts a JSONValue to a Tags Map
     */
    public static Map<String, Set<String>> convertToTagsMap(JsonValue jsonValue) {
        Map<String, Set<String>> tagGroups = new HashMap<>();

        if (jsonValue != null && jsonValue.isJsonMap()) {
            for (Map.Entry<String, JsonValue> groupEntry : jsonValue.getMap()) {
                Set<String> tags = new HashSet<>();
                for (JsonValue tag : groupEntry.getValue().getList()) {
                    if (tag.isString()) {
                        tags.add(tag.getString());
                    }
                }
                if (!tags.isEmpty()) {
                    tagGroups.put(groupEntry.getKey(), tags);
                }
            }
        }

        return tagGroups;
    }

    /**
     * Normalizes a set of tags. Each tag will be trimmed of white space and any tag that
     * is empty, null, or exceeds {@link #MAX_TAG_LENGTH} will be dropped.
     *
     * @param tags The set of tags to normalize.
     * @return The set of normalized, valid tags.
     */
    public static Set<String> normalizeTags(Set<String> tags) {
        if (tags == null) {
            return null;
        }

        Set<String> normalizedTags = new HashSet<>();

        for (String tag : tags) {
            if (tag == null) {
                Logger.debug("Null tag was removed from set.");
                continue;
            }

            tag = tag.trim();
            if (tag.length() <= 0 || tag.length() > MAX_TAG_LENGTH) {
                Logger.error("Tag with zero or greater than max length was removed from set: " + tag);
                continue;
            }

            normalizedTags.add(tag);
        }

        return normalizedTags;
    }
}
