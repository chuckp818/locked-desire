package com.lockeddesire.app

import android.app.Application
import com.lockeddesire.app.data.Repo
import com.lockeddesire.app.notify.Notifier

class LockedDesireApp : Application() {
    /** Einfacher Service-Locator statt eines DI-Frameworks. */
    val repo: Repo by lazy { Repo(this) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        Notifier.schedule(this)
    }
}
