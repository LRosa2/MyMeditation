package com.mymeditation.app.ui.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.mymeditation.app.R
import com.mymeditation.app.data.entities.TriggerEntity

class TriggerTimePickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : android.widget.LinearLayout(context, attrs, defStyleAttr) {

    private val txtDisplay: TextView
    private val btnClear: MaterialButton
    private val btnStart: MaterialButton
    private val btnEnd: MaterialButton

    private var currentSeconds: Int = 0
    private var isSpecial: Boolean = false // true if Start or End selected
    private var specialValue: Int = 0 // TIME_START or TIME_END

    var value: Int
        get() = if (isSpecial) specialValue else currentSeconds
        set(v) {
            if (v == TriggerEntity.TIME_START || v == TriggerEntity.TIME_END) {
                isSpecial = true
                specialValue = v
                currentSeconds = 0
            } else {
                isSpecial = false
                specialValue = 0
                currentSeconds = v
            }
            updateDisplay()
        }

    init {
        orientation = VERTICAL
        val view = LayoutInflater.from(context).inflate(R.layout.trigger_time_picker, this, true)

        txtDisplay = view.findViewById(R.id.txtTimeDisplay)
        btnClear = view.findViewById(R.id.btnClearTime)
        btnStart = view.findViewById(R.id.btnStart)
        btnEnd = view.findViewById(R.id.btnEnd)

        // Digit buttons
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

        updateDisplay()
    }

    private fun appendDigit(digit: Int) {
        isSpecial = false
        specialValue = 0
        val newSeconds = currentSeconds * 10 + digit
        // Cap at 59999 seconds (16h 39m 59s) to prevent overflow
        if (newSeconds <= 59999) {
            currentSeconds = newSeconds
        }
        updateDisplay()
    }

    private fun clear() {
        currentSeconds = 0
        isSpecial = false
        specialValue = 0
        updateDisplay()
    }

    private fun setStart() {
        isSpecial = true
        specialValue = TriggerEntity.TIME_START
        currentSeconds = 0
        updateDisplay()
    }

    private fun setEnd() {
        isSpecial = true
        specialValue = TriggerEntity.TIME_END
        currentSeconds = 0
        updateDisplay()
    }

    private fun updateDisplay() {
        txtDisplay.text = if (isSpecial) {
            TriggerEntity.formatTimeLabel(specialValue)
        } else {
            TriggerEntity.formatTimeLabel(currentSeconds)
        }
    }
}
