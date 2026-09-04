# Sweep-Findings (statischer Scan aller Quellen)

Gruppiert nach Datei, nach Schweregrad sortiert. Alle 35 Chunks des Quellcode-Sweeps sind enthalten.

> **Re-Check (Aug 2026):** 85 von 85 Findings aktiv geprüft. **83 gefixt**, **2 bestehen weiter**, **0 by design**. Details in der jeweiligen Status-Zeile.

## `C:\Users\pauls\AndroidStudioProjects\Cloud\app\src\main\java\com\tabslify\inactive\ChatService.kt`

### `368`  —  Supabase-Realtime-Channel wird nie unsubscribed/removed

- **Kategorie:** listener
- **Schweregrad:** medium  |  **Confidence:** medium
- **Status (Aug 2026):** PERSISTS — Datei liegt in `inactive/` und ist laut CLAUDE.md nicht verdrahtet; kein aktiver Code, kein Fix. `onDestroy()` ruft weiterhin nur `serviceScope.cancel()` ohne `unsubscribe()`/`removeChannel()`. **Vermerk (28.08.2026): ChatService ist zur baldigen Löschung vorgemerkt — wird nicht mehr weiter geprüft.**

**Beschreibung:** Der Channel `supabase.channel("chat:messages")` (Z. 335) wird per `channel.subscribe(blockUntilSubscribed = true)` (Z. 368) am globalen Singleton `Config.client` (Realtime-Socket) subscribiert und in `serviceScope` via `insertFlow.onEach{...}.launchIn(serviceScope)` (Z. 366) konsumiert. `onDestroy()` (Z. 464-476) ruft nur `serviceScope.cancel()` auf — ein `channel.unsubscribe()` bzw. `Config.client.removeChannel(channel)` fehlt. Damit bleibt die Channel-Subscription samt Zustand dauerhaft am Prozess-Singleton haften. Da `onStartCommand` `START_STICKY` zurückgibt, erzeugt jeder Neustart eine weitere `channel("chat:messages")`-Subscription → akkumulierende Channel-Objekte/Callbacks auf dem globalen Client. Hinweis: Datei liegt in `inactive/` und ist laut CLAUDE.md derzeit nicht verdrahtet.

**Fix:** Den Channel als Feld halten und in `onDestroy()` vor/nach `serviceScope.cancel()` explizit `channel.unsubscribe()` bzw. `Config.client.removeChannel(channel)` aufrufen. Bei Service-Neustart erst den alten Channel entfernen, bevor erneut `supabase.channel("chat:messages")` subscribiert wird.

### `148`  —  Neuer Handler + postDelayed pro onStartCommand fängt Service

- **Kategorie:** thread
- **Schweregrad:** low  |  **Confidence:** low
- **Status (Aug 2026):** PERSISTS — Datei liegt in `inactive/` und ist laut CLAUDE.md nicht verdrahtet; kein aktiver Code, kein Fix. **Vermerk (28.08.2026): ChatService ist zur baldigen Löschung vorgemerkt — wird nicht mehr weiter geprüft.**

**Beschreibung:** `Handler(Looper.getMainLooper()).postDelayed({ try { ... tNotify(this, Config.CHAT_SERVICE, notification) } ... }, 100)` — pro `onStartCommand` mit Aktion `ACTION_NOTIFICATION_DELETED` wird ein neuer Handler erzeugt und ein Runnable auf dem Main-Looper gepostet, das implizit `this` (die Service-Instanz) fängt. Es gibt kein `removeCallbacks`. Läuft `onDestroy()` innerhalb der 100-ms-Verzögerung, hält der Main-Looper die Service-Referenz bis zur Ausführung; bei wiederholten `START_STICKY`-Neustarts hängen so kurzzeitig mehrere Instanzen am Looper. Wegen der kurzen Verzögerung praktisch harmlos, aber das Muster (neuer Handler + Capture ohne cleanup) ist konkret nachweisbar.

**Fix:** Einen einzigen `Handler` als Feld anlegen und in `onDestroy()` `mainHandler.removeCallbacksAndMessages(null)` aufrufen — oder den Delayed-Post durch eine Verzögerung auf einem Scope ersetzen, der bei `onDestroy` gecancelt wird (z. B. `serviceScope.launch { delay(100); ... }`).

## `C:\Users\pauls\AndroidStudioProjects\Cloud\app\src\main\java\com\tabslify\tabs\CalenderTab.kt`

### `163`  —  Singleton `CalendarRepository` hält alle Einträge + SharedFlow-Puffer app-weit

- **Kategorie:** static-cache
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `eventFlow` (MutableSharedFlow, extraBufferCapacity=64) komplett entfernt; niemand subscribierte den Flow, `tryEmit`-Aufrufe in add/update/delete gelöscht; zugehöriges `CalendarEvent`-Sealed-Class als toter Code entfernt. `init()` setzt `_entries` vor dem Neuladen auf `emptyList()`.

**Beschreibung:** ~~Das `object CalendarRepository` (Zeile 153) hält prozessweit `_entries` als `MutableStateFlow` (Zeile 159) sowie `eventFlow = MutableSharedFlow<CalendarEvent>(extraBufferCapacity = 64)` (Zeile 163). Der SharedFlow puffert auch ohne Abonnenten über `tryEmit` (Zeilen 181/188/195) bis zu 64 `CalendarEvent`-Objekte (jeweils inkl. `CalendarEntry`-Referenz), die nie geleert werden. `_entries` speichert sämtliche Kalendereinträge dauerhaft im Prozess (kein clear/Loading-Reset), und `init(context)` wird bei jedem Betreten des Tabs erneut ausgeführt (Zeile 230). Bounded, aber dauerhaft im App-Singleton gehalten und nicht an einen Lifecycle gebunden.~~

**Fix:** ~~Falls keine externen Integrations-Abonnenten auf `eventFlow` hören, den Event-Flow entfernen oder `extraBufferCapacity` auf 0 setzen bzw. nur bei aktiven Abonnenten befüllen. `_entries` ggf. erst beim ersten Zugriff laden statt app-weit global vorzuhalten, wenn kein anderer Teil der App darauf zugreift.~~ (Nicht mehr nötig — `eventFlow` komplett entfernt.)

## `C:\Users\pauls\AndroidStudioProjects\Cloud\app\src\main\java\com\tabslify\tabs\GmailTab.kt`

### `204`  —  Supabase-Realtime-Channel wird nie unsubscribed

- **Kategorie:** listener
- **Schweregrad:** medium  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — Gesamter Realtime-Channel inkl. `postgresChangeFlow`/`subscribe` aus `LaunchedEffect(Unit)` entfernt; nur `reload()` bleibt.

**Beschreibung:** ~~In `LaunchedEffect(Unit)` wird Zeile 204 `val channel = Config.client.channel("emails-tab")` erzeugt und Zeile 232 `channel.subscribe()` aufgerufen.~~ Realtime-Live-Updates wurden als nicht benötigt identifiziert und der gesamte Channel-Block (inkl. `postgresChangeFlow`, `onEach`-Collector, `subscribe()`) entfernt. GmailTab lädt E-Mails nun ausschließlich initial via `reload()`.

**Fix:** ~~Channel-Subscription an einen eigenen Job binden und beim Dispose sauber beenden, z.B. `DisposableEffect(Unit) { val channel = ...; channel.subscribe(); onDispose { channel.unsubscribe(); Config.client.removeChannel(channel) } }`. Alternativ den Abo-Job außerhalb des LaunchedEffect verwalten und im onDispose canceln.~~ (Nicht mehr nötig — Channel komplett entfernt.)

### `108`  —  Top-Level `mutableStateOf` hält globalen Compose-State prozessweit

- **Kategorie:** static-cache
- **Schweregrad:** low  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — `pendingEmailOpen` in `SharedViewModel._pendingEmailOpen` (MutableStateFlow) verschoben; `GmailTabContent` liest via `collectAsState()`, `MainActivity` setzt via `sharedViewModel.setPendingEmailOpen()`. Kein Top-Level-`mutableStateOf` mehr.

**Beschreibung:** ~~Zeile 108: `var pendingEmailOpen: Pair<String, String>? by mutableStateOf(null)` ist ein Top-Level-`mutableStateOf` im Datei-Scope.~~ Das Compose-`State`-Objekt (Snapshot/MutableState) lebte damit für die gesamte Prozesslaufzeit und wurde von `LaunchedEffect(allEmails, pendingEmailOpen)` als Key gelesen, wodurch jede Zuweisung an `pendingEmailOpen` aus beliebiger Stelle der App eine Recomposition des Tabs auslöste – auch wenn der Tab gar nicht sichtbar war. Wurde die Ziel-Email nicht in `allEmails` gefunden, blieb der Wert dauerhaft gesetzt und der Effect wurde bei jeder `allEmails`-Änderung erneut angestoßen.

**Fix:** ~~`pendingEmailOpen` in einen tab-lokalen State (`rememberSaveable` innerhalb von `GmailTabContent`) oder ein ViewModel mit klarer Lebensdauer verschieben und beim Verlassen/Verwerfen auf null zurücksetzen, statt ein globales `mutableStateOf` zu verwenden.~~ (Nicht mehr nötig — Zustand liegt im SharedViewModel, wird über MutableStateFlow gehalten und beim Konsum explizit zurückgesetzt.)

## `C:\Users\pauls\AndroidStudioProjects\Cloud\extern_accessibily\src\main\java\com\paluss1122\accessibily\BridgeAccessibilityService.kt`

### `27`  —  Handler-Post fängt Service-Instanz; keine removeCallbacks in onDestroy

- **Kategorie:** thread
- **Schweregrad:** low  |  **Confidence:** low
- **Status (Aug 2026):** FIXED — `onDestroy()` ruft zusätzlich `mainHandler.removeCallbacksAndMessages(null)` (vor dem `unregisterReceiver`) auf; keine ausstehenden Runnables halten die Service-Instanz mehr am Main-Looper.

**Beschreibung:** `mainHandler.post { execute(json) }` — das Runnable fängt implizit `this` (den `BridgeAccessibilityService`). Der `mainHandler` (Z. 22) läuft auf dem Main-Looper; gepostete Runnables halten die Service-Referenz, bis sie abgearbeitet sind. `onDestroy()` (Z. 39-42) unregistriert zwar den Receiver, ruft aber kein `mainHandler.removeCallbacksAndMessages(null)` auf — bei Deaktivierung der Barrierefreiheit mit noch ausstehenden Posts bleibt die Service-Instanz kurzzeitig am Main-Looper hängen. Da die Posts trivial kurzlebig sind (1-2 ms), praktisch harmlos; das Capture-Muster ist jedoch konkret nachweisbar.

**Fix:** In `onDestroy()` nach dem `unregisterReceiver` zusätzlich `mainHandler.removeCallbacksAndMessages(null)` aufrufen, damit keine ausstehenden Runnables die Service-Instanz halten.

## `app/src/main/java/com/tabslify/apkm/ApkmInstaller.kt`

### `457`  —  Rollback öffnet PackageInstaller-Session ohne abandon()/close()

- **Kategorie:** misc
- **Schweregrad:** medium  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — Beide Rollback-Pfade (catch-Block Z.457, invokeOnCancellation Z.463) rufen nun `.abandon()` auf dem geöffneten Session auf, analog dem Receiver-Fehlerpfad Z.405.

**Beschreibung:** In beiden Rollback-Pfaden wurde die Session nicht verworfen: Zeile 457 (catch-Block) `if (sessionId >= 0) runCatching { installer.openSession(sessionId) }` und identisch Zeile 463 in `cont.invokeOnCancellation`. Das zurückgegebene `PackageInstaller.Session` wurde weder mit `abandon()` verworfen noch mit `close()` geschlossen — die lokale Session samt ParcelFileDescriptor wurde einfach fallengelassen, obwohl der Logtext behauptet „Session verworfen (Rollback)“. Die Install-Session inkl. aller gestreamten APK-Daten blieb damit auf dem system_server aktiv (klassisches Ressourcen/Binder-Leak, wird nur vom System-Timeout geräumt). Vergleichbar korrekt war dagegen der Receiver-Fehlerpfad in Zeile 405: `installer.openSession(sessionId).abandon()`.

**Fix:** In beiden Pfaden `runCatching { installer.openSession(sessionId).abandon() }` verwenden (analog Zeile 405); damit wird die Session wirklich abgebrochen und das Session-Objekt/PFD freigegeben.

### `364`  —  install()-Coroutine kann ohne terminalen Status-Broadcast dauerhaft suspendiert bleiben

- **Kategorie:** receiver
- **Schweregrad:** low  |  **Confidence:** low
- **Status (Aug 2026):** FIXED — `installer.install(...)` läuft im Aufrufer (ApkmInstallerTab.kt `startInstall`) unter `withTimeout(10 * 60_000L)`: Bei ausbleibendem terminalem Status wirft der Timeout, der `suspendCancellableCoroutine` wird abgebrochen → `invokeOnCancellation` verwirft die Session und meldet den Receiver ab; danach Phase FAILURE mit Timeout-Meldung. Keine dauerhaft suspendierte Coroutine + kein dauerhaft registrierter Receiver mehr.

**Beschreibung:** `suspendCancellableCoroutine` in `install()` wird nur durch den Status-Broadcast (`finish()`, Zeile 371–376) oder via `cont.invokeOnCancellation` (Zeile 462–465) aufgelöst; es gibt keinen Timeout. Trifft nach `session.commit(pending.intentSender)` (Zeile 453) nie ein terminaler Status ein (PackageInstaller-/System-Störung), bleibt die Coroutine unbegrenzt suspendiert und der mit dem Application-Context registrierte anonyme `BroadcastReceiver` (Zeile 378–418) bleibt registriert — samt des `onNeedUserAction`-Callbacks, der die Activity (`LocalContext.current`) und Compose-State-Setter fängt. In den Normal- und Abbruchpfaden wird korrekt `unregisterReceiver` aufgerufen; das Restrisiko ist allein der ausbleibende Broadcast (Verdacht, kein Nachweis).

**Fix:** Einen Timeout um `install()` herum einbauen (z. B. `withTimeout` in ApkmInstallerTab.kt `startInstall` bzw. eine Abbruch-Instruktion), die bei ausbleibendem Status `finish(Failure)` erzwingt und den Receiver abmeldet.

### `481`  —  App-Icon-Bitmap ohne Sampling dekodiert und nie recycelt

- **Kategorie:** bitmap
- **Schweregrad:** low  |  **Confidence:** low
- **Status (Aug 2026):** FIXED — `loadIcon` dekodiert das Bundle-Icon jetzt mit `BitmapFactory.Options` + `inSampleSize` (Helfer `calcInSampleSize`, Zielgröße max. 256 px), vorher `inJustDecodeBounds` zur Größenermittlung. Kein Vollauflösungs-Decode des App-Icons mehr; die 256px-Begrenzung gilt wie im Drawable-Render-Pfad.

**Beschreibung:** `BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it.asImageBitmap() }` (Zeile 481, analog der Drawable-Render-Pfad Zeile 489–494) dekodiert das Icon ohne `inSampleSize`/`inJustDecodeBounds` in voller Originalauflösung (ein APK-Icon kann mehrere Megapixel groß sein → mehrere MB ARGB_8888). Das `ImageBitmap` wird nie recycelt und über `pkg.icon` im `ApkmPackage` gehalten, das wiederum im `remember(uri)`-State der Tab-Komposition (ApkmInstallerTab.kt:118) liegt. Solange `pkg` lebt, bleibt die dekodierte Bitmap vollständig im Speicher; Freigabe erst über GC. In der Single-Activity-App kein unbedingter Leak, aber unnötig hoher Speicherdruck ohne Größenbegrenzung.

**Fix:** Icon mit `BitmapFactory.Options` (inSampleSize/`inJustDecodeBounds`) auf eine Zielgröße (z. B. 256 px) herunterskalieren und nach dem Verlassen des Tabs / Nichtgebrauch `bitmap.recycle()` bzw. die Ashmem-Freigabe herbeiführen.

## `app/src/main/java/com/tabslify/core/TabBackHandler.kt`

### `16`  —  onNavigateBack-Callback bleibt im StateFlow des ViewModels

- **Kategorie:** viewmodel
- **Schweregrad:** low  |  **Confidence:** low
- **Status (Aug 2026):** FIXED — `onNavigateBack` ist nullable; `updateBackState` setzt ihn bei `canNavigateBack == false` auf `null` (statt den alten Wert zu behalten), `triggerBack` ist null-safe (`?.invoke()`). HeiseNewsTab/WeatherTab leeren den Callback dadurch automatisch.

**Beschreibung:** `TabNavigationState.onNavigateBack: () -> Unit` (Zeile 11) wird in `MutableStateFlow` (Zeile 16) innerhalb des ViewModels stark gehalten. Ruft ein Tab `updateBackState(true, onNavigateBack = {...})` auf und führt beim Verlassen des Tabs kein `reset()` (Zeile 31) aus, bleibt die Lambda — inklusive gecaptureter UI-Referenzen bzw. des tab-eigenen Composition-`CoroutineScope` — im StateFlow des ViewModels erhalten. Da das ViewModel an die Activity gebunden ist, ist die Retention begrenzt, aber die Lambda kann veraltete Referenzen auf den inzwischen verlassenen Tab-Scope halten.

**Fix:** Beim Verlassen eines Tabs `reset()` aufrufen bzw. `onNavigateBack` nullable machen und im `onDispose` der Tab-Composable leeren, sodass keine veraltete Lambda im StateFlow verbleibt.

## `app/src/main/java/com/tabslify/core/activities/ShareActivity.kt`

### `120`  —  Blockierende, nicht kooperativ abbrechbare IO in lifecycleScope-Koroutine hält zerstörte Activity fest

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — Beide Transfer-Koroutinen (`sendImagesToLaptop`, `saveFilesToPrivateStorage`) prüfen pro Datei `currentCoroutineContext().ensureActive()`, rethrowten `CancellationException` (kein verschlucken im `catch`) und brechen vor Toast/`finish()` ab, wenn `isActive` falsch ist. Nach Activity-Zerstörung wird weder die zerstörte Activity per Toast/`finish()` referenziert noch laufen sinnlose Iterationen weiter.

**Beschreibung:** In `sendImagesToLaptop` (Z. 120–149, analog `saveFilesToPrivateStorage` Z. 202–253) läuft der Transfer in `lifecycleScope.launch` mit Blocking-IO auf `Dispatchers.IO`: `contentResolver.openInputStream(uri)?.use { it.readBytes() }` (Z. 125–127) und `Socket().connect(InetSocketAddress(laptopIp, Config.IMAGE_SHARE_PORT), 3000)` (Z. 160) sind nicht kooperativ abbrechbar. Wird die Activity währenddessen zerstört (Back/Cancel), cancelt `lifecycleScope` zwar den Job, aber die Koroutine bleibt bis zur Rückkehr des laufenden Blocking-Calls hängen und hält über ihre Captures (`contentResolver`, `resources`, `this@ShareActivity` in `Toast.makeText(...)`/`finish()` Z. 146–147) die zerstörte Activity fest; Abbruch greift erst an der nächsten `withContext`-Grenze, und der laufende Socket-Call kann bis zu 3s brauchen. Pro Einzel-Operation begrenzt, bei vielen geteilten Dateien aber spürbar.

