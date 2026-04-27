package com.mysimplemeditation.app.ui.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.mysimplemeditation.app.R
import com.mysimplemeditation.app.data.entities.TriggerEntity

class TriggerTimePickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : android.widget.LinearLayout(context, attrs, defStyleAttr) {

    private val txtDisplay: TextView
    private val btnClear: MaterialButton
    private val btnStart: MaterialButton
    private val btnEnd: MaterialButton

    private var digits: String = ""
    private var isSpecial: Boolean = false
    private var specialValue: Int = 0

    var value: Int
        get() = if (isSpecial) specialValue else digitsToSeconds()
        set(v) {
            if (v == TriggerEntity.TIME_START || v == TriggerEntity.TIME_END) {
                isSpecial = true
                specialValue = v
                digits = ""
            } else {
                isSpecial = false
                specialValue = 0
                digits = secondsToDigits(v)
            }
            updateDisplay()
        }

    private fun digitsToSeconds(): Int {
        if (digits.isEmpty()) return 0
        val padded = digits.padStart(6, '0')
        val h = padded.substring(0, padded.length - 4).toIntOrNull() ?: 0
        val m = padded.substring(padded.length - 4, padded.length - 2).toIntOrNull() ?: 0
        val s = padded.substring(padded.length - 2).toIntOrNull() ?: 0
        return h * 3600 + m * 60 + s
    }

    private fun secondsToDigits(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format("%d%02d%02d", h, m, s).trimStart('0').ifEmpty { "" }
        } else if (m > 0) {
            String.format("%d%02d", m, s).trimStart('0').ifEmpty { "" }
        } else if (s > 0) {
            s.toString()
        } else {
            ""
        }
    }

    private fun formatHMS(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format("%dh%02dm%02ds", h, m, s)
    }

    init {
        orientation = VERTICAL
        val view = LayoutInflater.from(context).inflate(R.layout.trigger_time_picker, this, true)

        txtDisplay = view.findViewById(R.id.txtTimeDisplay)
        btnClear = view.findViewById(R.id.btnClearTime)
        btnStart = view.findViewById(R.id.btnStart)
        btnEnd = view.findViewById(R.id.btnEnd)

        val digitIds = intArrayOf(
            R.id.btnDigit0, R.id.btnDigit1, R.id.btnDigit2, R.id.btnDigit3,
            R.id.btnDigit4, R.id.btnDigit5, R.id.btnDigit6, R.id.btnDigit7,
            R.id.btnDigit8, R.id.btnDigit9
        )
        for (i in digitIds.indices) {
            view.findViewById<MaterialButton>(digitIds[i]).setOnClickListener { appendDigit(i) }
        }

        btnClear.setOnClickListener { clear() }
        btnStart.setOnClickListener { setStart() }
        btnEnd.setOnClickListener { setEnd() }
        view.findViewById<MaterialButton>(R.id.btnBackspace).setOnClickListener { backspace() }

        updateDisplay()
    }

    private fun appendDigit(digit: Int) {
        isSpecial = false
        specialValue = 0
        if (digits.length < 6) {
            digits += digit.toString()
            updateDisplay()
        }
    }

    private fun clear() {
        digits = ""
        isSpecial = false
        specialValue = 0
        updateDisplay()
    }

    private fun backspace() {
        isSpecial = false
        specialValue = 0
        if (digits.isNotEmpty()) {
            digits = digits.dropLast(1)
        }
        updateDisplay()
    }

    private fun setStart() {
        isSpecial = true
        specialValue = TriggerEntity.TIME_START
        digits = ""
        updateDisplay()
    }

    private fun setEnd() {
        isSpecial = true
        specialValue = TriggerEntity.TIME_END
        digits = ""
        updateDisplay()
    }

    private fun updateDisplay() {
        txtDisplay.text = if (isSpecial) {
            TriggerEntity.formatTimeLabel(specialValue)
        } else {
            formatHMS(digitsToSeconds())
        }
    }
}
