package com.cloud.gmailtab

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.graphics.toColorInt

// ─── Datenmodell ────────────────────────────────────────────────────────────

data class GmailAccount(
    val id: String,
    val email: String,
    val displayName: String,
    val cookieKey: String,          // key im SharedPrefs zum Wiederherstellen
    val avatarColor: Long = 0xFF4285F4
)

private val AVATAR_COLORS = listOf(
    0xFF4285F4L, 0xFFEA4335L, 0xFF34A853L, 0xFFFBBC05L,
    0xFF9C27B0L, 0xFF00BCD4L, 0xFFFF5722L, 0xFF607D8BL
)

// ─── Persistenz ─────────────────────────────────────────────────────────────

private const val PREFS_NAME       = "gmail_multi_account"
private const val KEY_ACCOUNTS     = "accounts_json"
private const val KEY_ACTIVE_ID    = "active_account_id"
private const val KEY_COOKIE_PREFIX = "cookie_"

private fun loadAccounts(prefs: SharedPreferences): List<GmailAccount> {
    val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
    return try {
        val arr = org.json.JSONArray(raw)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            GmailAccount(
                id          = o.getString("id"),
                email       = o.getString("email"),
                displayName = o.getString("displayName"),
                cookieKey   = o.getString("cookieKey"),
                avatarColor = o.getLong("avatarColor")
            )
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveAccounts(prefs: SharedPreferences, accounts: List<GmailAccount>) {
    val arr = org.json.JSONArray()
    accounts.forEach { a ->
        arr.put(org.json.JSONObject().apply {
            put("id",          a.id)
            put("email",       a.email)
            put("displayName", a.displayName)
            put("cookieKey",   a.cookieKey)
            put("avatarColor", a.avatarColor)
        })
    }
    prefs.edit { putString(KEY_ACCOUNTS, arr.toString()) }
}

private fun saveCookiesForAccount(prefs: SharedPreferences, cookieKey: String, url: String) {
    val cm   = CookieManager.getInstance()
    val cookies = cm.getCookie(url) ?: return
    prefs.edit { putString(KEY_COOKIE_PREFIX + cookieKey, cookies) }
}

private fun restoreCookiesForAccount(prefs: SharedPreferences, cookieKey: String, url: String) {
    val cookies = prefs.getString(KEY_COOKIE_PREFIX + cookieKey, null) ?: return
    val cm = CookieManager.getInstance()
    cookies.split(";").forEach { cookie ->
        cm.setCookie(url, cookie.trim())
    }
    cm.flush()
}

private fun deleteCookiesForAccount(prefs: SharedPreferences, cookieKey: String) {
    prefs.edit { remove(KEY_COOKIE_PREFIX + cookieKey) }
}

// ─── Haupt-Composable ────────────────────────────────────────────────────────

enum class GmailScreen { ACCOUNT_PICKER, WEBVIEW, ADD_ACCOUNT }

@Composable
fun GmailTab() {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val scope   = rememberCoroutineScope()

    var accounts     by remember { mutableStateOf(loadAccounts(prefs)) }
    var activeAccount by remember {
        val savedId = prefs.getString(KEY_ACTIVE_ID, null)
        mutableStateOf(accounts.find { it.id == savedId } ?: accounts.firstOrNull())
    }
    var screen        by remember {
        mutableStateOf(
            if (accounts.isEmpty()) GmailScreen.ADD_ACCOUNT else GmailScreen.WEBVIEW
        )
    }
    var showDrawer    by remember { mutableStateOf(false) }
    var webViewRef    by remember { mutableStateOf<WebView?>(null) }
    var pageTitle     by remember { mutableStateOf("Gmail") }
    var isLoading     by remember { mutableStateOf(false) }
    var canGoBack     by remember { mutableStateOf(false) }

    // Wenn Account gewechselt → Cookies wiederherstellen
    LaunchedEffect(activeAccount) {
        activeAccount?.let {
            prefs.edit { putString(KEY_ACTIVE_ID, it.id) }
        }
    }

    BackHandler(enabled = canGoBack || showDrawer) {
        when {
            showDrawer -> showDrawer = false
            canGoBack  -> webViewRef?.goBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {

        // ── Haupt-Content ────────────────────────────────────────────────
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically { it / 10 }) togetherWith
                        (fadeOut(tween(180)) + slideOutVertically { -it / 10 })
            },
            modifier = Modifier.fillMaxSize()
        ) { currentScreen ->
            when (currentScreen) {
                GmailScreen.ACCOUNT_PICKER -> AccountPickerScreen(
                    accounts      = accounts,
                    activeAccount = activeAccount,
                    onSelectAccount = { acc ->
                        // Cookies des alten Accounts sichern
                        activeAccount?.let {
                            saveCookiesForAccount(prefs, it.cookieKey, "https://mail.google.com")
                        }
                        // Cookies löschen & neue laden
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        restoreCookiesForAccount(prefs, acc.cookieKey, "https://mail.google.com")

                        activeAccount = acc
                        webViewRef?.loadUrl("https://mail.google.com")
                        screen = GmailScreen.WEBVIEW
                    },
                    onAddAccount = {
                        // Cookies sichern
                        activeAccount?.let {
                            saveCookiesForAccount(prefs, it.cookieKey, "https://mail.google.com")
                        }
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        screen = GmailScreen.ADD_ACCOUNT
                    },
                    onDeleteAccount = { acc ->
                        deleteCookiesForAccount(prefs, acc.cookieKey)
                        accounts = accounts.filter { it.id != acc.id }
                        saveAccounts(prefs, accounts)
                        if (activeAccount?.id == acc.id) {
                            activeAccount = accounts.firstOrNull()
                            activeAccount?.let {
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                                restoreCookiesForAccount(prefs, it.cookieKey, "https://mail.google.com")
                                webViewRef?.loadUrl("https://mail.google.com")
                            }
                        }
                        screen = if (accounts.isEmpty()) GmailScreen.ADD_ACCOUNT else GmailScreen.WEBVIEW
                    },
                    onDismiss = { screen = GmailScreen.WEBVIEW }
                )

                GmailScreen.ADD_ACCOUNT -> AddAccountScreen(
                    onAccountAdded = { email, displayName ->
                        val newId  = System.currentTimeMillis().toString()
                        val newKey = "acc_$newId"
                        val color  = AVATAR_COLORS[accounts.size % AVATAR_COLORS.size]
                        val acc    = GmailAccount(newId, email, displayName, newKey, color)
                        // Cookies jetzt speichern
                        saveCookiesForAccount(prefs, newKey, "https://mail.google.com")

                        accounts = accounts + acc
                        saveAccounts(prefs, accounts)
                        activeAccount = acc
                        screen = GmailScreen.WEBVIEW
                    },
                    onBack = {
                        screen = if (accounts.isEmpty()) GmailScreen.ADD_ACCOUNT else GmailScreen.WEBVIEW
                    },
                    webViewCallback = { wv -> webViewRef = wv },
                    onPageTitleChange = { pageTitle = it },
                    onLoadingChange   = { isLoading = it },
                    onCanGoBackChange = { canGoBack = it }
                )

                GmailScreen.WEBVIEW -> {
                    if (activeAccount == null) {
                        EmptyAccountsPlaceholder { screen = GmailScreen.ADD_ACCOUNT }
                    } else {
                        GmailWebView(
                            account           = activeAccount!!,
                            prefs             = prefs,
                            onWebViewCreated  = { webViewRef = it },
                            onPageTitle       = { pageTitle = it },
                            onLoadingChange   = { isLoading = it },
                            onCanGoBackChange = { canGoBack = it }
                        )
                    }
                }
            }
        }

        // ── Top-Bar ──────────────────────────────────────────────────────
        if (screen != GmailScreen.ACCOUNT_PICKER) {
            TopBar(
                title         = if (screen == GmailScreen.WEBVIEW) pageTitle else "Konto hinzufügen",
                isLoading     = isLoading,
                canGoBack     = canGoBack && screen == GmailScreen.WEBVIEW,
                activeAccount = activeAccount,
                onBack        = { webViewRef?.goBack() },
                onMenuClick   = {
                    if (screen == GmailScreen.WEBVIEW) showDrawer = true
                },
                onRefresh     = { webViewRef?.reload() },
                modifier      = Modifier.align(Alignment.TopCenter)
            )
        }

        // ── Account-Drawer ────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showDrawer,
            enter   = fadeIn(),
            exit    = fadeOut()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { showDrawer = false }
            )
        }
        AnimatedVisibility(
            visible = showDrawer,
            enter   = slideInVertically { -it },
            exit    = slideOutVertically { -it }
        ) {
            AccountDrawer(
                accounts      = accounts,
                activeAccount = activeAccount,
                onSelectAccount = { acc ->
                    showDrawer = false
                    activeAccount?.let {
                        saveCookiesForAccount(prefs, it.cookieKey, "https://mail.google.com")
                    }
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    restoreCookiesForAccount(prefs, acc.cookieKey, "https://mail.google.com")
                    activeAccount = acc
                    webViewRef?.loadUrl("https://mail.google.com")
                },
                onManageAccounts = {
                    showDrawer = false
                    screen = GmailScreen.ACCOUNT_PICKER
                },
                onAddAccount = {
                    showDrawer = false
                    activeAccount?.let {
                        saveCookiesForAccount(prefs, it.cookieKey, "https://mail.google.com")
                    }
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    screen = GmailScreen.ADD_ACCOUNT
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

// ─── Gmail WebView ───────────────────────────────────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GmailWebView(
    account           : GmailAccount,
    prefs             : SharedPreferences,
    onWebViewCreated  : (WebView) -> Unit,
    onPageTitle       : (String) -> Unit,
    onLoadingChange   : (Boolean) -> Unit,
    onCanGoBackChange : (Boolean) -> Unit
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            buildWebView(ctx).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        onLoadingChange(true)
                        onCanGoBackChange(view.canGoBack())
                    }
                    override fun onPageFinished(view: WebView, url: String) {
                        onLoadingChange(false)
                        onCanGoBackChange(view.canGoBack())
                        // Cookies speichern wenn wir auf mail.google.com sind
                        if (url.contains("mail.google.com") || url.contains("google.com")) {
                            saveCookiesForAccount(prefs, account.cookieKey, "https://mail.google.com")
                        }
                    }
                    override fun onReceivedError(view: WebView, req: WebResourceRequest, error: WebResourceError) {}
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView, title: String) {
                        onPageTitle(title.ifBlank { "Gmail" })
                    }
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        onLoadingChange(newProgress < 100)
                    }
                }
                // Cookies wiederherstellen dann laden
                restoreCookiesForAccount(prefs, account.cookieKey, "https://mail.google.com")
                loadUrl("https://mail.google.com")
                onWebViewCreated(this)
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 56.dp)     // Platz für TopBar
    )
}

