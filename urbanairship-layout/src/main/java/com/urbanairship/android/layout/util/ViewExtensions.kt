package com.urbanairship.android.layout.util

import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_MASK
import android.view.MotionEvent.ACTION_UP
import android.view.View
import android.widget.EditText
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

internal val MotionEvent.isActionUp: Boolean
    get() = action and ACTION_MASK == ACTION_UP

@Throws(IllegalStateException::class)
private fun checkMainThread() {
    check(Thread.currentThread() == Looper.getMainLooper().thread) {
        "Must be called from main thread!"
    }
}
