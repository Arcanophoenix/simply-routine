package com.simplyroutine.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.simplyroutine.data.AppDatabase
import kotlinx.coroutines.*

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<BootStartWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        )
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val events = AppDatabase.getInstance(context).eventDao().getAllEventsOnce()
                AlertScheduler.rescheduleAll(context, events)
            } finally {
                pending.finish()
            }
        }
    }
}
