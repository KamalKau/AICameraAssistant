package com.example.aicameraassistant

import android.content.Context
import android.util.AttributeSet
import android.view.View
import org.webrtc.SurfaceViewRenderer

class FixedAspectSurfaceViewRenderer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceViewRenderer(context, attrs) {

    private var frameWidth = 0
    private var frameHeight = 0

    fun updateFrameSize(width: Int, height: Int, rotation: Int) {
        if (width <= 0 || height <= 0) return

        val measuredFrameWidth: Int
        val measuredFrameHeight: Int

        if (rotation == 90 || rotation == 270) {
            measuredFrameWidth = height
            measuredFrameHeight = width
        } else {
            measuredFrameWidth = width
            measuredFrameHeight = height
        }

        if (frameWidth == measuredFrameWidth && frameHeight == measuredFrameHeight) return

        frameWidth = measuredFrameWidth
        frameHeight = measuredFrameHeight
        post { requestLayout() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (frameWidth <= 0 || frameHeight <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val maxWidth = View.MeasureSpec.getSize(widthMeasureSpec)
        val maxHeight = View.MeasureSpec.getSize(heightMeasureSpec)

        if (maxWidth <= 0 || maxHeight <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val frameAspectRatio = frameWidth.toFloat() / frameHeight.toFloat()
        val containerAspectRatio = maxWidth.toFloat() / maxHeight.toFloat()

        val measuredWidth: Int
        val measuredHeight: Int

        if (frameAspectRatio > containerAspectRatio) {
            measuredWidth = maxWidth
            measuredHeight = (maxWidth / frameAspectRatio).toInt()
        } else {
            measuredHeight = maxHeight
            measuredWidth = (maxHeight * frameAspectRatio).toInt()
        }

        setMeasuredDimension(measuredWidth, measuredHeight)
    }
}
