/* Copyright Airship and Contributors */
package com.urbanairship.sample.preference

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation.findNavController
import androidx.navigation.ui.NavigationUI.setupWithNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.sample.R
import com.urbanairship.sample.databinding.FragmentTagsBinding
import com.urbanairship.util.UAStringUtil

/**
 * Fragment that manages Airship tags.
 */
class TagsFragment : Fragment(R.layout.fragment_tags) {

    private val viewModel: TagsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        setupWithNavController(toolbar, findNavController(view))

        val binding = FragmentTagsBinding.bind(view)
        with(binding) {
            initAddTag(addTagButton, addTagText)
            initTagList(recyclerView)
        }
    }

    private fun initTagList(recyclerView: RecyclerView) {
        val tagAdapter = TagAdapter()

        viewModel.tags.observe(viewLifecycleOwner) {
            tags -> tagAdapter.submitList(tags)
        }

        recyclerView.adapter = tagAdapter
        recyclerView.addItemDecoration(DividerItemDecoration(context, LinearLayout.VERTICAL))

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
            ): Int = makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                viewHolder1: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, i: Int) {
                (viewHolder as? TagAdapter.ViewHolder)?.tag?.let { tag ->
                    viewModel.removeTag(tag)
                }
            }
        })

        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun initAddTag(addTagButton: ImageButton, addTagEditText: EditText) {
        fun addTag() {
            val tag = addTagEditText.text.toString().trim()
            if (tag.isNotBlank()) {
                viewModel.addTag(tag)
            }
            addTagEditText.text.clear()
        }

        addTagButton.setOnClickListener { addTag() }

        addTagEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTag()
                true
            } else {
                false
            }
        }

        addTagEditText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                addTag()
                true
            } else {
                false
            }
        }
    }
}
