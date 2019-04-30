/* Copyright Airship and Contributors */

package com.urbanairship.debug

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.Navigation
import androidx.navigation.ui.NavigationUI
import com.urbanairship.debug.databinding.UaFragmentDebugBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment that lists all the debug entries. Entries are defined in `xml/debug_entries.xml`.
 */
open class DebugFragment : Fragment() {

    private val debugScreenEntryLiveData = MutableLiveData<List<DebugEntry>>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val dataBinding = DataBindingUtil.inflate<UaFragmentDebugBinding>(inflater, R.layout.ua_fragment_debug, container, false)

        GlobalScope.launch(Dispatchers.IO) {
            val entries = DebugEntry.parse(context!!, R.xml.debug_entries)
            withContext(Dispatchers.Main) {
                debugScreenEntryLiveData.value = entries
            }
        }

        val componentAdapter = DebugEntryAdapter {
            if (isResumed) {
                Navigation.findNavController(dataBinding.root).navigate(it.navigationId)
            }
        }

        debugScreenEntryLiveData.observe(this, Observer(componentAdapter::submitList))

        dataBinding.apply {
            lifecycleOwner = this@DebugFragment

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
