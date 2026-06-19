package com.latchi.admin

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.latchi.admin.model.UserItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class UserManagementActivity : AppCompatActivity() {

    companion object {
        private const val API_URL = "https://script.google.com/macros/s/AKfycbxThygspXN6eB8cDUfY7XavKmhXZfewEUfQqd3vARScZ5y7adterInsbXshNkgPgfiF/exec"
        private const val SECRET = "LatchiAdmin2026"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private lateinit var inputSearch: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var summaryText: TextView
    private lateinit var btnSelectAll: TextView
    private lateinit var btnClearSelection: TextView
    private lateinit var btnDeleteSelected: TextView
    private lateinit var recyclerView: RecyclerView

    private val allUsers = mutableListOf<UserItem>()
    private val visibleUsers = mutableListOf<UserItem>()
    private val selectedCodes = linkedSetOf<String>()
    private lateinit var adapter: UserSelectAdapter

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VipUiHelper.applyWindowBackground(this)
        AdminFloatingBackHelper.setup(this)
        buildUi()
        fetchUsers()
    }

    override fun onResume() {
        super.onResume()
        fetchUsers()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setContentView(root)

        root.addView(VipUiHelper.buildTopBar(
            this,
            title = "👥 إدارة المستخدمين",
            subtitle = "عرض / تحديد / حذف متعدد",
            onBack = { finish() }
        ))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(16))
        }
        root.addView(content, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))

        content.addView(VipUiHelper.buildPrimaryButton(this, "➕ إضافة مستخدم جديد", VipUiHelper.BtnVariant.GOLD) {
            startActivity(Intent(this, AddUserActivity::class.java))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply { bottomMargin = dp(12) })

        inputSearch = EditText(this).apply {
            hint = "🔍 ابحث بالاسم أو الكود"
            setHintTextColor(Color.parseColor("#7788AA"))
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_vip_input)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setSingleLine(true)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { applyFilter(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        content.addView(inputSearch, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnSelectAll = VipUiHelper.buildMiniButton(this, "✅ تحديد الكل", VipUiHelper.BtnVariant.NEON_BLUE) { selectAllVisible() }
        btnClearSelection = VipUiHelper.buildMiniButton(this, "↩️ إلغاء التحديد", VipUiHelper.BtnVariant.NEON_PURPLE) { clearSelection() }
        actionRow.addView(btnSelectAll, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        actionRow.addView(btnClearSelection, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(6) })
        content.addView(actionRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })

        summaryText = TextView(this).apply {
            text = "⏳ جاري جلب المستخدمين..."
            setTextColor(Color.parseColor("#7FE6FF"))
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        content.addView(summaryText)

        progressBar = ProgressBar(this).apply { visibility = View.GONE }
        content.addView(progressBar, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(12)
            bottomMargin = dp(12)
        })

        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@UserManagementActivity)
        }
        adapter = UserSelectAdapter(visibleUsers, selectedCodes,
            onToggle = { toggleSelection(it) },
            onOpen = {
                startActivity(Intent(this, UserDetailControlActivity::class.java).putExtra("userItem", it))
            }
        )
        recyclerView.adapter = adapter
        content.addView(recyclerView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        btnDeleteSelected = VipUiHelper.buildPrimaryButton(this, "🗑️ حذف المحددين", VipUiHelper.BtnVariant.NEON_GREEN) {
            deleteSelectedUsers()
        }
        content.addView(btnDeleteSelected, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(12) })
    }

    private fun fetchUsers() {
        progressBar.visibility = View.VISIBLE
        summaryText.text = "⏳ جاري جلب جميع المستخدمين..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req = Request.Builder().url("$API_URL?action=get_all_users&secret=${enc(SECRET)}").get().build()
                val json = client.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) throw Exception("HTTP ${res.code}")
                    JSONObject(body)
                }
                if (!json.optBoolean("success", false)) throw Exception(json.optString("message", "فشل الجلب"))
                val arr = json.optJSONArray("users") ?: JSONArray()
                val items = mutableListOf<UserItem>()
                for (i in 0 until arr.length()) {
                    val u = arr.getJSONObject(i)
                    items.add(
                        UserItem(
                            rowIdx = u.optInt("rowIdx"),
                            code = u.optString("code"),
                            name = u.optString("name"),
                            playlistUrl = u.optString("playlistUrl"),
                            expiresAt = u.optString("expiresAt"),
                            maxDevices = u.optInt("maxDevices"),
                            status = u.optString("status", "Active"),
                            registeredDevices = u.optString("registeredDevices", "None"),
                            linkExpiresAt = u.optString("linkExpiresAt", "")
                        )
                    )
                }
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    allUsers.clear()
                    allUsers.addAll(items)
                    applyFilter(inputSearch.text?.toString().orEmpty())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    summaryText.text = "❌ فشل جلب المستخدمين: ${e.localizedMessage}"
                }
            }
        }
    }

    private fun applyFilter(query: String) {
        val q = query.trim()
        visibleUsers.clear()
        visibleUsers.addAll(allUsers.filter { q.isBlank() || it.name.contains(q, true) || it.code.contains(q, true) })
        adapter.notifyDataSetChanged()
        updateSummary()
    }

    private fun toggleSelection(item: UserItem) {
        if (selectedCodes.contains(item.code)) selectedCodes.remove(item.code) else selectedCodes.add(item.code)
        adapter.notifyDataSetChanged()
        updateSummary()
    }

    private fun selectAllVisible() {
        visibleUsers.forEach { selectedCodes.add(it.code) }
        adapter.notifyDataSetChanged()
        updateSummary()
    }

    private fun clearSelection() {
        selectedCodes.clear()
        adapter.notifyDataSetChanged()
        updateSummary()
    }

    private fun updateSummary() {
        summaryText.text = "👥 الإجمالي: ${allUsers.size} • الظاهر: ${visibleUsers.size} • المحدد: ${selectedCodes.size}"
        btnDeleteSelected.text = if (selectedCodes.isEmpty()) "🗑️ حذف المحددين" else "🗑️ حذف ${selectedCodes.size} مستخدم"
    }

    private fun deleteSelectedUsers() {
        if (selectedCodes.isEmpty()) {
            Toast.makeText(this, "حدد مستخدمًا واحدًا على الأقل", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("تأكيد الحذف")
            .setMessage("هل تريد حذف ${selectedCodes.size} مستخدم من Google Sheet؟")
            .setPositiveButton("نعم") { _, _ -> executeDeleteSelected() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun executeDeleteSelected() {
        val deleting = selectedCodes.toList()
        progressBar.visibility = View.VISIBLE
        summaryText.text = "⏳ جاري حذف ${deleting.size} مستخدم..."
        CoroutineScope(Dispatchers.IO).launch {
            var deleted = 0
            deleting.forEach { code ->
                try {
                    val req = Request.Builder().url("$API_URL?action=delete_user&secret=${enc(SECRET)}&code=${enc(code)}").get().build()
                    client.newCall(req).execute().use { }
                    deleted++
                } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) {
                selectedCodes.clear()
                Toast.makeText(this@UserManagementActivity, "✅ تم حذف $deleted مستخدم", Toast.LENGTH_LONG).show()
                fetchUsers()
            }
        }
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private class UserSelectAdapter(
        private val items: List<UserItem>,
        private val selectedCodes: Set<String>,
        private val onToggle: (UserItem) -> Unit,
        private val onOpen: (UserItem) -> Unit
    ) : RecyclerView.Adapter<UserSelectAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(android.R.id.text1)
            val meta: TextView = view.findViewById(android.R.id.text2)
            val checkbox: android.widget.CheckBox = view.findViewById(android.R.id.checkbox)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val context = parent.context
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_vip_card)
                setPadding(20, 18, 20, 18)
            }
            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val title = TextView(context).apply {
                id = android.R.id.text1
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val meta = TextView(context).apply {
                id = android.R.id.text2
                setTextColor(Color.parseColor("#B8C0E0"))
                textSize = 11f
                setPadding(0, 8, 0, 0)
            }
            textCol.addView(title)
            textCol.addView(meta)
            val checkbox = android.widget.CheckBox(context).apply { id = android.R.id.checkbox }
            row.addView(textCol)
            row.addView(checkbox)
            return VH(row)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            holder.meta.text = "${item.code} • ${item.status} • ${item.expiresAt}"
            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = selectedCodes.contains(item.code)
            holder.checkbox.setOnCheckedChangeListener { _, _ -> onToggle(item) }
            holder.itemView.setOnClickListener { onOpen(item) }
            holder.itemView.setOnLongClickListener { onToggle(item); true }
        }
    }
}
