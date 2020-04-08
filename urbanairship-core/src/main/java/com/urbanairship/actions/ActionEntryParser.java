/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;

import com.urbanairship.Logger;
import com.urbanairship.util.UAStringUtil;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;

/**
 * Utility class to parse a list of {@link com.urbanairship.actions.ActionRegistry.Entry} from XML.
 *
 * The action entry format expected by this parser is given below. Both the class attribute and
 * at least one name are required. Predicate attributes are optional.
 *
 * <ActionEntry class="com.package.className" predicate="com.package.predicateClassName"
 * <name>name</name>
 * <name>alternateName</name>
 * </ActionEntry>
 */
class ActionEntryParser {

    private static final String ACTION_ENTRY_TAG = "ActionEntry";
    private static final String NAME_TAG = "name";

    private static final String CLASS_ATTRIBUTE = "class";
    private static final String PREDICATE_ATTRIBUTE = "predicate";

    /**
     * Generates a list of action entries from an xml resource file.
     *
     * @param context The application context.
     * @param resource The xml resource.
     * @return A list of action entries.
     */
    @NonNull
    public static List<ActionRegistry.Entry> fromXml(@NonNull Context context, @XmlRes int resource) {
        XmlResourceParser parser = context.getResources().getXml(resource);

        try {
            return parseEntries(parser);
        } catch (IOException | XmlPullParserException | Resources.NotFoundException | NullPointerException e) {
            // Note: NullPointerException can occur in rare circumstances further down the call stack
            Logger.error(e, "Failed to parse action entries.");
            return new ArrayList<>();
        } finally {
            parser.close();
        }
    }

    /**
     * Parses a list of action entries from an xml resource file.
     *
     * @param parser The xml parser.
     * @return A list of action entries.
     * @throws IOException if there is an IO failure getting the next element
     * @throws XmlPullParserException if there is an XML-related error getting the next element.
     */
    private static List<ActionRegistry.Entry> parseEntries(XmlResourceParser parser) throws IOException, XmlPullParserException {
        ArrayList<ActionRegistry.Entry> entries = new ArrayList<>();

        while (parser.next() != XmlPullParser.END_DOCUMENT) {

            int tagType = parser.getEventType();
            String tagName = parser.getName();

            // Start ActionEntry
            if (tagType == XmlPullParser.START_TAG && ACTION_ENTRY_TAG.equals(tagName)) {
                ActionRegistry.Entry entry = parseEntry(parser);

                if (entry != null) {
                    entries.add(entry);
                }
            }
        }

        return entries;
    }

    /**
     * Parses an action entry from an xml resource file.
     *
     * @param parser The xml parser.
     * @return The action entry, or null if one could not be found.
     * @throws IOException if there is an IO failure getting the next element
     * @throws XmlPullParserException if there is an XML-related error getting the next element.
     */
    @Nullable
    private static ActionRegistry.Entry parseEntry(XmlResourceParser parser) throws IOException, XmlPullParserException {
        String className = parser.getAttributeValue(null, CLASS_ATTRIBUTE);
        String predicateClassName = parser.getAttributeValue(null, PREDICATE_ATTRIBUTE);

        ArrayList<String> names = new ArrayList<>();

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            int tagType = parser.getEventType();
            String tagName = parser.getName();

            // Start name
            if (tagType == XmlPullParser.START_TAG && NAME_TAG.equals(tagName)) {
                String name = parseName(parser);

                if (name != null) {
                    names.add(name);
                }
            }

            // End ActionEntry
            if (tagType == XmlPullParser.END_TAG && ACTION_ENTRY_TAG.equals(tagName)) {
                break;
            }
        }

        if (names.isEmpty()) {
            Logger.error("Action names not found.");
            return null;
        }

        Class<? extends Action> clazz;
        try {
            clazz = Class.forName(className).asSubclass(Action.class);
        } catch (ClassNotFoundException e) {
            Logger.error("Action class %s not found.", className);
            return null;
        }

        ActionRegistry.Entry entry = new ActionRegistry.Entry(clazz, names);

        if (!UAStringUtil.isEmpty(predicateClassName)) {
            try {
                ActionRegistry.Predicate predicate = Class.forName(predicateClassName).asSubclass(ActionRegistry.Predicate.class).newInstance();
                entry.setPredicate(predicate);
            } catch (Exception e) {
                Logger.error("Predicate class %s not found. Skipping predicate.", predicateClassName);
            }
        }

        return entry;
    }

    /**
     * Parses an action name from an xml resource file.
     *
     * @param parser The xml parser.
     * @return The action name, or null if one could not be found.
     * @throws IOException if there is an IO failure getting the next element
     * @throws XmlPullParserException if there is an XML-related error getting the next element.
     */
    @Nullable
    private static String parseName(XmlResourceParser parser) throws IOException, XmlPullParserException {
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            int tagType = parser.getEventType();
            String tagName = parser.getName();

            // Name text
            if (tagType == XmlPullParser.TEXT) {
                return parser.getText();
            }

            // End Name
            if (tagType == XmlPullParser.END_TAG && NAME_TAG.equals(tagName)) {
                break;
            }
        }

        return null;
    }
}