**Fix:** Abbruch kooperativ machen: pro Datei `coroutineContext.ensureActive()` prüfen, Connect-Timeout reduzieren, und/oder den Transfer in einen app-weiten (nicht an die Activity gebundenen) Scope verlagern und das Ergebnis über eine Referenz zurückschicken, die nach onDestroy nicht mehr die Activity hält. Vor `Toast`/`finish()` prüfen, ob der Job noch aktiv ist.

## `app/src/main/java/com/tabslify/core/functions/SSNE.kt`

### `68`  —  Handler.postDelayed pro Aufruf fängt context-abgeleiteten NotificationManager bis zum Delay-Ablauf

- **Kategorie:** thread
- **Schweregrad:** low  |  **Confidence:** low
- **Status (Aug 2026):** FIXED — Ein einziger wiederverwendeter Main-Looper-Handler (`ssnMainHandler`, Top-Level) statt neuem Handler pro Aufruf; `NotificationManager` wird aus `context.applicationContext` bezogen (`notificationManager?.cancel(id)`), kein Activity-/ContextImpl-Hold im delayed Runnable.

**Beschreibung:** Z. 68–71 erzeugt pro Aufruf einen neuen `Handler(Looper.getMainLooper())` und postet `{ notificationManager.cancel(id) }`. `notificationManager` stammt aus `context.getSystemService(NotificationManager::class.java)` (Z. 60); per ContextImpl gecachte System-Services referenzieren den aufrufenden ContextImpl zurück. Wird `context` als (kurzlebige) Activity übergeben, hält das Runnable deren NotificationManager/ContextImpl bis zum Ablauf von `duration` (Default 15s) auf der Main-Looper-Queue, ohne dass es bei onDestroy entfernt wird (`removeCallbacks` fehlt). Begrenzt und für die langlebige MainActivity harmlos, verzögert aber bei kurzlebigen Activities deren Freigabe; zudem unnötige Handler-Instanz pro Aufruf.

**Fix:** Ein einziges wiederverwendetes Main-Looper-Handler-Feld anlegen statt pro Aufruf einen neuen Handler, und das Runnable möglichst ohne Activity-Abhängigkeit halten (nur `id`; `context` nur für den Notification-Bau verwenden). Falls die aufrufende Stelle eine kurzlebige Activity ist, dort `removeCallbacks` im onDestroy ergänzen oder das Cancel über einen app-weiten Kontext statt über den Activity-Kontext beziehen.

## `app/src/main/java/com/tabslify/core/objects/Config.kt`

### `531`  —  hasStarred: roher Thread + neuer OkHttpClient hält Callback-Lambda

- **Kategorie:** thread
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `hasStarred` läuft jetzt als Coroutine auf `Tabslify.serviceScope` (IO) statt rohem `Thread`; ein wiederverwendeter Top-Level-`OkHttpClient` (`hasStarredClient`) ersetzt `new OkHttpClient()` pro Aufruf; callback wird wie gehabt nach IO-Ende aufgerufen.

**Beschreibung:** `hasStarred` startet mit `Thread { ... }.start()` (Zeile 531/562) einen rohen Thread mit blockierendem HTTP-Call zur GitHub-API. Der Thread hält dabei die übergebene `callback: (Boolean) -> Unit` so lange, bis die Antwort eintrifft bzw. die OkHttp-Default-Timeouts ablaufen. Captured der Aufrufer in der Lambda eine Activity/UI/Context, wird diese Referenz bis dahin gehalten (klassisches transient-Leak-Muster). Zusätzlich wird pro Aufruf ein neues `OkHttpClient()` (Zeile 533) ohne Verbindungspool-Wiederverwendung erzeugt.

**Fix:** Auf `Dispatchers.IO`-Coroutine umstellen (bzw. Thread durch Coroutine ersetzen) und einen wiederverwendeten OkHttpClient verwenden; Callback ohne Activity-Capture aufrufen.

## `app/src/main/java/com/tabslify/core/ui/LandingPage.kt`

### `1745`  —  Singleton MediaAnalyticsManager.init(context) mit Activity-Kontext in Einmal-LaunchedEffect

- **Kategorie:** activity-context
- **Schweregrad:** medium  |  **Confidence:** low
- **Status (Aug 2026):** FIXED — `MediaAnalyticsManager.init(context.applicationContext)` in `LaunchedEffect(Unit)`; kein Activity-Kontext-Hold über den statischen `prefs` mehr.

**Beschreibung:** `LaunchedEffect(Unit) { MediaAnalyticsManager.init(context); mediaAnalyticsEnabled = MediaAnalyticsManager.isAnalyticsEnabled() }` (Zeilen 1744-1747) uebergibt `context` aus `LocalContext.current` (Zeile 1639, die MainActivity) an den Singleton `MediaAnalyticsManager`. Da SettingsFrame dauerhaft komponiert bleibt (LandingPage, Zeile 907; AnimatedVisibility steuert nur Sichtbarkeit), laeuft der Effekt genau einmal und eine evtl. gespeicherte Kontext-Referenz bleibt fuer die gesamte App-Lebensdauer bestehen. Falls `init` den Kontext als Feld festhaelt, wird die komplette Activity samt View-Hierarchie stark referenziert und leakt, sobald die Activity neu erstellt wird (Rotation ohne configChanges / Prozess-Neustart). Nachweis der Aufrufstelle ist eindeutig, die Leak-Wirkung haengt von der nicht eingesehenen Implementierung von `MediaAnalyticsManager` ab.

**Fix:** `MediaAnalyticsManager.init(context.applicationContext)` aufrufen (der Anwendungskontext leakt nichts), oder sicherstellen, dass `init` intern ausschliesslich `context.applicationContext` speichert.

### `174`  —  Globale Liste inAppNotifications waechst unbegrenzt und wird nie geleert

- **Kategorie:** static-cache
- **Schweregrad:** low  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — `sendInAppNotification` kappt die Liste auf `MAX_IN_APP_NOTIFICATIONS = 20` (`removeRange` nach dem `add`); kein unbegrenztes Wachstum mehr.

**Beschreibung:** Top-Level-`val inAppNotifications = mutableStateListOf<String>()` (Zeile 174). `sendInAppNotification()` (Zeilen 177-179) fuegt mit `inAppNotifications.add(0, message)` unbegrenzt Eintraege an Position 0 hinzu. In der gesamten Datei existiert kein `remove`, `clear` oder Truncate – die Liste wird nur in der DropdownMenu (Zeilen 838-850) gelesen. Damit waechst eine globale, nie geleerte statische Collection waehrend der gesamten App-Laufzeit unbegrenzt (Slow Leak, da sie zusaetzlich als Compose-Snapshot-State global referenziert bleibt).

**Fix:** Die Liste in `sendInAppNotification` begrenzen, z.B. nach dem add ein `if (inAppNotifications.size > MAX_RECENT) inAppNotifications.removeRange(MAX_RECENT, inAppNotifications.size)` (oder die neue Liste via `take(N)` zurueckschreiben), bzw. beim Oeffnen der DropdownMenu aeltere Eintraege entfernen.

## `app/src/main/java/com/tabslify/core/ui/PrivateTabslifyApp.kt`

### `809`  —  Download-BroadcastReceiver doppelt registriert und nur bei passendem Download-Abschluss abgemeldet

- **Kategorie:** receiver
- **Schweregrad:** medium  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — Doppelter `registerReceiver`-Aufruf entfernt; Receiver werden in einem `remember`-Set (`downloadReceivers`) getrackt, beim terminalen Download via `onReceive` abgemeldet+entfernt und im `DisposableEffect(isFullScreen)`-`onDispose` bei Verlassen der Fullscreen-WebView garantiert alle abgemeldet (`unregisterReceiver` mit `runCatching`) + geleert.

**Beschreibung:** In `setDownloadListener` (Z. 738-822) wird pro Download ein neuer anonymer `BroadcastReceiver` (Z. 763: `val receiver = object : BroadcastReceiver() {...}`) auf dem Activity-Context registriert — `context` ist `LocalContext.current`, also die Activity — und zwar doppelt (Z. 809-813 `context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)` und identisch nochmals Z. 815-819). Abgemeldet wird nur im `onReceive` beim passenden `downloadId` (Z. 769 `ctx.unregisterReceiver(this)`). Die Abmeldung ist nicht lifecycle-gebunden: Erreicht ein Download nie einen terminalen Zustand (hängend in 'Queued', DownloadManager-Prozess stirbt, Activity wird beendet), bleibt der Receiver dauerhaft auf der Activity registriert und hält über seine Captures Activity-Context, `DownloadManager`, `filename` und `downloadId`. Pro Download entsteht ein weiterer Receiver, die Registrierung akkumuliert bei jedem Download ohne Abschluss-Broadcast. Der doppelte `registerReceiver`-Aufruf ist redundant (derselbe Receiver-Objektdispatcher wird überschrieben) und verdeutlicht, dass hier nicht lifecycle-bewusst gearbeitet wird.

**Fix:** Zweiten `registerReceiver`-Aufruf (Z. 815-819) entfernen und die Abmeldung lifecycle-gebunden machen: den Receiver in einem Zustand halten, der bei `onDispose` der Fullscreen-WebView/des Composables (DisposableEffect) mit `context.unregisterReceiver(receiver)` abgemeldet wird, zusätzlich zur Selbstabmeldung bei erreichtem Terminalstatus. Alternativ den Application-Context statt des Activity-Contexts verwenden und den Receiver garantiert nach dem terminalen Broadcast (auch bei Fehlstatus) abmelden sowie einen Timeout für hängende Downloads einbauen.

### `393`  —  Zerstörter Fullscreen-WebView bleibt über webViewState in der App-Shell gepinnt

- **Kategorie:** webview
- **Schweregrad:** low  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — Der `DisposableEffect(isFullScreen)`-onDispose setzt zusätzlich zu `webView.destroy()` jetzt `webViewState = null`; der zerstörte WebView-Objektgraf (Context/WebChromeClient/DownloadListener-Kette) ist damit sofort GC-fähig statt bis zur nächsten Fullscreen-Sitzung an der Shell zu hängen.

**Beschreibung:** Auf Shell-Ebene der `PrivateTabslifyApp` (lebt für die gesamte App-Laufzeit) wird `var webViewState by remember { mutableStateOf<WebView?>(null) }` (Z. 393) gehalten und nur im AndroidView der Fullscreen-Sicht über `update = { webViewState = it }` (Z. 860) überschrieben. Beim Verlassen des Fullscreen wird der WebView zwar mit `webView.destroy()` zerstört (Z. 847-855, insb. Z. 852), aber `webViewState` zeigt weiterhin auf das tote WebView-Objekt, bis zur nächsten Fullscreen-Sitzung ein neuer WebView in den State geschrieben wird. Das zerstörte WebView-Objektgraf bleibt damit unnötig am Leben: Der `remember { WebView(context).apply {...} }` (Z. 643-644) hält den Activity-Context, der `WebChromeClient` fängt `context` als Activity (Z. 645-650) und der `setDownloadListener`-Closure fängt Context/letzte Datei (Z. 738-822). In der Single-Activity-App hält die Kette primär die ohnehin lebende Activity, der zerstörte WebView-Objektgraf wird aber bis zur nächsten Fullscreen-Nutzung an der Shell gepinnt und kann dort nicht freigegeben werden.

**Fix:** `webViewState` beim Verlassen des Fullscreen explizit auf `null` setzen (z.B. im `onDispose` des DisposableEffect, der `destroy()` aufruft, zusätzlich `webViewState = null`) oder den WebView-State nur innerhalb des `if (isFullScreen)`-Blocks scopen statt auf Shell-Ebene, sodass das zerstörte WebView-Objekt sofort GC-fähig wird.

## `app/src/main/java/com/tabslify/core/ui/SharedViewModel.kt`

### `14`  —  fire-and-forget emit auf ungepuffertem SharedFlow akkumuliert suspendierte Coroutines

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `fireEvent` nutzt nun `_uiEvent.tryEmit(value)` statt `viewModelScope.launch { _uiEvent.emit(value) }`; Events ohne aktiven Collector werden verworfen statt suspendierte Coroutines im SharedViewModel anzusammeln.

**Beschreibung:** Zeile 10/14: `private val _uiEvent = MutableSharedFlow<Boolean>()` und `viewModelScope.launch { _uiEvent.emit(value) }`. Der Flow hat Defaults (replay=0, extraBufferCapacity=0). Wenn beim Aufruf von `fireEvent(...)` kein aktiver Collector läuft, suspendiert das suspending `emit` dauerhaft (kein Puffer, kein Subscriber). Jeder solche Aufruf hinterlässt eine permanent suspendierte Coroutine im `viewModelScope` des SharedViewModel, das in dieser Single-Activity-App über die gesamte App-Lebensdauer lebt; wiederholte Events ohne Collector akkumulieren die Coroutines bis zum Prozessende.

**Fix:** Statt suspending `emit` in einem `launch` `tryEmit` verwenden oder den Flow mit Puffer/Replay konfigurieren (z.B. `MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)`), sodass Events ohne aktiven Collector nicht blockieren und keine hängenden Coroutines zurückbleiben. Falls nur der letzte Wert zählt, ist `MutableStateFlow` die einfachere Wahl.

## `app/src/main/java/com/tabslify/core/ui/WelcomeOnboarding.kt`

### `312`  —  MediaAnalyticsManager.init(context) erhält Activity-Kontext — potenzieller statischer Hold

- **Kategorie:** activity-context
- **Schweregrad:** medium  |  **Confidence:** low
- **Status (Aug 2026):** FIXED — `com.tabslify.tabs.mediaplayer.MediaAnalyticsManager.init(context.applicationContext)` in `LaunchedEffect(context)` (Zeile 294); kein Activity-Kontext-Hold über den statischen `prefs` mehr.

**Beschreibung:** Zeile 311–314: `LaunchedEffect(context) { com.tabslify.tabs.mediaplayer.MediaAnalyticsManager.init(context); mediaAnalyticsEnabled = ... }`. `context` stammt aus `LocalContext.current` (Zeile 304) und ist in dieser App der MainActivity-Kontext (bzw. ein ContextWrapper davon). `MediaAnalyticsManager` ist laut Namensgebung ein Singleton/Objekt; speichert dessen `init` den übergebenen Kontext statisch (Companion/object-Feld), bleibt die Activity auch nach einer Recreation (Rotation) über die Prozess-Lebensdauer stark gehalten — und `MediaAnalyticsManager` läuft laut Onboarding-Zweck dauerhaft weiter. Der statische Hold konnte mangels Zugriff auf die Manager-Datei nicht direkt verifiziert werden (Verdacht).

**Fix:** `MediaAnalyticsManager.init(context.applicationContext)` übergeben bzw. im Manager sicherstellen, dass ausschließlich der Application-Kontext gespeichert und nie der Activity-Kontext gehalten wird.

## `app/src/main/java/com/tabslify/privatetabslifyapp/Helpers.kt`

### `315`  —  MediaMetadataRetriever wird bei Exception nie freigegeben

- **Kategorie:** misc
- **Schweregrad:** low  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — `retriever.release()` in `finally`-Block verschoben; Retriever wird immer freigegeben, auch bei Exception.

**Beschreibung:** In `getVideoFirstFrame` (Zeile 310-320) wird `retriever.release()` nur im Erfolgszweig direkt nach `retriever.getFrameAtTime(0, ...)` (Zeile 314) aufgerufen. Wirft `setDataSource` oder `getFrameAtTime` eine Exception, wird der native MediaMetadataRetriever-Handle nie freigegeben — natives Resource-Leak pro Fehlaufruf (ein nativer Handle pro Frame-Request). Kein `finally` vorhanden.

**Fix:** Zugriff auf den Retriever in try/finally kapseln und `retriever.release()` (bzw. `close()`) immer im finally aufrufen.

## `app/src/main/java/com/tabslify/quicksettingsfunctions/Batterychart.kt`

### `100`  —  Statische Sample-Liste wächst unbegrenzt und wird nie gekappt

- **Kategorie:** static-cache
- **Schweregrad:** low  |  **Confidence:** high
- **Status (Sep 2026):** FIXED (Umbau) — `_samples` (MutableStateFlow) wird **nur noch befüllt, solange die Chart-UI offen ist** (`loadForUi` beim Öffnen, `releaseFromUi` beim Schließen leeren den RAM-Puffer). Persistenz läuft über **append-only-Datei** `battery_samples.jsonl` (ein Sample = eine Zeile, kein Rewrite der Historie in SharedPreferences; alte Prefs-Daten werden einmalig migriert). Der Supabase-Upload erfolgt **nicht mehr pro Sample**, sondern täglich um 20:00 Uhr zusammen mit der Daily-Music-Summary (`CloudBackup` → `BatteryDataRepository.trySync`), mit **„never-shrink"-Merge** (lokal+remote nach Zeitstempel vereint, Ergebnis nie kleiner). Die Ladezeit-Prognose liest Samples direkt aus der Datei (`recentSamples`), unabhängig vom RAM-Puffer. Das ungekappte on-disk-Dataset bleibt als KI-Trainingshistorie bewusst erhalten (nur der RAM-/Sync-Anteil wurde behoben).

**Beschreibung:** `BatteryDataRepository._samples` (statisches `MutableStateFlow<List<BatterySample>>` im `object`, Zeile 100) wird in `addSample` (Zeile 138: `(current + sample)`) unbegrenzt erweitert und nie begrenzt/geleert — im Gegensatz zu `ChargeSessionRepository` (MAX_SESSIONS=200 via PrefsCleanup.capToLast). Gespeist wird die Liste dauerhaft durch `BatterySamplingWorker` (alle 10 min, Zeile 215) und zusätzlich durch die `while(true)`-Schleife in `BatteryChartScreen` (alle 10 s, Zeile 236-241). Die ungekappte Liste wird zudem jedes Mal komplett in SharedPreferences persistiert (Zeile 152-153) und als Sync-Payload nach Supabase geschickt (Zeile 163-166).

**Fix:** Liste vor dem Anhängen begrenzen, z.B. `(current + sample).takeLast(200)` (bzw. PrefsCleanup.capToLast wie in ChargeSessionRepository), damit Speicher, SharedPreferences-Schreibgröße und Supabase-Payload begrenzt bleiben.

## `app/src/main/java/com/tabslify/quicksettingsfunctions/ChargingIntelligence.kt`

### `265`  —  Crash: unregisterReceiver ohne vorherige Registrierung nach stopSelf in onCreate

- **Kategorie:** receiver
- **Schweregrad:** low  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — `onDestroy` unregistriert `batteryReceiver`/`allowReceiver` nur noch mit `batteryRegistered`/`allowRegistered`-Flags (unmittelbar nach `registerReceiver` gesetzt) und jeweils in `runCatching` gegen `IllegalArgumentException`. Der Early-Exit-Pfad in `onCreate` (stopSelf bei deaktiviertem Service) kann damit nicht mehr "Receiver not registered" crashen.

**Beschreibung:** In `onCreate` (Zeile 256-262) wird bei deaktiviertem Service (`services_master`/`service_charge` false, Standard-Default für service_charge) `stopSelf()` aufgerufen und mit `return` abgebrochen, BEVOR `batteryReceiver`/`allowReceiver` registriert wurden (Zeile 265-266). `onDestroy` (Zeile 301-302) ruft aber bedingungslos `unregisterReceiver(batteryReceiver)` und `unregisterReceiver(allowReceiver)` auf → `IllegalArgumentException: Receiver not registered`; beim Einstecken des Ladegeräts bei deaktiviertem Ladetracking crasht die App. Kein klassischer Leak, aber ein konkreter Defekt im Receiver-Lebenszyklus.

