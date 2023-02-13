package com.urbanairship.android.layout.playground

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import com.urbanairship.android.layout.playground.databinding.ActivityOtherBinding

class OtherAndroidActivity : Activity() {
    private val binding by lazy { ActivityOtherBinding.inflate(LayoutInflater.from(this)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.run {
            title.setText(R.string.vanilla_activity_title)
            finishButton.setOnClickListener { finish() }
        }
    }
}

class OtherAppCompatActivity : AppCompatActivity() {
    private val binding by lazy { ActivityOtherBinding.inflate(LayoutInflater.from(this)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.run {
            title.setText(R.string.appcompat_activity_title)
            finishButton.setOnClickListener { finish() }
        }
    }
}
