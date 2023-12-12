package com.urbanairship.android.layout.playground.embedded

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.urbanairship.android.layout.playground.R
import com.urbanairship.android.layout.playground.databinding.FragmentEmbeddedListLayoutBinding

class EmbeddedListLayoutFragment : Fragment(R.layout.fragment_embedded_list_layout) {
    private val binding by lazy { FragmentEmbeddedListLayoutBinding.bind(requireView()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            // TODO: do something with this!
        }
    }
}