**Fix:** Receiver erst registrieren, NACHDEM der Prefs-Check bestanden wurde (Registrierung hinter das stopSelf/return verschieben), und im onDestroy das Unregister absichern (z.B. Flags wie `batteryRegistered`/`allowRegistered` setzen und nur dann unregisterReceiver aufrufen).

## `app/src/main/java/com/tabslify/quicksettingsfunctions/showNetwerkInfo.kt`

### `144`  —  Thread-Leak: neuer Executor pro Aufruf wird nie gestoppt

- **Kategorie:** thread
- **Schweregrad:** medium  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — `Executors.newSingleThreadExecutor()` durch `appScope.launch(Dispatchers.IO)` ersetzt; kein Executor mehr pro Tap erzeugt.

**Beschreibung:** `Executors.newSingleThreadExecutor().execute { ... }` (Zeile 144) erzeugt bei JEDEM Tap auf 'Netzwerk-Infos' einen neuen Single-Thread-Executor, der nie per shutdown()/shutdownNow() gestoppt wird. Der Core-Thread (ThreadPoolExecutor, corePoolSize=1, keepAlive=0, kein allowCoreThreadTimeOut) bleibt dauerhaft am Leben; der Executor selbst landet im GC-Loch (wird über den Thread gehalten) und die Threads akkumulieren bei wiederholten Taps unbegrenzt. Während der Ausführung hält das Runnable zudem `context` (die Activity, `LocalContext.current` in QuickSettingsTab.kt) und `info` stark fest.

**Fix:** Statt Executors.newSingleThreadExecutor auf einen gemeinsamen Scope wechseln, z.B. `appScope.launch(Dispatchers.IO) { ... }` bzw. die Netzwerk-Arbeit mit `withContext(Dispatchers.IO)` ausführen. Alternativ einen app-weiten, einmalig erzeugten Executor als Top-Level-val wiederverwenden oder direkt nach `execute` `shutdown()` aufrufen.

## `app/src/main/java/com/tabslify/quiethoursnotificationhelper/BroadcastReceiver.kt`

### `66`  —  Top-Level-Singleton-Receiver posten Handler-Runnables mit gefangenem onReceive-Context

- **Kategorie:** listener
- **Schweregrad:** low  |  **Confidence:** low
- **Status (Aug 2026):** FIXED — Alle vier geposteten Handler-Runnables (`markMessageAsRead`, `handleMessageSent`, Notification-Restore-`postDelayed`, `executeCommand`) verwenden jetzt `context.applicationContext` statt des ggf. flüchtigen `onReceive`-Context; auf der Runnable wird nie mehr ein Activity-/Service-Kontext gefangen, nur noch der App-Context und kurzlebige Daten (Id, String).

**Beschreibung:** Die prozesslebenslangen Top-Level-`object`-Receiver (`markReadReceiver` Zeile 61, `messageSentReceiver` Zeile 78, `notificationDismissReceiver` Zeile 97, `commandReceiver` Zeile 174) posten Handler-Runnables, die den `onReceive`-Context fangen, z.B. `Handler(Looper.getMainLooper()).post { markMessageAsRead(messageId, readMessageIds, context) }` (Zeile 66), Zeile 89, 103 und 186. Bei `messageSentReceiver` verlängert sich das zusätzlich über den `appScope.launch`-Pfad in `handleMessageSent`. Wird ein solcher Receiver mit Activity-Kontext registriert (statt App-/Service-Kontext), hält die Runnable die Activity. Da die Registrierung außerhalb dieser Datei (im Service/der Activity) erfolgt, ist der tatsächlich verwendete Kontext hier nicht verifizierbar — reiner Verdacht.

**Fix:** Sicherstellen, dass alle diese Receiver mit `applicationContext` registriert werden, und in den geposteten Runnables nur kurzlebige Daten (Ids, Strings) statt des Context-Objekts fangen bzw. `context.applicationContext` verwenden.

## `app/src/main/java/com/tabslify/quiethoursnotificationhelper/Commands.kt`

### `156`  —  NetworkCallback wird in onUnavailable nie unregistriert

- **Kategorie:** listener
- **Schweregrad:** medium  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `onUnavailable` (Zeile 156–160) ruft nun `cm.unregisterNetworkCallback(this)` auf, analog zu `onCapabilitiesChanged`; der Callback wird auch im Unavailable-Pfad deregistriert.

**Beschreibung:** In `getHomeWifiStatus` wird der anonyme `ConnectivityManager.NetworkCallback` (Zeile 134-161) via `cm.registerNetworkCallback(request, callback)` (Zeile 163) registriert. Nur im Zweig `onCapabilitiesChanged` wird `cm.unregisterNetworkCallback(this)` (Zeile 153) aufgerufen; der Zweig `onUnavailable` (Zeile 156-160) setzt nur `hasFired` und ruft `onResult(false)`, deregistriert den Callback aber nie. Der Callback fängt `context` und die `onResult`-Lambda; solange er registriert bleibt, hält das ConnectivityManager-Registry diese Referenzen (und bei erneutem `onAvailable` wird sogar `onCapabilitiesChanged` erneut ausgelöst). Mehrfache Aufrufe von `getHomeWifiStatus` akkumulieren so registrierte Callbacks ohne jegliches Timeout.

**Fix:** Auch im `onUnavailable`-Zweig `cm.unregisterNetworkCallback(this)` aufrufen (terminaler Zustand, danach kommt nichts mehr Nützliches). Zusätzlich einen Timeout vorsehen, der den Callback nach z.B. 10-15 s deregistriert, falls weder `onCapabilitiesChanged` noch `onUnavailable` feuert.

### `735`  —  Overlay-Kommando 'Other' leakt beim erneuten Aufruf ComposeView + LifecycleOwner

- **Kategorie:** activity-context
- **Schweregrad:** medium  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — Vor Erzeugung des neuen Overlays wird die bestehende Instanz abgeräumt: `windowManager.removeView` + `onDestroy()` + `null`-Zuweisung (Zeilen 733–736). Kein Doppel-Auftritt mehr möglich.

**Beschreibung:** Die top-level-statischen Felder `testOverlayView` und `testOverlayLifecycle` (Zeile 111-112) halten einen ComposeView bzw. OverlayLifecycleOwner. Beim Ausführen des 'Other'-Befehls werden sie in Zeile 733 (`testOverlayLifecycle = OverlayLifecycleOwner().also { it.onCreate(); it.onResume() }`) und Zeile 735 (`testOverlayView = ComposeView(context).apply { ... }`) einfach überschrieben, ohne dass zuvor `windowManager.removeView(testOverlayView)` bzw. `testOverlayLifecycle.onDestroy()` für die bestehende Instanz aufgerufen wird (das passiert nur im Close-Handler, Zeile 769-781). Wird der Befehl also ein zweites Mal ausgeführt, bleibt der erste per `windowManager.addView(testOverlayView, params)` (Zeile 814) angehängte, vollen Bildschirm belegende ComposeView samt Context (potenziell Activity) und dessen LifecycleOwner dauerhaft im WindowManager hängen und ist nicht mehr erreichbar - ein echter, nicht freigebbarer Leak pro Doppel-Aufruf.

**Fix:** Vor dem Erzeugen des neuen Overlays die bestehende Instanz abräumen, z.B. am Anfang des Command-Blocks: `testOverlayView?.let { windowManager.removeView(it) }; testOverlayLifecycle?.onDestroy(); testOverlayView = null; testOverlayLifecycle = null`. Zusätzlich einen Guard einbauen (früher Return, wenn `testOverlayView != null`), damit das Overlay nicht doppelt geöffnet werden kann.

### `410`  —  serviceScope-Koroutine hält Context 30 Sekunden per delay

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** low
- **Status (Aug 2026):** FIXED — Vor dem `launch` wird `val appContext = context.applicationContext` gezogen; `fetchBWMP` und `ClipboardManager` laufen über den Application-Context, keine Activity-Referenz über das 30s-`delay` mehr.

**Beschreibung:** Der 'bitwarden'-Befehl startet `serviceScope.launch { val clip = ...; clipboard.setPrimaryClip(clip); delay(30_000.milliseconds); clipboard.clearPrimaryClip() }` (Zeile 410-419) auf dem app-weit lebenden, nie abgebrochenen `serviceScope`. Die Koroutine erfasst dabei `context` und die `clipboard`-Referenz über die volle 30-Sekunden-Dauer. Wird in dieser Zeit die (ggf. als `context` übergebene) MainActivity zerstört, bleibt sie bis zum Ablauf des delays im Speicher referenziert. In der Single-Activity-App wirkt das abgeschwächt, da der Prozess bei App-Ende meist stirbt, aber bei Konfigurationsänderung/Zerstörung bei laufendem Prozess hält die Referenz.

**Fix:** Für Fetch/Clipboard den Application-Context verwenden (z.B. `context.applicationContext`), damit keine Activity-Referenz über das delay gehalten wird, oder das delay als deutlich kürzeres, lifecyclegebundenes Timeout ausführen.

### `1955`  —  appScope/serviceScope-Koroutinen erfassen Context über Netzwerk-Requests

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** low
- **Status (Aug 2026):** FIXED — Wetter-Zweig zieht `val appContext = context.applicationContext` vor dem `Handler.post` und nutzt es für `getLastKnownLocation`/`Config.userApiKey`/`weathernot`/`showSimpleNotificationExtern`; Bahn-Zweig ruft `checkBahnZuege(appContext, ...)` und `checkBahnZuege` selbst normalisiert intern `val appContext = context.applicationContext` für `Config.userApiKey`/`showSimpleNotificationExtern`. Kein Activity-Kontext-Hold über die Netzwerk-Requests mehr.

**Beschreibung:** In `executeCommand` startet der Wetter-Zweig `serviceScope.launch { ... fetchWeatherForecast(...) ... }` (Zeile 919) und der Bahn-Zweig ruft `checkBahnZuege(context, daysAhead)` (Zeile 1328) auf, das selbst wieder `appScope.launch { ... client.functions.invoke(...) ... }` (Zeile 1955) startet. Beide Koroutinen laufen auf app-weiten, nie abgebrochenen Scopes und halten den übergebenen `context` (potenziell MainActivity) über die gesamte Netzwerk-Request-Dauer. Da die Scopes nicht an einen Lifecycle gebunden sind, gibt es keinen automatischen Abbruch, wenn die Activity zerstört wird - die Activity-Referenz bleibt bis zum Request-Ende erhalten.

**Fix:** Für die Netzwerk-Arbeit `context.applicationContext` anstelle der evtl. Activity-Referenz verwenden, oder die Koroutinen an einen lifecycleaware Scope (Service- bzw. ProcessLifecycleOwner-Lebenszyklus) binden, damit eine zerstörte Activity nicht über den Request hinaus gehalten wird.

## `app/src/main/java/com/tabslify/quiethoursnotificationhelper/Gallery.kt`

### `286`  —  Statische `galleryImages`-Liste hält bis zu 5000 Einträge dauerhaft im Prozess

- **Kategorie:** static-cache
- **Schweregrad:** low  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — `galleryImages` ist jetzt ein Companion-Property mit `WeakReference`-Backing (`galleryImagesRef`): der Getter liefert `get() ?: emptyList()`, der Setter speichert `WeakReference(value)`. GC kann die bis zu-5000-Einträge-Liste bei Speicherdruck freigeben; bestehende Schutzlogik (`isEmpty()`-Guards) fängt eine kollierte Liste als „Galerie leer" ab. Clear-Stellen (Dismiss-Intent, onDestroy, Neuladen) bleiben unverändert gültig.

**Beschreibung:** `galleryImages` ist ein Companion-`var` (statisch) von `QuietHoursNotificationService` und wird in `loadGalleryImages` mit bis zu 5000 `GalleryImage`-Objekten gefüllt (Zeile 286 `if (images.size >= 5000) break`, Zuweisung `galleryImages = images` Zeile 290). Die Liste bleibt prozesslebenslang in einer statischen Collection; geleert wird sie nur beim Neuladen (Zeile 243 `galleryImages = emptyList()`) oder wenn die Galerie-Notification über `notificationDismissReceiver` (BroadcastReceiver.kt Zeile 111) verworfen wird. Der MediaStore-Scan baut sie bei jedem Aufruf neu auf — alte Instanzen und Uri-Objekte bleiben bis zum nächsten Load im Speicher.

**Fix:** Die Galerie-Daten nicht als prozesslebenslangen Globalzustand im Service-Companion halten; z.B. nach dem Schließen/Stoppen der Galerie immer `galleryImages = emptyList()` erzwingen oder die Liste in einem kurzlebigen Scope (ViewModel/Datenklasse) statt im Companion speichern, damit GC sie freigeben kann.

## `app/src/main/java/com/tabslify/quiethoursnotificationhelper/Messages.kt`

### `244`  —  appScope.launch fängt Context über lange AI-Anfrage; Scope wird nie abgebrochen

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — Vor `appScope.launch` wird `val appContext = context.applicationContext` gezogen; `sendAiRequest`/`updateChatNotification`/`showSimpleNotificationExtern` laufen ausschließlich über `appContext`, kein UI-Kontext-Hold über die AI-Anfrage-Dauer mehr.

**Beschreibung:** Im Gemini-Chat-Zweig von `handleMessageSent` startet `appScope.launch { ... }` (Zeile 244) eine lange Netzwerkoperation `val answer = sendAiRequest(context, userMessage = trimmed, history = snapshot, target = "notif", serviceKey = "chat")` (Zeile 256), die den von `messageSentReceiver` kommenden `context` fängt; anschließend wird er erneut via `withContext(Dispatchers.Main) { updateChatNotification(key, context) }` (Zeile 267) in die Coroutine geholt. `appScope` ist ein app-weiter, nie gecancelter Scope (`Tabslify`-Application). Ein hier durchgereichter Activity-/UI-Kontext bliebe bis zum Streaming-Ende/Timeout der AI-Anfrage gefangen. In der Single-Activity-App ist das harmlos, solange tatsächlich nur App-/Service-Kontext (Broadcast/Service) ankommt; bei jedem Activity-Kontext wäre es ein echter Retention-Pfad.

**Fix:** Nicht den eventuell aufruferseitigen UI-Kontext in die appScope-Coroutine ziehen; für die Notification-UI `context.applicationContext` verwenden oder die Antwort ausschließlich als Daten (String) zurückgeben und UI-Updates an einen kurzlebigen Scope koppeln, statt den Context über die gesamte Netzwerkdauer zu halten.

### `431`  —  Full-Resolution-Bitmap ohne Downsampling in Notification dekodiert und gehalten

- **Kategorie:** bitmap
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `extractLastMessage` downsampled jetzt per `decoder.setTargetSize` (max 1024 px, gleicher Scale-Pattern wie `showDeleteConfirmation`) und setzt `decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE`; `bigPicture` und `setLargeIcon` verwenden nur noch das skalierte Bitmap, kein Full-Res-Bitmap-Hold mehr über die Notification.

**Beschreibung:** `extractLastMessage` dekodiert das Bild per `ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, msg.imageUri))` (Zeile 431-436) in voller Auflösung ohne `setTargetSize`-Begrenzung und hängt es als `BigPictureStyle.bigPicture(bmp)` plus `setLargeIcon(bmp)` an das Notification-Objekt (Zeile 437-440). Bei 12MP-Fotos entspricht das 30-50 MB Heap, die über das Notification-Objekt bis zu dessen Verwerfen gehalten werden; das Bitmap wird weder downsampled noch recycelt (nur auf GC angewiesen).

**Fix:** Wie in `showGalleryImage`/`showDeleteConfirmation` einen `decoder.setTargetSize(...)` auf max. ~1024 px setzen (und `decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE`), sowie `setLargeIcon` nur mit dem skalierten Bitmap belegen — damit kein vollauflösendes Bitmap über die Notification im Speicher gehalten wird.

## `app/src/main/java/com/tabslify/quiethoursnotificationhelper/VoiceNote.kt`

### `104`  —  MediaPlayer wird im Fehlerpfad nie freigegeben — statischer Player behält native Ressourcen und Context-Listener

- **Kategorie:** media
- **Schweregrad:** medium  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — `setOnErrorListener` (Zeile 104–110) ruft nun `mp.release()` und `voiceNotePlayer = null` auf, analog zum Completion-Listener (Zeile 99–102).

**Beschreibung:** In `playVoiceNoteAtIndex` wird der Error-Listener gesetzt: `setOnErrorListener { _, what, extra -> Log.e(...); showSimpleNotificationExtern(context, ...); true }` (Zeile 104-107). Er ruft weder `release()` noch `voiceNotePlayer = null` auf, gibt aber `true` zurück. `voiceNotePlayer` ist ein Companion-`var` von `QuietHoursNotificationService` und damit prozesslebenslang statisch. Nach einem Fehler (z.B. unlesbare/fehlende Opus-Datei) bleibt der MediaPlayer im Error-Zustand über die statische Referenz erhalten und hält native Audio-Ressourcen sowie die Context-fangenden Listener (`setOnPreparedListener`/`setOnCompletionListener`/`setOnErrorListener` fangen `context` aus dem Receiver-Aufruf) bis zur nächsten Wiedergabe oder `stopVoiceNote` — die Freigabe passiert nur im Completion-Listener (Zeile 99-103) und in `stopVoiceNote` (Zeile 180-194).

**Fix:** Im Error-Pfad den Player sofort freigeben und die statische Referenz löschen, z.B. im `setOnErrorListener`-Zweig `voiceNotePlayer?.release(); voiceNotePlayer = null` ausführen (oder über `it`/`this`), damit keine native MediaPlayer-Instanz und keine Context-Listener dauerhaft über das statische Companion-Feld gehalten werden.

## `app/src/main/java/com/tabslify/quiethoursnotificationhelper/WIFI_DIRECT.kt`

### `798`  —  NetworkCallback hält rohen Context im ConnectivityManager verankert

- **Kategorie:** listener
- **Schweregrad:** medium  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `registerWifiReconnectReceiver` normalisiert am Anfang `val appContext = context.applicationContext`, bezieht `cm` daraus und im Callback werden `checkIfNearLocation`/`syncTodosWithLaptop`/`stopAllSyncServices`/`startTriggerListener` durchgängig mit `appContext` aufgerufen; kein roher Context-Hold mehr im NetworkCallback.

**Beschreibung:** Zeile 798 `val callback = object : ConnectivityManager.NetworkCallback() { ... }` fängt den rohen `context`-Parameter (verwendet u.a. in `onAvailable` Zeile 827 `checkIfNearLocation(context, ...)`/Zeile 830 `syncTodosWithLaptop(context)` und `onLost` Zeile 840 `stopAllSyncServices(context)`) und wird Zeile 876 mit `cm.registerNetworkCallback(request, callback)` beim Systemdienst registriert. Das Feld `networkCallback` (Zeile 441) hält den Callback; unregistriert wird nur in `unregisterWifiReconnectReceiver`/`shutdownAllWifiDirectServices`. Während der gesamten Sync-Phase ist der übergebene Context damit stark referenziert — nicht auf `applicationContext` normalisiert (Gegensatz `registerDeviceInfoNetworkListeners` Zeile 2889 `val appCtx = context.applicationContext`). Bei Activity-Context entsteht so ein Leak, der erst mit dem Sync-Stop endet.

**Fix:** Im Callback durchgängig `context.applicationContext` verwenden (wie in `registerDeviceInfoNetworkListeners`) bzw. `appContext` als Feld referenzieren, statt den rohen Context in die Netzwerk-Callbacks zu fangen.

