package com.example.aicameraassistant

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.animation.doOnEnd
import org.webrtc.SurfaceViewRenderer

class ControllerPreviewContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    val renderer = SurfaceViewRenderer(context)
    private val faceOverlay = ControllerFaceDetectionOverlayView(context)

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
        renderer.setZOrderMediaOverlay(false)
        addView(
            faceOverlay,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        faceOverlay.bringToFront()
    }

    fun setFaceDetectionOverlay(bounds: NormalizedFaceBounds, visible: Boolean) {
        setFaceDetectionOverlay(
            bounds = if (bounds.isValid()) listOf(bounds) else emptyList(),
            visible = visible
        )
    }

    fun setFaceDetectionOverlay(bounds: List<NormalizedFaceBounds>, visible: Boolean) {
        faceOverlay.setFaceDetectionOverlay(bounds, visible)
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
        faceOverlay.layout(0, 0, measuredWidth, measuredHeight)
        faceOverlay.bringToFront()

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
        faceOverlay.setVideoRect(
            RectF(
                visibleLeft,
                visibleTop,
                visibleLeft + displayWidthPx,
                visibleTop + displayHeightPx
            )
        )
    }
}

private class ControllerFaceDetectionOverlayView(context: Context) : View(context) {
    private val videoRect = RectF()
    private var bounds = emptyList<NormalizedFaceBounds>()
    private var overlayAlpha = 0f
    private var alphaAnimator: ValueAnimator? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(9, 255, 255, 255)
    }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = Color.argb(148, 255, 255, 255)
    }

    fun setVideoRect(rect: RectF) {
        videoRect.set(rect)
        invalidate()
    }

    fun setFaceDetectionOverlay(nextBounds: List<NormalizedFaceBounds>, visible: Boolean) {
        bounds = nextBounds
        animateAlpha(if (visible && nextBounds.any { it.isValid() }) 1f else 0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (overlayAlpha <= 0.01f || videoRect.isEmpty) return

        val density = resources.displayMetrics.density
        val radius = 34f * density
        val minBoxWidth = 96f * density
        val minBoxHeight = 128f * density

        fillPaint.alpha = (9 * overlayAlpha).toInt().coerceIn(0, 255)
        framePaint.alpha = (148 * overlayAlpha).toInt().coerceIn(0, 255)

        bounds.filter { it.isValid() }.forEach { box ->
            val boxWidth = ((box.right - box.left).toFloat() * videoRect.width())
                .coerceAtLeast(minBoxWidth)
                .coerceAtMost(videoRect.width())
            val boxHeight = ((box.bottom - box.top).toFloat() * videoRect.height())
                .coerceAtLeast(minBoxHeight)
                .coerceAtMost(videoRect.height())
            val left = (videoRect.left + (box.left.toFloat() * videoRect.width()))
                .coerceIn(videoRect.left, videoRect.right - boxWidth)
            val top = (videoRect.top + (box.top.toFloat() * videoRect.height()))
                .coerceIn(videoRect.top, videoRect.bottom - boxHeight)
            val right = left + boxWidth
            val bottom = top + boxHeight

            val rect = RectF(left, top, right, bottom)
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
            canvas.drawRoundRect(rect, radius, radius, framePaint)
        }
    }

    private fun animateAlpha(targetAlpha: Float) {
        if (overlayAlpha == targetAlpha) return
        alphaAnimator?.cancel()
        alphaAnimator = ValueAnimator.ofFloat(overlayAlpha, targetAlpha).apply {
            duration = if (targetAlpha > overlayAlpha) 120L else 220L
            addUpdateListener { animator ->
                overlayAlpha = animator.animatedValue as Float
                invalidate()
            }
            doOnEnd { alphaAnimator = null }
            start()
        }
    }
}
