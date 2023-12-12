package com.urbanairship.android.layout.playground.embedded

import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.FragmentActivity
import com.urbanairship.android.layout.playground.R
import com.urbanairship.android.layout.playground.databinding.ActivityEmbeddedBinding

class EmbeddedActivity : FragmentActivity(R.layout.activity_embedded) {
    private val binding by lazy { ActivityEmbeddedBinding.inflate(LayoutInflater.from(this)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, EmbeddedSingleLayoutFragment())
            .commitNow()

        binding.run {
            bottomNavigation.setOnItemSelectedListener {
                when (it.itemId) {
                    R.id.single_layout -> {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, EmbeddedSingleLayoutFragment())
                            .commitNow()
                        true
                    }
                    R.id.stacked_layout -> {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, EmbeddedStackedLayoutFragment())
                            .commitNow()
                        true
                    }
                    R.id.list_layout -> {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, EmbeddedListLayoutFragment())
                            .commitNow()
                        true
                    }
                    else -> false
                }
            }
        }
    }
}