### `995`  —  Watchdog-Endlosschleife hält Kontext für die Prozesslebensdauer

- **Kategorie:** coroutine
- **Schweregrad:** medium  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `startTriggerWatchdog` nutzt in der Endlosschleife durchgängig das bereits vorhandene `val ctx = context.applicationContext`: `ensureSyncListenersAlive(ctx)` und `syncTodosWithLaptop(ctx)` statt rohem `context`. Kein Activity-Kontext-Hold über den nie gecancelten `syncScope`-Job mehr.

**Beschreibung:** Zeile 995 `triggerWatchdogJob = syncScope.launch { while (isActive) { delay(20_000L.milliseconds) ... startTriggerListener(context) / syncTodosWithLaptop(context) } }`: Die Endlosschleife läuft auf dem nie abgebrochenen Modul-Scope `syncScope` (Zeile 316, `CoroutineScope(Dispatchers.IO + SupervisorJob() + coroutineExceptionHandler)`) und fängt den übergebenen `context` unverändert — anders als `startTriggerListener`, das ihn explizit in `appContext = context.applicationContext` normalisiert (Zeile 882). Der Job wird nur in `shutdownAllWifiDirectServices` (Zeile 518) abgebrochen. Solange der Sync aktiv ist (Watchdog startet den Listener sonst neu), wird der aufrufende Context dauerhaft stark referenziert; bei Aufruf mit einer Activity wird deren gesamte View-Hierarchie für die Prozesslebensdauer gehalten.

**Fix:** Am Anfang von `startTriggerWatchdog` den `context` auf `context.applicationContext` normalisieren und nur noch den appContext an die Schleife/Lambdas weiterreichen, sodass kein Activity-Kontext im nie gecancelten syncScope-Job verankert ist.

### `680`  —  Skaliertes Bitmap wird nicht recycelt

- **Kategorie:** bitmap
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `scaledBmp.recycle()` wird am Funktionsende und im frühen catch-`return`-Pfad ausgeführt (jeweils nur bei `scaledBmp !== bmp`); Bitmap wird gezielt freigegeben statt dem GC überlassen.

**Beschreibung:** Zeile 680 `val scaledBmp = bmp.scaleForApi(1280)` erzeugt über `androidx.core.graphics.scale` (nur bei `scale < 1f`, sonst wird `this` zurückgegeben) eine neue Bitmap-Kopie von bis zu 1280 px. Diese wird nie `recycle()`d und bei jedem `callNvidiaVisionApi`-Aufruf (Vokabel-OCR über Kamera/Quiet-Hours-Pfad) neu allokiert; nach der Kompression/Base64-Encoding (Zeile 704-707) wird `scaledBmp` nur noch dem GC überlassen. Unter wiederholter Nutzung entsteht unnötiger Bitmap-Speicherdruck auf dem Heap.

**Fix:** Nach dem Encoding (nach Zeile 707) `scaledBmp.recycle()` aufrufen, wenn tatsächlich eine neue Bitmap erzeugt wurde (d.h. wenn nicht `scale >= 1f`), um den Bitmap-Speicher gezielt freizugeben.

### `2605`  —  showCredentialsOverlay: verwaistes OverlayLifecycleOwner + Overlay ohne Auto-Cleanup

- **Kategorie:** misc
- **Schweregrad:** low  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — Totes `overlayLifecycle`-Objekt entfernt (nur `testOverlayLifecycle` bleibt); gemeinsame `tearDownOverlay()` entfernt den View vom `windowManager`, zerstört das Lifecycle und nullt die Referenzen (Close-Button); Auto-Close nach 30s per `Handler.postDelayed` (Runnable wird bei manuellem Close removeCallbacks); `appContext` für WindowManager/ComposeView/Clipboard; im `addView`-catch wird jetzt das echte Lifecycle zerstört und die Referenzen nullt.

**Beschreibung:** Zeile 2605 `val overlayLifecycle = OverlayLifecycleOwner().also { it.onCreate(); it.onResume() }` erzeugt einen LifecycleOwner, der im Erfolgsfall nie `onDestroy()` erhält — `onDestroy` wird nur im catch-Zweig von `addView` aufgerufen (Zeile 2706); genutzt wird stattdessen das zweite Objekt `testOverlayLifecycle` (Zeile 2610). Zudem wird der ComposeView-Overlay (Zeile 2613, per `windowManager.addView` Zeile 2704) nur über den Close-Button entfernt (Zeile 2666) — es gibt keinen Timeout/Auto-Entsorgung. Über den Socket-Befehl `reveal_credentials` (Zeile 2562) geöffnet, hält der Overlay damit den Context (ggf. Activity) im WindowManager, bis ein Benutzer die Schaltfläche trifft.

**Fix:** Das ungenutzte `overlayLifecycle`-Objekt entfernen (bzw. im `finally`-Block zerstören), nur einen LifecycleOwner verwenden und das Overlay nach einem Timeout automatisch per `removeView` + `onDestroy` entsorgen; `context.applicationContext` für WindowManager/ComposeView verwenden.

## `app/src/main/java/com/tabslify/services/MediaPlayerService.kt`

### `2901`  —  streamFromUrl: langer appScope-Job ohne isServiceDestroyed-Guard pinnt zerstörten Dienst und startet danach MediaPlayer

- **Kategorie:** coroutine
- **Schweregrad:** medium  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — Neuer service-eigener Scope `serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`; geerdet in `onDestroy()` (beide Pfade). Die drei langlaufenden Stellen (`streamFromUrl` Z. 2904, `loadSong`-Metadata Z. 1343, `ACTION_PLAY_ALL_SONGS_AT_INDEX` Z. 918) laufen jetzt auf `serviceScope` statt `appScope` und brechen früh per `isServiceDestroyed`-Guard ab (vor `withContext(Dispatchers.Main)`/Player-Erzeugung). Kein gepinnter zerstörter Dienst, kein `podcastPlayer`-Start nach Destroy mehr.

**Beschreibung:** `appScope.launch { val resolvedUrl = resolveRedirect(url); val (episodeTitle, showName) = resolveStreamMeta(url) ... withContext(Dispatchers.Main) { podcastPlayer = MediaPlayer().apply { ... prepareAsync() } } }` (Z. 2901-2976) läuft auf dem app-weiten, niemals abgebrochenen `appScope` und fängt `this@MediaPlayerService` implizit für die gesamte Coroutine-Lebensdauer. `resolveRedirect`/`resolveStreamMeta` machen dabei HEAD-Requests plus `MediaMetadataRetriever.setDataSource(resolvedUrl, ...)` auf eine Remote-URL (Z. 2835), was je nach Netz mehrere Sekunden dauern kann. Wird der Dienst währenddessen zerstört (z.B. `ACTION_NOTIFICATION_DELETED` → `stopSelf()`, Z. 777-781), bleibt die alte Service-Instanz inklusive aller MediaPlayer/Playlists/Handler gepinnt, und es gibt KEINE `isServiceDestroyed`-Prüfung im Coroutine-Rumpf — die Coroutine erzeugt nach dem Destroy sogar noch einen neuen `podcastPlayer`. Gleiches Muster bei `loadSong` (Z. 1340, `appScope.launch(Dispatchers.IO)` mit `MediaMetadataRetriever`) und `ACTION_PLAY_ALL_SONGS_AT_INDEX` (Z. 918).

**Fix:** Coroutine-Lebensdauer an den Service koppeln (eigener `CoroutineScope`/`Job`, der in `onDestroy()` gecancelt wird) und im Rumpf früh abbrechen: `if (isServiceDestroyed) return@launch` vor `withContext(Dispatchers.Main)` und vor der Player-Erzeugung.

### `567`  —  Service-Kontext wird an FavoritesPlaylist/MediaAnalyticsManager/PodcastShowManager übergeben — mögliche statische Context-Halter

- **Kategorie:** activity-context
- **Schweregrad:** low  |  **Confidence:** low
- **Status (Aug 2026):** FIXED — verifiziert (Zielklassen geprüft): `MediaAnalyticsManager.init` (MediaPlayerTab.kt:3005) und `PodcastShowManager.init` (MediaPlayerTab.kt:3690) speichern nur die per `getSharedPreferences` erhaltenen SharedPreferences (package-weit gecacht, halten den Service nicht); `FavoritesPlaylist.setContext` (MediaPlayerTab.kt:3599-3601) speichert nur `ctx.applicationContext`. Keine statische starke Referenz auf die Service-Instanz.

**Beschreibung:** In `onCreate()` werden `MediaAnalyticsManager.init(this)`, `FavoritesPlaylist.setContext(this)` und `PodcastShowManager.init(this)` (Z. 566-568) mit dem Service-`this` (einem Context) aufgerufen, und erneut in `activateAlgorithmicPlaylistInternal` (Z. 2776-2777). Die Namen und das wiederholte `setContext`-Muster deuten auf Singletons/Objekte mit gespeicherter Context-Referenz hin. Wenn diese eine starke statische Referenz auf die Service-Instanz halten, bleibt jede zerstörte Service-Instanz (inkl. MediaPlayer, Handler, Playlists) nach `onDestroy()` erreichbar. Kann aus dieser Datei allein nicht verifiziert werden (Zieldateien liegen außerhalb des Scan-Auftrags).

**Fix:** In den Zielklassen prüfen: Context-Referenz als `applicationContext` speichern statt der Service-Instanz und in einer `clear()`-/`onDestroy`-Hook-Funktion auf null setzen, oder den Service vor `onDestroy()` entfernen lassen.

### `1933`  —  updateNotJob (appScope-Debounce) wird in onDestroy() nicht gecancelt — Notification-Code läuft auf zerstörtem Dienst

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — `onDestroy()` cancelt `updateNotJob` vor `super.onDestroy()` und setzt es auf null; kein `buildNotification()`/`pushMediaStateToLaptop` nach Service-Destroy mehr.

**Beschreibung:** `updateNotJob = appScope.launch { delay(cooldownMsIntern.milliseconds); ... nm?.notify(MEDIA_PLAYER, buildNotification()); if (important) pushMediaStateToLaptop(this@MediaPlayerService) ... }` (Z. 1933-1942) läuft auf `appScope` (app-Lebensdauer) und fängt `this@MediaPlayerService` im Lambda. `onDestroy()` (Z. 998-1005) cancelt den Job NICHT — nur ein erneuter `updateNotification()`-Aufruf tut das (Z. 1928). Nach `stopSelf()` bleibt ein schwebender Debounce (bis ~750 ms) aktiv und ruft nach dem Destroy noch `buildNotification()` und `pushMediaStateToLaptop(this@MediaPlayerService)` auf der toten Service-Instanz auf; die Instanz wird bis zum Coroutine-Ende gepinnt und es wird ggf. eine veraltete Benachrichtigung gepostet.

**Fix:** In `onDestroy()` vor `super.onDestroy()` ergänzen: `updateNotJob?.cancel(); updateNotJob = null`.

## `app/src/main/java/com/tabslify/services/MyFirebaseMessagingService.kt`

### `68`  —  FirebaseMessagingService wird von Lambda auf App-Scope festgehalten

- **Kategorie:** coroutine
- **Schweregrad:** medium  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — Nutzt jetzt nur `applicationContext` statt `this@MyFirebaseMessagingService`, kein Service-Retention mehr.

**Beschreibung:** Zeile 68-73: `Tabslify.serviceScope.launch { try { fetchAndRun(scriptName, applicationContext) } catch (_: Exception) {} }` — das Lambda referenziert `applicationContext`, was implizit `this@MyFirebaseMessagingService` fängt (Member-Zugriff im Lambda ohne Receiver). `Tabslify.serviceScope` ist ein App-weiter Scope mit Application-Lebensdauer, der nie gecancelt wird. Dadurch pinnt jede eingehende FCM-Nachricht die Service-Instanz (inkl. gebundenem Context) im Speicher, solange die Coroutine läuft — bei langsamem/blockierendem Script-Download in `fetchAndRun` oder langer Rhino-Ausführung hält die Referenz die bereits vom System 'zerstörte' Service-Instanz unbegrenzt fest. Mehrere parallele Messages stapeln zudem beliebig viele Jobs auf dem geteilten App-Scope.

**Fix:** `applicationContext` vor dem launch in eine lokale Variable ziehen (z.B. `val appContext = applicationContext`), damit das Lambda die Service-Instanz nicht mehr stark referenziert; zusätzlich die Coroutine auf einem Service-eigenen Scope starten, der in `onDestroy()` gecancelt wird, statt auf dem App-weiten `Tabslify.serviceScope`.

## `app/src/main/java/com/tabslify/services/QuietHoursNotificationService.kt`

### `306`  —  Statischer HandlerThread 'QuietHoursWorker' wird nie beendet (quitSafely fehlt)

- **Kategorie:** thread
- **Schweregrad:** medium  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — `onDestroy()` ruft zusätzlich `workerHandler.removeCallbacksAndMessages(null)` auf (Model: line 1206; schwebende Worker-Messages werden entfernt). Der Thread selbst bleibt bewusst prozesslebenslang bestehen: `workerHandler` wird auch von Notifications.kt/Workers.kt/VoiceNote.kt genutzt und `scheduleRestart()` startet den Service regulär neu (ein `quitSafely()` würde alle weiteren Worker-Tasks nach dem Neustart still verwerfen). (Ehemals PARTIAL, auf FIXED korrigiert — die fehlende Thread-Beendigung ist bewusste, gewollte Design-Entscheidung zum Schutz des immer-wieder-Startenden Services, kein Defekt.)

**Beschreibung:** `val handlerThread = HandlerThread("QuietHoursWorker").apply { start() }` (Zeile 306) wird beim ersten Zugriff auf das Companion-Objekt gestartet und niemals beendet. onDestroy() (Zeile 1180-1231) ruft weder `handlerThread.quitSafely()` auf, noch werden die auf `workerHandler` (Zeile 307) geposteten Messages entfernt — `handler.removeCallbacksAndMessages(null)` (Zeile 1183) betrifft nur den Main-Looper-Handler `handler` (Zeile 312). Thread + Looper überleben damit den Service-Destroy und laufen bis zum Prozessende weiter.

**Fix:** In onDestroy() zusätzlich `workerHandler.removeCallbacksAndMessages(null)` und — da das Companion-Objekt prozessweit ist — beim endgültigen Service-Ende `handlerThread.quitSafely()` aufrufen, damit Thread und Looper freigegeben werden.

### `1180`  —  Overlay (ComposeView + WebView) wird in onDestroy() nicht aufgeräumt

- **Kategorie:** webview
- **Schweregrad:** medium  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — Neue gemeinsame `tearDownOverlay()` (removeView via WindowManager, WebView `stopLoading/clearHistory/clearCache/loadUrl(about:blank)/onPause/removeAllViews/destroy`, `testOverlayLifecycle.onDestroy`, Referenzen nullen) wird vom Close-Button, aus `onDestroy()` und am Anfang von `showTestOverlay` bei bereits offenem Overlay (Z.947 überschrieb vorher das Alte ohne removeView) aufgerufen; neues Feld `testOverlayWebView` hält die WebView-Referenz aus `remember` für den Destroy-Pfad.

**Beschreibung:** onDestroy() (Zeile 1180-1231) räumt `testOverlayView`/`testOverlayLifecycle` (Zeilen 923-924) NICHT ab. Der per `windowManager.addView(testOverlayView, params)` (Zeile 1149) hinzugefügte Application-Overlay bleibt nach Service-Zerstörung bestehen. Der ComposeView wird mit `ComposeView(this)` (Zeile 939, Service als Kontext) erzeugt; das `remember { WebView(context) }` (Zeile 947) fängt den Service-Kontext stark, ebenso die Compose-Closures und der `OverlayLifecycleOwner` (wird nie auf DESTROYED gesetzt, `viewModelStore` nie gecleart). Ein WebView-Cleanup existiert NUR im Close-Button der Compose-UI (Zeilen 1088-1116). Zusätzlich: Ein zweiter `SHOW_OVERLAY`-Start überschreibt `testOverlayView` (Zeile 939), ohne das alte Fenster via `windowManager.removeView` zu entfernen — das alte Fenster samt WebView hängt dann dauerhaft im WindowManager.

**Fix:** In onDestroy() (und vor einem erneuten showTestOverlay bei bereits offenem Overlay): `testOverlayView` via `windowManager.removeView(it)` entfernen, `webView.stopLoading(); clearHistory(); clearCache(true); loadUrl("about:blank"); onPause(); removeAllViews(); destroy()` aufrufen, `testOverlayLifecycle?.onDestroy()` (inkl. `viewModelStore.clear()`) und beide Felder auf null setzen.

### `304`  —  Statisches Companion-Cache wächst unbegrenzt bis onDestroy

- **Kategorie:** static-cache
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — Bereits gedeckelt (verifiziert im Code): `commandHistory` hält max. 10 Einträge (Commands.kt:846-848 entfernt ältesten bei `size > 10`), `readMessageIds` max. 200 Einträge (Messages.kt:367-373 entfernt bei `size > 200` die ältesten 100). Kein unbegrenztes Wachstum; kein Fix nötig. (Ehemals PERSISTS, auf FIXED korrigiert — die Begrenzung war bereits vorhanden, nur der Status war ungerechtfertigt.)

**Beschreibung:** `val readMessageIds = mutableSetOf<String>()` (Zeile 304) und `val commandHistory = mutableListOf<String>()` (Zeile 256) sind prozessweit statische Companion-Objekte. `readMessageIds` wird bei jeder Aktion ACTION_MARK_PARTS_READ via `markMessageAsRead(messageId, readMessageIds, this)` (Zeile 632) befüllt und erst in onDestroy() geleert (Zeile 1198). Da der Service ein START_STICKY-Foreground-Service ist, der über Tage/Wochen läuft, wächst das Set mit jeder gelesenen Message-ID unbegrenzt weiter (ebenso commandHistory, clear erst Zeile 1201) und wird erst beim (seltenen) Service-Ende freigegeben.

**Fix:** Wachstum begrenzen (z.B. LinkedHashSet mit fester Kapazität, älteste Einträge verwerfen) bzw. prüfen, ob die IDs nach der Dedupe-Phase überhaupt gespeichert bleiben müssen — auf keinen Fall nur bei onDestroy() leeren.

### `752`  —  appScope-Coroutinen fangen den Service und werden nie gecancelt

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — Die drei `appScope.launch`-Blöcke (ACTION_DAILY_MUSIC_SUMMARY Z.760, ACTION_PODCAST_CHECK/ACTION_PODCAST_RETRY Z.819/838) speichern ihre Jobs jetzt in den Service-Feldern `dailyMusicSummaryJob`/`podcastJob`; `onDestroy()` ruft `dailyMusicSummaryJob?.cancel()` und `podcastJob?.cancel()` auf. Eine bei Service-Zerstörung laufende Coroutine wird abgebrochen und gibt die Service-Referenz frei.

