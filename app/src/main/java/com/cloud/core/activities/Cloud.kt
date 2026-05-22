package com.cloud.core.activities

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.cloud.core.functions.ERRORINSERTDATA
import com.cloud.core.functions.errorInsert
import com.cloud.core.objects.Config
import com.cloud.core.objects.Config.client
import com.cloud.quicksettingsfunctions.BatteryDataRepository
import com.google.firebase.FirebaseApp
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
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

        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.1)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
        Coil.setImageLoader(imageLoader)

        Config.init(this)
        BatteryDataRepository.init(this)
    }
}

@Serializable
data class Script(val code: String)


suspend fun fetchAndRun(scriptName: String) {
    val script = client.from("scripts")
        .select { filter { eq("name", scriptName) } }
        .decodeSingle<Script>()

    val result = executeJs(script.code)
    println("Ergebnis: $result")
}

fun executeJs(code: String): String {
    TODO("code nachladen experiment")
//    val cx = Context.enter()
//    cx.optimizationLevel = -1 // required für Android
//    return try {
//        val scope = cx.initStandardObjects()
//        val result = cx.evaluateString(scope, code, "remote", 1, null)
//        Context.toString(result)
//    } finally {
//        Context.exit()
//    }
}