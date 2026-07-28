package com.tabslify.tabs.virustotal

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.tabslify.core.activities.Tabslify
import com.tabslify.tabs.aitab.isAppInForeground
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

var pendingVirusTotalReport: String? by mutableStateOf(null)

data class VirusTotalScanJob(
    val id: String,
    val mode: VirusTotalMode,
    val label: String,
    val state: VirusTotalState,
    val startedAt: Long
)

object VirusTotalScanManager {
    private const val PREFS_NAME = "virustotal_reports"
    private const val MISSING_API_KEY_MESSAGE = "Kein VirusTotal API-Key konfiguriert. Bitte VIRUSTOTAL_API_KEY in local.properties eintragen."

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
    private val repository by lazy { VirusTotalRepository(httpClient) }

    private val _jobs = MutableStateFlow<List<VirusTotalScanJob>>(emptyList())
    val jobs: StateFlow<List<VirusTotalScanJob>> = _jobs.asStateFlow()

    var vtTabVisible: Boolean = false
    private var initialized = false

    private fun ensureInitialized(context: Context) {
        if (!initialized) {
            _jobs.value = loadJobsFromPrefs(context)
            initialized = true
        }
    }

    fun startUrl(context: Context, url: String) {
        if (url.isBlank()) return
        startScan(context, VirusTotalMode.URL, url, url.take(30)) { repository.scanUrl(url.trim()) }
    }

    fun startHash(context: Context, hash: String) {
        if (hash.isBlank()) return
        startScan(context, VirusTotalMode.HASH, hash, hash.take(20)) { repository.lookupHash(hash.trim()) }
    }

    fun startFile(context: Context, fileName: String, bytes: ByteArray) {
        startScan(context, VirusTotalMode.FILE, fileName, fileName.take(30)) { repository.scanFile(fileName, bytes) }
    }

    private fun startScan(
        context: Context,
        mode: VirusTotalMode,
        rawInput: String,
        label: String,
        scanAction: suspend () -> VirusTotalState
    ) {
        ensureInitialized(context)
        val id = UUID.randomUUID().toString()
        if (!repository.hasApiKey) {
            val job = VirusTotalScanJob(id, mode, label, VirusTotalState.Error(MISSING_API_KEY_MESSAGE), System.currentTimeMillis())
            _jobs.value = _jobs.value + job
            persistJobs(context)
            return
        }

        val initialJob = VirusTotalScanJob(id, mode, label, VirusTotalState.Loading, System.currentTimeMillis())
        _jobs.value = _jobs.value + initialJob
        persistJobs(context)

        Tabslify.serviceScope.launch {
            val result = scanAction()
            val updatedJob = initialJob.copy(state = result)
            updateJob(id, result, context)
            onJobComplete(context, updatedJob)
        }
    }

    private fun updateJob(id: String, newState: VirusTotalState, context: Context) {
        ensureInitialized(context)
        val currentList = _jobs.value
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            val newList = currentList.toMutableList()
            newList[index] = newList[index].copy(state = newState)
            _jobs.value = newList
            persistJobs(context)
        }
    }

    private fun onJobComplete(context: Context, job: VirusTotalScanJob) {
        if (!vtTabVisible || !isAppInForeground()) {
            sendVirusTotalScanNotification(context, job)
        }
    }

    fun dismiss(context: Context, id: String) {
        ensureInitialized(context)
        val newList = _jobs.value.filter { it.id != id }
        _jobs.value = newList
        persistJobs(context)
    }

    fun reportById(context: Context, id: String): VirusTotalScanJob? {
        ensureInitialized(context)
        _jobs.value.find { it.id == id }?.let { return it }
        val loaded = loadJobsFromPrefs(context)
        return loaded.find { it.id == id }
    }

    private fun persistJobs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        val recentJobs = _jobs.value.sortedByDescending { it.startedAt }.take(20)
        
        for (job in recentJobs) {
            val jsonObj = JSONObject().apply {
                put("id", job.id)
                put("mode", job.mode.name)
                put("label", job.label)
                put("startedAt", job.startedAt)
                
                when (val state = job.state) {
                    is VirusTotalState.Loading -> {
                        put("type", "loading")
                    }
                    is VirusTotalState.Error -> {
                        put("type", "error")
                        put("errorMessage", state.message)
                    }
                    is VirusTotalState.Result -> {
                        put("type", "result")
                        put("malicious", state.stats.malicious)
                        put("suspicious", state.stats.suspicious)
                        put("harmless", state.stats.harmless)
                        put("undetected", state.stats.undetected)
                        put("total", state.stats.total)
                        put("permalink", state.permalink)
                    }
                    is VirusTotalState.Idle -> {
                        put("type", "idle")
                    }
                }
            }
            jsonArray.put(jsonObj)
        }
        prefs.edit { putString("jobs", jsonArray.toString()) }
    }

    private fun loadJobsFromPrefs(context: Context): List<VirusTotalScanJob> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("jobs", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<VirusTotalScanJob>()
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                val id = jsonObj.optString("id")
                val modeStr = jsonObj.optString("mode")
                val label = jsonObj.optString("label")
                val startedAt = jsonObj.optLong("startedAt")
                val type = jsonObj.optString("type")
                
                if (id.isEmpty() || modeStr.isEmpty()) continue
                
                val mode = try { VirusTotalMode.valueOf(modeStr) } catch (e: Exception) { continue }
                
                val state = when (type) {
                    "loading" -> VirusTotalState.Loading
                    "error" -> VirusTotalState.Error(jsonObj.optString("errorMessage"))
                    "result" -> VirusTotalState.Result(
                        stats = VirusTotalStats(
                            malicious = jsonObj.optInt("malicious"),
                            suspicious = jsonObj.optInt("suspicious"),
                            undetected = jsonObj.optInt("undetected"),
                            harmless = jsonObj.optInt("harmless"),
                            timeout = 0
                        ),
                        permalink = jsonObj.optString("permalink")
                    )
                    else -> VirusTotalState.Idle
                }
                
                list.add(VirusTotalScanJob(id, mode, label, state, startedAt))
            }
            list.sortedBy { it.startedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
