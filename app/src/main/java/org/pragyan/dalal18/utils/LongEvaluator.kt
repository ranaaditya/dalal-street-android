package org.pragyan.dalal18.utils

import android.animation.TypeEvaluator
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

class LongEvaluator : TypeEvaluator<Long> {
    override fun evaluate(fraction: Float, startValue: Long, endValue: Long): Long {
        return ((endValue.toFloat() - startValue.toFloat()) * fraction).toLong() + startValue
    }
}

fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}