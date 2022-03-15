package com.urbanairship.debug.contact.open

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentContactOpenBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController

class OpenChannelFragment : Fragment() {
    private val viewModel: OpenChannelViewModel by navGraphViewModels(R.id.ua_debug_contact_navigation)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = UaFragmentContactOpenBinding.inflate(inflater, container, false)

        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        binding.recyclerView.apply {
            val identifierAdapter = IdentifierAdapter {
                if (isResumed) {
                    val args = Bundle()
                    args.putString("name", it.first)
                    Navigation.findNavController(binding.root).navigate(R.id.openIdentifierFragment)
                }
            }
            viewModel.identifiers.observe(viewLifecycleOwner) { identifiers ->
                identifierAdapter.submitList(identifiers.map {
                    Pair(it.key, it.value)
                })
            }

            this.adapter = identifierAdapter
            this.addItemDecoration(DividerItemDecoration(requireContext(), LinearLayout.VERTICAL))

            val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
                override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                    return makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
                }

                override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                    return false
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val name = (viewHolder as IdentifierAdapter.ViewHolder).binding.name
                    name?.let {
                        viewModel.removeIdentifier(it)
                    }
                }
            })

            binding.addProperty.setOnClickListener {
                Navigation.findNavController(binding.root).navigate(R.id.openIdentifierFragment)
            }

            itemTouchHelper.attachToRecyclerView(this)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarWithNavController(R.id.toolbar)?.let {
            it.inflateMenu(R.menu.ua_menu_contact_create)

            it.setOnMenuItemClickListener { menuItem ->
                if (menuItem.itemId == R.id.ua_contact_create) {
                    if (viewModel.associateOpenChannel()) {
                        Navigation.findNavController(view).popBackStack()
                    }
                }
                true
            }
        }
    }
}
