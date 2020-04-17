/* Copyright Airship and Contributors */

package com.urbanairship.sample.preference;

import com.urbanairship.UAirship;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class TagsViewModel extends ViewModel {

    private MutableLiveData<List<String>> tagsLiveData = new MutableLiveData<>();
    private final List<String> tags;

    /**
     * Default constructor.
     */
    public TagsViewModel() {
        this.tags = new ArrayList<>(UAirship.shared().getChannel().getTags());
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
     * Adds a channel tag to Airship.
     *
     * @param tag The tag.
     */
    public void addTag(@NonNull String tag) {
        UAirship.shared().getChannel().editTags().addTag(tag).apply();
        tags.add(tag);
        updateList();
    }

    /**
     * Removes a channel tag from Airship.
     *
     * @param tag The tag.
     */
    public void removeTag(@NonNull String tag) {
        UAirship.shared().getChannel().editTags().removeTag(tag).apply();
        tags.remove(tag);
        updateList();
    }

    private void updateList() {
        tagsLiveData.setValue(new ArrayList<>(tags));
    }

}