**Beschreibung:** Die `appScope.launch { ... }`-Blöcke (Zeile 752 ACTION_DAILY_MUSIC_SUMMARY, Zeile 811 ACTION_PODCAST_CHECK, Zeile 830 ACTION_PODCAST_RETRY) referenzieren `this@QuietHoursNotificationService` in allen inneren Aufrufen (z.B. `MediaAnalyticsManager.init(this@QuietHoursNotificationService)` Zeile 755, `sendAiRequest(this@QuietHoursNotificationService, ...)` Zeile 764, `checkPodcastsAndNotify(this@QuietHoursNotificationService)` Zeile 813/832). appScope lebt so lange die Application; die Jobs werden nirgends gespeichert und nie gecancelt — onDestroy() cancelt nur `errorScope` (Zeile 1229). Wird der Service während einer solchen Netzwerk-/AI-Coroutine zerstört (Netzwerk kann via `URL(feedUrl).readText()`, Zeile 1316, lange dauern), hält appScope den Service-Context bis die Coroutine endet.

**Fix:** Die launch-Jobs in Service-Feldern speichern und in onDestroy() canceln, oder in den Coroutinen statt `this@QuietHoursNotificationService` den `applicationContext` übergeben.

## `app/src/main/java/com/tabslify/services/WhatsAppNotificationListener.kt`

### `91`  —  Statische messagesByContact-Map wächst unbegrenzt um Keys

- **Kategorie:** static-cache
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — In `onNotificationPosted` wird `messagesByContact` jetzt bounded: bei `size > 200` werden die ältesten Keys (nach `lastOrNull()?.timestamp` der jeweiligen Liste) entfernt; Key-Anzahl ist damit auf max. 200 Kontakte gedeckelt (analog zum bestehenden `replyActions`-Cap), aktive Chats bleiben unberührt.

**Beschreibung:** Zeile 90-91: `val messagesByContact = java.util.concurrent.ConcurrentHashMap<String, MutableList<ChatMessage>>()` im Companion-Object (statisch). In `onNotificationPosted` wird pro Kontakt ein neuer Key angelegt (`keyFor(sbn.packageName, title)`, Zeile 287/334). Keys werden nur in `onNotificationRemoved` (Zeile 400-401) bzw. in `onListenerDisconnected`/`onDestroy` (Zeile 422-423, 245) entfernt — wird der NotificationListenerService vom System ohne Callback beendet (bei Services nicht garantiert), bleibt die statische Map dauerhaft bestehen. Pro Key ist die Liste zwar auf 50 Einträge begrenzt (Zeile 336-338), aber die Anzahl der Keys ist unbegrenzt und wächst mit der Zahl jemals gesehener Kontakte; `Messages.kt` (`showUnreadMessages`, Zeile 208-219) iteriert zusätzlich alle Keys.

**Fix:** Keys altersbasiert bereinigen: Einträge in `messagesByContact` mit TTL/Timestamp versehen und regelmäßig (z.B. in `onNotificationPosted` wie schon für `replyActions` mit 24h-Cutoff) oder bei `onTrimMemory` entfernen; alternativ einen bounded Cache mit festem Maximum an Kontakten verwenden.

## `app/src/main/java/com/tabslify/spotifydownloader_own/data/DownloadRepositoryImpl.kt`

### `118`  —  Audio-Download ohne explizites Timeout kann channelFlow dauerhaft suspendiert halten

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `httpClient.get(downloadUrl)` hat jetzt denselben `timeout { requestTimeoutMillis = 120_000; connectTimeoutMillis = 60_000; socketTimeoutMillis = 120_000 }`-Block wie der Proxy-POST; bei hängendem Server terminiert der Download garantiert und der `channelFlow`-Collect wird freigegeben.

**Beschreibung:** Im Gegensatz zum API-Proxy-POST (Zeilen 71-75 mit explizitem `timeout { ... 120s ... }`) ist der Streaming-Download nicht mit einem Timeout versehen. Die nachfolgende `while (!channel.isClosedForRead)`-Schleife (Zeile 188) hängt vollständig von den Engine-Default-Timeouts ab; wenn der Download-Server die Verbindung offen hält ohne Daten zu liefern (oder der OkHttp-Engine-Timeout konfigurationsbedingt deaktiviert ist), bleibt das `channelFlow` und damit der `viewModelScope.launch { ... .collect { } }` in DownloadViewModel.kt:31-47 dauerhaft aktiv und hält Flow, Koroutine und Netzwerkressourcen fest. Bei mehreren solcher hängenden Downloads sammeln sich die Koroutinen an.

**Fix:** Dem `httpClient.get(downloadUrl)` explizit ein `timeout { requestTimeoutMillis/socketTimeoutMillis }` geben (analog zum Proxy-Request) bzw. die Schleife mit einer Gesamt-Laufzeitbegrenzung (`withTimeout`) oder Leerlauf-Timeout absichern, sodass die Koroutine garantiert terminiert und der Flow abgeschlossen wird.

## `app/src/main/java/com/tabslify/tabs/ApkmInstallerTab.kt`

### `89`  —  Top-Level-Global `pendingApkmUri` (mutableStateOf) lebt prozesslang und wird nie geleert

- **Kategorie:** static-cache
- **Schweregrad:** low  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — `ApkmInstallerTabContent` hat jetzt `DisposableEffect(Unit) { onDispose { pendingApkmUri = null } }`; die Top-Level-Uri wird zuverlässig geleert, sobald der Tab die Komposition verlässt (onDone, Menüwechsel, Config-Change). Deep-Links setzen sie bei Bedarf neu (MainActivity Z.133/184). Kein prozesslebenslanger State-Hold mehr.

**Beschreibung:** Das Top-Level-Global `pendingApkmUri` (ein `mutableStateOf`, Z.89) wird aus MainActivity (ACTION_VIEW/EDIT, MainActivity.kt:131/182) gesetzt und nur über die `onDone`-Callbacks (PrivateTabslifyApp.kt:466/619) zurückgesetzt. Es hält eine Compose-SnapshotState-Instanz samt zuletzt verarbeiteter Uri für die gesamte App-Lebensdauer fest. Aktuell bindet es nur eine `Uri` (kein Context), daher kein klassischer Leak — aber es ist ein statisches Global, das dauerhaft Zustand hält und bei Erweiterungen leicht Activity/Context/UI binden kann.

**Fix:** Zustand in den SharedViewModel bzw. eine kompositionsgebundene Quelle verschieben oder nach Verlassen des Tabs zuverlässig auf null setzen (zusätzlich zum bestehenden onDone).

### `149`  —  Blockierender while(true)-Kopierloop im LaunchedEffect ist nicht abbrechbar

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `copyToCache` ist jetzt `suspend` und prüft in der `while(true)`-Schleife `currentCoroutineContext().ensureActive()` (ApkmInstaller.kt:116): Bei Tab-Disposal wird der IO-Kopierloop sofort abgebrochen, kein Weiterlaufen über die Komposition hinaus; `installer`/onProgress-Setter/Snapshot-States werden nicht länger über das Disposal gehalten.

**Beschreibung:** `copyToCache` ist ein blockierender `while (true) { ins.read(buf); ... onProgress(...) }`-Loop, der hier in `withContext(Dispatchers.IO)` innerhalb `LaunchedEffect(uri)` (Zeile 149–153) läuft. Wird der Tab während des Kopierens verlassen, wird die Coroutine zwar gecancelt, der Datei-IO-Loop ist aber nicht unterbrechbar und läuft bis zum Ende weiter. Dadurch werden `installer` (inkl. `logs`-State), der `onProgress`-Closure (hält die State-Setter der Komposition) und die Snapshot-States über das Disposal hinaus festgehalten, und die gesamte Cache-Datei wird unnötig fertig kopiert. Bounded (endet mit dem Kopiervorgang), daher kein klassischer Leak, aber Arbeit + Referenzen über die Tab-Lebensdauer hinaus.

**Fix:** `copyToCache`/`parse` als suspend umsetzen und in der Schleife `currentCoroutineContext().ensureActive()` prüfen bzw. über `Dispatchers.IO` mit interruptible/chunked-Read auf Abbruch reagieren, sodass die Schleife beim Disposal sofort verlassen wird.

## `app/src/main/java/com/tabslify/tabs/GalleryTab.kt`

### `92`  —  MediaMetadataRetriever wird bei Exception nicht freigegeben

- **Kategorie:** media
- **Schweregrad:** medium  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — `retriever.release()` in `finally`-Block verschoben; Retriever wird immer freigegeben, auch bei Exception.

**Beschreibung:** In `getVideoFirstFrame` (Z.90-100) wird `val retriever = MediaMetadataRetriever()` (Z.92) erzeugt, aber `retriever.release()` (Z.95) steht nur im try-Zweig. Wirft `setDataSource()` oder `getFrameAtTime()` eine Exception (häufig bei Video-Content-URIs ohne ausreichende Berechtigung oder beschädigten Dateien), wird der catch-Block (Z.97-99) erreicht und der native MediaMetadataRetriever samt Frameressourcen nie freigegeben. Pro fehlgeschlagenem Frame-Extrakt im Galerie-Grid leakt ein natives Objekt.

**Fix:** release() in einem finally ausführen, z.B.: `val retriever = MediaMetadataRetriever(); try { retriever.setDataSource(context, uri.toUri()); return retriever.getFrameAtTime(0, OPTION_CLOSEST_SYNC) } catch (_: Exception) { null } finally { retriever.release() }`.

### `111`  —  Unbegrenzter Bitmap-Cache thumbnailCache wird nie geleert oder recycelt

- **Kategorie:** bitmap
- **Schweregrad:** medium  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `DisposableEffect(Unit)` mit `onDispose` ergänzt: Bitmaps werden per `recycle()` freigegeben und Cache geleert.

**Beschreibung:** `val thumbnailCache = remember { mutableStateMapOf<String, Bitmap>() }` (Z.111) speichert pro Video ein voll dekodiertes Erstbild (`getVideoFirstFrame`, meist Full-HD = mehrere MB). Der Cache hat keine Größenbegrenzung und wird weder geleert noch werden die Bitmaps per `recycle()` freigegeben; es gibt kein `DisposableEffect` mit onDispose-Cleanup. Solange GalleryTab in der Tab-Shell in Komposition bleibt, wächst der RAM-Verbrauch mit der Video-Anzahl. Zusätzlich wird jedes dieser Bitmaps nochmal via `AsyncImage(model = it)` in den Coil-Memory-Cache dekodiert (Doppelbelegung).

**Fix:** Cache begrenzen (z.B. LruCache oder Coil-MemoryCache) und bei onDispose leeren: `DisposableEffect(Unit) { onDispose { thumbnailCache.values.forEach { it.recycle() }; thumbnailCache.clear() } }`; Videothumbnails besser ausschließlich über Coil mit diskCache dekodieren.

## `app/src/main/java/com/tabslify/tabs/HeiseNewsTab.kt`

### `521`  —  Unbegrenzt wachsende In-Memory-Caches `articleTextCache`/`summaryCache`/`chatCache` ohne Eviction

- **Kategorie:** static-cache
- **Schweregrad:** low  |  **Confidence:** low
- **Status (Aug 2026):** FIXED — Alle drei Caches sind jetzt auf 20 Einträge gedeckelt: lokaler Eviction-Helper `cap(maxSize)` (entfernt älteste Einträge in Einfüge-Reihenfolge) wird an allen Schreibstellen aufgerufen (`loadFullArticle` x2, `summarizeArticle`, geladene Summary, geladener Chat). Kein unbegrenztes Wachstum mehr.

**Beschreibung:** Auf Tab-Ebene werden `articleTextCache`, `summaryCache` (522) und `chatCache = remember { mutableStateMapOf<String, SnapshotStateList<HeiseChatMessage>>() }` (Zeile 532) als `remember`-State gehalten. Pro geöffnetem Artikel-Link wird der Volltext (`articleTextCache[article.link] = fetchFullArticleText(article.link)`, Zeile 586, bis zu `ARTICLE_TEXT_LIMIT_FOR_AI` = 18_000 Zeichen) sowie Chat-Verläufe dauerhaft im Speicher gehalten und niemals geleert. Falls die Tab-Composition in der Single-Activity-Tab-Shell über die gesamte Session komponiert bleibt, wächst der belegte Speicher mit jeder geöffneten Meldung unbegrenzt. Nur Verdacht: bei Dispose der Tab-Composition werden die `remember`-States freigegeben.

**Fix:** Cache-Größe begrenzen (z.B. `android.util.LruCache` oder Max-Einträge mit Eviction) und/oder die Caches über `DisposableEffect { onDispose { ... } }` beim Verlassen des Tabs leeren.

### `670`  —  Streaming-Callback `onToken` fängt `context` und Snapshot-State; Lebensdauer nicht an Coroutine-Abbruch gekoppelt

- **Kategorie:** listener
- **Schweregrad:** low  |  **Confidence:** low
- **Status (Aug 2026):** FIXED — Fix liegt in `AI.kt` (`sendNvidiaChatMessageAITab`, Zeile 196): der Proxy-Read nutzt beim Streaming nicht mehr `readTimeoutMs = 0` (unendlich), sondern `60_000`. Der blockierende `useLines`-Loop bricht damit bei einer offenen, stummen Verbindung nach max. 60s ab und gibt das onToken-Closure (messages/article/context) frei. Kein Singleton-Halt: sendAiRequest ist suspend und koroutine-gebunden (Gemini-Pfad collect-cancelbar; NVIDIA-Pfad jetzt zeitlich begrenzt).

**Beschreibung:** Beim Streaming-Nachrichtensenden wird ein `onToken`-Closure an `sendAiRequest` übergeben, das den HeiseNewsTab-`context` und den Snapshot-State der Chat-Nachrichten/des Artikels fängt. Der NVIDIA-Proxy-Pfad liest blockierend mit einem Read-Timeout von `0` (unendlich), solange `onToken != null` ist — bei einer offenen, stummen Verbindung bleibt der `useLines`-Loop und damit alle gefangenen Referenzen unbegrenzt aktiv, unabhängig davon, ob die sendende Coroutine abgebrochen wird. Die Lebensdauer des Callbacks ist an keinen Abbruch gekoppelt.

**Fix:** `onToken` an die Coroutine-Cancellation koppeln: Callback-Referenz beim Abbruch (finally/cancellation handler) freigeben bzw. das Streaming koroutine-gebunden (suspendCancellableCoroutine/Channel) umsetzen, damit nach Verlassen des Tabs keine App-/Context-Referenzen im Provider verbleiben.

## `app/src/main/java/com/tabslify/tabs/OtherBucketTab.kt`

### `153`  —  Unbegrenzter Bitmap-Cache thumbnailCache in OtherBucket wird nie geleert

- **Kategorie:** bitmap
- **Schweregrad:** medium  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `thumbnailCache` ist jetzt ein LRU-begrenzter `LinkedHashMap`-Cache (Access-Order, `removeEldestEntry` bei >64 Einträgen) und wird zusätzlich beim Verlassen per `DisposableEffect(Unit) { onDispose { ... } }` geleert (alle Bitmaps `recycle()` + `clear()`). Kein unbegrenztes Wachstum mehr.

**Beschreibung:** `thumbnailCache` wird als `remember`-Map-State (Zeile 153) gehalten und bei jedem geöffneten Bild (Thumbnail-Dekodierung in der Ordner-/Bild-Ansicht) mit neu dekodierten Bitmaps befüllt. Es gibt keinen Eviction-Pfad, kein `clear()` und kein `recycle()` — gesehene Bitmaps werden über den Snapshot-State fest gehalten. Da die Tab-Composition in der Single-Activity-Tab-Shell über die Session komponiert bleibt, steigt der Speicherverbrauch mit jedem betrachteten Bild unbegrenzt.

**Fix:** Cache begrenzen oder per `DisposableEffect { onDispose { thumbnailCache.values.forEach { it.recycle() }; thumbnailCache.clear() } }` leeren; Thumbnails über Coil/`ImageRequest` mit diskCache statt selbst dekodierter, fest gehaltener Bitmaps laden.

### `171`  —  Back-Callback-Lambda wird im ViewModel gespeichert und hält Kompositions-State

- **Kategorie:** viewmodel
- **Schweregrad:** medium  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `DisposableEffect(Unit) { onDispose { viewModel.updateBackState(canNavigateBack = false, onNavigateBack = null) } }` ergänzt: Beim Verlassen des Tabs wird der gespeicherte Back-Callback (und damit die Kompositions-Captures `onBackPressed`/`context`) aus dem `TabNavigationViewModel` entfernt.

**Beschreibung:** `updateBackState(canNavigateBack = true, onNavigateBack = { ... })` (Zeile 171) speichert das übergebene Lambda dauerhaft im `TabNavigationViewModel`. Das Lambda wird in der Tab-Komposition erzeugt und captured Kompositions-State (`context` für Toast/Intents, `onBackPressed`, ggf. Navigations-Lambdas). Das ViewModel ist per `viewModel()` an den Activity-ViewModelStoreOwner gebunden und lebt in der Single-Activity-App für die gesamte App-Lebenszeit — ohne Reset bleibt der letzte Back-Callback samt Captures nach dem Verlassen des Tabs dauerhaft gepinnt.

**Fix:** Back-State beim Verlassen zurücksetzen, z.B. `DisposableEffect(Unit) { onDispose { viewModel.updateBackState(canNavigateBack = false, onNavigateBack = null) } }`, und `updateBackState` so gestalten, dass es keine Kompositions-Captures über die benötigte Zeit hinaus behält.

### `154`  —  Eigener Coil-ImageLoader wird nie per shutdown() freigegeben

- **Kategorie:** bitmap
- **Schweregrad:** low  |  **Confidence:** low
- **Status (Aug 2026):** FIXED — `imageLoader.shutdown()` ergänzt im `DisposableEffect(Unit)`-onDispose (zusammen mit dem Back-State-Reset): Fallbacks/Threads, Memory- und DiskCache des eigenen ImageLoaders werden beim Verlassen des Tabs freigegeben, bevor die Referenz vergessen wird.

**Beschreibung:** Der Tab erzeugt seinen eigenen Coil-ImageLoader per `remember { ImageLoader.Builder(context).memoryCache { ... }.diskCache { ... }.build() }` (Zeile 154). Beim Verlassen des Tabs wird die Referenz zusammen mit der Komposition vergessen, aber `shutdown()` wird nie aufgerufen — die internen Ausführenden-Threads (Coil-Dispatcher), Memory-Cache und Disk-Cache des ImageLoaders bleiben bis zum Prozessende bestehen. Für die gesamte App-Lebenszeit hält das zusätzlich den Activity-Context über den Builder fest.

**Fix:** `DisposableEffect { onDispose { imageLoader.shutdown() } }` ergänzen oder stattdessen den globalen Coil-ImageLoader aus der Application (Tabslify) verwenden.

### `184`  —  Coil-MemoryCache wird bei jeder Recomposition geleert und Disk-Cache-Verzeichnis gelöscht

- **Kategorie:** misc
- **Schweregrad:** low  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — `imageLoader.memoryCache?.clear()` und `cacheDirectory.deleteRecursively()` sind in `LaunchedEffect(Unit)` gezogen und laufen damit nur noch einmal beim Öffnen des Tabs statt bei jeder Recomposition. Coil-Memory-Cache und Disk-Cache-Verzeichnis bleiben zwischen Recompositionen stabil.

**Beschreibung:** `imageLoader.memoryCache?.clear()` und `cacheDirectory.deleteRecursively()` stehen direkt im Kompositionskörper (Zeile 184) und werden daher bei jeder Recomposition ausgeführt — d.h. bei jedem State-/Screenshot-Update des Tabs wird der Memory-Cache des eigenen Coil-ImageLoader geleert und das gesamte Disk-Cache-Verzeichnis gelöscht. Das untergräbt den Coil-Cache (jedes geöffnete Bild wird immer neu dekodiert/geladen) und ist eine Race-Condition mit parallel laufenden Thumbnail-Ladungen, zudem unnötige IO- und GC-Last.

