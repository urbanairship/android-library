/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter.ui.view

import android.os.Handler
import android.os.Looper
import androidx.annotation.RestrictTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Binds this [MessageView] to [viewModel]: wires the analytics/storage factories required to
 * render native messages and collects render states within [scope]. Collection always runs on
 * the main thread, regardless of [scope]'s own dispatcher, since [MessageView.render] must run
 * there. Close the returned [AutoCloseable] (or cancel [scope]) to unbind and report dismissal
 * for any active native message -- both are equivalent, since unbinding is driven by the
 * underlying collector job ending, not by which of the two triggered it. Teardown runs on the
 * main thread; if triggered from another thread, it may complete asynchronously shortly after
 * `close()`/cancellation returns rather than synchronously within the call.
 *
 * Use this overload when there is no [LifecycleOwner] to gate collection against (e.g., a
 * `MessageView` embedded by a cross-platform bridge that manages its own view lifecycle). For a
 * host with a `LifecycleOwner` (such as a `Fragment`), prefer the
 * [bind(viewModel, lifecycleOwner, minActiveState)][bind] overload instead.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public fun MessageView.bind(
    viewModel: MessageViewModel,
    scope: CoroutineScope,
): AutoCloseable = bindInternal(viewModel, reportDismissOnClose = true) { block ->
    scope.launch(Dispatchers.Main.immediate) { block() }
}

/**
 * Binds this [MessageView] to [viewModel]: wires the analytics/storage factories required to
 * render native messages and collects render states while [lifecycleOwner] is at least
 * [minActiveState].
 *
 * There's nothing for the caller to tear down: the collector job is scoped to
 * [lifecycleOwner]'s own `lifecycleScope`, so it's canceled automatically once the Lifecycle
 * drops below [minActiveState] or is destroyed -- there is no returned handle. Unlike the
 * [bind(viewModel, scope)][bind] overload, this never reports dismissal on its own, since a
 * `LifecycleOwner`'s view may be destroyed and recreated across configuration changes while
 * [viewModel] survives -- reporting a dismiss here would fire on every rotation. Callers with a
 * `LifecycleOwner` are expected to report dismissal explicitly (e.g., when the message is
 * cleared) using their own internal access to `MessageView`.
 */
internal fun MessageView.bind(
    viewModel: MessageViewModel,
    lifecycleOwner: LifecycleOwner,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
) {
    bindInternal(viewModel, reportDismissOnClose = false) { block ->
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(minActiveState, block)
        }
    }
}

/**
 * Shared wiring for both [bind] overloads. Cancels any previously active binding on this View
 * before wiring the new one, so calling `bind()` again without closing the prior result can't
 * leak the old collector job -- and, since a rebind is always a deliberate action to display
 * something else (unlike a lifecycle-driven `close()`, where [reportDismissOnClose] avoids a
 * false dismiss on Fragment rotation), reports dismissal for any native message left active by
 * the superseded binding.
 *
 * Teardown (clearing factories, and dismiss reporting when [reportDismissOnClose]) is driven by
 * the collector job's completion via [Job.invokeOnCompletion], not by the returned
 * [AutoCloseable] directly -- this way it runs the same whether the caller calls `close()` or
 * simply cancels the [CoroutineScope]/[LifecycleOwner] `launch` was given, and it only applies if
 * this is still the active binding, so a stale completion from a binding already superseded by a
 * later `bind()` call is a no-op. Since job completion can be signalled from any thread, the
 * teardown itself is dispatched onto the main thread, as it may touch the view hierarchy.
 */
private fun MessageView.bindInternal(
    viewModel: MessageViewModel,
    reportDismissOnClose: Boolean,
    launch: (block: suspend CoroutineScope.() -> Unit) -> Job,
): AutoCloseable {
    if (activeBindingJob != null) {
        activeBindingJob?.cancel()
        onDismissed()
    }

    analyticsFactory = { onDismiss ->
        viewModel.currentMessage?.let { viewModel.makeAnalytics(it, onDismiss) }
    }
    storageFactory = { viewModel.viewStateStorage }

    val job = launch { viewModel.states.collect(::render) }
    activeBindingJob = job

    job.invokeOnCompletion {
        runOnMainThread {
            if (activeBindingJob === job) {
                activeBindingJob = null
                analyticsFactory = null
                storageFactory = null
                if (reportDismissOnClose) {
                    onDismissed()
                }
            }
        }
    }

    return AutoCloseable { job.cancel() }
}

private fun runOnMainThread(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        block()
    } else {
        Handler(Looper.getMainLooper()).post(block)
    }
}
