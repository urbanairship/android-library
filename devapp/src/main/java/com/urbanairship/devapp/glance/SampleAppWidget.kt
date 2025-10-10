package com.urbanairship.devapp.glance

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.urbanairship.UALog
import com.urbanairship.devapp.MainActivity
import com.urbanairship.devapp.R
import com.urbanairship.devapp.glance.SampleAppWidget.Payload.CountryMedals
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json

/**
 * A sample app widget that displays medal counts for a list of countries.
 */
internal class SampleAppWidget : GlanceAppWidget() {

    /** Widget state */
    object StateDefinition : GlanceStateDefinition<Preferences> {
        private const val FILE_NAME = "sample_app_widget_store"

        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(FILE_NAME)

        override suspend fun getDataStore(context: Context, fileKey: String): DataStore<Preferences> {
            return context.dataStore
        }

        override fun getLocation(context: Context, fileKey: String): File {
            return File(context.applicationContext.filesDir, "widget_data/$FILE_NAME")
        }
    }

    override val stateDefinition = StateDefinition

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                Content()
            }
        }
    }

    @Composable
    fun Content() {
        val context = LocalContext.current
        val appIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val state = currentState<Preferences>()
        val data = state[dataPrefKey]?.let {
            try {
                Payload.fromJson(it)
            } catch (e: Exception) {
                UALog.e("Failed to parse data: $it", e)
                null
            }
        } ?: Payload.EMPTY

        val size = LocalSize.current
        val isNarrow = size.width <= 80.dp
        val isWide = size.width >= 240.dp

        Scaffold(
            titleBar = {
                TitleBar(
                    startIcon = ImageProvider(R.drawable.ic_notification),
                    title = if (isNarrow) "" else "Medals",
                )
            },
            horizontalPadding = 0.dp,
            modifier = GlanceModifier.fillMaxSize().clickable {
                context.startActivity(appIntent)

            }
        ) {
            LazyColumn(
                modifier = GlanceModifier.fillMaxSize()
            ) {

                if (data.countries.isEmpty()) {
                    item {
                        Text(
                            "No data available.",
                            style = TextStyle(
                                textAlign = TextAlign.Center,
                                fontStyle = FontStyle.Italic
                            ),
                            modifier = GlanceModifier.fillMaxWidth()
                        )
                    }
                } else {
                    items(data.countries) { country ->
                        MedalRow(country, isWide)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalUnitApi::class)
    @Composable
    fun MedalRow(data: CountryMedals, isWide: Boolean) {
        val flagSize = if (isWide) 24f else 18f
        val countryStyle = TextStyle(fontSize = TextUnit(flagSize, TextUnitType.Sp))

        val countSize = if (isWide) 16f else 14f
        val medalStyle = TextStyle(fontSize = TextUnit(countSize, TextUnitType.Sp), fontWeight = FontWeight.Medium)
        val totalStyle = TextStyle(fontSize = TextUnit(countSize, TextUnitType.Sp), fontWeight = FontWeight.Bold)

        Row(
            modifier = GlanceModifier.fillMaxWidth()
                .height(48.dp)
                .padding(start = 12.dp, end = 6.dp)
        ) {
            val modifier = GlanceModifier.defaultWeight()

            Text(text = countryFlag(data.country) ?: data.country, style = countryStyle, modifier = modifier)

            if (isWide) {
                Text(text = "${data.totalMedals}", style = totalStyle, modifier = modifier)
            }

            Text(text = "${GOLD}${data.gold}", style = medalStyle, modifier = modifier)
            Text(text = "${SILVER}${data.silver}", style = medalStyle, modifier = modifier)
            Text(text = "${BRONZE}${data.bronze}", style = medalStyle, modifier = modifier)
        }
    }

    internal companion object {
        const val GOLD = "\uD83E\uDD47"
        const val SILVER = "\uD83E\uDD48"
        const val BRONZE = "\uD83E\uDD49"

        val dataPrefKey = stringPreferencesKey("data")
    }

    /**
     * Represents the payload for the SampleAppWidget.
     *
     * The payload is expected to be a JSON object with a single key, `"countries"`, which contains
     * a list of objects representing the medal counts for each country. Each country object must
     * contain the fields:
     * - `"country"` a two-letter ISO 3166-1 alpha-2 country code
     * - `"medals"` an array of three integers `[gold, silver, bronze]`
     *
     * Example JSON:
     *
     * ```
     * {
     *  "countries": [
     *    {
     *      "country": "US",
     *      "medals": [10, 9, 8]
     *    },
     *    {
     *      "country": "FR",
     *      "medals": [7, 6, 5]
     *    },
     *    {
     *      "country": "GB",
     *      "medals": [4, 3, 2]
     *    }
     *  ]
     * }
     */
    @Serializable
    data class Payload(
        val countries: List<CountryMedals>
    ) {
        @Serializable
        data class CountryMedals(
            val country: String,
            val medals: List<Int>
        ) {
            @Transient
            val gold: Int = medals.getOrElse(0) { 0 }
            @Transient
            val silver: Int = medals.getOrElse(1) { 0 }
            @Transient
            val bronze: Int = medals.getOrElse(2) { 0 }
            @Transient
            val totalMedals: Int = medals.sum()
        }

        companion object {
            private val jsonSerializer = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

            val EMPTY = Payload(emptyList())

            fun fromJson(json: String): Payload =
                jsonSerializer.decodeFromString<Payload>(json)
        }
    }

    /**
     * Returns the emoji flag for the given ISO 3166-1 alpha-2 country [code].
     *
     * * If [code] does not have a length of 2, the result will be `null`.
     * * If [code] is not a valid alpha-2 country code, the result may be a blank or question-mark
     * flag, but this will differ based on Android version and OEM.
     */
    private fun countryFlag(code: String): String? {
        val sanitizedCode = code.uppercase().replace(Regex("[^A-Z]"), "")
        if (sanitizedCode.length != 2) {
            return null
        }

        return sanitizedCode.map { it.code + 0x1F1A5 }.joinToString("") {
            Character.toChars(it).concatToString()
        }
    }

}
