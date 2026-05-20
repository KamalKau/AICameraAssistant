package com.example.aicameraassistant

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

@Suppress("DEPRECATION")
fun showStatusPopup(
    context: Context,
    title: String,
    detail: String,
    badge: String,
    accentColor: Int,
    gravity: Int = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
    yOffsetDp: Int = 72
) {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        this.gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(10), dp(16), dp(10))
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(18, 24, 38), Color.rgb(9, 12, 20))
        ).apply {
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), Color.argb(72, 255, 255, 255))
        }
        elevation = dp(8).toFloat()
    }

    val badgeView = TextView(context).apply {
        text = badge
        setTextColor(Color.WHITE)
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        this.gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(accentColor)
        }
    }
    container.addView(
        badgeView,
        LinearLayout.LayoutParams(dp(34), dp(34)).apply {
            marginEnd = dp(11)
        }
    )

    val textColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    textColumn.addView(
        TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }
    )
    textColumn.addView(
        TextView(context).apply {
            text = detail
            setTextColor(Color.argb(178, 255, 255, 255))
            textSize = 12f
            includeFontPadding = false
        }
    )
    container.addView(textColumn)

    Toast(context).apply {
        duration = Toast.LENGTH_SHORT
        view = container
        setGravity(gravity, 0, dp(yOffsetDp))
        show()
    }
}
