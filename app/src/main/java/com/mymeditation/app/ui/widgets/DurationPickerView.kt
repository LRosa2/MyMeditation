package com.mymeditation.app.ui.widgets

import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText
import com.mymeditation.app.R

/**
 * A custom view that shows a single text field for duration input.
 * When tapped, opens a dialog with NumberPickers for minutes and seconds.
 *
 * Modes:
 * - MODE_DURATION (default): shows MM:SS format, pickers for minutes and seconds
 * - MODE_SECONDS: shows total seconds, single picker for seconds
 */
class DurationPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.textInputFilledStyle
) : com.google.android.material.textfield.TextInputLayout(context, attrs, defStyleAttr) {

    companion object {
        const val MODE_DURATION = 0
        const val MODE_SECONDS = 1
    }

    private val editDuration: TextInputEditText
    private var currentSeconds = 0
    private var mode = MODE_DURATION
    private var maxMinutes = 99
    private var maxSeconds = 59

    init {
        // Read durationMode from XML attributes
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.DurationPickerView)
            mode = typedArray.getInt(R.styleable.DurationPickerView_durationMode, MODE_DURATION)
            typedArray.recycle()
        }

        // Add TextInputEditText as direct child of this TextInputLayout
        editDuration = TextInputEditText(context).apply {
            id = R.id.editDuration
            isFocusable = false
            isFocusableInTouchMode = false
            isCursorVisible = false
            inputType = android.text.InputType.TYPE_NULL
        }
        addView(editDuration)

        editDuration.setOnClickListener { showPickerDialog() }

        updateDisplay()
    }

    fun setMode(mode: Int) {
        this.mode = mode
        updateDisplay()
    }

    fun getDurationSeconds(): Int = currentSeconds

    fun setDurationSeconds(seconds: Int) {
        currentSeconds = seconds
        updateDisplay()
    }

    private fun updateDisplay() {
        if (mode == MODE_SECONDS) {
            editDuration.setText(formatSeconds(currentSeconds))
        } else {
            editDuration.setText(formatDuration(currentSeconds))
        }
    }

    private fun formatDuration(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun formatSeconds(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) {
            String.format("%d min %02d sec", m, s)
        } else {
            String.format("%d sec", s)
        }
    }

    private fun showPickerDialog() {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_duration_picker, null)

        val pickerMinutes = dialogView.findViewById<NumberPicker>(R.id.pickerMinutes)
        val pickerSeconds = dialogView.findViewById<NumberPicker>(R.id.pickerSeconds)
        val layoutMinutes = dialogView.findViewById<LinearLayout>(R.id.layoutMinutes)
        val separator = dialogView.findViewById<TextView>(R.id.separator)
        val layoutSeconds = dialogView.findViewById<LinearLayout>(R.id.layoutSeconds)

        if (mode == MODE_SECONDS) {
            layoutMinutes.visibility = View.GONE
            separator.visibility = View.GONE

            pickerSeconds.minValue = 0
            pickerSeconds.maxValue = 5999
            pickerSeconds.value = currentSeconds
            pickerSeconds.wrapSelectorWheel = false
        } else {
            pickerMinutes.minValue = 0
            pickerMinutes.maxValue = maxMinutes
            pickerMinutes.value = currentSeconds / 60
            pickerMinutes.wrapSelectorWheel = true

            pickerSeconds.minValue = 0
            pickerSeconds.maxValue = maxSeconds
            pickerSeconds.value = currentSeconds % 60
            pickerSeconds.wrapSelectorWheel = true
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (mode == MODE_SECONDS) {
                    currentSeconds = pickerSeconds.value
                } else {
                    currentSeconds = pickerMinutes.value * 60 + pickerSeconds.value
                }
                updateDisplay()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.show()
    }
}
