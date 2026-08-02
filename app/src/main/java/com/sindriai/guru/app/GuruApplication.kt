package com.sindriai.guru.app

import android.app.Application

class GuruApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // ✅ Initialize Markdown renderer here
        //MarkdownRenderer.init(this)
    }
}