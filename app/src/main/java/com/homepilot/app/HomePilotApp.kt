package com.homepilot.app

import android.app.Application

class HomePilotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: HomePilotApp
            private set
    }
}
