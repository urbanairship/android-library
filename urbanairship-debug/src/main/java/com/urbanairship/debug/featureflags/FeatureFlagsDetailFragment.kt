package com.urbanairship.debug.featureflags

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.UALog
import com.urbanairship.debug.R
import com.urbanairship.debug.databinding.UaFragmentFeatureFlagsDetailBinding
import com.urbanairship.debug.databinding.UaItemFeatureFlagDetailsBinding
import com.urbanairship.debug.extensions.setupToolbarWithNavController
import com.urbanairship.debug.extensions.toFormattedJsonString
import com.urbanairship.debug.json.JsonRecyclerAdapter
import com.urbanairship.debug.json.JsonRecyclerView
import com.urbanairship.debug.utils.getParcelableCompat
import com.urbanairship.featureflag.FeatureFlagException
import com.urbanairship.featureflag.FeatureFlagManager
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.requireField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class FeatureFlagsDetailFragment : Fragment(R.layout.ua_fragment_feature_flags_detail) {

    private lateinit var binding: UaFragmentFeatureFlagsDetailBinding

    private val json: JsonMap by lazy {
        arguments?.getParcelableCompat<JsonValue>(JSON)?.optMap() ?: JsonMap.EMPTY_MAP
    }

    private val detailAdapter: FeatureFlagsDetailAdapter by lazy {
        FeatureFlagsDetailAdapter(lifecycleScope).apply {
            submitList(listOf(json))
        }
    }

    private val jsonAdapter: JsonRecyclerAdapter by lazy {
        JsonRecyclerAdapter(showDividers = false).apply {
            json.map { (key, value) -> JsonRecyclerView.Item(key, value) }.toList()
                .let { submitList(it) }
        }
    }

    private val concatAdapter by lazy {
        ConcatAdapter(detailAdapter, jsonAdapter)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = UaFragmentFeatureFlagsDetailBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        detailAdapter.onRefreshResult = { json ->
            detailAdapter.notifyDataSetChanged()
        }

        detailAdapter.onSharePayload = { json ->
            val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, json.toFormattedJsonString())

            startActivity(
                Intent.createChooser(intent, getString(R.string.ua_share_dialog_title))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        with(binding) {
            lifecycleOwner = this@FeatureFlagsDetailFragment
            list.adapter = concatAdapter
        }

        setupToolbarWithNavController(R.id.toolbar)?.let {
            it.title = json.requireField<JsonMap>("flag").requireField<String>("name")
        }
    }

    companion object {
        const val JSON: String = "json"
    }
}

private class FeatureFlagsDetailAdapter(
    private val scope: CoroutineScope,
    private val featureFlagManager: FeatureFlagManager = FeatureFlagManager.shared(),
) : ListAdapter<JsonMap, FeatureFlagsDetailAdapter.ViewHolder>(DIFF_UTIL) {

    var onRefreshResult: ((JsonMap) -> Unit)? = null
    var onSharePayload: ((JsonMap) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class ViewHolder(
        private val parent: ViewGroup,
        private val binding: UaItemFeatureFlagDetailsBinding = UaItemFeatureFlagDetailsBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(json: JsonMap) = with(binding) {
            val flagName = json.requireField<JsonMap>("flag").requireField<String>("name")
            name = flagName
            id = json.requireField<String>("flag_id")

            scope.launch {
                try {
                    val result = featureFlagManager.flag(flagName)
                    eligible = result.isEligible.toString()
                    exists = result.exists.toString()
                } catch (e: FeatureFlagException) {
                    UALog.e("Failed to evaluate flag: $flagName", e)
                    eligible = "error"
                    exists = "error"
                }
            }

            resultRefreshButton.setOnClickListener {
                onRefreshResult?.invoke(json)
            }

            sharePayloadButton.setOnClickListener {
                onSharePayload?.invoke(json)
            }

            executePendingBindings()
        }
    }

    companion object {
        private val DIFF_UTIL = object : DiffUtil.ItemCallback<JsonMap>() {
            override fun areItemsTheSame(old: JsonMap, new: JsonMap): Boolean = old === new
            override fun areContentsTheSame(old: JsonMap, new: JsonMap): Boolean = old == new
        }
    }
}
