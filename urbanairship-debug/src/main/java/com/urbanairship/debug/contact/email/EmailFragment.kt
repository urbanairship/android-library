package com.urbanairship.debug.contact.email

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.Navigation
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.debug.R
import com.urbanairship.debug.extensions.setupToolbarWithNavController
import com.urbanairship.debug.databinding.UaFragmentContactEmailBinding

class EmailFragment : Fragment() {
    private val viewModel: EmailAssociateViewModel by navGraphViewModels(R.id.ua_debug_contact_navigation)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = UaFragmentContactEmailBinding.inflate(inflater, container, false)

        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        binding.recyclerView.apply {
            val propertyAdapter =  PropertyAdapter {
                if (isResumed) {
                    val args = Bundle()
                    args.putString(PropertyFragment.ARGUMENT_PROPERTY_NAME, it.first)
                    Navigation.findNavController(binding.root).navigate(R.id.emailPropertyFragment, args)
                }
            }
            viewModel.properties.observe(viewLifecycleOwner, Observer { properties ->
                propertyAdapter.submitList(properties.map {
                    Pair(it.key, it.value)
                })
            })

            this.adapter = propertyAdapter
            this.addItemDecoration(DividerItemDecoration(requireContext(), LinearLayout.VERTICAL))

            val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
                override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                    return makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
                }

                override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, viewHolder1: RecyclerView.ViewHolder): Boolean {
                    return false
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, i: Int) {
                    val name = (viewHolder as com.urbanairship.debug.contact.email.PropertyAdapter.ViewHolder).binding.name
                    name?.let {
                        viewModel.removeProperty(it)
                    }
                }
            })

            binding.addProperty.setOnClickListener {
                Navigation.findNavController(binding.root).navigate(R.id.emailPropertyFragment)
            }

            itemTouchHelper.attachToRecyclerView(this)
        }

        binding.commercialCheckbox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setCommerical(isChecked)
        }

        binding.transactionalCheckbox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setTransactional(isChecked)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarWithNavController(R.id.toolbar)?.let {
            it.inflateMenu(R.menu.ua_menu_contact_create)

            it.setOnMenuItemClickListener { menuItem ->
                if (menuItem.itemId == R.id.ua_contact_create) {
                    if (viewModel.associateToContact()) {
                        Navigation.findNavController(view).popBackStack()
                    }
                }

                true
            }
        }
    }

    fun onCheckboxClicked(view: View) {
        if (view is CheckBox) {
            val checked: Boolean = view.isChecked

            when (view.id) {
                R.id.transactionalCheckbox -> {
                    viewModel.setTransactional(checked)
                }
                R.id.commercialCheckbox -> {
                    viewModel.setCommerical(checked)
                }
            }
        }
    }
}