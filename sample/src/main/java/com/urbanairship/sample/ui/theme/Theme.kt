import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.urbanairship.sample.ui.theme.DarkColorScheme
import com.urbanairship.sample.ui.theme.LightColorScheme

@Composable
internal fun AirshipTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}