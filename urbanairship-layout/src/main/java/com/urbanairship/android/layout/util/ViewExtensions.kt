package com.urbanairship.android.layout.util

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Looper
import android.text.Editable
import android.text.Layout
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
import androidx.core.net.toUri
import androidx.core.text.method.LinkMovementMethodCompat
import androidx.core.text.toSpannable
import androidx.core.view.descendants
import com.urbanairship.android.layout.gestures.PagerGestureEvent
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.property.MarkdownOptions
import com.urbanairship.android.layout.property.resolveHighlightColor
import com.urbanairship.android.layout.property.resolveHighlightCornerRadius
import com.urbanairship.android.layout.property.resolvedLinkColor
import com.urbanairship.android.layout.property.underlineLinks
import com.urbanairship.android.layout.view.PagerView
import com.urbanairship.android.layout.view.ScoreView
import com.urbanairship.android.layout.widget.AutoSizeProvider
import com.urbanairship.android.layout.widget.CheckableView
import com.urbanairship.android.layout.widget.CheckableViewAdapter
import com.urbanairship.android.layout.widget.ItemWrapper
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
                keyEvent?.action == KeyEvent.ACTION_DOWN && keyEvent.keyCode == KeyEvent.KEYCODE_ENTER
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
        .onStart { emit(checkableView.isChecked()) }
        .conflate()

internal fun ScoreView.scoreChanges(): Flow<Int> =
    callbackFlow {
        checkMainThread()
        onScoreSelectedListener = { score -> trySend(score) }
        awaitClose { onScoreSelectedListener = null }
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

/**
 * Whether this view lays out right-to-left.
 *
 * The one place direction is detected. Reads the view's own resolved layout direction, which
 * is what the platform resolves `Gravity.START`/`END`, `paddingStart`/`End` and every relative
 * API from, and what `WeightlessLinearLayout` positions children by — so a view asking this
 * gets the same answer as the layout around it.
 *
 * Deliberately not the Airship locale. A locale override selects which copy is delivered, not
 * how it is laid out; reading it here gave mirrored content inside an unmirrored layout.
 *
 * Unresolved until the view is attached, so ask it while rendering rather than while
 * constructing — or take it from [View.onRtlPropertiesChanged], which the platform calls as
 * soon as there is an answer.
 */
internal val View.isLayoutRtl: Boolean
    get() = layoutDirection == View.LAYOUT_DIRECTION_RTL

internal fun MotionEvent.isWithinClickableDescendantOf(view: View): Boolean =
    findTargetDescendant(view) { it.isClickable && it.isEnabled } != null

/** Returns true if this event's raw coordinates fall within [view]'s visible bounds. */
internal fun MotionEvent.isWithinBounds(view: View): Boolean {
    val rect = Rect().apply { view.getGlobalVisibleRect(this) }
    return rect.contains(rawX.toInt(), rawY.toInt())
}

/**
 * onTouchEvent handling shared by button-like views that wrap arbitrary content. The default
 * clickable behavior fires a click for any ACTION_UP within the view's bounds — even when the press
 * started on the view but the finger drifted onto a clickable child. Convert such an up into a
 * cancel before dispatching to [superOnTouchEvent] so the view doesn't fire its own click when the
 * release lands on a clickable descendant of [content].
 */
internal inline fun cancelClickIfReleasedOnClickableDescendant(
    event: MotionEvent,
    content: View,
    superOnTouchEvent: (MotionEvent) -> Boolean
): Boolean {
    if (event.isActionUp && event.isWithinClickableDescendantOf(content)) {
        val cancel = MotionEvent.obtain(event).apply {
            action = MotionEvent.ACTION_CANCEL
        }
        return try {
            superOnTouchEvent(cancel)
        } finally {
            cancel.recycle()
        }
    }
    return superOnTouchEvent(event)
}

internal fun MotionEvent.findTargetDescendant(
    view: View,
    filter: ((View) -> Boolean)
): View? = if (view is ViewGroup) {
    view.descendants.filter { filter.invoke(it) }
        .sortedByDescending { it.z }
        .firstOrNull(::isWithinBounds)
} else {
    if (filter.invoke(view) && isWithinBounds(view)) view else null
}

@Throws(IllegalStateException::class)
private fun checkMainThread() {
    check(Thread.currentThread() == Looper.getMainLooper().thread) {
        "Must be called from main thread!"
    }
}

/** Sets the given [html] on the TextView and supports both html links and plain text links. */
internal fun TextView.setHtml(
    context: Context,
    html: Spanned?,
    markdownOptions: MarkdownOptions?
) {
    movementMethod = LinkMovementMethodCompat.getInstance()

    text = if (html.isNullOrEmpty()) {
        null
    } else {
        val underlineLinks = markdownOptions.underlineLinks
        val linkColor = markdownOptions.resolvedLinkColor(context)
        val highlightColor = markdownOptions.resolveHighlightColor(context)
        val highlightCornerRadius = markdownOptions.resolveHighlightCornerRadius(context)

        html.toSpannable().apply {
            convertUrlSpans(underlineLinks, linkColor)
            // The highlight reads its geometry off the laid-out text, so we pass it via a
            // block so that RoundedBackgroundSpan can always operate on the current layout.
            convertHighlightSpans(highlightColor, highlightCornerRadius) { this@setHtml.layout }
            linkifyText(underlineLinks, linkColor)
        }
    }
}

/**
 * Finds standard background spans (from <span style='background-color'>)
 * and replaces them with custom RoundedBackgroundSpans.
 */
private fun Spannable.convertHighlightSpans(
    color: Int,
    cornerRadius: Float,
    layout: () -> Layout?
) {
    val bgSpans = getSpans(0, length, android.text.style.BackgroundColorSpan::class.java)
        ?: emptyArray()

    bgSpans.forEach { span ->
        val start = getSpanStart(span)
        val end = getSpanEnd(span)
        val flags = getSpanFlags(span)

        // Create the bubble with your specific styling
        val bubbleSpan = RoundedBackgroundSpan(
            backgroundColor = color,
            cornerRadius = cornerRadius,
            layout = layout
        )

        // Swap the spans
        removeSpan(span)
        setSpan(bubbleSpan, start, end, flags)
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
        view.context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri())
        )
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)

        // Optionally draw or remove the underline, defaulting to no underline.
        ds.isUnderlineText = underline ?: false

        // Optionally set the color, defaulting to the default color from ClickableSpan.
        ds.color = color ?: ds.color
    }
}

