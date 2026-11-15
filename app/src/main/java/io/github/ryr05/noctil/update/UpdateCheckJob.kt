package io.github.ryr05.noctil.update

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/** The twice-a-day background look for a new version (runs only when there's a network). */
class UpdateCheckJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            val result = Updates.check(applicationContext, notify = true)
            // A failed request (GitHub busy, flaky network) is retried a little later.
            jobFinished(params, result == Updates.Result.Failed)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope.coroutineContext.cancelChildren()
        return true
    }
}