// ─── AddAccount-Screen (neuer Login-WebView) ─────────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AddAccountScreen(
    onAccountAdded    : (email: String, displayName: String) -> Unit,
    onBack            : () -> Unit,
    webViewCallback   : (WebView) -> Unit,
    onPageTitleChange : (String) -> Unit,
    onLoadingChange   : (Boolean) -> Unit,
    onCanGoBackChange : (Boolean) -> Unit
) {
    var currentUrl    by remember { mutableStateOf("") }
    var webViewRef    by remember { mutableStateOf<WebView?>(null) }
    val onGmail       = currentUrl.contains("mail.google.com") && !currentUrl.contains("accounts.google")

    fun extractAndAdd(wv: WebView) {
        val js = """
            (function(){
                var selectors = ['[data-email]','[aria-label*="@"]','[data-hovercard-id*="@"]'];
                for(var s of selectors){
                    var el = document.querySelector(s);
                    if(el){
                        var e = el.getAttribute('data-email') || el.getAttribute('aria-label') || el.getAttribute('data-hovercard-id');
                        if(e && e.includes('@')) return e.trim();
                    }
                }
                var m = document.title.match(/[\w.+\-]+@[\w.\-]+\.[a-z]{2,}/i);
                if(m) return m[0];
                var all = document.body.innerText.match(/[\w.+\-]+@gmail\.com/i);
                return all ? all[0] : '';
            })()
        """.trimIndent()
        wv.evaluateJavascript(js) { result ->
            val email = result?.trim()?.removeSurrounding("\"")?.takeIf { it.contains("@") }
            if (email != null) {
                val name = email.substringBefore("@")
                    .replace(".", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                onAccountAdded(email, name)
            } else {
                // Fallback: ohne Email speichern, URL als Hinweis
                onAccountAdded("unbekannt@gmail.com", "Google-Konto")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(top = 56.dp)) {
        AndroidView(
            factory = { ctx ->
                buildWebView(ctx).apply {
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, fav: Bitmap?) {
                            currentUrl = url
                            onLoadingChange(true)
                            onCanGoBackChange(view.canGoBack())
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            currentUrl = url
                            onLoadingChange(false)
                            onCanGoBackChange(view.canGoBack())
                            if (url.contains("mail.google.com") && !url.contains("accounts.google")) {
                                extractAndAdd(view)
                            }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView, title: String) { onPageTitleChange(title) }
                        override fun onProgressChanged(view: WebView, p: Int) { onLoadingChange(p < 100) }
                    }
                    loadUrl("https://accounts.google.com/signin/v2/identifier?service=mail&flowName=GlifWebSignIn&flowEntry=ServiceLogin")
                    webViewCallback(this)
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Manueller Fallback-Button wenn auf mail.google.com
        if (onGmail) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF4285F4))
                    .clickable { webViewRef?.let { extractAndAdd(it) } }
                    .padding(horizontal = 28.dp, vertical = 14.dp)
            ) {
                Text("Konto bestätigen", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Account-Picker-Screen ───────────────────────────────────────────────────

@Composable
fun AccountPickerScreen(
    accounts      : List<GmailAccount>,
    activeAccount : GmailAccount?,
    onSelectAccount  : (GmailAccount) -> Unit,
    onAddAccount     : () -> Unit,
    onDeleteAccount  : (GmailAccount) -> Unit,
    onDismiss        : () -> Unit
) {
    var confirmDelete by remember { mutableStateOf<GmailAccount?>(null) }

    if (confirmDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Color(0xFF2A2A2A),
            title = { Text("Konto entfernen?", color = Color.White, fontWeight = FontWeight.Bold) },
            text  = { Text("${confirmDelete!!.email} wird aus der App entfernt.", color = Color(0xFFB0B0B0)) },
            confirmButton = {
                TextButton(onClick = { onDeleteAccount(confirmDelete!!); confirmDelete = null }) {
                    Text("Entfernen", color = Color(0xFFEA4335))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text("Abbrechen", color = Color(0xFF8888AA))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "Konten verwalten",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("${accounts.size} Konto${if (accounts.size != 1) "en" else ""}", color = Color(0xFF8888AA), fontSize = 13.sp)
            }
        }

        HorizontalDivider(color = Color(0xFF2A2A2A))
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            contentPadding    = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(accounts, key = { it.id }) { acc ->
                AccountRow(
                    account  = acc,
                    isActive = acc.id == activeAccount?.id,
                    onSelect = { onSelectAccount(acc) },
                    onDelete = { confirmDelete = acc }
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF2A2A2A))
                        .clickable { onAddAccount() }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3A3A4A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text("Konto hinzufügen", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun AccountRow(
    account  : GmailAccount,
    isActive : Boolean,
    onSelect : () -> Unit,
    onDelete : () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isActive) Color(0xFF1C2B4A) else Color(0xFF242424))
            .border(
                width = if (isActive) 1.5.dp else 0.dp,
                color = if (isActive) Color(0xFF4285F4) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onSelect() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarCircle(account)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(account.displayName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(account.email, color = Color(0xFF8888AA), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isActive) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E3A6E))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) { Text("Aktiv", color = Color(0xFF4285F4), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(6.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, null, tint = Color(0xFF555565), modifier = Modifier.size(18.dp))
        }
    }
}

// ─── Account-Drawer ──────────────────────────────────────────────────────────

@Composable
fun AccountDrawer(
    accounts         : List<GmailAccount>,
    activeAccount    : GmailAccount?,
    onSelectAccount  : (GmailAccount) -> Unit,
    onManageAccounts : () -> Unit,
    onAddAccount     : () -> Unit,
    modifier         : Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(Color(0xFF222228))
            .padding(vertical = 8.dp)
    ) {
        // Gmail-Logo Zeile
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GmailLogoText()
            Spacer(Modifier.weight(1f))
            // Aktiver Account Avatar
            activeAccount?.let { AvatarCircle(it, size = 34) }
        }

        HorizontalDivider(color = Color(0xFF2E2E38))
        Spacer(Modifier.height(4.dp))

        accounts.forEach { acc ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectAccount(acc) }
                    .background(if (acc.id == activeAccount?.id) Color(0xFF1A2540) else Color.Transparent)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarCircle(acc, size = 36)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(acc.displayName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(acc.email, color = Color(0xFF8888AA), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (acc.id == activeAccount?.id) {
                    Icon(Icons.Default.Email, null, tint = Color(0xFF4285F4), modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = Color(0xFF2E2E38))
        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAddAccount() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF2E2E3A)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(14.dp))
            Text("Konto hinzufügen", color = Color.White, fontSize = 14.sp)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onManageAccounts() }
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF2E2E3A)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Menu, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(14.dp))
            Text("Konten verwalten", color = Color.White, fontSize = 14.sp)
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ─── Top-Bar ─────────────────────────────────────────────────────────────────

@Composable
fun TopBar(
    title         : String,
    isLoading     : Boolean,
    canGoBack     : Boolean,
    activeAccount : GmailAccount?,
    onBack        : () -> Unit,
    onMenuClick   : () -> Unit,
    onRefresh     : () -> Unit,
    modifier      : Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canGoBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
            } else {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, null, tint = Color.White)
                }
            }

            Text(
                text     = title,
                color    = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            )

            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, null, tint = Color(0xFF8888AA))
            }

            activeAccount?.let {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clickable { onMenuClick() }
                ) { AvatarCircle(it, size = 32) }
            }
        }

        // Lade-Balken
        if (isLoading) {
            LinearProgressIndicator(
                modifier    = Modifier.fillMaxWidth().height(2.dp),
                color       = Color(0xFF4285F4),
                trackColor  = Color.Transparent
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }
    }
}