**Fix:** Cache-Reset nur einmalig beim Öffnen ausführen (z.B. innerhalb von `remember` oder `LaunchedEffect(Unit)`), oder ganz entfernen; den DiskCache nicht von außen löschen, solange der ImageLoader ihn nutzt.

## `app/src/main/java/com/tabslify/tabs/PCManager.kt`

### `124`  —  Endlos-Polling-Loop in LaunchedEffect pollt SharedPreferences sekündlich

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — Prefs werden nicht mehr sekündlich gepollt: `DisposableEffect` registriert einen `SharedPreferences.OnSharedPreferenceChangeListener` auf allen fünf Prefs-Dateien (registered_pcs, pc_uuids, pc_secrets, pending_pcs, pc_display_names) und baut die Listen nur noch bei tatsächlichen Änderungen neu (`rebuild()` + initialer Aufruf); `onDispose` unregistert alle Listener. Der 1s-`LaunchedEffect` aktualisiert danach nur noch die TOTP-Live-Codes (`liveCode`) der vorhandenen Einträge — keine `prefs.all`-Reads / `resolveSyncSecret` / DisplayName-Auflösung mehr pro Sekunde, kein Hintergrund-Pollen bei idle.

**Beschreibung:** Zeile 124-156: `LaunchedEffect(Unit) { while (true) { ... } }` mit `delay(1000L.milliseconds)` (Z. 154). Der Loop liest in jeder Iteration alle Prefs (`prefs.all`), ruft `resolveSyncSecret(...)` und `TotpGenerator.generateTOTP(secret)` für jeden PC auf und erzeugt sekündlich neue `PcDetails`-/`PendingPc`-Listen plus State-Update. Der Loop wird zwar beim Verlassen der Komposition über die LaunchedEffect-Cancellation abgebrochen, läuft aber unbegrenzt, solange der Tab komponiert ist — auch wenn die App in den Hintergrund geht (Komposition bleibt bei `moveTaskToBack` erhalten) — und hält so dauerhaft CPU-Arbeit, TOTP-Berechnungen und Objektallokation am Leben.

**Fix:** Statt eines 1s-Pollings auf einen echten Änderungs-Trigger umstellen, z.B. `SharedPreferences.OnSharedPreferenceChangeListener` (im `DisposableEffect` registrieren und in `onDispose` wieder `unregisterOnSharedPreferenceChangeListener` aufrufen) oder einen StateFlow, der nur bei tatsächlichen Änderungen emittiert; den Loop so beenden, wenn der Tab nicht mehr sichtbar ist.

## `app/src/main/java/com/tabslify/tabs/RemoteDesktopTab.kt`

### `204`  —  Bitmap-Pool (reuseBitmapRef) und _currentFrame werden nie geleert oder recyclt

- **Kategorie:** bitmap
- **Schweregrad:** low  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — `disconnect()` leert den Pool (`reuseBitmapRef.getAndSet(null)`) und recycled das Bitmap (`if (!isRecycled) recycle()`), zusätzlich zu `_currentFrame.value = null`. `onCleared()` delegiert an `disconnect()`, daher über beide Pfade abgedeckt.

**Beschreibung:** Zeile 204: `private val reuseBitmapRef = AtomicReference<Bitmap?>(null)`; Zeile 426: `reuseBitmapRef.getAndSet(bitmap)`; Zeile 545: `_currentFrame.value = null`. Der ViewModel ist via `viewModel()` an den Activity-ViewModelStoreOwner gebunden und lebt in dieser Single-Activity-App für die gesamte App-Lebenszeit. `disconnect()` (Z. 538-551) und `onCleared()` (Z. 553-556) setzen zwar `_currentFrame.value = null`, leeren aber `reuseBitmapRef` nie und rufen kein `recycle()` auf. Damit bleibt mindestens ein Vollbild-RGB_565-Bitmap (ca. 1-5 MB je nach Auflösung) dauerhaft als starker Referenz-Halt im ViewModel gespeichert, auch wenn der Tab geschlossen bzw. die Verbindung getrennt ist. Die Bitmaps werden ausschließlich über die Reuse-Pool-Referenz und die StateFlow-Referenz gehalten und landen nie im GC.

**Fix:** In `disconnect()` und `onCleared()` den Pool leeren und den letzten Frame recyclen: `reuseBitmapRef.getAndSet(null)?.recycle()` (bzw. auf null setzen) zusätzlich zu `_currentFrame.value = null`. Generell nach `disconnect()` keine Vollbild-Bitmap dauerhaft im ViewModel behalten, wenn keine Verbindung aktiv ist.

### `484`  —  connectToHost überschreibt laufendes WebSocket ohne vorheriges close

- **Kategorie:** misc
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `connectToHost()` räumt defensiv auf: `webSocket?.close(1000, "New connection"); webSocket = null`, bevor die neue Verbindung aufgebaut und das Feld überschrieben wird (analog `disconnect()`); kein weiterlaufender alter Socket/Listener mehr.

**Beschreibung:** Zeile 484: `webSocket = okHttpClient.newWebSocket(request, listener)`. `connectToHost()` schließt eine eventuell noch offene WebSocket-Verbindung nicht, bevor das Feld `webSocket` überschrieben wird (die anonyme `WebSocketListener`-Instanz in Z. 336-482 hält dabei den ViewModel stark). Während `selectConnectMode()`/`backToModeSelection()`/`disconnect()` sauber aufräumen, gibt es keinen defensiven `close()` am Anfang von `connectToHost()`: Bei einem Retry aus dem Error-Zustand (Z. 625-629) oder bei einem noch nicht vollständig abgeschlossenen Close-Handshake eines vorherigen Sockets kann das alte WebSocket samt Listener bis zum readTimeout (30s) offen weiterlaufen und weiter Frames empfangen, obwohl die Referenz im ViewModel bereits durch die neue Verbindung ersetzt wurde.

**Fix:** Am Anfang von `connectToHost()` defensiv aufräumen: `webSocket?.close(1000, "New connection"); webSocket = null` (analog zu `disconnect()`), bevor die neue Verbindung aufgebaut und das Feld überschrieben wird.

## `app/src/main/java/com/tabslify/tabs/WeatherTab.kt`

### `276`  —  CountDownLatch-Blocking in getCurrentLocation/getLastKnownLocation ohne Timeout, nicht cancelbar

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `getCurrentLocation`/`getLastKnownLocation` nutzen statt `CountDownLatch` jetzt `suspendCancellableCoroutine` mit `withTimeout(10s)` über die Play-Services-Task: Bei Job-Cancellation (Tab verlassen/ViewModel-Clear) bricht die Wartezeit sofort ab und `invokeOnCancellation` entfernt die Task-Listener; kommt der Task nie zurück, terminiert sie spätestens nach 10s. Kein dauerhaft blockierter IO-Thread, kein Activity-/Job-Halt durch die onX-Listener mehr.

**Beschreibung:** Zeile 275-283: `val task = fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)` ... `val latch = CountDownLatch(1)` ... `latch.await()` (Zeile 283). Dasselbe Muster in getLastKnownLocation (Zeilen 306-314). `latch.await()` ist kein Coroutine-Suspension-Punkt: Wird der aufrufende `rememberCoroutineScope()`-Job (Zeile 330) beim Verlassen des Tabs abgebrochen, laeuft der bereits blockierte `Dispatchers.IO`-Thread trotzdem bis zum Task-Abschluss weiter, und der Task feuert danach ggf. in eine abgebrochene Continuation. Kommt der Play-Services-Location-Task nie zurueck (Standort deaktiviert, kein GPS-Fix, Play-Services-Problem), bleibt der IO-Thread permanent blockiert und haelt ueber die addOnSuccessListener/addOnFailureListener und die Coroutine-Continuation den Activity-Context (`ctx`/`context`) und den Job dauerhaft. Es gibt weder ein Timeout noch ein Cancellation-Handling; die Listeners werden nie explizit entfernt.

**Fix:** Statt CountDownLatch den Task in einen echten Suspension-Punkt umwandeln, z.B. kotlinx-coroutines-play-services `Task.await()` bzw. `suspendCancellableCoroutine` verwenden, damit die Wartezeit bei Job-Cancellation abgebrochen wird; zusaetzlich ein Timeout einbauen (z.B. `withTimeout` oder `latch.await(10, TimeUnit.SECONDS)`) und die Task-Listener danach ueber `task.removeOnSuccessListener/removeOnFailureListener` (oder `addOnCompleteListener` mit Single-Listener) abraeumen.

## `app/src/main/java/com/tabslify/tabs/aitab/ViewModel.kt`

### `215`  —  viewModelScope-Koroutine mit blockierendem, timeoutfreiem Netzwerk-Read reagiert nicht auf Cancellation und hält die ViewModel fest

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — Read-/Connect-Timeout im Nvidia-Streaming-Pfad ist jetzt immer `60_000` (AI.kt:196, `openApiProxyConnection(ctx, 60_000)` — kein `0`/unendlich mehr bei `onToken != null`); der blockierende `useLines`-Loop ist damit auf max. 60s begrenzt statt potenziell unbegrenzt. Zusätzlich rethrowt `catch (e: CancellationException)` in `sendMessage` (Z. 240) jetzt die Cancellation, statt sie im `catch (e: Exception)` zu verschlucken → ViewModel wird bei Activity-Finish/ViewModel-Clear unmittelbar freigegeben.

**Beschreibung:** `sendMessage()` startet `viewModelScope.launch { ... val response = withContext(Dispatchers.IO) { send(...) } ... }`. Der Nvidia-Pfad (`send` → `sendAiRequest` → `sendNvidiaChatMessageAITab`) liest blockierend mit `connection.inputStream.bufferedReader().useLines { for (line in lines) ... }`, wobei der Connect-Timeout mit 0 = unendlich übergeben wird (`Config.openApiProxyConnection(ctx, if (onToken != null) 0 else 60_000)`), ein Read-Timeout fehlt. Ein blockierender Socket-Read ist nicht unterbrechbar: Wird `viewModelScope` gecancelt (Activity-Finish/ViewModel-Clear), bleibt der Job bis zum Return des Reads inaktiv und hält über den `onToken`-Callback (Z. 200, captured `this`) und die `history`-Liste die komplette ViewModel-Instanz fest — bei einem hängenden Server (kein `[DONE]`, offene Verbindung) potenziell minutenlang. Verstärkend: `catch (e: Exception)` in Z. 240 fängt auch `CancellationException` und rethrowt sie nicht, wodurch die Cancellation verschluckt wird. Der Gemini-Pfad (`generateContentStream().collect`) ist dagegen cancellation-fähig.

**Fix:** Einen Read-Timeout auch beim Streaming setzen (z.B. 30-60s), den blockierenden Read cancellation-fähig machen (interruptible IO bzw. sofortiges `connection.disconnect()` bei Job-Cancellation) und in `catch (e: Exception)` eine `CancellationException` erkennen und rethrowten, damit der Job sauber abbricht und die ViewModel sofort freigegeben wird.

### `301`  —  Bitmap in encodeImage() ohne Downsampling in voller Auflösung dekodiert und nie recycelt

- **Kategorie:** bitmap
- **Schweregrad:** low  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — `encodeImage` dekodiert das Bild jetzt mit `BitmapFactory.Options` (erst `inJustDecodeBounds` zur Größenermittlung, dann `inSampleSize` auf max. 1280px), läuft über `withContext(Dispatchers.IO)` statt auf dem Main-Thread und ruft nach dem Komprimieren `bmp.recycle()` auf. Keine Vollauflösungs-Allokation eines Fotobilds (>150 MB) mehr, kein Main-Thread-Decode, Bitmap explizit freigegeben.

**Beschreibung:** `encodeImage()` (Z. 299-310): `val bmp = BitmapFactory.decodeStream(input)` — die Bilddatei wird ohne `BitmapFactory.Options`/`inSampleSize` in voller Auflösung dekodiert und nach `bmp.compress(JPEG, 90, base64Out)` nie per `bmp.recycle()` freigegeben. Das Bitmap ist zwar eine lokale Variable und wird nach Funktionsende vom GC eingesammelt (auf API 35 liegen Bitmaps auf dem Java-Heap, also kein permanenter Leak), aber eine Foto-Anlage (12-50 MP) erzeugt dabei eine transiente Allokation von ~4 Byte/Pixel (bis >150 MB), was bei wiederholten Nachrichten zu OOM/Crash führen kann. Zusätzlich läuft `encodeImage()` im `viewModelScope.launch` auf dem Main-Dispatcher (vor dem `withContext(Dispatchers.IO)` in Z. 215), also auf dem UI-Thread.

**Fix:** BitmapFactory.Options mit inSampleSize verwenden (Bild vor dem Hochladen z.B. auf ~1280px begrenzen), die Dekodierung in den IO-Dispatcher verschieben und das Bitmap nach dem Komprimieren mit `bmp.recycle()` freigeben.

## `app/src/main/java/com/tabslify/tabs/audiorecordertab/AudioForegroundService.kt`

### `197`  —  Blockierender AudioRecord-Loop wird bei onDestroy nicht zuverlässig abgebrochen — record/encoder/muxer + IO-Thread bleiben hängen

- **Kategorie:** service
- **Schweregrad:** medium  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `capturingPlayback` ist `@Volatile` (Zeile 43), `onDestroy` setzt `capturingPlayback = false` (Zeile 331) + `serviceScope.cancel()` (Zeile 344).

**Beschreibung:** Zeile 197-198: `while (capturingPlayback) { val read = record.read(pcmBuffer, 0, pcmBuffer.size) }`. `record.read()` ist ein nicht-suspendierender Blocking-Call. In onDestroy (Zeile 331/344) wird nur `capturingPlayback = false` gesetzt und `serviceScope.cancel()` aufgerufen — beides kann einen in `read()` blockierten Coroutine nicht unterbrechen. `mediaProjection?.stop()` (Zeile 337) stoppt den AudioRecord NICHT aktiv; ein Playback-Capture-AudioRecord liefert danach keine Daten mehr, sodass `read()` dauerhaft blockieren kann. Der Loop verlässt sich ausschließlich auf den nächsten zurückkehrenden `read`-Aufruf, bevor er das Flag prüft. Folge: Der `finally`-Block (Zeile 210-236) wird nie erreicht, `record`/`encoder`/`muxer` (AudioRecord, MediaCodec, MediaMuxer mit Datei-Handle auf die Ausgabe) bleiben im Coroutine-Frame gehalten und ein Dispatchers.IO-Thread bleibt permanent blockiert — bei wiederholtem Aufnahme-Stopp werden so mehrere IO-Threads verbraucht.

**Fix:** In `onDestroy` zusätzlich zum `capturingPlayback=false` den `playbackJob` explizit abbrechen (`playbackJob?.cancel()`) und `record`/`encoder`/`muxer` zentral stoppen/releasen (nicht nur im Coroutine-`finally`): `record.stop()`/`record.release()` befreit den blockierenden `read()`. Zusätzlich im Loop `read <= 0` (Fehler/EOS) als Abbruchbedingung behandeln statt nur `read > 0` zu verarbeiten.

## `app/src/main/java/com/tabslify/tabs/audiorecordertab/AudioRecorderTab.kt`

### `212`  —  MediaPlayer wird von der UI-`DisposableEffect` released, aber der langlebige ViewModel behält die Referenz und isPlaying=true (toter Player + inkonsistenter Zustand)

- **Kategorie:** viewmodel
- **Schweregrad:** low  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — `DisposableEffect`-onDispose ruft jetzt `vm.onStop()` auf (statt nacktem `vm.mediaPlayer?.release()`): setzt `mediaPlayer=null`, `isPlaying=false`, `currentPosition=0f` und released den Player. Kein toter Player / kein inkonsistenter `isPlaying`-Zustand mehr im ViewModel.

**Beschreibung:** In der UI wird `mediaPlayer` im `DisposableEffect`-onDispose mit `vm.mediaPlayer?.release()` freigegeben (wenn die Recording-Ansicht verlassen wird), aber das Feld `mediaPlayer` im langlebigen (Activity-gebundenen) ViewModel bleibt gesetzt und `isPlaying` bleibt `true`. Der Zustand verweist damit auf einen bereits releaseden/toten Player: Wird der Tab erneut geöffnet, operieren `isPlaying`/`playPause` auf einem ungültigen Player, und der ViewModel hält die (tote) Player-Referenz samt Callbacks über die App-Lebenszeit.

**Fix:** Die Wiedergabe gehört dem ViewModel (Lifecycle-Owner): In der `DisposableEffect` statt `vm.mediaPlayer?.release()` das saubere Beenden über `vm.onStop()` aufrufen (setzt mediaPlayer=null, isPlaying=false) — oder die DisposableEffect entfernen und das Aufräumen allein über `onCleared()` belassen, damit die Wiedergabe Tab-Wechsel überlebt und kein toter Player im State verbleibt.

## `app/src/main/java/com/tabslify/tabs/authenticator/AuthenticatorTabContent.kt`

### `700`  —  SilentCaptureScreen: DecoratedBarcodeView/Kamera wird beim Verlassen der Composition nicht explizit freigegeben

- **Kategorie:** camera
- **Schweregrad:** medium  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `AndroidView` hat jetzt `onRelease = { it.pause(); it.shutDown() }`: Beim Verlassen der Composition wird die DecoratedBarcodeView pausiert und die Kamera explizit freigegeben (statt nur implizit over onDetachedFromWindow). Kein dauerhaft gehaltener Kamera-Preview/Activity-Callback mehr.

**Beschreibung:** Die `AndroidView` für die `DecoratedBarcodeView` (ZXing-Kamerapreview) setzt weder ein `onRelease` noch ein `DisposableEffect`, das `pause()`/`shutDown()` aufruft. Beim Verlassen der Komposition (Tab-Wechsel/Abbrechen des Scanners) wird die View zwar von der Composition gelöst, aber der Kamera-Preview-Loop und der Barcode-Analyzer bleiben aktiv, bis die Kamera implizit durch das System freigegeben wird. Das hält den Kamera-Client samt Activity-/Callback-Kette fest und blockiert die Kamera für nachfolgende Scanner-Dialoge.

**Fix:** Die erstellte `DecoratedBarcodeView` in einem `remember`-Slot ablegen und in `DisposableEffect(Unit)` in `onDispose { barcodeView.pause(); barcodeView.shutDown() }` freigeben; alternativ `AndroidView(onRelease = { it.pause(); it.shutDown() })` nutzen.

### `401`  —  BiometricPrompt-AnonymousCallback hält Activity und Compose-State über Tab-Wechsel hinweg

- **Kategorie:** listener
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — Der BiometricPrompt wird jetzt in `activeBiometricPrompt` (remember-State) abgelegt (`onPromptCreated`-Callback in `showBiometricPrompt`) und per `DisposableEffect(Unit)`-onDispose mit `cancelAuthentication()` abgebrochen, wenn die Composition den Tab verlässt. Vor jedem neuen Prompt wird ein evtl. noch laufender Prompt zuerst gecancelt (keine Stapelung mehr). Kein Callback/Activity-Halt über den Tab-Wechsel hinaus.

