package com.latchi.admin

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ServerListActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }


    private lateinit var btnBack: TextView
    private lateinit var inputSearch: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ServerAdapter
    private val allUsers = mutableListOf<UserItem>()
    private val serverMap = mutableMapOf<String, MutableList<UserItem>>()
    private val displayServers = mutableListOf<ServerEntry>()

    data class ServerEntry(val name: String, val usersCount: Int, val usersList: List<UserItem>, val expiringLinksCount: Int = 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VipUiHelper.applyWindowBackground(this)
        AdminFloatingBackHelper.setup(this)
        setContentView(R.layout.activity_server_list)

        btnBack = findViewById(R.id.btnBack)
        inputSearch = findViewById(R.id.inputSearch)
        progressBar = findViewById(R.id.progressBar)
        recyclerView = findViewById(R.id.recyclerView)

        btnBack.setOnClickListener { finish() }

        adapter = ServerAdapter(displayServers) { entry ->
            val intent = Intent(this, UserListByServerActivity::class.java).apply {
                putExtra("serverName", entry.name)
                putParcelableArrayListExtra("usersList", java.util.ArrayList(entry.usersList))
            }
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterServers(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        fetchAllUsers()
    }

    private fun fetchAllUsers() {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiUrl = "https://script.google.com/macros/s/AKfycbwoxD7eNi6AVvhw9l_hPzaUkVt1F9U6trUXs28QYuNld_Ip15ZoefcTAdkd4B_DqoGO/exec"
                val encSecret = URLEncoder.encode("LatchiAdmin2026", "UTF-8")
                val getUrl = "$apiUrl?action=get_all_users&secret=$encSecret"

                val connection = URL(getUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 25000

                val res = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(res)
                val success = json.optBoolean("success", false)
                val usersArr = json.optJSONArray("users") ?: JSONArray()

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (success) {
                        allUsers.clear()
                        serverMap.clear()
                        for (i in 0 until usersArr.length()) {
                            val u = usersArr.getJSONObject(i)
                            val item = UserItem(
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
                            allUsers.add(item)
                            serverMap.getOrPut(item.getServerName()) { mutableListOf() }.add(item)
                        }
                        filterServers(inputSearch.text.toString())
                        val totalExpiring = allUsers.count { AdminExpiryHelper.isExpiringSoon(it.linkExpiresAt) || AdminExpiryHelper.isExpired(it.linkExpiresAt) }
                        if (totalExpiring > 0) {
                            AlertDialog.Builder(this@ServerListActivity)
                                .setTitle(getString(R.string.link_expiry_alert_title))
                                .setMessage(getString(R.string.link_expiry_alert_message, totalExpiring))
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    } else {
                        Toast.makeText(this@ServerListActivity, "خطأ في جلب المستخدمين: ${json.optString("message")}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@ServerListActivity, "فشل الاتصال: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun filterServers(query: String) {
        displayServers.clear()
        val sorted = serverMap.keys.sorted()
        for (serverName in sorted) {
            val list = serverMap[serverName] ?: emptyList()
            if (query.isBlank() || serverName.contains(query, ignoreCase = true)) {
                val expiring = list.count { AdminExpiryHelper.isExpiringSoon(it.linkExpiresAt) || AdminExpiryHelper.isExpired(it.linkExpiresAt) }
                displayServers.add(ServerEntry(serverName, list.size, list, expiring))
            }
        }
        adapter.notifyDataSetChanged()
    }

    class ServerAdapter(private val items: List<ServerEntry>, private val onClick: (ServerEntry) -> Unit) : RecyclerView.Adapter<ServerAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.txtServerName)
            val usersCount: TextView = v.findViewById(R.id.txtServerUsersCount)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH = VH(LayoutInflater.from(p.context).inflate(R.layout.item_server_card, p, false))
        override fun getItemCount(): Int = items.size
        override fun onBindViewHolder(h: VH, i: Int) {
            val entry = items[i]
            h.name.text = entry.name
            h.usersCount.text = if (entry.expiringLinksCount > 0) {
                h.itemView.context.getString(R.string.users_count_with_alert, entry.usersCount, entry.expiringLinksCount)
            } else {
                h.itemView.context.getString(R.string.users_count_label, entry.usersCount)
            }
            h.usersCount.setTextColor(if (entry.expiringLinksCount > 0) Color.parseColor("#F59E0B") else Color.parseColor("#C7B7D8"))
            h.itemView.setOnClickListener { onClick(entry) }
        }
    }
}
