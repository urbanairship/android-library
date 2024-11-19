package com.urbanairship.android.layout.util

import android.content.Intent
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.Patterns
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MASK
import android.view.MotionEvent.ACTION_UP
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.TextUtilsCompat
import androidx.core.text.method.LinkMovementMethodCompat
import androidx.core.text.toSpannable
import androidx.core.view.descendants
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.android.layout.gestures.PagerGestureEvent
import com.urbanairship.android.layout.view.PagerView
import com.urbanairship.android.layout.view.ScoreView
import com.urbanairship.android.layout.widget.CheckableView
import com.urbanairship.android.layout.widget.CheckableViewAdapter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
internal fun EditText.textChanges(debounceDuration: Duration = .1.seconds): Flow<String> =
    callbackFlow {
        checkMainThread()

        val listener = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable) {
                trySend(s.toString())
            }
        }

        addTextChangedListener(listener)
        awaitClose { removeTextChangedListener(listener) }
    }
        .onStart { emit(text.toString()) }
        .distinctUntilChanged()
        .debounce(debounceDuration)
        .conflate()

internal fun EditText.onEditing(idleDelay: Duration = 1.seconds): Flow<Boolean> =
    callbackFlow {
        checkMainThread()

        onFocusChangeListener = View.OnFocusChangeListener { _, isFocused ->
            trySend(isFocused)
        }

        var textEditTimeoutJob: Job? = null
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                trySend(true)
                textEditTimeoutJob?.cancel()
                textEditTimeoutJob = scope.launch {
                    delay(idleDelay)
                    if (isActive) {
                        trySend(false)
                    }
                }
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable) = Unit
        }

        addTextChangedListener(textWatcher)

        setOnEditorActionListener { _, actionId, keyEvent ->
            val isDoneAction =
                actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE
            val isDoneKey =
                keyEvent?.action == KeyEvent.ACTION_DOWN && keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER
            if (isDoneKey || isDoneAction) {
                trySend(false)
                textEditTimeoutJob?.cancel()
                true
            } else {
                false
            }
        }

        awaitClose {
            onFocusChangeListener = null
            setOnEditorActionListener(null)
            removeTextChangedListener(textWatcher)
        }
    }
        .onStart { emit(false) }
        .distinctUntilChanged()
        .conflate()

@OptIn(FlowPreview::class)
internal fun View.debouncedClicks(debounceMillis: Long = 100L): Flow<Unit> =
    callbackFlow {
        checkMainThread()

        setOnClickListener { trySend(Unit) }
        awaitClose { setOnClickListener(null) }
    }
        .debounce(debounceMillis)
        .conflate()

internal fun CheckableView<*>.checkedChanges(): Flow<Boolean> =
    callbackFlow {
        checkMainThread()

        val listener = CheckableViewAdapter.OnCheckedChangeListener { _, isChecked ->
            trySend(isChecked)
        }

        checkedChangeListener = listener
        awaitClose { checkedChangeListener = null }
    }
        .onStart { emit(checkableView.isChecked) }
        .conflate()

internal fun ScoreView.scoreChanges(): Flow<Int> =
    callbackFlow {
        checkMainThread()

        val listener = ScoreView.OnScoreSelectedListener { score -> trySend(score) }

        scoreSelectedListener = listener
        awaitClose { scoreSelectedListener = null }
    }.conflate()

internal fun PagerView.pagerScrolls(): Flow<PagerScrollEvent> =
    callbackFlow {
        checkMainThread()

        val listener = PagerView.OnScrollListener { position, isInternalScroll ->
            trySend(PagerScrollEvent(position, isInternalScroll))
        }

        scrollListener = listener
        awaitClose { scrollListener = null }
    }.conflate()

internal data class PagerScrollEvent(
    val position: Int,
    val isInternalScroll: Boolean
)

internal fun PagerView.pagerGestures(): Flow<PagerGestureEvent> =
    callbackFlow {
        checkMainThread()

        val listener = object : PagerView.OnPagerGestureListener {
            override fun onGesture(event: PagerGestureEvent) {
                trySend(event)
            }
        }

        gestureListener = listener
        awaitClose { gestureListener = null }
    }.conflate()