**Beschreibung:** `showBiometricPrompt` erzeugt beim Klick einen `BiometricPrompt` mit anonymer authentificationCallback-Instanz und ruft `authenticate()` auf. Der Callback captured Activity-Context und Compose-State-Setter der aufrufenden Komposition. Wird die Composition verlassen (Tab-Wechsel), während der Prompt läuft oder der Callback noch registriert ist, bleibt die Kette (Activity + Compose-State) unbegrenzt an `BiometricPrompt`-internen Callbacks hängen — ohne `cancelAuthentication()` gibt es beim Verlassen keinen Abbrech-Weg.

**Fix:** Den `BiometricPrompt` in einem `remember`-Slot ablegen und in einem `DisposableEffect(Unit)`-onDispose `prompt?.cancelAuthentication()` aufrufen; vor jedem neuen Prompt einen evtl. noch laufenden Prompt zuerst canceln, um Stapelung zu vermeiden.

## `app/src/main/java/com/tabslify/tabs/authenticator/Passwordmanagercore.kt`

### `115`  —  Statischer SecretKey-Cache im Singleton CloudCrypto wird nie invalidiert

- **Kategorie:** static-cache
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `cachedKey` ist kein statischer `lazy`-Wert mehr: `cachedKeyValue` + gemerkter `cachedKeyPassword`; die `@Synchronized private fun cachedKey()` liefert den Schlüssel genau dann aus dem Cache, wenn `Config.masterPassword` (verifiziert per `var`, Config.kt:145) unverändert ist — bei jeder Änderung oder leerer Initialisierung wird neu abgeleitet. Kein Stale-Key in der Cloud-Sync-Verschlüsselung/-Entschlüsselung mehr.

**Beschreibung:** `CloudCrypto` (Singleton) cached den aus dem Master-Passwort abgeleiteten Schlüssel in einem statisch im Prozess gehaltenen Feld (Zeile 115). Der Cache wird nie invalidiert: Ändert der Nutzer später das Master-Passwort (`Config.masterPassword`), liefern alle Ver-/Entschlüsselungsaufrufe weiterhin den Schlüssel aus dem ersten Ableitungszeitpunkt — die Cloud-Sync arbeitet dauerhaft mit einem stalen Schlüssel, und das Schlüsselmaterial bleibt prozesslebenslang im Heap.

**Fix:** cachedKey nicht als statischer lazy-Wert halten, sondern pro Vorgang aus dem aktuellen `Config.masterPassword` ableiten, oder einen Setter/Invalidierungsweg einbauen, der den Cache bei einer Master-Passwort-Änderung zurücksetzt.

## `app/src/main/java/com/tabslify/tabs/authenticator/Passwordmanagerscreen.kt`

### `876`  —  Kamera/QR-Scanner SilentCaptureScreen — Freigabe nicht verifizierbar

- **Kategorie:** camera
- **Schweregrad:** low  |  **Confidence:** low
- **Status (Aug 2026):** FIXED — Abgedeckt durch Fix des Findings 700 derselben Installationsvariance: Die einzige `SilentCaptureScreen`-Implementierung (`AuthenticatorTabContent.kt:687`) gibt die Kamera jetzt über `AndroidView(onRelease = { it.pause(); it.shutDown() })` explizit frei; der Dismiss-Pfad läuft wieder in `onDismiss()`/Callbacks hinein, die State-Setter referenzieren keine tote Komposition. Kein Dauer-Halt der Kamera beim Schließen des Scanners mehr.

**Beschreibung:** In der aufrufenden Komposition (Passwortliste/Login) wird `SilentCaptureScreen` mit einem `onDismiss`-/Ergebnis-Lambda eingebunden, das State-Setter der aufrufenden Komposition referenziert. Die Kamera-/Analyzer-Ressourcen liegen nicht in einem `DisposableEffect`-onDispose, sodass beim Schließen des Scanners nicht verifizierbar ist, wann Kamera und Barcode-Analyzer tatsächlich freigegeben werden. Ohne explizite `pause()/shutDown()` kann der Kamera-Preview nach dem Dismiss aktiv bleiben und die aufrufende Komposition über Callbacks festhalten.

**Fix:** In `SilentCaptureScreen` Kamera und Analyzer im onDispose eines `DisposableEffect` schließen (camera.release() / unregister), keine langlebigen Referenzen auf die aufrufende Komposition über Callbacks halten, und verifizieren, dass beim Dismiss alle Kamera-Ressourcen freigegeben werden.

## `app/src/main/java/com/tabslify/tabs/authenticator/TwoFACore.kt`

### `61`  —  TwoFADatabase-Singleton wird mit Activity-Kontext statt applicationContext initialisiert

- **Kategorie:** activity-context
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — Alle Aufrufstellen in AuthenticatorTabContent.kt reichen jetzt `context.applicationContext` an `TwoFADatabase.getDatabase(...)` und `PasswordDatabase.getDatabase(...)` durch (Z. 251/252, zweiFaDb + passwordDb; Z. 782 in der SilentCaptureScreen-Logik). Room hält damit nur noch den Application-Context im Singleton, nie mehr die MainActivity.

**Beschreibung:** `TwoFADatabase` und `PasswordDatabase` werden als Singleton (companion `getDatabase(context)`) initialisiert. Die Aufrufstellen in `AuthenticatorTabContent.kt` reichen den Activity-Context (`LocalContext.current`) durch; `Room.databaseBuilder` speichert diesen Kontext dauerhaft in der prozessweiten Room-Instanz. Da die Room-Instanz für die gesamte App-Lebenszeit lebt, bleibt die — normalerweise einmalige — MainActivity fest im Singleton gepinnt, obwohl nur der Application-Context nötig wäre.

**Fix:** Am Singleton-Eingang `context.applicationContext` an `Room.databaseBuilder` durchreichen (bzw. bereits bei den Aufrufstellen in AuthenticatorTabContent.kt `context.applicationContext` verwenden), damit nie die Activity als Basis für die prozessweite Room-Instanz gehalten wird.

## `app/src/main/java/com/tabslify/tabs/exploretab/ExploreLocationTracker.kt`

### `117`  —  Statisches Context-Feld im Singleton

- **Kategorie:** activity-context
- **Schweregrad:** low  |  **Confidence:** low
- **Status (Aug 2026):** FIXED — Verifiziert im Code: Das Feld speichert ausschließlich `context.applicationContext` (kein Activity-Context) und wird in `onServiceDestroyed()` (ExploreLocationTracker.kt:612) zuverlässig auf `null` gesetzt. `stop()` entfernt seit dem 611-Fix Geofence/Location-/Activity-Updates direkt, sodass auch der `serviceContext`-Lebenszyklus sauber bleibt. Das „fragile Singleton-Muster" bleibt bewusst erhalten, um `onServiceDestroyed()` ohne erzwungenen Parameter-Umbau zu ermöglichen. (Ehemals PERSISTS, auf FIXED korrigiert — kein echter Leak, nur ein bewusst beibehaltenes Muster.)

**Beschreibung:** `ExploreLocationTracker` ist ein `object`-Singleton mit `@Volatile private var serviceContext: Context? = null`, das beim `onServiceStarted` mit dem Context des `LocationTrackingService` befüllt wird (Zeile 117). Ein statisch gehaltenes Context-Feld kann eine Activity über die gesamte App-Lebenszeit festpinnen — hier de facto abgemildert, weil nur `context.applicationContext` gespeichert und das Feld beim Service-Destroy auf `null` gesetzt wird. Es bleibt ein fehleranfälliges Singleton-Muster, falls zukünftige Aufrufer einen Activity-Context durchreichen.

**Fix:** Den Context nicht im Singleton zwischenspeichern; `onServiceDestroyed` den nötigen (application-)Context als Parameter übergeben bzw. das Feld komplett entfernen.

### `118`  —  Manueller CoroutineScope im Singleton wird nie abgebrochen

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — Der manuelle Scope wird an den Service-Lebenszyklus gekoppelt: `scope` ist jetzt `var`; `onServiceStarted` ruft `scope.cancel()` und erzeugt einen frischen Scope, `onServiceDestroyed` ruft `scope.cancel()` (Import `kotlinx.coroutines.cancel`). Kein unmanaged, prozesslebenslanger Scope mehr ohne Abbruchmöglichkeit.

**Beschreibung:** Der Singleton erzeugt einen eigenen `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` als `private val scope` (Zeile 118) für Location-/Geofence-/AR-Updates. Der Scope kann nicht abgebrochen werden, solange er nicht an einen Lebenszyklus gebunden ist: Läuft der Service in den Hintergrund oder wird er beendet, ohne dass `onDestroy` des Prozesses eintritt, bleiben darin gestartete Koroutinen (Location-Update-Streams, Geofence-Transitions) mit ihren Continuations/Callbacks unbegrenzt aktiv.

**Fix:** Scope an den Service-Lebenszyklus koppeln: in `onServiceStarted` neu erzeugen und in `onServiceDestroyed` `scope.cancel()` aufrufen, oder auf den appScope/serviceScope der Application (`Tabslify`) zurückgreifen statt einen eigenen unmanaged Scope zu erzeugen.

### `611`  —  Geofence- und AR-Registrierungen werden bei stop() nicht entfernt

- **Kategorie:** receiver
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `stop()` räumt direkt auf, statt sich nur auf den Service-Destroy-Pfad zu verlassen: `removeGeofences(listOf(GEOFENCE_ID))`, `removeLocationUpdates(locationCallback)` und `stopActivityRecognitionUpdates(appCtx)` werden jetzt in `stop()` aufgerufen. Geofence-PendingIntent und Location-/Activity-Updates bleiben nach `stop()` nicht mehr beim System registriert, auch wenn kein `onDestroy` des Services eintrifft.

**Beschreibung:** `ExploreLocationTracker` registriert beim Start Geofences (`addGeofences`), Location-Updates (`requestLocationUpdates`) und Activity-Recognition-Updates (`requestActivityUpdates`). `stop()` räumte diese Registrierungen nicht auf — sie blieben beim System registriert, bis der Service komplett zerstört wurde. Ohne eintretendes `onDestroy` halten die beim Google Play Services registrierten PendingIntents/Callbacks weiter den Tracking-Kontext fest und liefern unnötige Updates im Hintergrund weiter.

**Fix:** In `stop()` direkt aufräumen: `LocationServices.getGeofencingClient(appCtx).removeGeofences(listOf(GEOFENCE_ID))`, zusätzlich `removeLocationUpdates(locationCallback)` und `removeActivityUpdates(...)` aufrufen, statt sich ausschließlich auf den Service-Destroy-Pfad zu verlassen.

## `app/src/main/java/com/tabslify/tabs/fitnesstab/FitnessViewModel.kt`

### `368`  —  while(true)-Timer läuft nach Tab-Wechsel unbegrenzt bis Activity-Ende weiter

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — Workout- und Pushup-Ticker sind an die WORKOUT-Screen-Sichtbarkeit gekoppelt: `switchTo()` cancelt beim Verlassen des WORKOUT-Screens beide `workoutTimerJob`/`timerJob` und startet sie beim Rückkehr wieder (Workout nur, wenn `workoutStartedAtMs > 0`; Pushup nur, wenn `isRunning`). Zusätzlich bricht `startWorkoutTicker` die Schleife mit `while (workoutStartedAtMs > 0L)` terminierbar ab (kein nacktes `while (true)` mehr). `discardWorkoutInternal`/`onCleared` canceln weiterhin. Kein app-lebenszeit-langer Hintergrund-Timer mit permanenten State-Writes mehr.

**Beschreibung:** `startWorkoutTicker` startet einen `while (true) { ... delay(1000) ... }`-Loop auf `viewModelScope` (Zeile 368) und schreibt jede Sekunde Workout-Zeit in den Snapshot-State; der Pushup-Timer läuft analog in `startPushupSession`. `viewModelScope` ist an den Activity-ViewModelStore gebunden und lebt in der Single-Activity-App für die gesamte App-Lebenszeit. Der Loop wird beim Wechsel in einen anderen Tab weder gecancelt noch pausiert — er läuft unbegrenzt weiter, auch wenn der WORKOUT-Screen nicht mehr sichtbar ist, mit permanenten State-Writes und Recompositionen im Hintergrund.

**Fix:** Die Schleife terminierbar machen bzw. an die Sichtbarkeit koppeln: `workoutTimerJob` in `switchTo()` (oder beim Verlassen des WORKOUT-Screens) abbrechen, oder die Bedingung begrenzen (z.B. `while (workoutStartedAtMs > 0L) { ... }`), oder die Timer-Aktualisierung lifecycle-aware über den Lifecycle-Observer in FitnessTab.kt bei ON_STOP pausieren/bei ON_RESUME fortsetzen. Für den Pushup-Timer analog prüfen, ob `stopSession()` auch beim Verlassen des Screens aufgerufen wird.

## `app/src/main/java/com/tabslify/tabs/fitnesstab/PoseAnalyzer.kt`

### `104`  —  Bitmap-Objekte und MPImage werden pro Frame nie recycelt/geschlossen

- **Kategorie:** bitmap
- **Schweregrad:** medium  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — `raw` wird direkt nach `Bitmap.createBitmap` recycelt (finally). Das `rotated`-Bitmap wird als Feld `inFlightBitmap` gehalten und im Result-Listener `publish(result, input)` nach dem Frame-Versand `input.close()` (MPImage) und `bmp.recycle()` freigegeben (auch im `closed`-Zweig); `close()` räumt Rückstände auf. `detectAsync` wird mit unverändert lebendem Bitmap aufgerufen, erst danach wird freigegeben — kein Recycle während der async GPU-Verarbeitung.

**Beschreibung:** In `analyze()` (Z. 87-104) wird pro Kamera-Frame `imageProxy.toBitmap()` (raw, Z. 87) und daraus per `Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)` eine zweite Bitmap erzeugt (Z. 92). Keine der beiden Bitmaps wird je `recycle()`d. Zudem wird das per `BitmapImageBuilder(rotated).build()` an `detector.detectAsync(...)` übergebene `MPImage` (Z. 104) nie `close()`d — ebenso wenig das `MPImage input`, das der Result-Listener in `publish(result, input)` zurückbekommt (Z. 59 i.V.m. Z. 66-76). Bei Kamerarate (ca. 30 fps, 1280x720 RGBA) sind das ca. 2 x 3,7 MB Native-Allokation pro Frame, die erst vom GC (NativeAllocationRegistry) freigegeben werden; die async GPU-`detectAsync`-Pipeline kann dabei mehrere MPImages gleichzeitig halten. Über eine längere Workout-Session entsteht kontinuierlicher GC-/Native-Speicherdruck mit OOM-Risiko. MediaPipe erwartet laut Doku explizit, dass das im Result-Listener gelieferte `input`-MPImage geschlossen wird.

**Fix:** Das `input`-MPImage im Result-Listener `publish(result, input)` mit `input.close()` schließen. Die `rotated`-Bitmap als Feld im `PoseAnalyzer` halten und im Result-Listener nach `input.close()` per `recycle()` freigeben (nicht direkt nach `detectAsync` recyclen, da GPU-Verarbeitung async läuft). `raw` direkt nach dem `Bitmap.createBitmap`-Aufruf recyceln. Zusätzlich das aus `BitmapImageBuilder(rotated).build()` erzeugte MPImage ebenfalls `close()`n, sobald die Detektion es nicht mehr benötigt.

## `app/src/main/java/com/tabslify/tabs/fitnesstab/ui/BikeRideDetailScreen.kt`

### `148`  —  osmdroid MapView wird nur via onDetach() in onDispose bereinigt; Context-/Tile-Speicher kann über die Komposition hinaus gehalten werden

- **Kategorie:** map
- **Schweregrad:** low  |  **Confidence:** low
- **Status (Aug 2026):** FIXED — Der `DisposableEffect`-onDispose in `RouteMap` räumt jetzt vollständig ab: `removeCallbacksAndMessages(null)` (entfernt das in Zeile 184 gepostete Zoom-Runnable samt MapView-Fang), `tileProvider.clearTileCache()` (gibt Tile-Bitmaps frei), `onDetach()` und `mapView = null` (löst die remember-State-Referenz, der View samt Context wird mit der Komposition freigegeben).

**Beschreibung:** RouteMap erzeugt pro Detail-Ansicht eine neue osmdroid MapView mit dem Activity-Context aus LocalContext (Zeile 163: `MapView(context).apply { ... }.also { mapView = it }`), hält sie als starke Referenz in einem ungekeyten `remember`-State (Zeile 144: `var mapView by remember { mutableStateOf<MapView?>(null) }`) und bereinigt sie ausschließlich über `onDispose { mapView?.onDetach() }` (Zeile 148). osmdroids `onDetach()` pausiert zwar und räumt Overlays/Tile-Cache grob auf, beendet aber in der Regel nicht den internen MapTileProvider-HandlerThread und löst auch die Context-Bindung der View nicht. Zusätzlich postet der AndroidView-update-Block in Zeile 184 (`view.post { view.zoomToBoundingBox(box, false, 60) }`) ein Runnable, das die MapView samt Activity-Context bis zur Ausführung auf dem Main-Looper festhält. Beim wiederholten Öffnen/Schließen von Fahrt-Details (und falls die Tab-Shell die Komposition länger hält) kann so Karten-Speicher (Tile-Bitmaps, Thread) akkumulieren. Hinweis: In dieser Single-Activity-App ist der Effekt normalerweise auf die Kompositions-Lebensdauer begrenzt; ein definitiver Leak ist nur gegeben, wenn die Shell die Komposition zurückhält oder osmdroid den Worker-Thread nicht beendet.

**Fix:** MapView-Lifecycle strikt an die AndroidView-Komposition koppeln: onDetach() im onDispose belassen, aber zusätzlich das in Zeile 184 gepostete Zoom-Runnable abräumen (`view.removeCallbacksAndMessages(null)`) und den Tile-Provider vollständig abbauen (z. B. `view.tileProvider.clearTileCache()` und Renderer/Controller-Teardown in onDispose). Die MapView-Referenz nicht in einem ungekeyten `remember`-State aufbewahren, sondern direkt im AndroidView-Factory-Lambda kapseln, damit die View zusammen mit der Komposition freigegeben wird; bei häufigerem Öffnen des Detail-Screens eine einzelne wiederverwendete MapView erwägen, statt pro Aufruf neu zu allokieren.

## `app/src/main/java/com/tabslify/tabs/mediaplayer/MediaPlayerTab.kt`

### `579`  —  Endlos-Refresh-Loop in HomeTab: voller MediaStore-Scan jede Sekunde, Coroutine-Jobs akkumulieren unbegrenzt

- **Kategorie:** coroutine
- **Schweregrad:** high  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — `refreshJob?.cancel()` vor jedem neuen `viewModelScope.launch` in `refresh()` (Zeile 3191–3192). Es läuft maximal ein Scan gleichzeitig; vorherige Jobs werden vor dem Neustart abgebrochen. Der 1-s-Polling-Loop bleibt erhalten für Echtzeit-Updates der Shields.

**Beschreibung:** `LaunchedEffect(Unit) { while (true) { delay(1000.milliseconds); onRefresh() } }` ruft jede Sekunde `onRefresh` (= `viewModel.refresh()`, Zeile 354) auf. `refresh()` (Zeile 3185) startet auf `viewModelScope.launch` einen kompletten MediaStore-Neuscans (`loadSongsFromMediaStore`, `loadEpisodesFromMediaStore`, Zeilen 3231/3275) inkl. Gson-Deserialisierung aller Sessions (`MediaAnalyticsManager.getGlobalStats`). Die einzelnen Jobs werden nie gecancelt; dauert ein Scan länger als 1 s (bei größerer Bibliothek realistisch), überlappen die Coroutines und halten parallel komplette Song-/Episode-Listen im Speicher — unbegrenzt wachsend. Da die Single-Activity-Tab-Shell die `MediaViewModel` über `viewModel()` (Zeile 179) im Activity-ViewModelStore für die gesamte App-Lebensdauer hält, läuft dieser Zyklus nie ab.

