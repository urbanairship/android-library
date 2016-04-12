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

package com.urbanairship;

/**
 * Config parser interface.
 */
interface ConfigParser {

    /**
     * Count of config elements.
     *
     * @return Count of config elements.
     */
    int getCount();

    /**
     * Gets the name of the config element.
     *
     * @param index The index of the config element.
     * @return The name of the config element at the given index.
     */
    String getName(int index);

    /**
     * Gets the string value of the config element.
     *
     * @param index The index of the config element.
     * @return The string value of the config element at the given index.
     */
    String getString(int index);

    /**
     * Gets the boolean value of the config element.
     *
     * @param index The index of the config element.
     * @return The boolean value of the config element at the given index.
     */
    boolean getBoolean(int index);

    /**
     * Gets the string array value of the config element.
     *
     * @param index The index of the config element.
     * @return The string array value of the config element at the given index.
     */
    String[] getStringArray(int index);

    /**
     * Gets the resource ID of the config element.
     *
     * @param index The index of the config element.
     * @return The resource ID value of the config element at the given index.
     */
    int getDrawableResourceId(int index);

    /**
     * Gets the color value of the config element.
     *
     * @param index The index of the config element.
     * @return The color value of the config element at the given index.
     */
    int getColor(int index);

    /**
     * Gets the long value of the config element.
     *
     * @param index The index of the config element.
     * @return The long value of the config element at the given index.
     */
    long getLong(int index);
}