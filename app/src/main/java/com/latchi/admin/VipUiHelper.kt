package com.latchi.admin

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * مكتبة مساعدة لإنشاء عناصر واجهة المستخدم بنمط VIP Royal.
 *
 * توفّر:
 * - بطاقات بأسلوب Neon Royal Blue
 * - أزرار بـ 4 أنماط (ذهبي، أزرق نيون، أخضر نيون، بنفسجي نيون)
 * - Overlay منبثقة بأنماط النجاح/التحذير/الخطأ
 * - TopBar موحّد مع زر رجوع
 * - نسخ/لصق من الحافظة
 */
object VipUiHelper {

    enum class BtnVariant { GOLD, NEON_BLUE, NEON_GREEN, NEON_PURPLE }

    /**
     * يطبّق خلفية التدرج الـ VIP على النافذة (windowBackground).
     */
    fun applyWindowBackground(activity: Activity) {
        activity.window.setBackgroundDrawableResource(R.drawable.bg_vip_gradient)
    }

    /**
     * يقرأ من الحافظة النصية ويستدعي callback عند النجاح.
     */
    fun pasteFromClipboard(activity: Activity, callback: (String) -> Unit) {
        try {
            val cb = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cb.primaryClip
            val text = clip?.getItemAt(0)?.coerceToText(activity)?.toString().orEmpty()
            if (text.isBlank()) {
                Toast.makeText(activity, "📋 الحافظة فارغة", Toast.LENGTH_SHORT).show()
            } else {
                callback(text)
            }
        } catch (e: Exception) {
            Toast.makeText(activity, "❌ تعذر اللصق", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * يبني بطاقة (Card) بنمط VIP Neon Blue بحدود زرقاء نيون.
     */
    fun buildCard(ctx: Context): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_vip_card)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    /**
     * يبني تسمية لحقل إدخال بنمط VIP (أزرق نيون، bold).
     */
    fun buildInputLabel(ctx: Context, text: String): TextView {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(Color.parseColor("#7FE6FF"))
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(ctx, 4), dp(ctx, 8), 0, dp(ctx, 6))
        }
    }

    /**
     * يبني زر VIP كبير بنمط محدد.
     */
    fun buildPrimaryButton(ctx: Context, text: String, variant: BtnVariant, onClick: () -> Unit): TextView {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(Color.parseColor("#0A0F2C"))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setBackgroundResource(
                when (variant) {
                    BtnVariant.GOLD -> R.drawable.bg_vip_btn_gold
                    BtnVariant.NEON_BLUE -> R.drawable.bg_vip_btn_neon_blue
                    BtnVariant.NEON_GREEN -> R.drawable.bg_vip_btn_neon_green
                    BtnVariant.NEON_PURPLE -> R.drawable.bg_vip_btn_neon_purple
                }
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            setPadding(dp(ctx, 8), dp(ctx, 12), dp(ctx, 8), dp(ctx, 12))
        }
    }

    /**
     * يبني زر صغير (mini) بألوان نيون.
     */
    fun buildMiniButton(ctx: Context, text: String, variant: BtnVariant, onClick: () -> Unit): TextView {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(Color.parseColor("#0A0F2C"))
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setBackgroundResource(
                when (variant) {
                    BtnVariant.GOLD -> R.drawable.bg_vip_btn_gold
                    BtnVariant.NEON_BLUE -> R.drawable.bg_vip_btn_neon_blue
                    BtnVariant.NEON_GREEN -> R.drawable.bg_vip_btn_neon_green
                    BtnVariant.NEON_PURPLE -> R.drawable.bg_vip_btn_neon_purple
                }
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            setPadding(dp(ctx, 6), dp(ctx, 8), dp(ctx, 6), dp(ctx, 8))
        }
    }

