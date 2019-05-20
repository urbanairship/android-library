/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug.deviceinfo

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout

import com.urbanairship.debug.R
import com.urbanairship.util.UAStringUtil

import com.urbanairship.debug.databinding.UaFragmentDeviceInfoTagsBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController

/**
 * Fragment that manages Urban Airship tags.
 */
class DeviceInfoTagsFragment : Fragment() {

    private lateinit var viewModel: DeviceInfoTagsViewModel
    private lateinit var addTagButton: ImageButton
    private lateinit var addTagEditText: EditText
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel = ViewModelProviders.of(this).get(DeviceInfoTagsViewModel::class.java)
        val binding = UaFragmentDeviceInfoTagsBinding.inflate(inflater, container, false)
        binding.setLifecycleOwner(this)
        binding.setViewModel(viewModel)

        this.addTagButton = binding.addTagButton
        this.addTagEditText = binding.addTagText
        this.recyclerView = binding.recyclerView

        initAddTag()
        initTagList()

        return binding.getRoot()
    }

    private fun initTagList() {
        val tagAdapter = DeviceInfoTagAdapter()
        viewModel.getTags().observe(this, Observer<List<String>> { tagAdapter.submitList(it) })

        recyclerView.adapter = tagAdapter
        recyclerView.addItemDecoration(DividerItemDecoration(context!!, LinearLayout.VERTICAL))

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                return ItemTouchHelper.Callback.makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, viewHolder1: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, i: Int) {
                val tag = (viewHolder as DeviceInfoTagAdapter.ViewHolder).tag
                viewModel.removeTag(tag)
            }
        })

        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarWithNavController(R.id.toolbar)
    }

    private fun initAddTag() {
        addTagButton.setOnClickListener {
            addTag()
            addTagEditText.text.clear()
        }

        addTagEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTag()
            }
            false
        }

        addTagEditText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    addTag()
                }
            }
            false
        }
    }

    private fun addTag() {
        val tag = addTagEditText.text.toString().trim { it <= ' ' }
        if (!UAStringUtil.isEmpty(tag)) {
            viewModel.addTag(tag)
        }
        addTagEditText.text.clear()
    }

}