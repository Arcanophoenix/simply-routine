package com.simplyroutine.service

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BootStartWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.startForegroundService(
            Intent(applicationContext, TimekeeperService::class.java)
        )
        return Result.success()
    }
}
