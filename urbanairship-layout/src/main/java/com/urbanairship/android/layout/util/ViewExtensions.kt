package com.urbanairship.android.layout.util

import android.graphics.RectF
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_MASK
import android.view.MotionEvent.ACTION_UP
import android.view.View
import android.widget.EditText
import androidx.core.text.TextUtilsCompat
import com.urbanairship.UAirship
import com.urbanairship.android.layout.gestures.PagerGestureEvent
import com.urbanairship.android.layout.view.PagerView
import com.urbanairship.android.layout.view.ScoreView
import com.urbanairship.android.layout.widget.CheckableView
import com.urbanairship.android.layout.widget.CheckableViewAdapter
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart

@OptIn(FlowPreview::class)
internal fun EditText.textChanges(debounceMillis: Long = 100L): Flow<String> =
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
        .debounce(debounceMillis)
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

/** Returns view bounds in the view's coordinate space. */
internal val View.localBounds: RectF
    get() = RectF(0f, 0f, width.toFloat(), height.toFloat())

internal val View.isLayoutRtl: Boolean
    get() = TextUtilsCompat.getLayoutDirectionFromLocale(UAirship.shared().locale) == View.LAYOUT_DIRECTION_RTL

@Throws(IllegalStateException::class)
private fun checkMainThread() {
    check(Thread.currentThread() == Looper.getMainLooper().thread) {
        "Must be called from main thread!"
    }
}
