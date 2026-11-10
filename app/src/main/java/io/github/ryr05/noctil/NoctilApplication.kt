package io.github.ryr05.noctil

import android.app.Application
import io.github.ryr05.noctil.calendar.CalendarSync
import io.github.ryr05.noctil.data.Repository
import io.github.ryr05.noctil.focus.FocusEngine
import io.github.ryr05.noctil.data.db.NoctilDatabase
import io.github.ryr05.noctil.reminders.HabitReminders
import io.github.ryr05.noctil.reminders.LockscreenCard
import io.github.ryr05.noctil.reminders.Notifications
import io.github.ryr05.noctil.reminders.ReminderScheduler
import io.github.ryr05.noctil.widget.Surfaces
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class NoctilApplication : Application() {
    lateinit var scheduler: ReminderScheduler
        private set
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        LockscreenCard.createChannel(this)
        scheduler = ReminderScheduler(this)
        repository = Repository(this, NoctilDatabase.build(this), scheduler)
        FocusEngine.init(this)

        // Keep widgets and the lockscreen card in step with the data.
        repository.scope.launch {
            repository.snapshot.filter { it.loaded }.collectLatest {
                delay(300) // wait for a burst of changes to settle
                Surfaces.refreshAll(this@NoctilApplication)
                CalendarSync.sync(this@NoctilApplication)
                HabitReminders.scheduleAll(this@NoctilApplication, it.habits)
            }
        }
        Surfaces.scheduleMidnightRefresh(this)
        CalendarSync.watch(this)
    }
}
