package com.latchi.admin

import android.content.Context
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.latchi.admin.model.UserItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class UserDetailControlActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }


    private lateinit var userItem: UserItem
    private lateinit var lblInfoName: TextView
    private lateinit var lblInfoCode: TextView
    private lateinit var lblInfoMaxDev: TextView
    private lateinit var lblInfoRegDev: TextView
    private lateinit var lblInfoExpiry: TextView
    private lateinit var lblInfoPlaylist: TextView
    private lateinit var lblInfoLinkExpiry: TextView
    private lateinit var badgeStatus: TextView

    private lateinit var btnToggleStatus: TextView
    private lateinit var btnEditData: TextView
    private lateinit var btnRenewSubmit: TextView
    private lateinit var btnDeleteAccount: TextView

    private lateinit var btnRenew24h: TextView
    private lateinit var btnRenew1m: TextView
    private lateinit var btnRenew3m: TextView
    private lateinit var btnRenew1y: TextView

    private var selectedRenewDays = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VipUiHelper.applyWindowBackground(this)
        AdminFloatingBackHelper.setup(this)
        setContentView(R.layout.activity_user_detail_control)

        userItem = intent.getParcelableExtra("userItem") ?: run { finish(); return }

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        lblInfoName = findViewById(R.id.lblInfoName)
        lblInfoCode = findViewById(R.id.lblInfoCode)
        lblInfoMaxDev = findViewById(R.id.lblInfoMaxDev)
        lblInfoRegDev = findViewById(R.id.lblInfoRegDev)
        lblInfoExpiry = findViewById(R.id.lblInfoExpiry)
        lblInfoPlaylist = findViewById(R.id.lblInfoPlaylist)
        lblInfoLinkExpiry = findViewById(R.id.lblInfoLinkExpiry)
        badgeStatus = findViewById(R.id.badgeStatus)

        btnToggleStatus = findViewById(R.id.btnToggleStatus)
        btnEditData = findViewById(R.id.btnEditData)
        btnRenewSubmit = findViewById(R.id.btnRenewSubmit)
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount)

        btnRenew24h = findViewById(R.id.btnRenew24h)
        btnRenew1m = findViewById(R.id.btnRenew1m)
        btnRenew3m = findViewById(R.id.btnRenew3m)
        btnRenew1y = findViewById(R.id.btnRenew1y)

        populateDetails()
        setupRenewButtons()

        btnToggleStatus.setOnClickListener { executeToggleStatus() }
        btnEditData.setOnClickListener { openEditDialog() }
        btnRenewSubmit.setOnClickListener { executeRenew() }
        btnDeleteAccount.setOnClickListener { confirmDelete() }
    }

    private fun populateDetails() {
        lblInfoName.text = "${getString(R.string.name_label)} ${userItem.name}"
        lblInfoCode.text = "${getString(R.string.code_label)} ${userItem.code}"
        lblInfoMaxDev.text = "📱 ${getString(R.string.max_devices_hint)}: ${userItem.maxDevices}"
        lblInfoRegDev.text = "📱 ${getString(R.string.registered_data)}: ${userItem.registeredDevices}"
        lblInfoExpiry.text = "${getString(R.string.expiry_label)} ${userItem.expiresAt}"
        lblInfoPlaylist.text = "${getString(R.string.playlist_label)} ${userItem.playlistUrl}"
        lblInfoLinkExpiry.text = AdminExpiryHelper.statusText(this, userItem.linkExpiresAt)
        lblInfoLinkExpiry.setTextColor(AdminExpiryHelper.color(userItem.linkExpiresAt))

        val isActive = userItem.status.equals("Active", true)
        badgeStatus.text = if (isActive) "✅ ${getString(R.string.active)}" else "❌ ${getString(R.string.inactive)}"
        badgeStatus.setBackgroundColor(if (isActive) Color.parseColor("#16A34A") else Color.parseColor("#DC2626"))
    }

    private fun setupRenewButtons() {
        val buttons = listOf(btnRenew24h to 1, btnRenew1m to 30, btnRenew3m to 90, btnRenew1y to 365)
        buttons.forEach { (btn, days) ->
            btn.setOnClickListener {
                buttons.forEach { (b, _) -> b.setBackgroundColor(Color.parseColor("#3d3d5c")) }
                btn.setBackgroundColor(Color.parseColor("#FFD700"))
                btn.setTextColor(Color.parseColor("#000000"))
                selectedRenewDays = days
            }
        }
    }

    private fun showProgressDialog(title: String, message: String): AlertDialog {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(40, 50, 40, 50)
            setBackgroundColor(Color.parseColor("#1E1E38"))

            addView(ProgressBar(this@UserDetailControlActivity).apply {
                isIndeterminate = true
                layoutParams = LinearLayout.LayoutParams(100, 100).apply { bottomMargin = 30 }
            })
            addView(TextView(this@UserDetailControlActivity).apply {
                text = message
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
            })
        }
        return AlertDialog.Builder(this).setTitle(title).setView(container).setCancelable(false).show()
    }

    private fun getBaseUrl(): String {
        return getSharedPreferences("admin_prefs", Context.MODE_PRIVATE).getString("apiUrl", "https://script.google.com/macros/s/AKfycbzlzc-Ipjq7E9KPjpioJcNSV2OMle7Ma17GruKxqBJxk0k7ktNoM5C3Ko9st7yMS1p1/exec") ?: "https://script.google.com/macros/s/AKfycbzlzc-Ipjq7E9KPjpioJcNSV2OMle7Ma17GruKxqBJxk0k7ktNoM5C3Ko9st7yMS1p1/exec"
    }

    private fun executeToggleStatus() {
        val newStatus = if (userItem.status.equals("Active", true)) "Inactive" else "Active"
        val pd = showProgressDialog(getString(R.string.toggle_status), "جارِ تطبيق الحالة [$newStatus] في Google Sheets...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val encCode = URLEncoder.encode(userItem.code, "UTF-8")
                val encStatus = URLEncoder.encode(newStatus, "UTF-8")
                val encSecret = URLEncoder.encode("LatchiAdmin2026", "UTF-8")

                val url = "${getBaseUrl()}?action=update_status&secret=$encSecret&code=$encCode&new_status=$encStatus"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000; conn.readTimeout = 25000

                val res = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(res)
                val success = json.optBoolean("success", false)

                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    if (success) {
                        userItem = userItem.copy(status = newStatus)
                        populateDetails()
                        Toast.makeText(this@UserDetailControlActivity, getString(R.string.status_updated_success), Toast.LENGTH_LONG).show()
                    } else Toast.makeText(this@UserDetailControlActivity, getString(R.string.error_prefix, json.optString("message")), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { pd.dismiss(); Toast.makeText(this@UserDetailControlActivity, getString(R.string.connection_failed), Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun openEditDialog() {
        fun styledInput(value: String, hintText: String, number: Boolean = false): EditText {
            return EditText(this).apply {
                setText(value)
                hint = hintText
                setHintTextColor(Color.parseColor("#B8AFC8"))
                setTextColor(Color.WHITE)
                setSingleLine(false)
                minLines = 1
                setPadding(18, 12, 18, 12)
                setBackgroundColor(Color.parseColor("#121228"))
                if (number) inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
        }

        val inputName = styledInput(userItem.name, getString(R.string.name_hint))
        val inputMaxDev = styledInput(userItem.maxDevices.toString(), getString(R.string.max_devices_hint), number = true)
        val inputExpiry = styledInput(userItem.expiresAt, getString(R.string.expiry_date_hint))
        val inputPlaylist = styledInput(userItem.playlistUrl, getString(R.string.playlist_hint))
        val inputLinkExpiry = styledInput(userItem.linkExpiresAt, getString(R.string.link_expiry_hint))

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
            setBackgroundResource(R.drawable.bg_success_dialog)
            addView(TextView(context).apply {
                text = getString(R.string.edit_account_title)
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 20 }
            })
            listOf(inputName, inputMaxDev, inputExpiry, inputPlaylist, inputLinkExpiry).forEach { input ->
                addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 })
            }
        }

        AlertDialog.Builder(this)
            .setView(container)
            .setPositiveButton(getString(R.string.save_changes)) { _, _ ->
                executeEditUser(
                    inputName.text.toString(),
                    inputMaxDev.text.toString().toIntOrNull() ?: 1,
                    AdminExpiryHelper.normalizedOrBlank(inputExpiry.text.toString()),
                    inputPlaylist.text.toString(),
                    AdminExpiryHelper.normalizedOrBlank(inputLinkExpiry.text.toString())
                )
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun executeEditUser(name: String, maxDev: Int, expiry: String, playlist: String, linkExpiry: String) {
        val pd = showProgressDialog(getString(R.string.edit_account_title), "جارِ حفظ التعديلات في Google Sheets...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val encCode = URLEncoder.encode(userItem.code, "UTF-8")
                val encName = URLEncoder.encode(name, "UTF-8")
                val encExpiry = URLEncoder.encode(expiry, "UTF-8")
                val encPlaylist = URLEncoder.encode(playlist, "UTF-8")
                val encLinkExpiry = URLEncoder.encode(linkExpiry, "UTF-8")
                val encSecret = URLEncoder.encode("LatchiAdmin2026", "UTF-8")

                val url = "${getBaseUrl()}?action=edit_user&secret=$encSecret&code=$encCode&name=$encName&max_devices=$maxDev&expires_at=$encExpiry&playlist_url=$encPlaylist&link_expires_at=$encLinkExpiry"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"; conn.connectTimeout = 15000; conn.readTimeout = 25000

                val res = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(res)
                val success = json.optBoolean("success", false)

                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    if (success) {
                        userItem = userItem.copy(name = name, maxDevices = maxDev, expiresAt = expiry, playlistUrl = playlist, linkExpiresAt = linkExpiry)
                        populateDetails()
                        Toast.makeText(this@UserDetailControlActivity, getString(R.string.changes_saved_success), Toast.LENGTH_LONG).show()
                    } else Toast.makeText(this@UserDetailControlActivity, getString(R.string.error_prefix, json.optString("message")), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { pd.dismiss(); Toast.makeText(this@UserDetailControlActivity, getString(R.string.connection_failed), Toast.LENGTH_LONG).show() } }
        }
    }

    private fun executeRenew() {
        val pd = showProgressDialog(getString(R.string.renew_subscription), "جارِ تطبيق التجديد وتمديد الصلاحية في Google Sheets...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val encCode = URLEncoder.encode(userItem.code, "UTF-8")
                val encDuration = URLEncoder.encode(selectedRenewDays.toString(), "UTF-8")
                val encSecret = URLEncoder.encode("LatchiAdmin2026", "UTF-8")

                val url = "${getBaseUrl()}?action=renew_user&secret=$encSecret&code=$encCode&duration_days=$encDuration"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"; conn.connectTimeout = 15000; conn.readTimeout = 25000

                val res = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(res)
                val success = json.optBoolean("success", false)
                val newExpiry = json.optString("newExpiry", userItem.expiresAt)

                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    if (success) {
                        userItem = userItem.copy(expiresAt = newExpiry, status = "Active")
                        populateDetails()
                        Toast.makeText(this@UserDetailControlActivity, getString(R.string.renew_success, newExpiry), Toast.LENGTH_LONG).show()
                    } else Toast.makeText(this@UserDetailControlActivity, getString(R.string.error_prefix, json.optString("message")), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { pd.dismiss(); Toast.makeText(this@UserDetailControlActivity, getString(R.string.connection_failed), Toast.LENGTH_LONG).show() } }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_confirm_title))
            .setMessage(getString(R.string.delete_confirm_message, userItem.name))
            .setPositiveButton(getString(R.string.delete_confirm_yes)) { _, _ -> executeDelete() }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun executeDelete() {
        val pd = showProgressDialog(getString(R.string.delete_user_forever), "جارِ حذف المستخدم نهائياً من Google Sheets...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val encCode = URLEncoder.encode(userItem.code, "UTF-8")
                val encSecret = URLEncoder.encode("LatchiAdmin2026", "UTF-8")

                val url = "${getBaseUrl()}?action=delete_user&secret=$encSecret&code=$encCode"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"; conn.connectTimeout = 15000; conn.readTimeout = 25000

                val res = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(res)
                val success = json.optBoolean("success", false)

                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    if (success) {
                        Toast.makeText(this@UserDetailControlActivity, getString(R.string.delete_success), Toast.LENGTH_LONG).show()
                        finish()
                    } else Toast.makeText(this@UserDetailControlActivity, getString(R.string.error_prefix, json.optString("message")), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { pd.dismiss(); Toast.makeText(this@UserDetailControlActivity, getString(R.string.connection_failed), Toast.LENGTH_LONG).show() } }
        }
    }
}
