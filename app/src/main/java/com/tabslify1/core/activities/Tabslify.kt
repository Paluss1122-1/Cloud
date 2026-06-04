package com.tabslify1.core.activities

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.tabslify1.core.functions.ERRORINSERTDATA
import com.tabslify1.core.functions.errorInsert
import com.tabslify1.core.objects.Config
import com.tabslify1.core.objects.Config.client
import com.tabslify1.core.objects.prvt
import com.tabslify1.quicksettingsfunctions.BatteryDataRepository
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.time.Instant

class Tabslify : Application() {

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

        if (prvt()) {
            Firebase.appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            Firebase.appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }

        FirebaseApp.initializeApp(this)

        if (prvt()) {
            serviceScope.launch {
                client.auth.awaitInitialization()
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