package com.spendlens.app

import android.app.Application
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

open class SpendLensApp : Application() {

    /** Built on first use. Tests replace it by overriding [createContainer]. */
    val container: AppContainer by lazy { createContainer() }

    protected open fun createContainer(): AppContainer = AppContainer.production(this)

    protected open val scheduleBackgroundSync: Boolean = true

    override fun onCreate() {
        super.onCreate()
        if (scheduleBackgroundSync) runCatching { SyncWorker.schedule(this) }
    }
}

/** Picks up new bank SMS every 6 hours. It only reads; it never sends anything anywhere. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? SpendLensApp ?: return Result.success()
        return try {
            app.container.importEngine.syncNew()
            Result.success()
        } catch (e: Exception) {
            Result.success() // try again at the next scheduled run rather than looping
        }
    }

    companion object {
        private const val NAME = "sms-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(NAME) }
        }
    }
}
