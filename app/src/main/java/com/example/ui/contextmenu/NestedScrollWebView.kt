package com.example.ui.contextmenu

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView

/**
 * NestedScrollWebView ensures smooth, isolated vertical and horizontal scrolling inside
 * Jetpack Compose ModalBottomSheet and nested scroll containers.
 *
 * When the user touches or drags within the WebView, it requests parent containers
 * (including ModalBottomSheet's drag / nested scrolling gestures) to disallow intercepting touch
 * events as long as the WebView can scroll, or whenever the user moves their finger vertically/horizontally.
 * It also notifies parent containers when the top edge is reached so overscroll pull-down can dismiss cleanly if desired.
 */
class NestedScrollWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    private var startY = 0f
    private var startX = 0f
    private var isDragging = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.y
                startX = event.x
                isDragging = false
                // Prevent bottom sheet from immediately snatching the touch event
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - startY
                val dx = event.x - startX
                
                if (Math.abs(dy) > 10 || Math.abs(dx) > 10) {
                    isDragging = true
                }

                // If scrolling downwards at the very top (scrollY == 0 and pulling down),
                // allow parent bottom sheet to intercept for swipe-down to dismiss.
                // Otherwise, keep the touch exclusively inside WebView for uninterrupted web page scrolling.
                if (scrollY == 0 && dy > 15 && Math.abs(dy) > Math.abs(dx)) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                } else {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        // If clamped at the top and pulling downwards, let parent handle
        if (clampedY && scrollY <= 0) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
    }
}