// ─── Hilfs-Composables ───────────────────────────────────────────────────────

@Composable
fun AvatarCircle(account: GmailAccount, size: Int = 42) {
    val color = Color(account.avatarColor)
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text     = account.displayName.firstOrNull()?.uppercase() ?: account.email.firstOrNull()?.uppercase() ?: "?",
            color    = Color.White,
            fontSize = (size * 0.42f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun GmailLogoText() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf(
            'G' to Color(0xFF4285F4),
            'm' to Color(0xFFEA4335),
            'a' to Color(0xFFFBBC05),
            'i' to Color(0xFF4285F4),
            'l' to Color(0xFF34A853)
        ).forEach { (char, clr) ->
            Text(
                text = char.toString(),
                color = clr,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun EmptyAccountsPlaceholder(onAddAccount: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 80.dp)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF4285F4).copy(0.3f), Color.Transparent))),
            contentAlignment = Alignment.Center
        ) {
            GmailLogoText()
        }

        Spacer(Modifier.height(28.dp))
        Text("Noch kein Konto", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Melde dich mit deinem Google-Konto an, um Gmail zu nutzen.",
            color = Color(0xFF8888AA),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF4285F4))
                .clickable { onAddAccount() }
                .padding(horizontal = 32.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text("Konto hinzufügen", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
fun buildWebView(context: Context): WebView = WebView(context).apply {
    val webview = this
    settings.apply {
        javaScriptEnabled               = true
        domStorageEnabled               = true
        allowFileAccess                 = true
        allowContentAccess              = true
        loadsImagesAutomatically        = true
        blockNetworkLoads               = false
        mixedContentMode                = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        useWideViewPort                 = true
        loadWithOverviewMode            = true
        builtInZoomControls             = false
        displayZoomControls             = false
        setSupportZoom(false)
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(true)
        mediaPlaybackRequiresUserGesture = false
        cacheMode                       = WebSettings.LOAD_DEFAULT
        userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(webview, true)
    }
    setBackgroundColor("#1A1A1A".toColorInt())
    isVerticalScrollBarEnabled = false
}