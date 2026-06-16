package com.latchi.admin

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

object AdminFloatingBackHelper {
    private const val TAG = "admin_professional_floating_back"

    fun setup(activity: Activity) {
        if (activity is MainActivity) return // Root screen

        val root = activity.findViewById<FrameLayout?>(android.R.id.content) ?: return
        if (root.findViewWithTag<View>(TAG) != null) return

        val backFab = LinearLayout(activity).apply {
            tag = TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_vip_pill_blue)
            setPadding(dp(activity, 18), dp(activity, 10), dp(activity, 18), dp(activity, 10))
            elevation = dp(activity, 16).toFloat()
            isClickable = true
            isFocusable = true

            addView(TextView(activity).apply {
                text = "← رجوع / Back"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
            })

            setOnClickListener {
                if (!activity.isFinishing) {
                    activity.finish()
                }
            }
        }

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            marginStart = dp(activity, 20)
            bottomMargin = dp(activity, 20)
        }

        root.addView(backFab, params)
    }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
