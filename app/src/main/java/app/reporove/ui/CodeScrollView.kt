package app.reporove.ui

import android.content.Context
import android.view.MotionEvent
import androidx.core.widget.NestedScrollView

/** Keep touch ownership in the native reader; nested deltas still reach the Compose header. */
class CodeScrollView(context: Context) : NestedScrollView(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) parent?.requestDisallowInterceptTouchEvent(false)
        return handled
    }
}
