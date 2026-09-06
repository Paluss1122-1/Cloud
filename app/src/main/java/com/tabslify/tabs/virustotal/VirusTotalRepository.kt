package com.tabslify.tabs.virustotal

import android.util.Base64
import com.tabslify.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.milliseconds

private const val BASE_URL = "https://www.virustotal.com/api/v3"

class VirusTotalRepository(private val httpClient: HttpClient) {

    val hasApiKey: Boolean get() = BuildConfig.VIRUSTOTAL_API_KEY.isNotBlank()

    suspend fun lookupHash(hash: String): VirusTotalState = withContext(Dispatchers.IO) {
        try {
            val response = httpClient.get("$BASE_URL/files/$hash") {
                headers { append("x-apikey", BuildConfig.VIRUSTOTAL_API_KEY) }
            }
            if (!response.status.isSuccess()) {
                return@withContext VirusTotalState.Error("VirusTotal Fehler: ${response.status}")
            }
            val data = Json.parseToJsonElement(response.bodyAsText()).jsonObject["data"]?.jsonObject
            val stats = data?.get("attributes")?.jsonObject?.get("last_analysis_stats")?.jsonObject
            val id = data?.get("id")?.jsonPrimitive?.content ?: hash
            VirusTotalState.Result(parseStats(stats), "https://www.virustotal.com/gui/file/$id")
        } catch (e: Exception) {
            VirusTotalState.Error("Anfrage fehlgeschlagen: ${e.localizedMessage}")
        }
    }

    suspend fun scanUrl(url: String): VirusTotalState = withContext(Dispatchers.IO) {
        try {
            val urlId = Base64.encodeToString(
                url.toByteArray(),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )

            val existing = httpClient.get("$BASE_URL/urls/$urlId") {
                headers { append("x-apikey", BuildConfig.VIRUSTOTAL_API_KEY) }
            }
            if (existing.status.isSuccess()) {
                val stats = Json.parseToJsonElement(existing.bodyAsText())
                    .jsonObject["data"]?.jsonObject
                    ?.get("attributes")?.jsonObject
                    ?.get("last_analysis_stats")?.jsonObject
                if (stats != null) {
                    return@withContext VirusTotalState.Result(
                        parseStats(stats),
                        "https://www.virustotal.com/gui/url/$urlId"
                    )
                }
            }

            val submit = httpClient.submitForm(
                url = "$BASE_URL/urls",
                formParameters = Parameters.build { append("url", url) }
            ) {
                headers { append("x-apikey", BuildConfig.VIRUSTOTAL_API_KEY) }
            }
            if (!submit.status.isSuccess()) {
                return@withContext VirusTotalState.Error("Einreichen fehlgeschlagen: ${submit.status}")
            }
            val analysisId = Json.parseToJsonElement(submit.bodyAsText())
                .jsonObject["data"]?.jsonObject?.get("id")?.jsonPrimitive?.content
                ?: return@withContext VirusTotalState.Error("Keine Analyse-ID erhalten")

            pollAnalysis(analysisId, "https://www.virustotal.com/gui/url/$urlId")
        } catch (e: Exception) {
            VirusTotalState.Error("Scan fehlgeschlagen: ${e.localizedMessage}")
        }
    }

    suspend fun scanFile(fileName: String, bytes: ByteArray): VirusTotalState = withContext(Dispatchers.IO) {
        try {
            val response = httpClient.submitFormWithBinaryData(
                url = "$BASE_URL/files",
                formData = formData {
                    append(
                        "file",
                        bytes,
                        io.ktor.http.Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        }
                    )
                }
            ) {
                headers { append("x-apikey", BuildConfig.VIRUSTOTAL_API_KEY) }
            }
            if (!response.status.isSuccess()) {
                return@withContext VirusTotalState.Error("Upload fehlgeschlagen: ${response.status}")
            }
            val analysisId = Json.parseToJsonElement(response.bodyAsText())
                .jsonObject["data"]?.jsonObject?.get("id")?.jsonPrimitive?.content
                ?: return@withContext VirusTotalState.Error("Keine Analyse-ID erhalten")

            pollAnalysis(analysisId, null)
        } catch (e: Exception) {
            VirusTotalState.Error("Upload fehlgeschlagen: ${e.localizedMessage}")
        }
    }

    private suspend fun pollAnalysis(analysisId: String, permalinkFallback: String?): VirusTotalState {
        repeat(15) {
            val response: HttpResponse = httpClient.get("$BASE_URL/analyses/$analysisId") {
                headers { append("x-apikey", BuildConfig.VIRUSTOTAL_API_KEY) }
            }
            if (response.status.isSuccess()) {
                val attributes = Json.parseToJsonElement(response.bodyAsText())
                    .jsonObject["data"]?.jsonObject?.get("attributes")?.jsonObject
                if (attributes?.get("status")?.jsonPrimitive?.content == "completed") {
                    val stats = attributes["stats"]?.jsonObject
                    val permalink = permalinkFallback ?: "https://www.virustotal.com/gui/analyses/$analysisId"
                    return parseStats(stats).let { VirusTotalState.Result(it, permalink) }
                }
            }
            delay(3000.milliseconds)
        }
        return VirusTotalState.Error("Analyse dauert zu lange, bitte später erneut versuchen")
    }

    private fun parseStats(stats: JsonObject?): VirusTotalStats {
        fun value(key: String) = stats?.get(key)?.jsonPrimitive?.intOrNull ?: 0
        return VirusTotalStats(
            malicious = value("malicious"),
            suspicious = value("suspicious"),
            harmless = value("harmless"),
            undetected = value("undetected"),
            timeout = value("timeout")
        )
    }
}
