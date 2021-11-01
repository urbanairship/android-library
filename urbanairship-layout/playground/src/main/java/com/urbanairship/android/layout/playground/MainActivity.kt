package com.urbanairship.android.layout.playground

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.urbanairship.android.layout.playground.databinding.ActivityMainBinding
import com.urbanairship.android.layout.ui.ModalActivity
import com.urbanairship.android.layout.util.ResourceUtils

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREF_NAME = "layout-playground"
        private const val PREF_KEY = "selected-layout"
        private const val SAMPLE_LAYOUTS_PATH = "sample_layouts"
    }

    private lateinit var binding: ActivityMainBinding

    private val sharedPrefs by lazy {
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private val adapter by lazy {
        ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line)
    }

    private var previousSelection: String?
        get() = sharedPrefs.getString(PREF_KEY, null)
        set(value) = sharedPrefs.edit(commit = true) {
            putString(PREF_KEY, value)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        initViews(binding)
    }

    override fun onResume() {
        super.onResume()

        if (binding.layoutSpinnerText.adapter != adapter) {
            binding.layoutSpinnerText.setAdapter(adapter)
        }
    }

    private fun initViews(binding: ActivityMainBinding) {
        val layoutFiles = ResourceUtils.listJsonAssets(this, SAMPLE_LAYOUTS_PATH)
        adapter.addAll(layoutFiles)

        previousSelection?.let { selected ->
            if (adapter.getPosition(selected) > -1) {
                binding.layoutSpinnerText.setText(selected, false)
            }
        }

        binding.layoutSpinnerText.setOnItemClickListener { _, _, position, _ ->
            if (position == ListView.INVALID_POSITION) return@setOnItemClickListener
            previousSelection = adapter.getItem(position)
        }

        binding.showModal.setOnClickListener {
            startActivity(Intent(this, ModalActivity::class.java)
                .putExtra(ModalActivity.EXTRA_MODAL_ASSET, binding.layoutSpinnerText.text.toString()))
        }
    }
}
