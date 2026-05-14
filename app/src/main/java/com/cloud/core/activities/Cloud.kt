package com.cloud.core.activities

import android.app.Application
import com.cloud.core.functions.ERRORINSERTDATA
import com.cloud.core.functions.errorInsert
import com.cloud.core.objects.Config
import com.cloud.quicksettingsfunctions.BatteryDataRepository
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant

class Cloud : Application() {

    companion object {
        lateinit var coroutineExceptionHandler: CoroutineExceptionHandler
        val appScope by lazy {
            CoroutineScope(SupervisorJob() + Dispatchers.Main + coroutineExceptionHandler)
        }
        val serviceScope by lazy {
            CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)
        }
    }

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runBlocking {
                try {
                    errorInsert(
                        ERRORINSERTDATA(
                            "UncaughtException: ${thread.name}",
                            throwable.stackTraceToString().take(8000),
                            Instant.now().toString(),
                            "ERROR"
                        )
                    )
                } catch (_: Exception) {}
            }
            defaultHandler?.uncaughtException(thread, throwable)
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