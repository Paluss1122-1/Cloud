package com.cloud.authenticator

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.graphics.drawable.Icon
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.core.net.toUri
import kotlinx.coroutines.runBlocking

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
        val loginFields = findLoginFields(structure)

        if (loginFields.usernameId == null && loginFields.passwordId == null && loginFields.otpId == null) {
            callback.onSuccess(null)
            return
        }

        val domain = extractRequestDomain(structure)
        Log.d(TAG, "AutoFill request – domain: $domain  pkg: ${structure.activityComponent.packageName}")

        val inlineRequest = request.inlineSuggestionsRequest
        val responseBuilder = FillResponse.Builder()
        var hasDataset = false

        if (loginFields.usernameId != null || loginFields.passwordId != null) {
            val db = PasswordDatabase.getDatabase(applicationContext)
            val entries = runBlocking {
                if (domain.isNotEmpty()) db.passwordDao().findByDomain(domain)
                else db.passwordDao().search(structure.activityComponent.packageName)
            }
            entries.take(5).forEachIndexed { index, entry ->
                val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
                    setTextViewText(android.R.id.text1, "☁️ ${entry.name}")
                    setTextViewText(android.R.id.text2, entry.username.ifEmpty { "(kein Benutzername)" })
                }
                val ds = Dataset.Builder()
                loginFields.usernameId?.let { ds.setValue(it, AutofillValue.forText(entry.username), presentation) }
                loginFields.passwordId?.let { ds.setValue(it, AutofillValue.forText(entry.password), presentation) }
                if (inlineRequest != null) createInlinePresentation(inlineRequest, index, entry)?.let { ds.setInlinePresentation(it) }
                responseBuilder.addDataset(ds.build())
                hasDataset = true
            }
        }

        loginFields.otpId?.let { otpFieldId ->
            val twoFaDb = TwoFADatabase.getDatabase(applicationContext)
            val twoFaEntries = runBlocking { twoFaDb.twoFADao().getAll() }
            val matched = twoFaEntries.filter { entry ->
                domain.isNotEmpty() && (
                        entry.url.contains(domain, ignoreCase = true) ||
                                entry.name.lowercase().contains(domain.substringBefore(".")) ||
                                domain.contains(entry.name.lowercase().replace(" ", ""))
                        )
            }
            matched.take(3).forEachIndexed { index, entry ->
                val code = TotpGenerator.generateTOTP(entry.secret)
                val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
                    setTextViewText(android.R.id.text1, "🛡️ ${entry.name}")
                    setTextViewText(android.R.id.text2, code)
                }
                val ds = Dataset.Builder().setValue(otpFieldId, AutofillValue.forText(code), presentation)
                if (inlineRequest != null) createInlinePresentation(
                    inlineRequest, index,
                    PasswordEntry(name = entry.name, username = code, password = "")
                )?.let { ds.setInlinePresentation(it) }
                responseBuilder.addDataset(ds.build())
                hasDataset = true
            }
        }

        if (!hasDataset) { callback.onSuccess(null); return }
        callback.onSuccess(responseBuilder.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    data class LoginFields(
        val usernameId: AutofillId? = null,
        val passwordId: AutofillId? = null,
        val otpId: AutofillId? = null
    )

    private fun findLoginFields(structure: AssistStructure): LoginFields {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var otpId: AutofillId? = null

        fun searchNode(node: AssistStructure.ViewNode) {
            val hints = node.autofillHints
            val inputType = node.inputType
            val hint = node.hint?.lowercase() ?: ""
            val idEntry = node.idEntry?.lowercase() ?: ""

            val isPassword = (
                    hints?.any { h -> h.contains("password", true) || h == "current-password" || h == "new-password" } == true
                            || (inputType and android.text.InputType.TYPE_MASK_VARIATION) == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                            || (inputType and android.text.InputType.TYPE_MASK_VARIATION) == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                            || (inputType and android.text.InputType.TYPE_MASK_VARIATION) == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                            || hint.contains("passwort") || hint.contains("password") || hint.contains("pin")
                            || idEntry.contains("password") || idEntry.contains("passwd") || idEntry.contains("pwd")
                    )

            if (isPassword && node.autofillId != null) passwordId = node.autofillId

            val isOtp = (
                    hints?.any { h -> h.contains("one-time-code", true) || h.contains("otp", true) } == true
                            || hint.contains("otp") || hint.contains("einmal") || hint.contains("token") || hint.contains("authenticator")
                            || idEntry.contains("otp") || idEntry.contains("totp") || idEntry.contains("token") || idEntry.contains("mfa") || idEntry.contains("tfa")
                    )

            if (isOtp && !isPassword && node.autofillId != null) otpId = node.autofillId

            val isUsername = (
                    hints?.any { h -> h.contains("username", true) || h.contains("email", true) || h == "username" || h == "email" } == true
                            || hint.contains("user") || hint.contains("email") || hint.contains("benutzername")
                            || hint.contains("login") || hint.contains("e-mail")
                            || idEntry.contains("user") || idEntry.contains("email") || idEntry.contains("login")
                            || (inputType and android.text.InputType.TYPE_MASK_VARIATION) == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                            || (inputType and android.text.InputType.TYPE_MASK_VARIATION) == android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                    )

            if (isUsername && !isPassword && !isOtp && node.autofillId != null) usernameId = node.autofillId

            for (i in 0 until node.childCount) searchNode(node.getChildAt(i))
        }

        for (i in 0 until structure.windowNodeCount) {
            searchNode(structure.getWindowNodeAt(i).rootViewNode)
        }

        return LoginFields(usernameId, passwordId, otpId)
    }

    private fun extractDomain(raw: String): String {
        return try {
            val normalized = if (raw.contains("://")) raw else "https://$raw"
            val host = normalized.toUri().host ?: return ""
            host.lowercase().removePrefix("www.").trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractRequestDomain(structure: AssistStructure): String {
        fun searchWebDomain(node: AssistStructure.ViewNode): String? {
            node.webDomain?.takeIf { it.isNotBlank() }?.let { return it }
            for (i in 0 until node.childCount) {
                searchWebDomain(node.getChildAt(i))?.let { return it }
            }
            return null
        }

        for (i in 0 until structure.windowNodeCount) {
            val domain = searchWebDomain(structure.getWindowNodeAt(i).rootViewNode)
            if (!domain.isNullOrBlank()) return extractDomain(domain)
        }

        for (i in 0 until structure.windowNodeCount) {
            val title = structure.getWindowNodeAt(i).title?.toString() ?: ""
            val urlRegex = Regex("""https?://[^\s/$.?#].\S*""")
            urlRegex.find(title)?.value?.let { return extractDomain(it) }
        }

        return ""
    }

    private fun createInlinePresentation(
        inlineRequest: InlineSuggestionsRequest,
        index: Int,
        entry: PasswordEntry
    ): InlinePresentation? {
        val specs = inlineRequest.inlinePresentationSpecs
        if (specs.isEmpty()) return null
        val spec = if (index < specs.size) specs[index] else specs.last()
        return try {
            createInlineChip(spec, entry)
        } catch (e: Exception) {
            Log.e(TAG, "createInlinePresentation failed: ${e.message}")
            null
        }
    }

    @SuppressLint("RestrictedApi")
    private fun createInlineChip(spec: InlinePresentationSpec, entry: PasswordEntry): InlinePresentation? {
        return try {
            val pendingIntent = PendingIntent.getActivity(
                this, 0,
                packageManager.getLaunchIntentForPackage(packageName)!!,
                PendingIntent.FLAG_IMMUTABLE
            )
            val content = InlineSuggestionUi.newContentBuilder(pendingIntent)
                .setTitle(entry.name)
                .setSubtitle(entry.username.ifEmpty { "Kein Benutzername" })
                .setStartIcon(Icon.createWithResource(this, android.R.drawable.ic_lock_lock))
                .build()
            InlinePresentation(content.slice, spec, false)
        } catch (e: Exception) {
            Log.e(TAG, "createInlineChip failed: ${e.message}")
            null
        }
    }
}