package com.urbanairship.debug.automation

data class AutomationDetail(val title: String, val body: String? = null, val callback: (() -> Unit?)? = null)
