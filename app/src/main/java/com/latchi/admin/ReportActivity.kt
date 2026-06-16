package com.latchi.admin

import android.content.Context
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class ReportActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VipUiHelper.applyWindowBackground(this)
        AdminFloatingBackHelper.setup(this)
        setContentView(R.layout.activity_report)

        val btnBack = findViewById<TextView>(R.id.btnReportBack)
        btnBack.setOnClickListener { finish() }

        val webView = findViewById<WebView>(R.id.reportWebView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
        }

        val reportFile = File(filesDir, "latchi_sanitized_report.html")
        if (reportFile.exists()) {
            webView.loadUrl("file://" + reportFile.absolutePath)
        } else {
            webView.loadData("<h2 style='color:white;text-align:center;'>التقرير غير متوفر</h2>", "text/html", "UTF-8")
        }
    }
}
