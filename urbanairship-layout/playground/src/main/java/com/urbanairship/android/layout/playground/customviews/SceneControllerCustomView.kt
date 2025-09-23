package com.urbanairship.android.layout.playground.customviews

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.urbanairship.android.layout.AirshipCustomViewArguments
import com.urbanairship.android.layout.AirshipCustomViewHandler
import com.urbanairship.android.layout.scenecontroller.PagerController
import com.urbanairship.android.layout.scenecontroller.SceneController
import com.urbanairship.json.jsonMapOf

public class SceneControllerCustomView: AirshipCustomViewHandler {

    override fun onCreateView(context: Context, args: AirshipCustomViewArguments): View =
        ComposeView(context).apply {
            setContent {
                MaterialTheme {
                    SceneControllerCustomView(args)
                }
            }
        }
}

@Composable
fun SceneControllerCustomView(args: AirshipCustomViewArguments) {
    val controller = args.sceneController
    val state = controller.pager.state.collectAsState().value

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            text = "Scene Controller Custom View"
        )

        NavigationState(state)

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Button(
                modifier = Modifier.fillMaxWidth().weight(1f),
                enabled = state.canGoBack,
                onClick = { controller.pager.navigate(PagerController.NavigationRequest.BACK)}
            ) {
                Text("Back")
            }

            Spacer(Modifier.width(8.dp))

            Button(
                modifier = Modifier.fillMaxWidth().weight(1f),
                enabled = state.canGoNext,
                onClick = { controller.pager.navigate(PagerController.NavigationRequest.NEXT)}
            ) {
                Text("Next")
            }
        }

        Spacer(Modifier.height(32.dp))

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
        ) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { controller.dismiss() }
            ) {
                Text("Dismiss")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { controller.dismiss(true) }
            ) {
                Text("Dismiss and Cancel Future")
            }
        }
    }
}

@Composable
private fun NavigationState(state: PagerController.State) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.LightGray)
    ) {
        Text(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            text = "Navigation State",
            textAlign = TextAlign.Center
        )

        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp)
        ) {
            Text("Can Go Back:")

            Spacer(Modifier.fillMaxWidth())

            Text(
                text = if (state.canGoBack) "✅" else "❌",
                textAlign = TextAlign.End
            )
        }

        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp)
        ) {
            Text("Can Go Forward:")

            Spacer(Modifier.fillMaxWidth())

            Text(
                text = if (state.canGoNext) "✅" else "❌",
                textAlign = TextAlign.End
            )
        }
    }
}

@Preview()
@Composable
private fun EventsScreenPreview() {
    MaterialTheme {
        SceneControllerCustomView(
            args = AirshipCustomViewArguments(
                name = "preview",
                properties = jsonMapOf(),
                sizeInfo = AirshipCustomViewArguments.SizeInfo(true, true),
                sceneController = SceneController.empty()
            )
        )
    }
}