    /**
     * يبني TopBar موحّد مع زر رجوع وعنوان رئيسي/فرعي.
     */
    fun buildTopBar(ctx: Context, title: String, subtitle: String, onBack: () -> Unit): LinearLayout {
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_vip_topbar)
            setPadding(dp(ctx, 8), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14))
        }

        // Back button
        val back = TextView(ctx).apply {
            text = "←"
            setTextColor(Color.parseColor("#7FE6FF"))
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_vip_pill_blue)
            isClickable = true
            isFocusable = true
            setOnClickListener { onBack() }
            setPadding(dp(ctx, 14), dp(ctx, 6), dp(ctx, 14), dp(ctx, 6))
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(ctx, 48), dp(ctx, 40)))

        // Title column
        val titleCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 14), 0, 0, 0)
        }
        titleCol.addView(TextView(ctx).apply {
            text = title
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        titleCol.addView(TextView(ctx).apply {
            text = subtitle
            setTextColor(Color.parseColor("#7FE6FF"))
            textSize = 10f
            setPadding(0, dp(ctx, 2), 0, 0)
        })
        bar.addView(titleCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        return bar
    }

    /**
     * Overlay منبثقة للنجاح (خلفية خضراء نيون + fade-in animation).
     */
    fun showSuccessOverlay(
        activity: Activity,
        title: String,
        message: String,
        primaryText: String? = null,
        onPrimary: (() -> Unit)? = null,
        secondaryText: String? = null,
        onSecondary: (() -> Unit)? = null
    ) {
        showCustomOverlay(activity, title, message,
            R.drawable.bg_vip_success_overlay,
            Color.parseColor("#39FF8B"),
            primaryText, onPrimary, secondaryText, onSecondary)
    }

    /**
     * Overlay منبثقة للتحذير (خلفية عنبرية نيون).
     */
    fun showWarningOverlay(
        activity: Activity,
        title: String,
        message: String,
        primaryText: String? = null,
        onPrimary: (() -> Unit)? = null,
        secondaryText: String? = null,
        onSecondary: (() -> Unit)? = null
    ) {
        showCustomOverlay(activity, title, message,
            R.drawable.bg_vip_overlay,
            Color.parseColor("#FFB347"),
            primaryText, onPrimary, secondaryText, onSecondary)
    }

    /**
     * Overlay منبثقة للخطأ (خلفية حمراء).
     */
    fun showErrorOverlay(
        activity: Activity,
        message: String,
        primaryText: String? = "حسناً",
        onPrimary: (() -> Unit)? = null
    ) {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 28), dp(activity, 24), dp(activity, 28), dp(activity, 22))
            setBackgroundResource(R.drawable.bg_vip_error_overlay)
        }
        container.addView(TextView(activity).apply {
            text = "⚠️ تنبيه"
            setTextColor(Color.parseColor("#FF5577"))
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        container.addView(TextView(activity).apply {
            text = message
            setTextColor(Color.parseColor("#F2F4FF"))
            textSize = 14f
            setPadding(0, dp(activity, 12), 0, 0)
            setLineSpacing(4f, 1.05f)
        })
        val dialogRef: AlertDialog
        if (primaryText != null) {
            val buttonRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(activity, 16), 0, 0)
            }
            // Will set onClick after dialogRef is created
            val primaryBtn = buildMiniButton(activity, primaryText, BtnVariant.NEON_BLUE) { /* placeholder */ }
            buttonRow.addView(primaryBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 46)))
            container.addView(buttonRow)
            dialogRef = AlertDialog.Builder(activity).setView(container).create()
            primaryBtn.setOnClickListener {
                onPrimary?.invoke()
                dialogRef.dismiss()
            }
        } else {
            dialogRef = AlertDialog.Builder(activity).setView(container).create()
        }
        dialogRef.setOnShowListener {
            container.alpha = 0f
            container.scaleX = 0.92f
            container.scaleY = 0.92f
            container.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(260).start()
        }
        dialogRef.setCanceledOnTouchOutside(true)
        dialogRef.show()
    }

    private fun showCustomOverlay(
        activity: Activity,
        title: String,
        message: String,
        bgRes: Int,
        accentColor: Int,
        primaryText: String?,
        onPrimary: (() -> Unit)?,
        secondaryText: String?,
        onSecondary: (() -> Unit)?
    ) {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 28), dp(activity, 24), dp(activity, 28), dp(activity, 22))
            setBackgroundResource(bgRes)
        }
        container.addView(TextView(activity).apply {
            text = title
            setTextColor(accentColor)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        container.addView(TextView(activity).apply {
            text = message
            setTextColor(Color.parseColor("#F2F4FF"))
            textSize = 13f
            setPadding(0, dp(activity, 14), 0, 0)
            setLineSpacing(4f, 1.1f)
        })

        var primaryButton: TextView? = null
        if (primaryText != null) {
            primaryButton = buildMiniButton(activity, primaryText, BtnVariant.NEON_BLUE) {
                onPrimary?.invoke()
            }
        }
        val secondaryButton: TextView? = if (secondaryText != null) {
            buildMiniButton(activity, secondaryText, BtnVariant.NEON_PURPLE) {
                onSecondary?.invoke()
            }
        } else null

        if (primaryButton != null || secondaryButton != null) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(activity, 16), 0, 0)
            }
            if (secondaryButton != null) {
                row.addView(secondaryButton, LinearLayout.LayoutParams(0, dp(activity, 46), 1f).apply { marginEnd = dp(activity, 6) })
            }
            if (primaryButton != null) {
                row.addView(primaryButton, LinearLayout.LayoutParams(0, dp(activity, 46), 1f).apply { marginStart = if (secondaryButton != null) dp(activity, 6) else 0 })
            }
            container.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        val dialog = AlertDialog.Builder(activity).setView(container).create()
        dialog.setOnShowListener {
            container.alpha = 0f
            container.scaleX = 0.92f
            container.scaleY = 0.92f
            container.animate()
                .alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(280).start()
        }
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
    }

    private fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
}
