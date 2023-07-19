package com.urbanairship.debug.json

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentJsonViewerBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController
import com.urbanairship.debug.extensions.toFormattedJsonString
import com.urbanairship.debug.utils.getParcelableCompat
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue

/**
 * Generic JSON viewer fragment.
 *
 * Displays top-level keys as expandable sections that reveal pretty-printed json values.
 * Also includes a Share action in the toolbar so that the raw JSON can be copied to the clipboard
 * or shared with other apps.
 */
class JsonViewerFragment : Fragment(R.layout.ua_fragment_json_viewer) {
    private lateinit var binding: UaFragmentJsonViewerBinding
    private val title: String by lazy {
        arguments?.getString(TITLE) ?: getString(R.string.ua_debug_json_viewer_title)
    }
    private val json: JsonMap by lazy {
        arguments?.getParcelableCompat<JsonValue>(JSON)?.optMap() ?: JsonMap.EMPTY_MAP
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = UaFragmentJsonViewerBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {
            lifecycleOwner = this@JsonViewerFragment

            list.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            list.show(json)
        }

        setupToolbarWithNavController(R.id.toolbar)?.let {
            it.title = title

            it.inflateMenu(R.menu.ua_menu_share)
            it.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.ua_share -> {
                        val intent = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, json.toFormattedJsonString())

                        startActivity(
                            Intent.createChooser(intent, getString(R.string.ua_share_dialog_title))
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

                        true
                    }
                    else -> false
                }
            }
        }
    }

    companion object {
        const val JSON: String = "json"
        const val TITLE: String = "title"
    }
}
