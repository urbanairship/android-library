/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.DividerItemDecoration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.urbanairship.debug.databinding.FragmentDebugBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment that lists all the debug screens.
 */
class DebugFragment : Fragment() {

    private val debugScreenEntryLiveData = MutableLiveData<List<DebugScreenEntry>>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val dataBinding = DataBindingUtil.inflate<FragmentDebugBinding>(inflater, R.layout.fragment_debug, container, false)

        GlobalScope.launch(Dispatchers.IO) {
            val entries = DebugScreenEntry.parse(context!!, R.xml.screens)
            withContext(Dispatchers.Main) {
                debugScreenEntryLiveData.value = entries
            }
        }

        val componentAdapter = DebugScreenEntryAdapter {
            if (isResumed) {
                startActivity(Intent(context, Class.forName(it.activityClass)))
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

}
