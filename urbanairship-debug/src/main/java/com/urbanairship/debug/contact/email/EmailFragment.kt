package com.urbanairship.debug.contact.email

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.navigation.navGraphViewModels
import com.urbanairship.debug.R
import com.urbanairship.debug.extensions.setupToolbarWithNavController
import com.urbanairship.debug.databinding.UaFragmentContactEmailBinding

class EmailFragment : Fragment() {
    private val viewModel: EmailAssociateViewModel by navGraphViewModels(R.id.ua_debug_contact_navigation)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = UaFragmentContactEmailBinding.inflate(inflater, container, false)

        binding.lifecycleOwner = this
        binding.viewModel = viewModel

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
}