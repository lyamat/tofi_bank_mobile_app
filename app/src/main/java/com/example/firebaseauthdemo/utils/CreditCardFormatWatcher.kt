package com.example.firebaseauthdemo.utils

import android.annotation.SuppressLint
import android.text.Editable
import com.google.android.material.internal.TextWatcherAdapter

@SuppressLint("RestrictedApi")
class CreditCardFormatWatcher : TextWatcherAdapter() {

    @SuppressLint("RestrictedApi")
    override fun afterTextChanged(s: Editable) {
        super.afterTextChanged(s)

        if (s.isEmpty()) return

        s.forEachIndexed { index, c ->
            val spaceIndex = index == 4 || index == 9 || index == 14
            when {
                !spaceIndex && !c.isDigit() -> s.delete(index, index + 1)
                spaceIndex && !c.isWhitespace() -> s.insert(index, " ")
            }
        }
        if (s.isNotEmpty()) {
            if (s.last().isWhitespace())
                s.delete(s.length - 1, s.length)
        }

    }
}