package com.example.aicameraassistant

import android.content.Context
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import org.webrtc.SurfaceViewRenderer

class ControllerPreviewContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    val renderer = SurfaceViewRenderer(context)

    private var videoAspectRatio = 9f / 16f
    private var rotateContent = false
    private var displayWidthPx = 0
    private var displayHeightPx = 0
    var onVideoRectChanged: ((RectF) -> Unit)? = null

    init {
        clipChildren = true
        clipToPadding = true
        addView(
            renderer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun setVideoLayout(width: Int, height: Int, rotateClockwise: Boolean) {
        if (width <= 0 || height <= 0) return

        val nextAspectRatio = width.toFloat() / height.toFloat()
        if (videoAspectRatio == nextAspectRatio && rotateContent == rotateClockwise) return

        videoAspectRatio = nextAspectRatio
        rotateContent = rotateClockwise
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = View.MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = View.MeasureSpec.getSize(heightMeasureSpec)

        setMeasuredDimension(availableWidth, availableHeight)

        if (availableWidth <= 0 || availableHeight <= 0) {
            measureChildren(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val containerAspectRatio = availableWidth.toFloat() / availableHeight.toFloat()

        val displayWidth: Int
        val displayHeight: Int

        if (videoAspectRatio > containerAspectRatio) {
            displayWidth = availableWidth
            displayHeight = (availableWidth / videoAspectRatio).toInt()
        } else {
            displayHeight = availableHeight
            displayWidth = (availableHeight * videoAspectRatio).toInt()
        }

        displayWidthPx = displayWidth
        displayHeightPx = displayHeight

        val childWidth: Int
        val childHeight: Int

        if (rotateContent) {
            childWidth = displayHeight
            childHeight = displayWidth
        } else {
            childWidth = displayWidth
            childHeight = displayHeight
        }

        val childWidthSpec = View.MeasureSpec.makeMeasureSpec(childWidth, View.MeasureSpec.EXACTLY)
        val childHeightSpec = View.MeasureSpec.makeMeasureSpec(childHeight, View.MeasureSpec.EXACTLY)
        renderer.measure(childWidthSpec, childHeightSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = renderer
        val childWidth = child.measuredWidth
        val childHeight = child.measuredHeight

        val childLeft = (measuredWidth - childWidth) / 2
        val childTop = (measuredHeight - childHeight) / 2

        renderer.pivotX = childWidth / 2f
        renderer.pivotY = childHeight / 2f
        renderer.rotation = if (rotateContent) 90f else 0f

        child.layout(
            childLeft,
            childTop,
            childLeft + childWidth,
            childTop + childHeight
        )

        val visibleLeft = (measuredWidth - displayWidthPx) / 2f
        val visibleTop = (measuredHeight - displayHeightPx) / 2f
        onVideoRectChanged?.invoke(
            RectF(
                visibleLeft,
                visibleTop,
                visibleLeft + displayWidthPx,
                visibleTop + displayHeightPx
            )
        )
    }
}
