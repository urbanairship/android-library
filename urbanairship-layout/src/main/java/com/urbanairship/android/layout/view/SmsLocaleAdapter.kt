/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.SpinnerAdapter
import android.widget.TextView
import com.urbanairship.android.layout.R
import com.urbanairship.android.layout.property.SmsLocale
import com.urbanairship.android.layout.property.TextInputTextAppearance
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.util.airshipEmojiFlag

internal class SmsLocaleAdapter(
    private val context: Context,
    private val locales: List<SmsLocale>,
    private val appearance: TextInputTextAppearance?
): BaseAdapter(), SpinnerAdapter {

    override fun getCount(): Int = locales.size

    override fun getItem(position: Int): SmsLocale = locales[position]

    override fun getItemId(position: Int): Long = locales[position].countryCode.hashCode().toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val result = View.inflate(context, R.layout.ua_layout_ic_dropdown, null)

        val textView = result.findViewById<TextView>(R.id.selected_item)
        textView.text = getItem(position).countryCode.airshipEmojiFlag
        if (appearance != null) {
            LayoutUtils.applyTextAppearance(textView, appearance)
        }

        return result
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val result = View.inflate(context, R.layout.ua_layout_ic_dropdown_item, null)
        val textView = result.findViewById<TextView>(R.id.selected_item)
        textView.text = getItem(position).displayValue()
        if (appearance != null) {
            LayoutUtils.applyTextAppearance(textView, appearance)
        }
        return result
    }
}

private fun SmsLocale.displayValue(): String {
    return listOf(
        countryCode.airshipEmojiFlag,
        countryCode,
        prefix
    ).joinToString(" ")
}
