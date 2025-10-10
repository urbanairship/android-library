package com.urbanairship.devapp.preferencecenter

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.fragment.app.FragmentActivity
import androidx.glance.LocalContext
import com.urbanairship.preferencecenter.ui.PreferenceCenterFragment
import com.urbanairship.devapp.R
import com.urbanairship.devapp.databinding.FragmentPreferenceCenterBinding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferenceCenterScreen(
    modifier: Modifier = Modifier,
    context: Context
) {
    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(text = "Preference Center")
            }
        )
    }, modifier = modifier) { paddingValues ->
        Surface(Modifier.padding(paddingValues)) {
            AndroidViewBinding(FragmentPreferenceCenterBinding::inflate) {
                val fragmentManager = (context as FragmentActivity).supportFragmentManager

                if (fragmentManager.findFragmentById(R.id.preference_center_fragment_container_view) == null) {
                    val fragment = PreferenceCenterFragment.create("app_default")
                    fragmentManager.beginTransaction()
                        .add(R.id.preference_center_fragment_container_view, fragment).commit()
                }
            }
        }
    }
}
