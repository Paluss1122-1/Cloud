package com.cloud.authenticator

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.graphics.drawable.Icon
import android.os.CancellationSignal
import android.service.autofill.*
import android.util.Log
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.autofill.inline.v1.InlineSuggestionUi

class CloudAutofillService : AutofillService() {

    companion object {
        private const val TAG = "CLOUD_AUTOFILL"
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.last().structure
        val testEntries = listOf(
            TestEntry("Test Account 1", "123456"),
            TestEntry("Test Account 2", "789012"),
            TestEntry("Demo Login", "345678")
        )

        val parsedField = findOtpField(structure) ?: run {
            Log.e(TAG, "No field found for autofill!")
            callback.onSuccess(null)
            return
        }

        val inlineRequest = request.inlineSuggestionsRequest

        val response = FillResponse.Builder()
        var datasetsAdded = 0

        testEntries.forEachIndexed { index, entry ->
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, "☁️ ${entry.name}: ${entry.code}")
            }

            val datasetBuilder = Dataset.Builder()
                .setValue(parsedField, AutofillValue.forText(entry.code), presentation)

            if (inlineRequest != null) {
                val inlinePresentation = createInlinePresentation(inlineRequest, index, entry)
                if (inlinePresentation != null) {
                    datasetBuilder.setInlinePresentation(inlinePresentation)
                }
            }

            response.addDataset(datasetBuilder.build())
            datasetsAdded++
        }

        callback.onSuccess(response.build())
    }

    private fun createInlinePresentation(
        inlineRequest: InlineSuggestionsRequest,
        index: Int,
        entry: TestEntry
    ): InlinePresentation? {

        val presentationSpecs = inlineRequest.inlinePresentationSpecs

        if (presentationSpecs.isEmpty()) {
            return null
        }

        val spec = if (index < presentationSpecs.size) {
            presentationSpecs[index]
        } else {
            presentationSpecs.last()
        }

        return try {
            val result = createInlinePresentationWithBuilder(spec, entry)
            result
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("RestrictedApi")
    private fun createInlinePresentationWithBuilder(
        spec: InlinePresentationSpec,
        entry: TestEntry
    ): InlinePresentation? {
        try {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                packageManager.getLaunchIntentForPackage(packageName)!!,
                PendingIntent.FLAG_IMMUTABLE
            )

            val content = InlineSuggestionUi.newContentBuilder(pendingIntent)
                .setTitle("☁️ ${entry.name}")
                .setSubtitle("Code: ${entry.code}")
                .setStartIcon(Icon.createWithResource(this, android.R.drawable.ic_lock_lock))
                .build()

            val slice = content.slice

            return InlinePresentation(slice, spec, false)

        } catch (e: Exception) {
            Log.e("CLOUD_AUTOFILL", "❌ Failed to create inline presentation", e)
            return null
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private fun extractUrl(structure: AssistStructure): String? {
        for (i in 0 until structure.windowNodeCount) {
            val domain = searchForWebDomain(structure.getWindowNodeAt(i).rootViewNode)
            if (domain != null) return domain
            val title = structure.getWindowNodeAt(i).title?.toString()
            if (!title.isNullOrBlank()) return title
        }
        return null
    }

    private fun searchForWebDomain(node: AssistStructure.ViewNode): String? {
        node.webDomain?.let { return it }
        for (i in 0 until node.childCount) {
            val result = searchForWebDomain(node.getChildAt(i))
            if (result != null) return result
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
        val idEntry = node.idEntry?.lowercase() ?: ""

        // Erweiterte Erkennung für verschiedene Felder
        if (hints != null && hints.any {
                it.contains("username", true) ||
                        it.contains("email", true) ||
                        it.contains("password", true) ||
                        it.contains("otp", true) ||
                        it.contains("code", true)
            }) {
            return node.autofillId
        }

        // Prüfe hint und idEntry
        if (hint.contains("email") || hint.contains("user") || hint.contains("password") ||
            hint.contains("otp") || hint.contains("code") || hint.contains("token") ||
            hint.contains("verification") ||
            idEntry.contains("email") || idEntry.contains("user") || idEntry.contains("password")
        ) {
            return node.autofillId
        }

        // Numerisches Input-Feld als Fallback
        if (android.text.InputType.TYPE_CLASS_NUMBER ==
            (inputType and android.text.InputType.TYPE_MASK_CLASS)) {
            return node.autofillId
        }

        for (i in 0 until node.childCount) {
            val result = searchNode(node.getChildAt(i))
            if (result != null) return result
        }
        return null
    }

    // Test-Datenklasse
    data class TestEntry(val name: String, val code: String)
}