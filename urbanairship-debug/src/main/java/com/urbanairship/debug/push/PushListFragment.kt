/* Copyright Airship and Contributors */

package com.urbanairship.debug.push

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.DividerItemDecoration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.Navigation
import com.urbanairship.debug.R
import com.urbanairship.debug.ServiceLocator
import com.urbanairship.debug.databinding.UaFragmentPushListBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController

/**
 * PushItem list fragment.
 */
class PushListFragment : Fragment() {

    private val viewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProviders.of(this, ViewModelFactory(context!!)).get(PushListViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val dataBinding = DataBindingUtil.inflate<UaFragmentPushListBinding>(inflater, R.layout.ua_fragment_push_list, container, false)

        val pushAdapter = PushAdapter {
            if (isResumed) {
                val args = Bundle()
                args.putString(PushDetailsFragment.ARGUMENT_PUSH_ID, it.pushId)
                Navigation.findNavController(dataBinding.root).navigate(R.id.pushDetailsFragment, args)
            }
        }

        viewModel.pushes.observe(this, Observer(pushAdapter::submitList))

        dataBinding.apply {
            lifecycleOwner = this@PushListFragment
            viewModel = this@PushListFragment.viewModel

            pushes.apply {
                addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
                adapter = pushAdapter
            }
        }

        return dataBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbarWithNavController(R.id.toolbar)
    }

    internal class ViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PushListViewModel(ServiceLocator.shared(context).getPushRepository()) as T
        }
    }
}