internal val MotionEvent.isActionUp: Boolean
    get() = action and ACTION_MASK == ACTION_UP

internal val MotionEvent.isActionDown: Boolean
    get() = action and ACTION_MASK == ACTION_DOWN

/** Returns view bounds in the view's coordinate space. */
internal val View.localBounds: RectF
    get() = RectF(0f, 0f, width.toFloat(), height.toFloat())

internal val View.isLayoutRtl: Boolean
    get() = TextUtilsCompat.getLayoutDirectionFromLocale(UAirship.shared().locale) == View.LAYOUT_DIRECTION_RTL


internal fun MotionEvent.findTargetDescendant(
    view: View,
    filter: ((View) -> Boolean)
): View? {
    fun MotionEvent.isTouchWithin(v: View): Boolean {
        val rect = Rect().apply { v.getGlobalVisibleRect(this) }
        return rect.contains(rawX.toInt(), rawY.toInt())
    }

    return if (view is ViewGroup) {
        view.descendants.filter { filter.invoke(it) }
            .sortedByDescending { it.z }
            .firstOrNull(::isTouchWithin)
    } else {
        if (filter.invoke(view) && isTouchWithin(view)) view else null
    }
}

@Throws(IllegalStateException::class)
private fun checkMainThread() {
    check(Thread.currentThread() == Looper.getMainLooper().thread) {
        "Must be called from main thread!"
    }
}

/** Sets the given [html] on the TextView and supports both html links and plain text links. */
internal fun TextView.setHtml(html: Spanned?, underline: Boolean = false, color: Int? = null) {
    movementMethod = LinkMovementMethodCompat.getInstance()

    text = if (html.isNullOrEmpty()) {
        null
    } else {
        html.toSpannable().apply {
            convertUrlSpans(underline, color)
            linkifyText(underline, color)
        }
    }
}

/** Replaces URLSpans with ClickableSpans. */
private fun Spannable.convertUrlSpans(underline: Boolean?, color: Int?) {
    val urlSpans = getSpans(0, length, URLSpan::class.java) ?: emptyArray()
    urlSpans.forEach { span ->
        val linkSpan = LinkSpan(span.url, underline, color)
        setSpan(linkSpan, getSpanStart(span), getSpanEnd(span), getSpanFlags(span))
        removeSpan(span)
    }
}

/** Converts text URLs and email addresses to clickable links. */
private fun Spannable.linkifyText(underline: Boolean?, color: Int?) = with(this) {
    forEachMatching(emailPattern, underline, color) { email -> "mailto:$email" }

    forEachMatching(urlPattern, underline, color) { url ->
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
    }
}

private fun Spannable.forEachMatching(regex: Regex, underline: Boolean?, color: Int?, linkFactory: (url: String) -> String) =
    regex.findAll(this).forEach { match ->
        val startIndex = match.range.first
        val endIndex = match.range.last + 1

        val isSpanAlreadyCreated = getSpans(startIndex, endIndex, ClickableSpan::class.java).isNotEmpty()
        if (!isSpanAlreadyCreated) {
            val linkSpan = LinkSpan(linkFactory.invoke(match.value.trim()), underline, color)
            setSpan(linkSpan, startIndex, endIndex, 0)
        }
    }

private val emailPattern = Patterns.EMAIL_ADDRESS.toRegex()
private val urlPattern = Patterns.WEB_URL.toRegex()

/** ClickableSpan that opens a URL in the browser. */
private class LinkSpan(
    private val url: String,
    private val underline: Boolean?,
    private val color: Int?
) : ClickableSpan() {
    override fun onClick(view: View) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        ContextCompat.startActivity(view.context, intent, null)
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)

        // Optionally draw or remove the underline, defaulting to no underline.
        ds.isUnderlineText = underline ?: false

        // Optionally set the color, defaulting to the default color from ClickableSpan.
        ds.color = color ?: ds.color
    }
}
