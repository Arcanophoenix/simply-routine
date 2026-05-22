package com.simplyroutine.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simplyroutine.data.AppDatabase
import kotlinx.coroutines.*

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        context.startForegroundService(Intent(context, TimekeeperService::class.java))
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
