/* Copyright Airship and Contributors */
package com.urbanairship.devapp.embedded

/**
 * The manual test plan for [EmbeddedAiScreen], shown in a sheet from the screen itself so the
 * steps are next to the controls they talk about.
 *
 * @param id Short label, e.g. "A".
 * @param title What the case is checking.
 * @param covers The review finding or commit it pins, when it has one.
 * @param setup Screen state to get into before starting, beyond the usual reset.
 * @param steps What to do, in order.
 * @param pass What a working build does.
 * @param fail What a broken one does, when that is worth spelling out.
 * @param note Anything easy to misread while running it.
 */
internal data class EmbeddedAiTestCase(
    val id: String,
    val title: String,
    val covers: String? = null,
    val setup: String? = null,
    val steps: List<String>,
    val pass: String,
    val fail: String? = null,
    val note: String? = null
)

internal const val TEST_PLAN_PREAMBLE: String =
    "Every case runs entirely on this screen. Navigating away disposes the state holder and " +
            "starts a fresh session, which invalidates the run.\n\n" +
            "Start each case with Dismiss all, then Reset count."

internal val EMBEDDED_AI_TEST_PLAN: List<EmbeddedAiTestCase> = listOf(
    EmbeddedAiTestCase(
        id = "A",
        title = "It ranks at all",
        steps = listOf(
            "Tap Cat sale. It displays right away and Evaluations stays 0 — one candidate is " +
                    "not worth a round trip.",
            "Tap Dog clearance. Brief placeholder, then content. Evaluations: 1."
        ),
        pass = "Cat sale is showing. Dog has the better priority (0 vs 20), so Cat winning " +
                "means the model actually ranked rather than priority order leaking through.",
        fail = "Dog clearance is showing — something fell back. Check the log for \"skipped\" " +
                "or \"nothing to rank on\".",
        note = "The placeholder in step 2 is correct: it is the first ranking, with nothing " +
                "committed yet."
    ),
    EmbeddedAiTestCase(
        id = "B",
        title = "State survives a collection restart",
        covers = "The session fix",
        steps = listOf(
            "Tap Cat sale, Dog clearance, Loyalty. Let it settle.",
            "Reset count now, so the baseline is 0.",
            "With Unstable filter lambda off, tap Recompose three times. Expect 0, no change, " +
                    "no flash — nothing restarts.",
            "Turn Unstable filter lambda on. Expect still 0, card unchanged. A collection " +
                    "restarted here and the session absorbed it.",
            "Tap Recompose three more times."
        ),
        pass = "Evaluations stays 0 throughout and the card never flashes.",
        fail = "The count climbs 1, 2, 3 with a placeholder flash each time. That is the " +
                "pre-fix behaviour, where the state belonged to the collection."
    ),
    EmbeddedAiTestCase(
        id = "C",
        title = "allowDisplayInterruptions",
        setup = "Turn Show carousel on first — order is only visible in the list, and a single " +
                "view cannot tell \"appended\" apart from \"was not ranked first anyway\".",
        steps = listOf(
            "Leave allowDisplayInterruptions off (the default).",
            "Tap Cat sale and Dog clearance. Let it rank. Note the carousel page order.",
            "Tap Free shipping.",
            "Turn allowDisplayInterruptions on, Dismiss all, Reset count, and repeat steps 2–3."
        ),
        pass = "With it off: the card on screen never blanks, and Free shipping is the last " +
                "page whatever it scored. With it on: Free shipping can take a ranked position.",
        note = "Two surfaces are live with the carousel on, each with its own session, so every " +
                "count goes up by 2."
    ),
    EmbeddedAiTestCase(
        id = "D",
        title = "Filtered instances never reach the model",
        setup = "Turn Filter out dog content on BEFORE queuing. Filtering after a ranking does " +
                "not re-ask, so there would be no new prompt to inspect.",
        steps = listOf(
            "Tap Cat sale, Dog clearance, Loyalty.",
            "In the log, find the AI [embedded_selection] entry and read its prompt."
        ),
        pass = "The candidate list holds Cat and Loyalty only — no \"dog beds, leashes, and " +
                "chew toys\".",
        fail = "Dog is in the prompt but not on screen. The filter ran after candidate " +
                "selection, so ineligible content skewed the scores of content that can display."
    ),
    EmbeddedAiTestCase(
        id = "E",
        title = "No model never blanks the view",
        covers = "Review finding 9",
        setup = "Remove gemini.apiKey from local.properties, rebuild, reinstall.",
        steps = listOf("Tap Cat sale, Dog clearance, Loyalty."),
        pass = "Content appears immediately with no placeholder frame at all, showing Dog " +
                "clearance — priority order, and priority 0 wins.",
        fail = "Any blank frame before content. Availability is synchronous, so there is no " +
                "round trip to show a placeholder through.",
        note = "Restore the key and rebuild before running anything else."
    ),
    EmbeddedAiTestCase(
        id = "F",
        title = "A malformed context item costs only itself",
        covers = "Review finding 11",
        steps = listOf("Tap Free shipping on its own."),
        pass = "The purple card renders, and the log shows \"Dropping malformed " +
                "content_description additional_context item\" twice.",
        fail = "Nothing renders — an advisory field took the whole payload down with it.",
        note = "The warnings appear at queue time, which is when the layout parses."
    ),
    EmbeddedAiTestCase(
        id = "G",
        title = "Strategy",
        steps = listOf(
            "Queue three candidates, let it rank, note the order.",
            "Toggle PRIORITY_THEN_SCORE."
        ),
        pass = "The new order leads with priority among scored candidates — Dog 0, Loyalty 10, " +
                "Cat 20.",
        note = "A placeholder and a fresh evaluation here are correct, not a regression: the " +
                "config changed, and a ranking made under the old one should not be kept."
    )
)

internal const val TEST_PLAN_FOOTNOTE: String =
    "Two behaviours that look like failures but are not.\n\n" +
            "Rotating, or navigating away and back, gives a placeholder and a new evaluation. " +
            "The session is remember, not rememberSaveable, and Activity recreation destroys " +
            "it either way — the view-system holder is a field on the View, so it goes too.\n\n" +
            "Queuing the second candidate always shows a placeholder. That is the first " +
            "ranking, with nothing committed to hold the screen with."
