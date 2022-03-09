package com.urbanairship.debug.contact

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.navigation.Navigation
import com.urbanairship.debug.DebugEntry
import com.urbanairship.debug.DebugEntryAdapter
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentDebugBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import kotlinx.coroutines.withContext

class ContactFragment : Fragment() {
    private val contactScreenEntryLiveData = MutableLiveData<List<DebugEntry>>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val dataBinding = DataBindingUtil.inflate<UaFragmentDebugBinding>(inflater, R.layout.ua_fragment_debug, container, false)

        lifecycleScope.launch(Dispatchers.IO) {
            val entries = DebugEntry.parse(requireContext(), R.xml.ua_contact_channels)
            withContext(Dispatchers.Main) {
                contactScreenEntryLiveData.value = entries
            }
        }

        val componentAdapter = DebugEntryAdapter {
            if (isResumed) {
                Navigation.findNavController(dataBinding.root).navigate(it.navigationId)
            }
        }

        contactScreenEntryLiveData.observe(viewLifecycleOwner, Observer(componentAdapter::submitList))

        dataBinding.apply {
            lifecycleOwner = this@ContactFragment

            screens.apply {
                addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
                adapter = componentAdapter
            }
        }

        return dataBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarWithNavController(R.id.toolbar)
    }
}