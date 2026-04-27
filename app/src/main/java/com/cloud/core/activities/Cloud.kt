package com.cloud.core.activities

import android.app.Application
import com.cloud.core.functions.ERRORINSERTDATA
import com.cloud.core.functions.errorInsert
import com.cloud.core.objects.Config
import com.cloud.quicksettingsfunctions.BatteryDataRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import android.os.Process

class Cloud : Application() {

    companion object {
        lateinit var coroutineExceptionHandler: CoroutineExceptionHandler
    }

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CoroutineScope(Dispatchers.IO).launch {
                errorInsert(
                    ERRORINSERTDATA(
                        "UncaughtException: ${thread.name}",
                        throwable.stackTraceToString().take(8000),
                        Instant.now().toString(),
                        "ERROR"
                    )
                )
            }
            Thread.sleep(2000)
            defaultHandler?.uncaughtException(thread, throwable)
            Process.killProcess(Process.myPid())
        }

        coroutineExceptionHandler = CoroutineExceptionHandler { context, throwable ->
            CoroutineScope(Dispatchers.IO).launch {
                errorInsert(
                    ERRORINSERTDATA(
                        "CoroutineException: $context",
                        throwable.stackTraceToString().take(8000),
                        Instant.now().toString(),
                        "ERROR"
                    )
                )
            }
        }

        Config.init(this)
        BatteryDataRepository.init(this)
    }
}