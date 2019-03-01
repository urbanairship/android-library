/* Copyright Urban Airship and Contributors */

package com.urbanairship.sample.preference;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;

import com.urbanairship.UAirship;

import java.util.ArrayList;
import java.util.List;

public class TagsViewModel extends ViewModel {

    private MutableLiveData<List<String>> tagsLiveData = new MutableLiveData<>();
    private final List<String> tags;

    /**
     * Default constructor.
     */
    public TagsViewModel() {
        this.tags = new ArrayList<>(UAirship.shared().getPushManager().getTags());
        updateList();
    }

    /**
     * Gets the tags live data.
     *
     * @return The tags live data.
     */
    public LiveData<List<String>> getTags() {
        return tagsLiveData;
    }

    /**
     * Adds a channel tag to Urban Airship.
     *
     * @param tag The tag.
     */
    public void addTag(String tag) {
        UAirship.shared().getPushManager().editTags().addTag(tag).apply();
        tags.add(tag);
        updateList();
    }

    /**
     * Removes a channel tag from Urban Airship.
     *
     * @param tag The tag.
     */
    public void removeTag(String tag) {
        UAirship.shared().getPushManager().editTags().removeTag(tag).apply();
        tags.remove(tag);
        updateList();
    }

    private void updateList() {
        tagsLiveData.setValue(new ArrayList<>(tags));
    }

}
