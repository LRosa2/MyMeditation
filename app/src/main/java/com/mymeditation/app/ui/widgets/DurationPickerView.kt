package com.mymeditation.app.ui.widgets

import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.mymeditation.app.R

class DurationPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.textInputFilledStyle
) : com.google.android.material.textfield.TextInputLayout(context, attrs, defStyleAttr) {

    private val editDuration: TextInputEditText
    private var currentSeconds: Int = 0

    fun getDurationSeconds(): Int = currentSeconds

    fun setDurationSeconds(seconds: Int) {
        currentSeconds = seconds
        updateDisplay()
    }

    init {
        editDuration = TextInputEditText(context).apply {
            id = R.id.editDuration
            isFocusable = false
            isFocusableInTouchMode = false
            isCursorVisible = false
            inputType = android.text.InputType.TYPE_NULL
        }
        addView(editDuration)
        editDuration.setOnClickListener { showKeypadDialog() }
        updateDisplay()
    }

    private fun updateDisplay() {
        val h = currentSeconds / 3600
        val m = (currentSeconds % 3600) / 60
        val s = currentSeconds % 60
        editDuration.setText(if (h > 0) {
            String.format("%dH%02dM%02dS", h, m, s)
        } else if (m > 0) {
            String.format("%dM%02dS", m, s)
        } else {
            String.format("%dS", s)
        })
    }

    private fun showKeypadDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.duration_time_picker, null)
        val txtDisplay = dialogView.findViewById<TextView>(R.id.txtDurationDisplay)
        val btnClear = dialogView.findViewById<MaterialButton>(R.id.btnClearDuration)

        var tempSeconds = currentSeconds

        fun updateTempDisplay() {
            val h = tempSeconds / 3600
            val m = (tempSeconds % 3600) / 60
            val s = tempSeconds % 60
            txtDisplay.text = if (h > 0) {
                String.format("%dH%02dM%02dS", h, m, s)
            } else if (m > 0) {
                String.format("%dM%02dS", m, s)
            } else {
                String.format("%dS", s)
            }
        }

        val digitIds = intArrayOf(
            R.id.btnDigit0, R.id.btnDigit1, R.id.btnDigit2, R.id.btnDigit3,
            R.id.btnDigit4, R.id.btnDigit5, R.id.btnDigit6, R.id.btnDigit7,
            R.id.btnDigit8, R.id.btnDigit9
        )
        for (i in digitIds.indices) {
            dialogView.findViewById<MaterialButton>(digitIds[i]).setOnClickListener {
                val newSeconds = tempSeconds * 10 + i
                if (newSeconds <= 59999) {
                    tempSeconds = newSeconds
                    updateTempDisplay()
                }
            }
        }

        dialogView.findViewById<MaterialButton>(R.id.btnBackspace).setOnClickListener {
            tempSeconds = tempSeconds / 10
            updateTempDisplay()
        }

        btnClear.setOnClickListener {
            tempSeconds = 0
            updateTempDisplay()
        }

        updateTempDisplay()

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                currentSeconds = tempSeconds
                updateDisplay()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialogView.findViewById<MaterialButton>(R.id.btnOk).setOnClickListener {
            currentSeconds = tempSeconds
            updateDisplay()
            dialog.dismiss()
        }

        dialog.show()
    }
}
