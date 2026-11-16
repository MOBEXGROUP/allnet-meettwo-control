package org.roomscreen.meettwo

import android.app.Application

class MeetTwoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val android.content.Context.container: AppContainer
    get() = (applicationContext as MeetTwoApp).container