/**
 * Whether the length this view was given on an axis is slack inherited from an auto-sized ancestor.
 *
 * Walks up to the nearest view that can answer, the same way [borrowedPercentBase] does and for the
 * same reason: the wrapper views in between — button and toggle layouts, async layouts — hold no
 * size of their own and pass their spec straight through, so they'd only have to forward the answer
 * unchanged. The first ancestor that owns a length is the one that decides.
 *
 * Returns false when nothing above answers, which keeps the existing behaviour for any hierarchy
 * this doesn't model.
 */
internal fun View.hasAutoSizedAncestor(horizontal: Boolean): Boolean {
    var node = parent
    while (node is View) {
        if (node is AutoSizeProvider) return node.isAutoSized(horizontal)
        node = node.parent
    }
    return false
}

/**
 * Whether the length this view was offered on an axis was measured out of content it is part of,
 * rather than written by the author.
 *
 * Stronger than [hasAutoSizedAncestor], which reads the spec a view was handed: a stack that has
 * settled its own length hands its `auto` children exact lengths — its cross axis, or a ration of
 * its main one — and those children then report a length of their own. The number is still their
 * subtree's extent divided up, so the walk carries on past them.
 *
 * A view whose own item states a length stops it: that length is a box, however the stack above
 * arrived at its own.
 */
internal fun View.hasContentSizedAncestor(horizontal: Boolean): Boolean {
    // The frame a container puts around this very item declares the item's own length, so an
    // `auto` on it is this view restating what it already knows from its `ItemProperties` -- not an
    // ancestor that measured this view as part of its own content. From anywhere deeper it reads
    // like any other ancestor, so only the item's own frame is skipped.
    var node = parent
    if (node is ItemWrapper) node = (node as View).parent

    while (node is View) {
        val lp = node.layoutParams
        val declared = if (horizontal) lp?.width else lp?.height
        if (declared == ViewGroup.LayoutParams.WRAP_CONTENT) return true
        // A node holding a share of its parent settles nothing: keep walking, and let whatever it
        // is a share of answer. Stopping here reads `100%` of a content-sized parent as a length.
        if (node is AutoSizeProvider && !node.inheritsLength(horizontal)) {
            return node.isAutoSized(horizontal)
        }
        node = node.parent
    }
    return false
}
