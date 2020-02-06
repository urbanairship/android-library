/* Copyright Airship and Contributors */

package com.urbanairship.debug.deviceinfo.identifiers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentDeviceInfoAssociatedIdentifiersBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController

/**
 * Fragment that manages Associated Identifiers.
 */
class AssociatedIdentifiersFragment : Fragment() {

    private lateinit var viewModel: AssociatedIdentifiersViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel = ViewModelProviders.of(this).get(AssociatedIdentifiersViewModel::class.java)
        val binding = UaFragmentDeviceInfoAssociatedIdentifiersBinding.inflate(inflater, container, false)
        binding.apply {
            lifecycleOwner = this@AssociatedIdentifiersFragment
            viewModel = this@AssociatedIdentifiersFragment.viewModel
        }

        val idAdapter = AssociatedIdentifiersAdapter()
        viewModel.identifiers.observe(this, Observer(idAdapter::submitList))

        binding.identifiers.apply {
            adapter = idAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))

            val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
                override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                    return makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
                }

                override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, viewHolder1: RecyclerView.ViewHolder): Boolean {
                    return false
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, i: Int) {
                    val tag = (viewHolder as AssociatedIdentifiersAdapter.ViewHolder).key
                    viewModel.remove(tag!!)
                }
            })

            itemTouchHelper.attachToRecyclerView(this)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarWithNavController(R.id.toolbar)
    }
}
