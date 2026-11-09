package io.github.ryr05.noctil

import android.app.Application
import io.github.ryr05.noctil.data.Repository
import io.github.ryr05.noctil.data.db.NoctilDatabase
import io.github.ryr05.noctil.reminders.Notifications
import io.github.ryr05.noctil.reminders.ReminderScheduler

class NoctilApplication : Application() {
    lateinit var scheduler: ReminderScheduler
        private set
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        scheduler = ReminderScheduler(this)
        repository = Repository(this, NoctilDatabase.build(this), scheduler)
    }
}