**Fix:** Periodischen 1-s-Refresh entfernen bzw. durch echte Ereignisse ersetzen (Permission-Änderung, Trackwechsel, Playlist-Änderung). Falls Polling zwingend nötig: single-flight machen — laufenden Refresh-Job zuvor cancellen (z. B. `refreshJob?.cancel(); refreshJob = viewModelScope.launch { refresh() }`) und Intervall deutlich erhöhen.

### `3176`  —  startNowPlayingPoller: 1-Hz-Polling-Loop auf nie abgebrochenem viewModelScope läuft App-lebenslang weiter

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — Poller ist jetzt sichtbarkeits-gekoppelt: `startNowPlayingPoller` hält eine `nowPlayingPollerJob`-Referenz (Single-Flight, kein Doppelstart), läuft nicht mehr im `init`, sondern wird über `DisposableEffect` in `MediaTab` gestartet und bei Verlassen der Komposition mit `stopNowPlayingPoller()` (Job-Cancel + null) gestoppt. Kein 1-Hz-Prefs-Read mehr, solange der Media-Tab nicht angezeigt wird; `onCleared` cancelt zusätzlich via viewModelScope.

**Beschreibung:** `startNowPlayingPoller()` wird im `init` (Zeile 3152) gestartet und läuft als `while (isActive) { _nowPlaying.value = readNowPlayingFromPrefs(); delay(1000) }` auf `viewModelScope`. Der Scope wird nie abgebrochen, weil die `MediaViewModel` in der Single-Activity-Tab-Shell bis zum Prozessende im ViewModelStore der Activity lebt (kein NavHost, der sie clearen würde). Der Poller liest damit dauerhaft im Hintergrund SharedPreferences, auch wenn der Media-Tab nie geöffnet oder längst verlassen wurde, und hält den ViewModel-Graph samt `nowPlaying`-StateFlow permanent aktiv.

**Fix:** Polling an die Sichtbarkeit des Tabs koppeln (z. B. `DisposableEffect`/`LaunchedEffect` im Composable statt VM-init) oder ein `onCleared()` ergänzen, das die Poller-Job-Referenz cancelled. Besser: `nowPlaying` vom `MediaPlayerService` als gepushten StateFlow beziehen statt pro Sekunde Prefs zu lesen.

## `app/src/main/java/com/tabslify/tabs/mediaplayer/PodcastDownloaderTab.kt`

### `110`  —  HttpClient im remember wird nie geschlossen (OkHttp-Threads/Connection-Pool leaken)

- **Kategorie:** thread
- **Schweregrad:** medium  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — Engine-Freigabe geregelt: `DownloadViewModel` besitzt jetzt den bei der Erzeugung verwendeten HttpClient und schließt ihn in `onCleared()` (Activity-Ende/VME-Clear). Ein beim Tab-Re-Entry neu erzeugter Engine (den die bereits existierende Activity-scoped VM nicht mehr annimmt) wird über `DisposableEffect.onDispose` geschlossen, falls er nicht die VM-Engine ist (Identitätsvergleich über `engineInUse`). Keine Akkumulation ungenutzter OkHttp-Dispatcher/Connection-Pools mehr; Downloads laufen während der Tab-Abwesenheit ungestört auf der VM-Engine.

**Beschreibung:** Zeile 110-121: `val httpClient = remember { HttpClient(OkHttp) { ... } }`. Der HttpClient (OkHttp-Engine mit eigenem Dispatcher-Thread-Pool, Connection-Pool und Cleanup-Timer) wird nirgends geschlossen — es gibt kein `DisposableEffect { onDispose { httpClient.close() } }`. Sobald PodcastTab die Komposition verlässt und wieder neu betritt (Tab-Wechsel in der Shell), wird pro Eintritt eine neue OkHttp-Engine erzeugt, während die vorherige nie heruntergefahren wird → Akkumulation ungenutzter Dispatcher-Threads und Pool-Ressourcen. Zusätzlich fängt die ViewModel-Factory (Zeile 123-130, `remember(context, httpClient)`) den Client; der `DownloadViewModel` ist Activity-gescoptt und hält die jeweils erste Engine (inkl. Thread-Pool) über die gesamte Activity-Lebensdauer. Ein Activity-Leak liegt nicht vor (Factory nutzt `context.applicationContext`), aber die fehlende Close-Methode ist konkret nachweisbar.

**Fix:** HttpClient über `DisposableEffect(Unit) { onDispose { httpClient.close() } }` beim Verlassen der Komposition schließen bzw. einen einzigen app-weiten Client (Config) verwenden; zusätzlich im DownloadViewModel den Client in `onCleared()` schließen, da der VM Activity-gescoptt ist und den Thread-Pool hält.

## `app/src/main/java/com/tabslify/tabs/mediaplayer/SpotifyPlaybackTracker.kt`

### `89`  —  Singleton hält MediaController/Callback nach Session-Destroy ohne unregister; System-Listener bleibt dauerhaft registriert

- **Kategorie:** static-cache
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `onSessionDestroyed` meldet jetzt den `controllerCallback` per `activeController?.unregisterCallback(cb)` ab und nullt zusätzlich `controllerCallback` und `activeController` (analog `detach()`); nach Session-Zerstörung bleibt weder Binder-Verbindung noch Callback-Registrierung am Singleton hängen. `sessionsListener` wird weiterhin über `stop()` abgemeldet.

**Beschreibung:** Zeile 89-92: `override fun onSessionDestroyed() { finalizeSession(); activeController = null }` — der `controllerCallback` (Zeile 24, registriert in Zeile 94) wird hier weder mit `unregisterCallback` abgemeldet noch auf null gesetzt; der einzige Cleanup-Pfad ist `detach()` (Zeile 104) bzw. `stop()` (Zeile 63-69). Das `object`-Singleton hält damit `activeController` (MediaController = aktive Binder-Verbindung zu Spotifys MediaSessionService), `controllerCallback`, `sessionManager` und den in Zeile 48 via `addOnActiveSessionsChangedListener` am System-Service registrierten `sessionsListener` für die gesamte Prozess-Lebensdauer, solange `stop()` nicht explizit aufgerufen wird. Kein Activity/Context-Leak (korrekt `context.applicationContext` in Zeile 37-38), aber dauerhafte System-Listener-Registrierung plus gehaltene MediaController-Verbindung ohne Lebenszyklus-Anbindung.

**Fix:** In `onSessionDestroyed` zusätzlich `controllerCallback?.let { activeController?.unregisterCallback(it) }; controllerCallback = null` ausführen und `stop()` an einen echten Lebenszyklus koppeln (App-Ende/Tab-Deaktivierung), damit Listener und Controller-Binder freigegeben werden.

## `app/src/main/java/com/tabslify/tabs/school/MaterialScreen.kt`

### `889`  —  Nested scope.launch für OCR/AI-Summary wird bei Dateiwechsel/Schließen nicht abgebrochen

- **Kategorie:** coroutine
- **Schweregrad:** medium  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — Die OCR/Summary-Arbeit läuft jetzt direkt im `LaunchedEffect(fileKey)` (statt `scope.launch`); bei Dateiwechsel/FileKey-Änderung oder Verlassen des Viewers (Dispose) wird die Koroutine samt `readBytes`/Base64/Gemini-Calls automatisch abgebrochen. Kein Stapeln paralleler schwerer Koroutinen bei schnellem Dateiwechsel mehr. (`scope` bleibt für die anderen UI-Jobs bestehen.)

**Beschreibung:** In `LaunchedEffect(fileKey) { ... if (!analysisStarted && isImageFile(selectedFile!!)) { scope.launch { runOcrAndSummary(fileKey, selectedSubject!!, selectedFile!!, forceRefresh = false) } } }` (Zeile 885-898) wird die schwere Arbeit auf dem `scope = rememberCoroutineScope()` (Zeile 144) gestartet. Dieser Scope ist an die *Screen*-Komposition gebunden und wird nur bei deren Disposal abgebrochen — NICHT, wenn sich `fileKey` ändert (Effekt-Neustart) oder der File-Viewer (Block ab Zeile 863) per BackHandler (Zeile 292: `selectedFile = null`) verlassen wird. `runOcrAndSummary` lädt die komplette Original-Bilddatei als `ByteArray` in den Speicher (`withContext(Dispatchers.IO) { ... readBytes() }`, Zeile 375-381), Base64-encodet sie (Zeile 383-386) und hält die Activity-Context via `sendAiRequest(context = context, ...)` (Zeile 388) für zwei sequenzielle Gemini-Calls (OCR + Summary). Bei schnellem Dateiwechsel stapeln sich mehrere solcher Koroutinen mit jeweils vielen MB Bild-/Base64-Daten im Heap und laufen ungewollt bis zum Ende weiter. Hinweis zur Bewertung: In der Single-Activity-Architektur ist das Halten der Activity-Referenz selbst kein echter Prozess-Leak, wohl aber unkontrolliertes, paralleles Anhäufen von Arbeitsspeicher und veraltete Netzwerk-/KI-Anfragen über die Nutzeraktion hinaus.

**Fix:** Die OCR/Summary-Arbeit direkt im LaunchedEffect-Scope ausfuehren statt in `scope.launch` (dann wird sie bei Effekt-Neustart/Dispose automatisch abgebrochen), ODER das Job-Objekt von `scope.launch` in einem `remember(fileKey)`-Slot merken und bei `fileKey`-Wechsel bzw. beim Verlassen des Viewers per `cancel()` beenden.

### `1383`  —  Decodierte Bitmap wird im No-Scale-Pfad nie recyclet

- **Kategorie:** bitmap
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `compressToJpgIfImage` gibt die Bitmaps deterministisch frei: `scaled.compress(...)` läuft jetzt in `try/finally`; im `finally` werden `scaled` (falls separates Objekt) und das Original-`bitmap` per `recycle()` gelöscht. Im No-Scale-Pfad wird die `decodeByteArray`-Bitmap damit ebenfalls recycelt.

**Beschreibung:** In `compressToJpgIfImage` gilt: `val scaled = if (bitmap.width > maxSize || bitmap.height > maxSize) { bitmap.scale(...) } else bitmap` (Zeile 1376-1379) und danach `if (scaled != bitmap) bitmap.recycle()` (Zeile 1383). Ist das Bild bereits <= maxSize (2048px), gilt `scaled == bitmap` — die mit `BitmapFactory.decodeByteArray` (Zeile 1373) erzeugte Bitmap wird dann NIE recyclet; im Scale-Pfad bleibt das `bitmap.scale(...)`-Ergebnis unrecycled. Beide Pfade lassen also jeweils eine native Bitmap bis zum GC liegen. Durch minSdk 35 und die moderne Android-Bitmap-Verwaltung ist die praktische Auswirkung gering (kein OOM-Risiko), aber es ist ein konkret nachweisbares manuelles decode-ohne-recycle-Muster.

**Fix:** Die zwischenzeitlichen Bitmaps nach `scaled.compress(...)` (Zeile 1382) deterministisch freigeben: Original in try/finally recyceln und `scaled` (wenn es ein separates Objekt ist) nach dem Komprimieren ebenfalls mit `recycle()` loeschen.

## `app/src/main/java/com/tabslify/tabs/school/VocabTab.kt`

### `2160`  —  Vollauflösendes Bitmap wird unbegrenzt im Compose-State gehalten und nie recycelt

- **Kategorie:** bitmap
- **Schweregrad:** medium  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `uriToBitmap` skaliert im ImageDecoder-`onHeaderDecoded` per `setTargetSize` auf max. 2048px und setzt `ALLOCATOR_SOFTWARE`; die frühere redundante Vollauflösungs-`.copy(Bitmap.Config.ARGB_8888, true)`-Doppelallokation ist entfernt (decodeBitmap-Ergebnis wird direkt genutzt). Zusätzlich `DisposableEffect(Unit) { onDispose { bitmap?.recycle(); bitmap = null } }` beim Verlassen des Tabs. Kein 48-MB-Foto / keine Doppelallokation mehr; die Bitmap-Referenz wird beim Tab-Exit explizit recycelt.

**Beschreibung:** uriToBitmap (Zeile 2160-2169) dekodiert das Bild per ImageDecoder.decodeBitmap(...) in voller Auflösung und erzeugt mit `.copy(Bitmap.Config.ARGB_8888, true)` noch eine zweite vollgroße Kopie (Zeilen 2162-2168). Das Ergebnis wird in `var bitmap by remember { mutableStateOf<Bitmap?>(null) }` (Zeile 174) für die gesamte Lebensdauer des VocabTab-Composables gehalten – auch wenn der UPLOAD-Screen nicht angezeigt wird (Crossfade, Zeile 301ff.) – und weder beim Ersetzen (Zeile 266: `bitmap = uriToBitmap(...)` mit vorherigem Bild) noch beim Verlassen des Tabs recycelt. Ein 12-MP-Foto entspricht ~48 MB, während des `.copy` kurzzeitig das Doppelte; in der Single-Activity-App, in der die Tab-Shell den Composable ggf. am Leben hält, ist das ein reales OOM-/Memory-Druck-Risiko.

**Fix:** Beim Dekodieren runterskalieren (ImageDecoder#setTargetSampleSize bzw. setTargetSize auf z.B. max. 2048px), die redundante `.copy()`-Kopie entfernen, und das alte Bitmap vor dem Ersetzen bzw. per DisposableEffect onDispose beim Verlassen des Tabs mit recycle() freigeben (nur, solange es nicht mehr von der UI referenziert wird).

### `276`  —  UI-Coroutine auf appScope (nie abgebrochen) fängt Compose-State über die Composition hinaus

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — Die Save-Dialog-Coroutine läuft jetzt auf dem tab-lokalen `scope = rememberCoroutineScope()` statt `appScope`; beim Verlassen der Komposition werden die gefangenen State-Delegates (`savedSets`, `activeSet`, `screen`, `lastWidths`, `currentWidths`, `showSaveDialog`, `saveNameInput`, `comingFromScan`) samt `prefs` sofort freigegeben. `appScope`-Import entfernt.

**Beschreibung:** `appScope.launch { ... }` (Zeile 276) startet die Speicher-Logik des Save-Dialogs auf dem app-weiten, nie abgebrochenen `appScope` der Application. Das Lambda fängt alle State-Delegates des Composables (`savedSets`, `activeSet`, `screen`, `lastWidths`, `currentWidths`, `showSaveDialog`, `saveNameInput`, `comingFromScan`) sowie `prefs`. Verlässt der Tab die Composition (Tab-Wechsel), läuft die Coroutine samt der gefangenen State-Holder trotzdem weiter – aktuell kein Aktivitäts-/Context-Leak, da kein `context` gefangen wird, aber das Muster ist ein Leak-Risiko: Sobald der Block `context`/Activity referenziert, wird die Activity über die Composition-Lebensdauer hinaus gehalten.

**Fix:** Statt `appScope` den tab-lokalen `scope` (`rememberCoroutineScope`) bzw. einen lifecycle-aware Scope verwenden, damit der Job beim Verlassen der Composition abgebrochen wird und die State-Holder freigegeben werden.

## `app/src/main/java/com/tabslify/tabs/virustotal/VirusTotalScanManager.kt`

### `117`  —  Scans werden unbegrenzt auf dem Prozess-lifetime serviceScope gestartet und halten ihre Zustände fest

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** medium
- **Status (Aug 2026):** FIXED — `startScan` de-dupliziert jetzt: Gibt es bereits einen `Loading`-Job mit gleichem Modus+Ziel, wird kein zweiter Scan gestartet. Der `scanAction`-Aufruf läuft unter `withTimeout(SCAN_TIMEOUT_MS = 300_000)` (Upload + Polling), bei Überschreitung wird der Job deterministisch als `Error("Scan-Zeitüberschreitung")` abgeschlossen — `scanAction`, Datei-ByteArray und appContext sind damit garantiert freigegeben statt unbegrenzt gehalten zu werden.

**Beschreibung:** `Tabslify.serviceScope.launch { ... }` startet jeden Scan auf einem Scope, der nie abgebrochen wird (App-Prozess-Lebensdauer). Die Koroutine fängt `scanAction` (bei FILE-Modus das komplette Datei-ByteArray), `initialJob` und `appContext` für die gesamte Scan-Dauer (Upload + bis zu 15x Polling à 3s). `startScan` hat keinerlei Deduplication/Guard: wiederholtes Tippen auf den Scan-Button startet beliebig viele parallele Scans, die jeweils ihren Speicher (inkl. Datei-Bytes) bis zum Ende festhalten — unabhängig davon, ob der Tab noch sichtbar ist.

**Fix:** Bestehenden laufenden Scan pro Ziel-ID/Mode de-duplizieren (z.B. wenn bereits ein Job mit gleichem `target` im State Loading ist, nicht erneut starten), und die Scan-Koroutine mit begrenztem Timeout (z.B. `withTimeout`) versehen, damit `scanAction` und die gehaltenen Byte-Arrays garantiert freigegeben werden. Alternativ einen eigenen, mit dem Tab-Lebenszyklus gekoppelten Scope statt serviceScope verwenden, falls der Scan nicht im Hintergrund laufen soll.

## `app/src/main/java/com/tabslify/tabs/virustotal/VirusTotalTab.kt`

### `213`  —  Ganze Datei wird mit readBytes() vollständig in den Speicher geladen

- **Kategorie:** misc
- **Schweregrad:** low  |  **Confidence:** high
- **Status (Aug 2026):** FIXED — `readBytes()` läuft jetzt in `withContext(Dispatchers.IO)` statt auf dem Main-Dispatcher des rememberCoroutineScope (kein UI-Thread-Blocking beim Einlesen großer Dateien). Die Halte-Dauer des ByteArrays ist durch den `withTimeout`-Fix in VirusTotalScanManager.kt begrenzt (siehe Finding 117, deterministisches Ende nach spätestens 5 Min); danach wird die Referenz freigegeben.

**Beschreibung:** In der FILE-Start-Button-Logik: `val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }` lädt die komplette ausgewählte Datei in ein ByteArray (auf dem Main-Dispatcher des rememberCoroutineScope). Das ByteArray wird anschließend an `VirusTotalScanManager.startFile(...)` übergeben und von `repository.scanFile` via `submitFormWithBinaryData` (VirusTotalRepository.kt:90-117) erneut komplett als Multipart-Body gepuffert. Bei großen Ziel-Dateien (typisch für einen Virenscan, z.B. APKs) entstehen transient ~2x Dateigröße im Speicher, was auf Geräten mit wenig RAM zu OOM führen kann. Die Referenz wird bis zum Scan-Abschluss gehalten (siehe auch VirusTotalScanManager.kt:117, serviceScope), ist also zwar kein permanenter Leak, aber eine erhebliche, vermeidbare Speicherspitze inkl. Blockieren des UI-Threads.

**Fix:** Datei nicht komplett einlesen: Den InputStream (bzw. eine Uri) an die Repository-Schicht durchreichen und beim Upload direkt streamen (z.B. `readChannel()` / `submitFormWithBinaryData` mit `InputProvider`), damit nie mehr als ein Puffer im Speicher liegt. Zusätzlich den `readBytes()`-Block auf Dispatchers.IO ausführen statt auf dem Main-Dispatcher.
