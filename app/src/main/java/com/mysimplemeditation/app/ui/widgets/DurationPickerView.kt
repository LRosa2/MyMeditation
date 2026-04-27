package com.mysimplemeditation.app.ui.widgets

import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.mysimplemeditation.app.R

class DurationPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val textInputLayout: TextInputLayout
    private val editDuration: TextInputEditText
    private var currentSeconds: Int = 0

    fun getDurationSeconds(): Int = currentSeconds

    fun setDurationSeconds(seconds: Int) {
        currentSeconds = seconds
        updateDisplay()
    }

    init {
        orientation = VERTICAL

        // Read hint from attrs
        val hintText = attrs?.let {
            val ta = context.obtainStyledAttributes(it, intArrayOf(android.R.attr.hint))
            val hint = ta.getString(0)
            ta.recycle()
            hint
        } ?: ""

        // Create TextInputLayout with TextInputEditText
        textInputLayout = TextInputLayout(context, attrs, defStyleAttr).apply {
            if (hintText.isNotEmpty()) this.hint = hintText
        }

        editDuration = TextInputEditText(context).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isCursorVisible = false
            inputType = android.text.InputType.TYPE_NULL
            setPadding(paddingLeft, paddingTop + 24, paddingRight, paddingBottom)
        }

        textInputLayout.addView(editDuration)
        addView(textInputLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        editDuration.setOnClickListener { showKeypadDialog() }
        updateDisplay()
    }

    private fun formatHMS(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format("%dh%02dm%02ds", h, m, s)
    }

    private fun updateDisplay() {
        editDuration.setText(formatHMS(currentSeconds))
    }

    private fun showKeypadDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.duration_time_picker, null)
        val txtDisplay = dialogView.findViewById<TextView>(R.id.txtDurationDisplay)
        val btnClear = dialogView.findViewById<MaterialButton>(R.id.btnClearDuration)

        var digits = ""

        fun digitsToSeconds(): Int {
            if (digits.isEmpty()) return 0
            val padded = digits.padStart(6, '0')
            val h = padded.substring(0, padded.length - 4).toIntOrNull() ?: 0
            val m = padded.substring(padded.length - 4, padded.length - 2).toIntOrNull() ?: 0
            val s = padded.substring(padded.length - 2).toIntOrNull() ?: 0
            return h * 3600 + m * 60 + s
        }

        fun updateTempDisplay() {
            txtDisplay.text = formatHMS(digitsToSeconds())
        }

        val digitIds = intArrayOf(
            R.id.btnDigit0, R.id.btnDigit1, R.id.btnDigit2, R.id.btnDigit3,
            R.id.btnDigit4, R.id.btnDigit5, R.id.btnDigit6, R.id.btnDigit7,
            R.id.btnDigit8, R.id.btnDigit9
        )
        for (i in digitIds.indices) {
            dialogView.findViewById<MaterialButton>(digitIds[i]).setOnClickListener {
                if (digits.length < 6) {
                    digits += i.toString()
                    updateTempDisplay()
                }
            }
        }

        dialogView.findViewById<MaterialButton>(R.id.btnBackspace).setOnClickListener {
            if (digits.isNotEmpty()) {
                digits = digits.dropLast(1)
                updateTempDisplay()
            }
        }

        btnClear.setOnClickListener {
            digits = ""
            updateTempDisplay()
        }

        val h = currentSeconds / 3600
        val m = (currentSeconds % 3600) / 60
        val s = currentSeconds % 60
        digits = if (h > 0) {
            String.format("%d%02d%02d", h, m, s).trimStart('0').ifEmpty { "" }
        } else if (m > 0) {
            String.format("%d%02d", m, s).trimStart('0').ifEmpty { "" }
        } else if (s > 0) {
            s.toString()
        } else {
            ""
        }
        updateTempDisplay()

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                currentSeconds = digitsToSeconds()
                updateDisplay()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialogView.findViewById<MaterialButton>(R.id.btnOk).setOnClickListener {
            currentSeconds = digitsToSeconds()
            updateDisplay()
            dialog.dismiss()
        }

        dialog.show()
    }
}
