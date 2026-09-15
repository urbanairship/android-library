/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine

internal sealed class DelegatePreparerResult<out DataOut> {
    data class Prepared<DataOut>(val data: DataOut) : DelegatePreparerResult<DataOut>()
    data object Cancel : DelegatePreparerResult<Nothing>()
    data object Skip : DelegatePreparerResult<Nothing>()
    data object Penalize : DelegatePreparerResult<Nothing>()
}
