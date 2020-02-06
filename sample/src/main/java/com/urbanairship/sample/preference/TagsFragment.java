/* Copyright Airship and Contributors */

package com.urbanairship.sample.preference;

import androidx.lifecycle.ViewModelProviders;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.ItemTouchHelper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.urbanairship.sample.R;
import com.urbanairship.sample.databinding.FragmentTagsBinding;
import com.urbanairship.util.UAStringUtil;

import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

/**
 * Fragment that manages Airship tags.
 */
public class TagsFragment extends Fragment {

    private TagsViewModel viewModel;
    private ImageButton addTagButton;
    private EditText addTagEditText;
    private RecyclerView recyclerView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        viewModel = ViewModelProviders.of(this).get(TagsViewModel.class);
        FragmentTagsBinding binding = FragmentTagsBinding.inflate(inflater, container, false);
        binding.setLifecycleOwner(this);
        binding.setViewModel(viewModel);

        this.addTagButton = binding.addTagButton;
        this.addTagEditText = binding.addTagText;
        this.recyclerView = binding.recyclerView;

        initAddTag();
        initTagList();

        return binding.getRoot();
    }

    private void initTagList() {
        final TagAdapter tagAdapter = new TagAdapter();
        viewModel.getTags().observe(this, tagAdapter::submitList);

        recyclerView.setAdapter(tagAdapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(getContext(), LinearLayout.VERTICAL));

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                return makeMovementFlags(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder viewHolder1) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
                String tag = ((TagAdapter.ViewHolder) viewHolder).getTag();
                viewModel.removeTag(tag);
            }
        });

        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        NavigationUI.setupWithNavController(toolbar, Navigation.findNavController(view));
    }

    private void initAddTag() {
        addTagButton.setOnClickListener(v -> {
            addTag();
            addTagEditText.getText().clear();
        });

        addTagEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTag();
                return true;
            }
            return false;
        });

        addTagEditText.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    addTag();
                }
                return true;
            }
            return false;
        });
    }

    private void addTag() {
        String tag = addTagEditText.getText().toString().trim();
        if (!UAStringUtil.isEmpty(tag)) {
            viewModel.addTag(tag);
        }
        addTagEditText.getText().clear();
    }

}
