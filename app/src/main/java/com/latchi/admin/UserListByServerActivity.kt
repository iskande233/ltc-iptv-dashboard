package com.latchi.admin

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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.latchi.admin.model.UserItem

class UserListByServerActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }


    private lateinit var btnBack: TextView
    private lateinit var txtHeaderTitle: TextView
    private lateinit var inputSearch: EditText
    private lateinit var btnFilterAll: TextView
    private lateinit var btnFilterActive: TextView
    private lateinit var btnFilterInactive: TextView
    private lateinit var btnFilterExpired: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UserAdapter

    private var serverName = ""
    private var allUsersList = emptyList<UserItem>()
    private val displayUsersList = mutableListOf<UserItem>()
    private var currentFilter = "All"
    private var currentQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VipUiHelper.applyWindowBackground(this)
        AdminFloatingBackHelper.setup(this)
        setContentView(R.layout.activity_user_list_by_server)

        btnBack = findViewById(R.id.btnBack)
        txtHeaderTitle = findViewById(R.id.txtHeaderTitle)
        inputSearch = findViewById(R.id.inputSearch)
        btnFilterAll = findViewById(R.id.btnFilterAll)
        btnFilterActive = findViewById(R.id.btnFilterActive)
        btnFilterInactive = findViewById(R.id.btnFilterInactive)
        btnFilterExpired = findViewById(R.id.btnFilterExpired)
        recyclerView = findViewById(R.id.recyclerView)

        serverName = intent.getStringExtra("serverName") ?: getString(R.string.server_users)
        allUsersList = intent.getParcelableArrayListExtra<UserItem>("usersList") ?: emptyList()

        txtHeaderTitle.text = "${getString(R.string.server_users)} ($serverName)"
        btnBack.setOnClickListener { finish() }

        adapter = UserAdapter(displayUsersList) { item ->
            val intent = Intent(this, UserDetailControlActivity::class.java).apply {
                putExtra("userItem", item)
            }
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        setupFilters()
        setupSearch()
        applyFilter()
    }

    private fun setupFilters() {
        val filters = listOf(
            btnFilterAll to "All",
            btnFilterActive to "Active",
            btnFilterInactive to "Inactive",
            btnFilterExpired to "Expired"
        )
        filters.forEach { (btn, status) ->
            btn.setOnClickListener {
                filters.forEach { (b, _) ->
                    b.setBackgroundColor(Color.parseColor("#3d3d5c"))
                    b.setTextColor(Color.WHITE)
                }
                btn.setBackgroundColor(Color.parseColor("#FFD700"))
                btn.setTextColor(Color.parseColor("#000000"))
                currentFilter = status
                applyFilter()
            }
        }
    }

    private fun setupSearch() {
        inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString() ?: ""
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun applyFilter() {
        displayUsersList.clear()
        for (u in allUsersList) {
            val statusOk = when (currentFilter) {
                "Active" -> u.status.equals("Active", true)
                "Inactive" -> u.status.equals("Inactive", true)
                "Expired" -> u.expiresAt.contains("انتهى") || u.status.equals("Expired", true) || AdminExpiryHelper.isExpired(u.linkExpiresAt)
                else -> true
            }
            val searchOk = currentQuery.isBlank() || u.name.contains(currentQuery, true) || u.code.contains(currentQuery, true)
            
            if (statusOk && searchOk) {
                displayUsersList.add(u)
            }
        }
        adapter.notifyDataSetChanged()
    }

    class UserAdapter(private val items: List<UserItem>, private val onClick: (UserItem) -> Unit) : RecyclerView.Adapter<UserAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.txtUserName)
            val code: TextView = v.findViewById(R.id.txtUserCode)
            val status: TextView = v.findViewById(R.id.txtUserStatus)
            val expiry: TextView = v.findViewById(R.id.txtUserExpiry)
            val linkExpiry: TextView = v.findViewById(R.id.txtUserLinkExpiry)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int): VH = VH(LayoutInflater.from(p.context).inflate(R.layout.item_user_card, p, false))
        override fun getItemCount(): Int = items.size
        override fun onBindViewHolder(h: VH, i: Int) {
            val item = items[i]
            h.name.text = item.name
            h.code.text = "${h.itemView.context.getString(R.string.code_label)} ${item.code}"
            h.expiry.text = "${h.itemView.context.getString(R.string.expiry_label)} ${item.expiresAt}"
            h.linkExpiry.text = AdminExpiryHelper.statusText(h.itemView.context, item.linkExpiresAt)
            h.linkExpiry.setTextColor(AdminExpiryHelper.color(item.linkExpiresAt))
            h.status.text = if (item.status.equals("Active", true)) "✅ ${h.itemView.context.getString(R.string.active)}" else "❌ ${h.itemView.context.getString(R.string.inactive)}"
            h.status.setBackgroundColor(if (item.status.equals("Active", true)) Color.parseColor("#16A34A") else Color.parseColor("#DC2626"))
            h.itemView.setOnClickListener { onClick(item) }
        }
    }
}
