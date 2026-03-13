package com.cloud.authenticator

import android.app.assist.AssistStructure
import android.os.CancellationSignal
import android.service.autofill.*
import android.util.Log
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import kotlinx.coroutines.runBlocking

class CloudAutofillService : AutofillService() {

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.last().structure
        val urlHint = extractUrl(structure)
        Log.d("CLOUD_AUTOFILL", "onFillRequest url=$urlHint")

        val entries = runBlocking {
            TwoFADatabase.getDatabase(this@CloudAutofillService).twoFADao().getAll()
        }

        val match = if (urlHint != null) {
            entries.firstOrNull { it.url.isNotBlank() && urlHint.contains(it.url, ignoreCase = true) }
        } else null

        val toShow = if (match != null) listOf(match) else entries.take(5)

        if (toShow.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val parsedField = findOtpField(structure) ?: run {
            callback.onSuccess(null)
            return
        }

        val datasetList = toShow.map { entry ->
            val code = TotpGenerator.generateTOTP(entry.secret, System.currentTimeMillis())
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, "☁️ ${entry.name}: $code")
            }
            Dataset.Builder()
                .setValue(parsedField, AutofillValue.forText(code), presentation)
                .build()
        }

        val response = FillResponse.Builder()
        datasetList.forEach { response.addDataset(it) }
        callback.onSuccess(response.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private fun extractUrl(structure: AssistStructure): String? {
        for (i in 0 until structure.windowNodeCount) {
            val url = structure.getWindowNodeAt(i).title?.toString()
            if (!url.isNullOrBlank()) return url
        }
        return null
    }

    private fun findOtpField(structure: AssistStructure): android.view.autofill.AutofillId? {
        for (i in 0 until structure.windowNodeCount) {
            val result = searchNode(structure.getWindowNodeAt(i).rootViewNode)
            if (result != null) return result
        }
        return null
    }

    private fun searchNode(node: AssistStructure.ViewNode): android.view.autofill.AutofillId? {
        val hints = node.autofillHints
        val inputType = node.inputType
        val hint = node.hint?.lowercase() ?: ""

        // OTP/Code Felder erkennen
        if (hints != null && hints.any { it.contains("otp", true) || it.contains("code", true) || it.contains("password", true) }) {
            return node.autofillId
        }
        if (hint.contains("otp") || hint.contains("code") || hint.contains("token") || hint.contains("verification")) {
            return node.autofillId
        }
        // Numerisches Input-Feld als Fallback
        if (android.text.InputType.TYPE_CLASS_NUMBER == (inputType and android.text.InputType.TYPE_MASK_CLASS)) {
            return node.autofillId
        }

        for (i in 0 until node.childCount) {
            val result = searchNode(node.getChildAt(i))
            if (result != null) return result
        }
        return null
    }
}