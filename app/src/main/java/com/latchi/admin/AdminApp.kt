package com.latchi.admin

import android.content.Context
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication

class AdminApp : MultiDexApplication() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}
