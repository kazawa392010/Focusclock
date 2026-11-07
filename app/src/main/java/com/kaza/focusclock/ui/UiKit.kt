package com.kaza.focusclock.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class UiKit(val context: Context, val dark: Boolean, val accent: Int) {
    val background = Color.parseColor(if (dark) "#11131A" else "#F3F5F9")
    val surface = Color.parseColor(if (dark) "#1B1F2A" else "#FFFFFF")
    val surfaceAlt = Color.parseColor(if (dark) "#242938" else "#E9EDF5")
    val text = Color.parseColor(if (dark) "#F1F3F8" else "#18202D")
    val muted = Color.parseColor(if (dark) "#A3AABD" else "#687386")
    val line = Color.parseColor(if (dark) "#303747" else "#DDE2EB")
    val success = Color.parseColor("#59C69B")
    val warning = Color.parseColor("#E6A45C")
    val danger = Color.parseColor("#E17878")

    fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    fun text(value: String, size: Float = 14f, color: Int = text, bold: Boolean = false): TextView = TextView(context).apply {
        this.text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = true
        setLineSpacing(dp(2).toFloat(), 1f)
    }

    fun column(padding: Int = 0): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        if (padding > 0) setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    }

    fun row(gravity: Int = Gravity.CENTER_VERTICAL): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        this.gravity = gravity
    }

    fun card(): LinearLayout = column(16).apply {
        this.background = rounded(surface, 18)
        elevation = dp(1).toFloat()
    }

    fun rounded(color: Int, radius: Int = 14, strokeColor: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    fun button(label: String, filled: Boolean = false): TextView = text(label, 13f, if (filled) Color.WHITE else accent, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(11), dp(14), dp(11))
        this.background = rounded(if (filled) accent else surfaceAlt, 12, if (filled) null else line)
        isClickable = true
        isFocusable = true
    }

    fun pill(label: String, color: Int = accent): TextView = text(label, 11f, color, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(9), dp(5), dp(9), dp(5))
        this.background = rounded(if (dark) blend(color, Color.BLACK, 0.78f) else blend(color, Color.WHITE, 0.82f), 50)
    }

    fun divider(): View = View(context).apply {
        setBackgroundColor(line)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(10); bottomMargin = dp(10)
        }
    }

    fun gap(height: Int = 12): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    fun hGap(width: Int = 8): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(width), 1)
    }

    fun progress(progress: Int, color: Int = accent): ProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        this.max = 100
        this.progress = progress.coerceIn(0, 100)
        progressDrawable = android.graphics.drawable.LayerDrawable(arrayOf(
            rounded(surfaceAlt, 20),
            android.graphics.drawable.ClipDrawable(rounded(color, 20), Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)
        )).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
        minimumHeight = dp(8)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8))
    }

    fun addSpace(parent: LinearLayout, height: Int = 12) { parent.addView(gap(height)) }

    private fun blend(a: Int, b: Int, ratio: Float): Int {
        val inverse = 1f - ratio
        return Color.rgb(
            (Color.red(a) * inverse + Color.red(b) * ratio).toInt(),
            (Color.green(a) * inverse + Color.green(b) * ratio).toInt(),
            (Color.blue(a) * inverse + Color.blue(b) * ratio).toInt()
        )
    }
}
