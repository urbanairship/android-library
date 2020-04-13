/* Copyright Airship and Contributors */

package com.urbanairship.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.navigation.Navigation
import com.urbanairship.debug.databinding.UaFragmentDebugBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment that lists all the debug entries. Entries are defined in `xml/ua_debug_entries.xmlxml`.
 */
open class DebugFragment : androidx.fragment.app.Fragment() {

    private val debugScreenEntryLiveData = MutableLiveData<List<DebugEntry>>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val dataBinding = DataBindingUtil.inflate<UaFragmentDebugBinding>(inflater, R.layout.ua_fragment_debug, container, false)

        GlobalScope.launch(Dispatchers.IO) {
            val entries = DebugEntry.parse(requireContext(), R.xml.ua_debug_entries)
            withContext(Dispatchers.Main) {
                debugScreenEntryLiveData.value = entries
            }
        }

        val componentAdapter = DebugEntryAdapter {
            if (isResumed) {
                Navigation.findNavController(dataBinding.root).navigate(it.navigationId)
            }
        }

        debugScreenEntryLiveData.observe(viewLifecycleOwner, Observer(componentAdapter::submitList))

        dataBinding.apply {
            lifecycleOwner = this@DebugFragment

            screens.apply {
                addItemDecoration(androidx.recyclerview.widget.DividerItemDecoration(context, androidx.recyclerview.widget.DividerItemDecoration.VERTICAL))
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
