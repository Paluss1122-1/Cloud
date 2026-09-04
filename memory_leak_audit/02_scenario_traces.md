# Szenario-Simulationen (Code-Pfad-Traces)

Jedes Szenario wurde als statischer Trace durch den echten Code verfolgt. Kein Laufzeit-Sandbox möglich (Projektregel), daher logische Simulation.

## Szenario 1


### `app/src/main/java/com/tabslify/core/ui/PrivateTabslifyApp.kt:809`  —  Download-COMPLETE-Receiver im Browser-Vollbild doppelt registriert und ohne Lifecycle-Unregister – latenter Activity-Context-Leak

- **Kategorie:** listener
- **Schweregrad:** medium  |  **Confidence:** medium

**Beschreibung:** In `setDownloadListener` (Zeile 738) wird derselbe anonyme `BroadcastReceiver` ZWEIMAL mit identischem `IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)` gegen die Activity (context = LocalContext.current, Zeile 385) registriert: `context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)` an Zeile 809–813 UND erneut an 815–819. Unregistered wird nur per `ctx.unregisterReceiver(this)` in onReceive bei Abschluss genau dieses Downloads (Zeile 769); das umgebende `DisposableEffect(webView, activity)` (Zeile 827) entfernt ausschließlich den LifecycleObserver, NICHT den Receiver. Der Receiver captured `dm`, `filename` und den Activity-Kontext. Bricht der Download nie ab (Tab-/Vollbild-Wechsel, abgebrochener Download, Netzverlust) oder wird das Vollbild-Composable entfernt, bleibt der Receiver dauerhaft registriert und hält bei einem echten Activity-Recreate (Locale-/DarkMode-Wechsel, Memory-Pressure – Rotation selbst ist durch configChanges abgedeckt) die zerstörte Activity samt DownloadManager fest; bei wiederholten Browser-Downloads akkumulieren diese Registrationen. Die doppelte Registration ist zudem toter Code (zweiter unregisterReceiver wirft bei deduplizierter Registration).

**Fix:** Die zweite `context.registerReceiver(...)`-Zeile (815–819) löschen. Die Receiver-Lebensdauer an die Composition koppeln: im onDispose des `DisposableEffect(webView, activity)` (Zeile 827) `runCatching { context.unregisterReceiver(receiver) }` ergänzen, wenn der Download noch nicht abgeschlossen ist; alternativ den Receiver als `remember`-Feld halten und beim Verlassen des Vollbilds (onHideCustomView / onDispose) verlässlich abzumelden.

### `app/src/main/AndroidManifest.xml:97`  —  Rotation wird per configChanges geschluckt – die Activity wird nicht neu aufgebaut, klassische Rotations-Leaks greifen nicht

- **Kategorie:** misc
- **Schweregrad:** low  |  **Confidence:** high

**Beschreibung:** Die Szenario-Prämisse ('bei jeder Rotation wird die Activity neu aufgebaut') trifft auf diese App NICHT zu: MainActivity deklariert `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|keyboard|smallestScreenSize"`. Ein Rotation-Sturm (20-30x) löst daher keinen Activity-Recreate aus: onCreate in MainActivity.kt:56 (Service-Starts MediaPlayerService/QuietHoursNotificationService/ChatService/startBatteryWorker, setOnExitAnimationListener, registerForActivityResult) läuft genau einmal, Activity-scoped ViewModels (`viewModel()` in PrivateTabslifyApp.kt:382-383, MediaPlayerTab.kt:179, FitnessTab.kt), `rememberSaveable`-States (selectedMenuItem/PrivateTabslifyApp.kt:386, isFullScreen:389, webViewUrl:391) und alle DisposableEffects überleben die Rotation unverändert; onDestroy/onDispose werden pro Rotation nicht aufgerufen. Es entstehen also KEINE kumulierten Retained-References, keine doppelten Listener und keine weiterlaufenden Coroutines aus 'Rotation 1' – die üblichen Rotations-Leak-Pfade existieren hier nicht.

**Fix:** Kein Fix nötig – Verhalten ist beabsichtigt (configChanges schützt Vollbild-WebView, Kamera-Binding und Tab-State). Für den Owner als Hinweis: Nur wenn gewünscht ist, dass Rotation den Standard-Recreate-Pfad durchläuft, müsste configChanges reduziert werden; dann wären die per-ConfigChange geschützten Zustände (Browser-WebView, PushUp-Kamera) bewusst zu verifizieren. Empfehlung: so lassen.
## Szenario 2


### `app/src/main/java/com/tabslify/core/ui/LandingPage.kt:365`  —  Jeder Tabwechsel disposed die komplette Shell und komponiert den neuen Tab doppelt (Preview + Hauptinstanz)

- **Kategorie:** misc
- **Schweregrad:** medium  |  **Confidence:** high

**Beschreibung:** Tabwechsel laufen über `key(selectedMenuItem, reloadKey) { ... PrivateTabslifyApp(...) }` (Zeile 365). Jede Auswahl ändert den Key, wodurch die gesamte App-Shell inkl. aller `remember`/`rememberSaveable`-Zustände disposed und neu komponiert wird. Zusätzlich wird bei `pendingOverlayItem != null` (Zeile 405) eine zweite, vollständige `PrivateTabslifyApp(initialMenuItem = item, ...)` (Zeile 420) in einer versteckten Box komponiert, nur um via `graphicsLayer.toImageBitmap()` (Zeile 430) ein Fullscreen-Bitmap (~10 MB bei 1080x2400x4) für den Übergang zu capturen — gleichzeitig zur laufenden Haupt-Shell (Zeile 368). Das bedeutet: Pro Tab-Hop wird der neue Tab 2x vollständig initialisiert (MediaStore-Scan, Gallery-Thumbnails, osmdroid-MapView, ExoPlayer etc.) und es entsteht ein doppelter Peak-Memory sowie pro Wechsel ein Fullscreen-Bitmap. Beim Vorschau-Öffnen des Explore-Tabs feuert zudem der ON_RESUME-Lifecycle-Observer von ExploreTabContent (Zeile 137-143 `Lifecycle.Event.ON_RESUME -> { ... ExploreLocationTracker.start(ctx) }`) und startet den `ExploreForegroundService` (Location-Foreground-Service) bereits durch das bloße Hovern über den Tab-Eintrag im Landing-Menü.

**Fix:** Nur ein `PrivateTabslifyApp`-Instanz komponieren. Die Vorschau-Komposition sollte auf eine leichte, side-effect-freie Repräsentation reduziert werden (z.B. statisches Icon/Blur statt des vollen Tab-Inhalts) oder ganz entfallen. Für den Tabwechsel `rememberSaveable`-Zustand in der Shell erhalten (z.B. `MovableContentOf`/`saveableStateHolder` statt `key`-Recreation), damit nicht jede Auswahl die Shell neu aufbaut und den WebView-/Browser-Zustand verliert.

### `app/src/main/java/com/tabslify/tabs/mediaplayer/MediaPlayerTab.kt:3147`  —  Activity-gescopte ViewModels bleiben nach erstem Tabbesuch dauerhaft im ViewModelStore und halten Tab-Daten + Endlos-Poller

- **Kategorie:** viewmodel
- **Schweregrad:** medium  |  **Confidence:** high

**Beschreibung:** Mehrere Tabs nutzen `viewModel()` (Activity-Scope): `MediaTab(viewModel: MediaViewModel = viewModel())` (Zeile 179), `AITabContent(vm: AITabViewModel = viewModel())` (AITab.kt Zeile 117), `ExploreTabContent` (Zeile 75), `AudioRecorderTab(vm = viewModel())`. Die Composables werden beim Tabwechsel lazy disposed, aber die ViewModels werden einmalig beim ersten Besuch erzeugt und verbleiben bis zum Activity-Tod im ViewModelStore — inkl. aller Daten. Beispiel `MediaViewModel` (Zeile 3127): `init { ... startNowPlayingPoller() }` (Zeile 3147-3153) startet `while (isActive) { _nowPlaying.value = readNowPlayingFromPrefs(); delay(1000) }` (Zeile 3176-3183), das ununterbrochen weiterläuft, solange die App lebt, und `_uiState` (Zeile 3129) hält die komplette Song-/Episode-Liste. `AITabViewModel.history` (ViewModel.kt Zeile 62) hält den gesamten Chat-Verlauf im Speicher. Beim schnellen Hopping durch ~40 Tabs akkumulieren so alle besuchten Tab-ViewModels samt State im Activity-ViewModelStore; freigegeben wird erst beim Activity-Destroy.

**Fix:** Tab-ViewModels entweder im Tab-Lebenszyklus erzeugen (per `remember { }` wie beim CONTACTS-Tab, ggf. mit eigenem ViewModelStoreOwner pro Tab) oder nach dem Verlassen des Tabs gezielt aufräumen (`viewModelStore.clear()` / `onCleared`). Die `startNowPlayingPoller()`-Schleife sollte nur laufen, solange der Tab sichtbar ist, statt permanent im Activity-Scope.

### `app/src/main/java/com/tabslify/core/ui/PrivateTabslifyApp.kt:393`  —  Zerstörter WebView bleibt nach Verlassen des Vollbild-Browsers über webViewState referenziert

- **Kategorie:** webview
- **Schweregrad:** low  |  **Confidence:** medium

**Beschreibung:** `var webViewState by remember { mutableStateOf<WebView?>(null) }` (Zeile 393) liegt im äußeren Shell-Scope und wird per `AndroidView(update = { webViewState = it })` (Zeile 860) gesetzt. Beim Verlassen des Vollbild-Modus ruft der `DisposableEffect(isFullScreen)`-onDispose zwar `webView.stopLoading(); webView.onPause(); webView.destroy()` (Zeile 850-852) auf, setzt aber `webViewState` nicht auf null. Der zerstörte WebView (der weiterhin seinen Activity-Context und die WebChromeClient-/WebViewClient-Instanzen referenziert) bleibt so bis zur nächsten Vollbild-Öffnung oder bis die Shell durch einen Tabwechsel (key-Recreation in LandingPage) verworfen wird, im Speicher.

**Fix:** Beim Verlassen des Vollbild-Modus `webViewState = null` setzen (im onDispose von `DisposableEffect(isFullScreen)` oder beim Update des AndroidView) bzw. die Referenz nur lokal im Vollbild-Block halten und nicht im äußeren remember-State speichern.

### `app/src/main/java/com/tabslify/core/ui/PrivateTabslifyApp.kt:763`  —  Download-BroadcastReceiver wird pro Download registriert, nur bei Download-Abschluss abgemeldet und zweimal (redundant) registriert

- **Kategorie:** receiver
- **Schweregrad:** low  |  **Confidence:** medium

**Beschreibung:** Im `setDownloadListener` des Vollbild-WebViews wird pro Download ein anonymer `BroadcastReceiver` erzeugt und am Activity-Context registriert — und zwar doppelt mit identischem Filter: `context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ...)` (Zeile 809) und erneut Zeile 815 (identischer Aufruf). Die Abmeldung passiert nur in `onReceive` (Zeile 769 `ctx.unregisterReceiver(this)`) und nur, wenn der passende downloadId eintrifft. Verlässt der Nutzer den Vollbild-Browser (WebView wird per `webView.destroy()` Zeile 852 entsorgt), während ein Download noch läuft, bleibt der Receiver am Activity-Context (der für die gesamte App-Session lebt) registriert und hält `dm`, `filename`, `downloadId` und den Context fest, bis der Download abgeschlossen ist. Mehrere in-flight Downloads über die Session akkumulieren so Receiver-Objekte.

**Fix:** Den doppelten `registerReceiver`-Aufruf (Zeile 815) entfernen. Den Receiver-Lebenszyklus an die Komposition koppeln: in einem `DisposableEffect` registrieren und im `onDispose` immer `unregisterReceiver` aufrufen (nicht nur bei passendem downloadId), sodass beim Verlassen des Vollbild-Modus keine Receiver zurückbleiben.

### `app/src/main/java/com/tabslify/tabs/mediaplayer/MediaPlayerTab.kt:579`  —  HomeTab startet jede Sekunde einen vollen MediaStore-Rescan, dessen refresh()-Coroutines beim Tab-Wechsel nicht gecancelt werden

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** high

**Beschreibung:** HomeTab hat `LaunchedEffect(Unit) { while (true) { delay(1000.milliseconds); onRefresh() } }` (Zeile 580-584), das über `onRefresh` = `viewModel.refresh()` (Zeile 3185) jede Sekunde `loadSongsFromMediaStore()` + `loadEpisodesFromMediaStore()` + algorithmische Playlists in einem `viewModelScope.launch` ausführt (Zeile 3190-3219). viewModelScope ist Activity-gebunden; die `refresh()`-Coroutines werden beim Verlassen des Tabs NICHT gecancelt (nur die LaunchedEffect-Schleife endet). Bei schnellem Tab-Hopping in/aus MediaTab stapeln sich mehrfach überlappende Voll-Scans, die jeweils komplette Song-/Episode-Listen allokieren, bis sie fertig sind.

**Fix:** `refresh()` sollte dem Tab-Lebenszyklus folgen (eigener CoroutineContext, der bei Tab-Exit gecancelt wird) statt `viewModelScope`; außerdem Wiederholungs-Scans verhindern (z.B. nur bei Datenänderung statt sekündlich, oder ein einziges In-Flight-Job-Flag).
## Szenario 3


### `app/src/main/java/com/tabslify/services/MediaPlayerService.kt:2997`  —  onTaskRemoved ist leer – MediaSessionService-Automatik wird deaktiviert, Service bleibt nach App-Kill weiterlaufen

- **Kategorie:** service
- **Schweregrad:** medium  |  **Confidence:** high

**Beschreibung:** Zeile 2997: `override fun onTaskRemoved(rootIntent: Intent?) {}` überschreibt die Default-Implementierung von MediaSessionService mit einem leeren Body. Die Basisimplementierung ruft `stopSelf()` auf, wenn der Task entfernt wird und gerade nicht aktiv abgespielt wird. Dadurch bleibt der Service nach dem Wegwischen der App aus den Recents („Kill die App") auch bei pausierter/gestoppter Wiedergabe dauerhaft im Vordergrund weiterlaufen und hält die Referenzen: den pausierten `musicPlayer`/`podcastPlayer` (android.media.MediaPlayer mit nativen Buffern), das `mediaSession` (Zeile 1045 wird nur in onDestroy released), die Notification und den `positionSaveRunnable`-Loop (Zeile 2227, postet bei Screen-on alle 5 s weiter). Bei „mehrfach über Stunden" wird so nie freigegeben, was der Nutzer per Kill erwartet. onDestroy (Zeile 998) selbst räumt zwar korrekt auf, wird aber auf diesem Pfad nie aufgerufen.

**Fix:** `onTaskRemoved` so implementieren, dass bei nicht aktiver Wiedergabe gestoppt wird, z. B.: `override fun onTaskRemoved(rootIntent: Intent?) { if (!isPlayingMusic && !isPlayingPodcast && currentStreamName.isEmpty()) stopSelf() else super.onTaskRemoved(rootIntent) }` (bzw. `super.onTaskRemoved(...)` aufrufen und nur bei aktiver Wiedergabe eine Foreground-Notification weiterführen).

### `app/src/main/java/com/tabslify/tabs/mediaplayer/MediaPlayerTab.kt:579`  —  HomeTab: 1-Sekunden-Refresh-Schleife mit vollem MediaStore-Rescan und überlappenden viewModelScope-Coroutinen

- **Kategorie:** coroutine
- **Schweregrad:** medium  |  **Confidence:** high

**Beschreibung:** Zeile 579-584: `LaunchedEffect(Unit) { while (true) { delay(1000.milliseconds); onRefresh() } }` ruft sekündlich `viewModel.refresh()` auf (Zeile 3185), das je Sekunde `loadSongsFromMediaStore()` und `loadEpisodesFromMediaStore()` (zwei komplette MediaStore-Queries), `PodcastShowManager.getShows()`, `MediaAnalyticsManager.getGlobalStats()` (deserialisiert die komplette Session-Liste aus Prefs, die über die Stunden unbegrenzt wächst) und die algorithmischen Playlists neu berechnet. Jeder `refresh()` startet eine eigene `viewModelScope.launch` (Zeile 3190), die nicht abgebrochen wird, wenn die nächste Sekunde schon eine neue startet – dauert ein Query länger als 1 s, stapeln sich die Coroutinen. Über „mehrfach über Stunden" erzeugt das dauerhafte große Allokations-Churn und GC-Druck, auch wenn die Daten gleich bleiben.

**Fix:** Refresh an echte Datenänderungen binden statt an ein 1-s-Tick (z. B. nur bei Service-Notification-/Action-Änderung, bei Permission-Grant oder bei Rückkehr in den Tab) und laufende `refresh()`-Jobs im ViewModel zuerst canceln (Job-Referenz halten und `cancel()` vor neuem Launch).

### `app/src/main/java/com/tabslify/services/MediaPlayerService.kt:1933`  —  Service-Coroutinen laufen im Prozess-weiten appScope und werden bei onDestroy nicht gecancelt

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** high

**Beschreibung:** Mehrere Coroutinen starten im application-weiten `Tabslify.appScope` und halten dabei eine starke Referenz auf die Service-Instanz: Zeile 1933 `updateNotJob = appScope.launch { delay(...); ... pushMediaStateToLaptop(this@MediaPlayerService) }`, Zeile 1340 `appScope.launch(Dispatchers.IO) { ... handler.post { dummyPlayer?.setMediaMetadata(...) } }` (Metadaten-Extraktion beim Songwechsel), Zeile 2901 `appScope.launch { ... withContext(Dispatchers.Main) { podcastPlayer = MediaPlayer().apply { ... prepareAsync() } } }` in streamFromUrl. appScope (Tabslify.kt Zeile 39) lebt für den ganzen Prozess; onDestroy cancelled nichts davon. Wird der Service gestoppt, während so eine Coroutine noch pending/läuft, bleibt die zerstörte Service-Instanz (inkl. ihres `handler`) bis zum Ende der Coroutine retained, und bei streamFromUrl kann nach dem Zerstören noch ein neuer `MediaPlayer` erzeugt werden, der nie mehr released wird (kleiner nativer Leak pro unterbrochenem Stream).

**Fix:** Die Coroutinen in einen Service-eigenen `CoroutineScope(SupervisorJob() + Dispatchers.Main)` legen und in onDestroy `serviceScope.cancel()` aufrufen; alternativ in streamFromUrl nach dem `withContext(Dispatchers.Main)` prüfen, ob `isServiceDestroyed` gesetzt ist, und den erzeugten MediaPlayer dann sofort `release()`n.

### `app/src/main/java/com/tabslify/tabs/mediaplayer/MediaPlayerTab.kt:3176`  —  startNowPlayingPoller: unbedingte 1-Sekunden-Endlosschleife über die gesamte App-Session

- **Kategorie:** viewmodel
- **Schweregrad:** low  |  **Confidence:** high

**Beschreibung:** Zeile 3176-3183: `fun startNowPlayingPoller() { viewModelScope.launch { while (isActive) { _nowPlaying.value = readNowPlayingFromPrefs(); delay(1000.milliseconds) } } }`. Die Schleife wird einmal im `init` des `MediaViewModel` gestartet (Zeile 3152) und läuft, solange das ViewModel lebt. Das ViewModel ist über `viewModel()` in `MediaTab` (Zeile 179) an den Activity-ViewStore gebunden – in dieser Single-Activity-App bedeutet das: für die gesamte App-Session, auch wenn der Media-Tab gar nicht in der Komposition ist. Es ist also eine nie gecancelte Coroutine, die sekündlich SharedPreferences liest und StateFlow-Objekte allokiert, über Stunden hinweg.

**Fix:** Den Poller nur starten, solange der Tab sichtbar ist (z. B. über eine Lifecycle-/`LaunchedEffect`-Kontrolle starten/stoppen) oder die Aktualisierung über einen beobachteten `nowPlaying`-Flow aus einer zentralen Quelle (Service-Callback statt Prefs-Polling) ersetzen.

### `app/src/main/java/com/tabslify/tabs/mediaplayer/SpotifyPlaybackTracker.kt:94`  —  MediaController wird nie release()d – bei jedem neuen Spotify-Session-Wechsel bleibt ein nicht freigegebener Controller zurück

- **Kategorie:** media
- **Schweregrad:** low  |  **Confidence:** low

**Beschreibung:** In `attach()` (Zeile 76-95) wird bei jedem Auftauchen eines neuen Spotify-Sessions ein neuer `MediaController` gespeichert und `activeController?.registerCallback(callback, mainHandler)` registriert. `detach()` (Zeile 102-108) ruft nur `activeController?.unregisterCallback(cb)` auf und setzt `controllerCallback = null`, aber nie `activeController?.release()`. Auch `onSessionDestroyed` (Zeile 89-92) nullt `activeController`, ohne den Callback zu deregistrieren bzw. den Controller freizugeben. MediaController hält Binder-Connections/Death-Recipients zur MediaSession des Spotify-Prozesses; ein ohne `release()` verworfenes Objekt hält die Verbindung bis zum Session-Ende. Über viele Spotify-Play-Zyklen („mehrfach über Stunden") churnen so nicht freigegebene Controller. start()/stop() (Zeile 35/63) sind zwar balanciert über WhatsAppNotificationListener (Zeile 415/425), aber der einzelne Controller wird nie explizit freigegeben.

**Fix:** In `detach()` (und vor dem Überschreiben in `attach()`) zusätzlich `activeController?.release()` aufrufen und in `onSessionDestroyed` `controllerCallback?.let { activeController?.unregisterCallback(it) }; controllerCallback = null` ergänzen.
## Szenario 4


### `app/src/main/java/com/tabslify/quiethoursnotificationhelper/WIFI_DIRECT.kt:993`  —  Watchdog- und Trigger-Accept-Loop halten den rohen (Activity-)Kontext dauerhaft

- **Kategorie:** activity-context
- **Schweregrad:** high  |  **Confidence:** high

**Beschreibung:** `startTriggerWatchdog(context)` erzeugt `triggerWatchdogJob = syncScope.launch { while (isActive) { delay(20_000L); ... syncTodosWithLaptop(context); startTriggerListener(context) } }` (Z. 993-1019) und captured den rohen `context` im Endlos-Loop (kein `.applicationContext`). Ebenso captured der ewige Accept-Loop `triggerJob` (via `launchServer`, Handler Z. 885-987) den `context` (u.a. `syncScope.launch { syncTodosWithLaptop(context, true) }`, `stopAllSyncServices(context)`). Beide Jobs werden nur in `shutdownAllWifiDirectServices` (Service-onDestroy, Z. 516-547) gecancelt. `PCManager` (Composable, `context = LocalContext.current` = MainActivity, PCManager.kt:86) ruft `syncTodosWithLaptop(context, true)` (PCManager.kt:300) und `stopAllSyncServices(context)` (PCManager.kt:420/463) auf; `stopAllSyncServices` startet am Ende `startTriggerListener(context)` mit diesem Kontext (Z. 1155-1157). Sobald der Trigger so mit Activity-Kontext (neu) gestartet wurde, hält die stundenlange Reconnect-Spirale die - auch nach Activity-Destroy - tote MainActivity samt Compose-Baum im Speicher, weil der Foreground-Service weiterläuft. Der Guard `if (triggerJob?.isActive == true) return` (Z. 881) verhindert sogar, dass ein späterer Service-Neustart (`startTriggerListenerIfHomeWifi(this)`) den toten Activity-Kontext ersetzt.

**Fix:** In `startTriggerListener`, `startTriggerWatchdog` und `ensureSyncListenersAlive` überall auf `context.applicationContext` binden und die Funktionen nur mit applicationContext aufrufen (in PCManager `syncTodosWithLaptop(context.applicationContext, true)` bzw. `stopAllSyncServices(context.applicationContext)`). Zusätzlich den Watchdog/Trigger-Loop an den Service-Lifecycle koppeln (canceln bei Service-onStop) und beim Neustart den bestehenden `triggerJob`/`triggerWatchdogJob` immer durch den neuen Kontext ersetzen statt per `isActive`-Guard den alten (toten) Kontext zu behalten.

### `app/src/main/java/com/tabslify/quiethoursnotificationhelper/WIFI_DIRECT.kt:1297`  —  Receiver werden mit rohem Kontext registriert und nie korrekt unregistered (Kontext-Mismatch)

- **Kategorie:** receiver
- **Schweregrad:** high  |  **Confidence:** high

**Beschreibung:** Bei erfolgreichem Sync registriert `syncTodosWithLaptop` die drei System-Receiver mit dem übergebenen `context`: `context.registerReceiver(akkuReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)`, ebenso `bluetoothReceiver` und `volumeChangeReceiver` (Z. 1297-1303). Lief der Sync über `PCManager` (PCManager.kt:300), ist `context` die MainActivity. `stopAllSyncServices(context)` unregistriert mit seinem EIGENEN `context` (Z. 1130-1141, catch `IllegalArgumentException`); über `onPcCallFailure(context.applicationContext)` (Z. 377-380) oder den `onLost`-NetworkCallback (Service-Kontext, Z. 840) kommt dort eine andere Context-Instanz an. `unregisterReceiver` mit nicht identischer Context-Instanz wirft IllegalArgumentException, wird geschluckt → die Receiver bleiben registriert (ReceiverDispatcher im LoadedApk hält die Context-Instanz) und halten die tote Activity über die gesamte Reconnect-Spirale fest. Selbst `shutdownAllWifiDirectServices` im Service-onDestroy unregistriert diese drei Receiver nicht (s. separate Finding).

**Fix:** Die drei Receiver IMMER mit `context.applicationContext` registrieren und mit `context.applicationContext` unregistrieren; im `stopAllSyncServices` den für die Registrierung verwendeten (application-)Kontext über einen statischen Feld-Holder speichern und unabhängig vom Funktionsparameter unregister aufrufen. Besser: Registrierung einmalig an den `QuietHoursNotificationService`-Lifecycle (onCreate/onDestroy) binden statt pro Sync-Zyklus.

### `app/src/main/java/com/tabslify/quiethoursnotificationhelper/WIFI_DIRECT.kt:516`  —  shutdownAllWifiDirectServices unregistriert akkuReceiver/bluetoothReceiver/volumeChangeReceiver nicht

- **Kategorie:** service
- **Schweregrad:** medium  |  **Confidence:** medium

**Beschreibung:** `shutdownAllWifiDirectServices` (aus `QuietHoursNotificationService.onDestroy`, Z. 1216) cancelt `triggerJob`/`triggerWatchdogJob`/`pendingSyncJob`/`listenerJob` etc., schließt ServerSockets und unregistriert `networkCallback` (Z. 541) sowie die Device-Info-Listener (Z. 542) - aber NICHT `akkuReceiver`, `bluetoothReceiver`, `volumeChangeReceiver` (die werden nur in `stopAllSyncServices`, Z. 1130-1141, unregistered). Wird der Foreground-Service zerstört, während die Receiver registriert sind (Verbindung aktiv), bleiben die drei Receiver an den toten Service-Kontext gebunden; der ReceiverDispatcher hält die Service-Instanz, sodass diese nicht freigegeben werden kann. Da die Receiver (je nach letztem Sync-Aufrufer) zusätzlich an einen Activity-Kontext gebunden sein können, verlängert das die Lebensdauer der toten Activity zusätzlich.

**Fix:** In `shutdownAllWifiDirectServices` dieselben drei `unregisterReceiver`-Aufrufe wie in `stopAllSyncServices` (mit applicationContext und try/catch IllegalArgumentException) ergänzen, damit der Service-Destroy-Pfad die Receiver vollständig entfernt.

### `app/src/main/java/com/tabslify/quiethoursnotificationhelper/WIFI_DIRECT.kt:264`  —  BluetoothProfile.ServiceListener bleibt bei ausbleibendem Callback am Kontext hängen

- **Kategorie:** listener
- **Schweregrad:** low  |  **Confidence:** low

**Beschreibung:** `connectBluetoothDevice(context, nameOrAddress, ...)` registriert via `adapter.getProfileProxy(context, listener, profile)` (Z. 290) einen `BluetoothProfile.ServiceListener`; `adapter.closeProfileProxy(profile, proxy)` wird nur im `onServiceConnected`-finally (Z. 277) aufgerufen. Kommt der Callback nie (Bluetooth aus/fault, Profil nicht verfügbar), bleibt der Listener - der den übergebenen `context` captured - beim Bluetooth-Service registriert. Der Aufruf kommt aus `handleExecuteCommand` (Z. 2579) mit dem Kontext des Execute-Listeners, der je nach Aufruferkette ein Activity-Kontext sein kann; `withTimeoutOrNull(8000)` (Z. 2583) bricht nur die aufrufende Coroutine ab, nicht die Bluetooth-Proxy-Verbindung.

**Fix:** Nach dem `withTimeoutOrNull`-Abbruch zusätzlich die Profile-Proxy für beide Profile schließen (closeProfileProxy) und den Listener auf `context.applicationContext` beschränken, um die Bluetooth-Profilverbindung samt Listener nicht offen zu halten.

### `app/src/main/java/com/tabslify/quiethoursnotificationhelper/WIFI_DIRECT.kt:582`  —  Client-Handler-Coroutines überleben Job-Cancel auf nie beendeten Top-Level-Scopes

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** medium

**Beschreibung:** `launchServer` startet pro akzeptiertem Client `scope.launch { client.use { try { handler(it) } catch ... } }` (Z. 582-591) direkt auf den statischen, für die App-Lebensdauer nie gecancellten Scopes `syncScope` (Z. 316-318) bzw. `mediaScope` (Z. 383-385), nicht als Kind des zurückgegebenen Server-Jobs. `stopAllSyncServices`/`shutdownAllWifiDirectServices` cancellt nur die Server-Jobs (Z. 1104-1107, 517-527) und schließt die ServerSockets, aber bereits gestartete Handler-Coroutines (blockiert in `readLine()`/`readBytes()`, z.B. Z. 891, 1848, 2319, 2336) laufen weiter bis zum geerbten 5s-`soTimeout` und halten dabei den `context`. Während der Reconnect-Spirale, in der der Laptop wiederholt Verbindungen auf den Trigger-/Media-/Execute-Ports öffnet, stapeln sich kurzzeitig blockierte Handler-Coroutines auf dem ewigen Scope.

**Fix:** Handler-Coroutines als Kinder des Server-Jobs starten (z.B. `val child = launch { ... }` und bei Job-Cancel im `finally` auch `child.cancel()`), oder die Handler-Arbeit in einen eigenen, mitgeltenden Child-Job pro `launchServer`-Aufruf kapseln, der beim Shutdown mit abgebrochen wird.
## Szenario 5


### `app/src/main/java/com/tabslify/services/QuietHoursNotificationService.kt:1183`  —  Statischer workerHandler hält zerstörte Service-Instanz über selbst-reschedulenden checkRunnable fest

- **Kategorie:** service
- **Schweregrad:** medium  |  **Confidence:** high

**Beschreibung:** In onCreate wird `handler.post(checkRunnable)` (Z.467) mit `checkRunnable = getCheckRunnable(this)` (Z.314-326) ausgeführt; das Runnable erfasst die Service-Instanz. `scheduleNextCheck` (Notifications.kt Z.89-93) legt ein NEUES Runnable an, das denselben Context erfasst, und postet es auf den STATISCHEN `workerHandler` (Z.307, Handler auf statischem `HandlerThread` Z.306). In `onDestroy` (Z.1183) wird nur `handler.removeCallbacksAndMessages(null)` auf dem Main-Handler aufgerufen — der `workerHandler` wird NIE geleert. Das Runnable ruft bei jedem Durchlauf erneut `checkQuietHours(context)` und `scheduleNextCheck(context)` mit der ZERSTÖRTEN Service-Instanz auf und plant sich dadurch selbst dauerhaft neu. Die statische Handler-Queue hält damit permanent eine starke Referenz auf eine (zerstörte) Service-Instanz samt Closure-Stack; bei jedem Service-Neustart über `scheduleRestart` (Z.1225-1256) wird die vorherige Instanz auf diese Weise festgehalten und nie freigegeben. Nebenbei räumt `scheduleNextCheck` mit `workerHandler.removeCallbacksAndMessages(null)` auch fremde Tasks auf dem geteilten Handler ab (z.B. VoiceNote-Aufgaben aus VoiceNote.kt Z.41).

**Fix:** In `onDestroy` zusätzlich `workerHandler.removeCallbacksAndMessages(null)` aufrufen. Den checkRunnable nicht mit der Service-Instanz erzeugen, sondern mit `applicationContext` (der Runnable nutzt nur getSharedPreferences/startForegroundService, beides funktioniert mit App-Context). Alternativ einen im Service-Lifecycle gehaltenen, nicht-statischen Handler verwenden statt des statischen `workerHandler`, damit mit der Instanz auch die Queue freigegeben wird.

### `app/src/main/java/com/tabslify/services/MyFirebaseMessagingService.kt:68`  —  Unbegrenzte serviceScope.launch-Coroutines pro FCM-Nachricht ohne Limit/Cancel

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** medium

**Beschreibung:** `onMessageReceived` startet für jede Daten-Nachricht mit `script_name` einen neuen `Tabslify.serviceScope.launch { fetchAndRun(scriptName, applicationContext) }` auf dem prozessglobalen `serviceScope` (Tabslify.kt Z.42-44, Dispatchers.IO + SupervisorJob). Es gibt keinerlei Dedupe, Limit, Warteschlange oder Job-Tracking; `fetchAndRun` (Tabslify.kt Z.132-141) macht eine Supabase-Abfrage (30s-Timeout laut Config.kt HttpTimeout) plus Rhino-JS-Ausführung (executeJs). Bei 100+ Nachrichten/Stunde und langsamer Cloud stapeln sich die Coroutines im IO-Dispatcher und halten Script-Objekte/Rhino-Contexts/AppBridge (Tabslify.kt Z.143-157, hält Context) fest, bis sie abarbeiten.

**Fix:** Single-flight pro scriptName: vorher laufenden Job canceln bzw. einen laufenden Job pro scriptName wiederverwenden (z.B. `Map<String, Job>`), oder einen dedizierten, begrenzten Scope mit eigener Queue verwenden; `fetchAndRun` mit `withTimeout` und zusätzlichem Fehler-Handling versehen.

### `app/src/main/java/com/tabslify/services/QuietHoursNotificationService.kt:1496`  —  cms()-basierte, nie gecancelte Notification-IDs wachsen über viele Zyklen

- **Kategorie:** misc
- **Schweregrad:** low  |  **Confidence:** high

**Beschreibung:** `showSimpleNotification` (Z.1477-1505) und der Email-Zweig von `MyFirebaseMessagingService` (Z.64: `tNotify(applicationContext, System.currentTimeMillis().toInt(), notification)`) verwenden `cms()` = `System.currentTimeMillis().toInt()` (Config.kt Z.181) — pro Aufruf eine neue, eindeutige ID. `showSimpleNotification` cancelt nur bei `duration > Duration.ZERO` (Default `Duration.ZERO`, Z.1481); die FCM-Email-Notification wird nie gecancelt und gehört zur Gruppe "SSN" ohne Summary (SSNE.kt Z.30-36, gleiche cms()-Logik Z.58-72). Über viele Zyklen/Sturm wachsen so Notification-Tray/SystemUI dauerhaft (keine Ersetzung, keine Gruppen-Kollapsierung) und pro Notification bleiben PendingIntent/Notification-Objekte im System hängen.

**Fix:** Feste, kollabierbare IDs pro Kategorie verwenden (z.B. Kategorie-basierte ID statt cms()), mit `setGroup` + feste Group-Summary-ID, und in `onDestroy` bzw. nach Ablauf `notificationManager.cancel(id)` aufrufen; `showSimpleNotification` einen Standard-Duration-Cancel geben.

### `app/src/main/java/com/tabslify/services/WhatsAppNotificationListener.kt:166`  —  Pro Notification neue Forward-Coroutine mit blockierendem Socket ohne Abbruch

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** medium

**Beschreibung:** `onNotificationPosted` (Z.255-270) ruft bei JEDER Notification (auch fremder Apps) bei verbundenem Laptop `forwardNotificationsToLaptop(it, packageManager)` auf; dort wird pro Event `pendingForwardJob?.cancel()` + `pendingForwardJob = forwardScope.launch { ... }` (Z.165-166) gestartet und ALLE aktiven Notifications werden in ein JSONArray serialisiert und über `java.net.Socket().use { connect(..., 3000) }` (Z.219-226) gesendet. Der Cancel bricht nur die `delay(...)`-Debounce-Phase ab, nicht einen bereits laufenden blockierenden Socket-Connect (der läuft bis zum 3s-Timeout weiter). Bei 100+ Notifications/Stunde und nicht erreichbarem Laptop bleiben so mehrere Coroutines gleichzeitig auf IO-Threads blockiert und halten Notifications-Arrays plus Listener-Referenzen fest. Zusätzlich startet Z.340 pro WhatsApp-Nachricht einen weiteren `forwardScope.launch` (getAll/insert/sendBroadcast), der die komplette Liste kopiert.

**Fix:** Forwarding auf nicht-blockierendes Connect mit `withTimeout` umstellen oder auf einen einzelnen laufenden Job mit Neustart begrenzen; die aktiven Notifications nicht bei jedem Event komplett neu serialisieren, sondern nur den Diff bzw. die neue Notification senden.
## Szenario 6


### `app/src/main/java/com/tabslify/tabs/mediaplayer/MediaPlayerTab.kt:580`  —  1-Sekunden-Refresh-Loop in HomeTab läuft ungebremst im Hintergrund weiter

- **Kategorie:** coroutine
- **Schweregrad:** medium  |  **Confidence:** high

**Beschreibung:** In HomeTab (Zeile 579-584): `LaunchedEffect(Unit) { while (true) { delay(1000.milliseconds); onRefresh() } }`. `onRefresh` ist `viewModel.refresh()` (Zeile 354), das pro Durchlauf per `withContext(Dispatchers.IO)` `loadSongsFromMediaStore()` (öffnet für JEDEN Song einen FileDescriptor, Zeile 2076-2078), `loadEpisodesFromMediaStore()` und `MediaAnalyticsManager.getGlobalStats()` → `getSessions()` (komplette Gson-Deserialisierung der Session-Historie) ausführt. Die Schleife ist an die Komposition gebunden und läuft auch weiter, wenn die Activity nur gestoppt ist (Background) — über 8h sind das ca. 28.800 Refreshs. Da ein Refresh (>1s MediaStore-Query + JSON-Parse) länger als das 1s-Intervall dauern kann, startet jede Sekunde eine neue `viewModelScope.launch`-Coroutine, ohne dass die vorherige abgeschlossen ist → überlappende IO-Coroutinen stauen sich und der Main-Thread wird mit UI-State-Writes überlastet. Die Loop kennt weder Lifecycle-Zustand noch ein 'noch läuft ein Refresh'-Guard.

**Fix:** Die Polling-Schleife an das Lifecycle koppeln (z.B. `repeatOnLifecycle(Lifecycle.State.RESUMED)`) oder beim Activity-Stopp pausieren; zudem ein Guard in `refresh()` (`if (refreshing) return` / Job-Referenz canceln) gegen überlappende Läufe; idealerweise Refresh nur bei echten Zustandsänderungen statt alle 1s.

### `app/src/main/java/com/tabslify/tabs/GmailTab.kt:204`  —  Supabase-Realtime-Channel bleibt nach Tab-Verlassen subscribed

- **Kategorie:** listener
- **Schweregrad:** low  |  **Confidence:** medium

**Beschreibung:** In `LaunchedEffect(Unit)` (Zeile 201-235): `val channel = Config.client.channel("emails-tab"); channel.postgresChangeFlow<PostgresAction>(...).launchIn(this); channel.subscribe()` (Zeile 204/205/232). Beim Verlassen des Gmail-Tabs wird zwar die LaunchedEffect-Coroutine gecancelt (Flow-Collector beendet), aber der Realtime-Channel bleibt im `Config.client`-Singleton subscribed — es gibt kein `channel.unsubscribe()`/`client.removeChannel(channel)`. Damit bleibt der Realtime-WebSocket zur Supabase für die gesamte 8h-Hintergrundsession offen (hält Netzwerkverbindung, weckt ggf. den Prozess) und der Channel wird dauerhaft in der Channel-Map des Supabase-Clients gehalten.

**Fix:** Channel-Lebenszyklus an die Komposition koppeln: in `DisposableEffect(Unit)` subscriben und in `onDispose { channel.unsubscribe() }` (bzw. `client.removeChannel(channel)`) sauber schließen.

### `app/src/main/java/com/tabslify/tabs/exploretab/ExploreLocationTracker.kt:199`  —  Geofence wird nie entfernt; kontinuierliche GPS-Schleife über die ganze Session

- **Kategorie:** service
- **Schweregrad:** low  |  **Confidence:** high

**Beschreibung:** `registerGeofence` (Zeile 171-212) registriert einen Geofence mit `Geofence.NEVER_EXPIRE` (Zeile 183) und `addGeofences(request, geofencePendingIntent(context))` (Zeile 199). Ein Grep über den gesamten Code bestätigt: `removeGeofences` existiert nirgends — weder `stop()` (Zeile 611-628) noch `onServiceDestroyed()` (Zeile 589-609) räumen den Geofence ab. Er bleibt nach dem ersten Start dauerhaft im System registriert (100 m-Radius um Config.LAT/LON). Zusätzlich läuft bei 'außer Haus' für die ganze 8h-Session eine kontinuierliche Location-Schleife: `requestLocationUpdates(locationRequestFor(currentMode), callback, Looper.getMainLooper())` (Zeile 572, Intervalle 5-120 s je nach Activity-Profil) plus `startActivityRecognitionUpdates` alle 30 s plus `ExploreWorker` alle 15 min. `stop()` räumt die Updates nicht direkt ab, sondern verlässt sich darauf, dass der asynchrone `ExploreForegroundService.onDestroy` → `onServiceDestroyed()` sie entfernt.

**Fix:** In `stop()` bzw. `onServiceDestroyed()` zusätzlich `LocationServices.getGeofencingClient(ctx).removeGeofences(geofencePendingIntent(ctx))` aufrufen (gleiche requestId/PendingIntent), und die Location-/ActivityUpdates synchron im `stop()`-Pfad entfernen statt nur über den Service-onDestroy.

### `app/src/main/java/com/tabslify/tabs/mediaplayer/MediaPlayerTab.kt:3020`  —  MediaAnalyticsManager-Sessionliste wächst unbegrenzt

- **Kategorie:** static-cache
- **Schweregrad:** low  |  **Confidence:** high

**Beschreibung:** `MediaAnalyticsManager.addSession` (Zeile 3020-3025) hängt jeden `ListenSession` ohne Cap an die JSON-Liste in den Prefs an; `getSessions()` (Zeile 3027-3035) deserialisiert bei jedem Aufruf die komplette Historie. `addSession` wird aus 10+ Stellen des MediaPlayerService (z.B. Zeile 1024, 1295, 1404, 2764, 2871, 2986) und aus `SpotifyPlaybackTracker.kt:131` aufgerufen — bei einer 8h-Hörsession mit ~4-min-Titeln entstehen ca. 120+ Einträge/Tag. Es gibt kein automatisches Trim (`rebuildSessions` wird nur bei manuellen Reset-Kommandos gerufen, Commands.kt:1699/1730). Jede Sekunde wird die wachsende Liste via der 1s-Loop (Finding 1) komplett neu aus dem JSON geparst → wachsender Arbeitsspeicherbedarf pro Aufruf (transiente Allokation der kompletten Historie) und O(n²)-Aufwand über die Tage.

**Fix:** Sessionliste begrenzen: in `addSession` älteste Einträge entfernen (z.B. > 90 Tage oder > 5000 Einträge), oder `getSessions()` auf einen Zeitraum/limit beschränken.
## Szenario 7


### `app/src/main/java/com/tabslify/tabs/authenticator/AuthenticatorTabContent.kt:700`  —  SilentCaptureScreen gibt die Kamera/DecoratedBarcodeView nie per release() frei

- **Kategorie:** camera
- **Schweregrad:** medium  |  **Confidence:** medium

**Beschreibung:** Das QR-Scanning wird pro Öffnung als neuer `DecoratedBarcodeView` in einem `AndroidView(factory = { ctx -> DecoratedBarcodeView(ctx).apply { ... decodeContinuous(...); resume() } })` erzeugt (Zeilen 700-833). Es gibt kein `DisposableEffect` mit `onDispose { barcodeView.pause(); barcodeView.release() }`. `AndroidView` entfernt beim Disposal nur die View aus der Hierarchie; `pause()` (Zeile 709) stoppt nur den Preview. Der Kamera-Treiber (Camera.open in der journeyapps/ZXing CameraManager) bleibt ohne `release()`/`closeDriver()` offen, bis die View irgendwann durch GC freigegeben wird. Jedes Öffnen des Scanners (2FA-Hinzufügen über Passwordmanagerscreen → AddEditPasswordDialog → showScanner) hält so native Kamera-Ressourcen; bei vielen Scan-Zyklen können sich Kamera-Objekte ansammeln bzw. `CameraInUseException` bei erneutem `resume()` auftreten.

**Fix:** Eine Referenz auf die DecoratedBarcodeView in ein remember-State legen und in einem `DisposableEffect(Unit) { onDispose { barcodeView.pause(); barcodeView.release() } }` um den AndroidView explizit freigeben.

### `app/src/main/java/com/tabslify/tabs/authenticator/TwoFACore.kt:64`  —  TwoFADatabase-Room-Singleton pinnt Activity-Context für die Prozesslebensdauer

- **Kategorie:** activity-context
- **Schweregrad:** medium  |  **Confidence:** high

**Beschreibung:** `getDatabase(context)` baut die Room-Datenbank mit dem rohen Context: `Room.databaseBuilder(context, TwoFADatabase::class.java, "twofa_database")` (Zeile 64). Aufgerufen wird sie u.a. mit `LocalContext.current` (= Activity) in AuthenticatorTabContent.kt:242 (`val twoFaDb = remember { TwoFADatabase.getDatabase(context) }`) und in SilentCaptureScreen (Zeile 769: `TwoFADatabase.getDatabase(context)`). Der `@Volatile INSTANCE`-Singleton hält die RoomDatabase für immer; Room speichert den übergebenen Context (Activity bzw. ContextThemeWrapper) in `mContext` und gibt ihn nie frei. PasswordDatabase in Passwordmanagercore.kt:99 macht es dagegen korrekt mit `context.applicationContext` — TwoFADatabase ist die einzige DB im Authenticator-Pfad mit Activity-Context. Bei jeder Activity-Neuerstellung (nicht durch Rotation, da `configChanges="orientation|screenSize|..."` im Manifest, aber durch Locale-/uiMode-/Theme-Wechsel, z.B. Config.setAppLanguage) bleibt die alte Activity inkl. komplettem Compose-Tree (und ggf. WebView) über den Singleton gepinnt.

**Fix:** In TwoFACore.kt Zeile 64 `context.applicationContext` verwenden: `Room.databaseBuilder(context.applicationContext, TwoFADatabase::class.java, "twofa_database")` — analog zu PasswordDatabase.getDatabase.

### `app/src/main/java/com/tabslify/tabs/authenticator/AuthenticatorTabContent.kt:178`  —  ON_RESUME-Observer re-armt den BiometricPrompt nach jedem Verwerfen — Entsperr-Loop mit neuen Cipher/BiometricPrompt/BiometricFragment pro Zyklus

- **Kategorie:** listener
- **Schweregrad:** low  |  **Confidence:** medium

**Beschreibung:** Der Lifecycle-Observer armiert bei jedem Resume erneut: `if (event == Lifecycle.Event.ON_RESUME && lockEnabled && !isAuthenticated && !shouldShowPrompt) { shouldShowPrompt = true }` (Zeile 178). Der onError-Zweig (Zeile 210) setzt bei Cancel/Timeouts nur `shouldShowPrompt = false`, lässt aber `isAuthenticated = false`. Jeder spätere ON_RESUME (Screen an/aus, Rückkehr in die App, Share-Sheet) setzt daraufhin `shouldShowPrompt = true`, und die `LaunchedEffect(shouldShowPrompt)` (Zeile 186) ruft `showBiometricPrompt(...)` auf, das in Zeilen 390-399 je Durchlauf ein neues `Cipher` (KeyStore-Operation) + `BiometricPrompt` + neue `BiometricFragment`-Transaktion erzeugt. Der Nutzer kann die Sperre über „Abbrechen" nicht dauerhaft verlassen — die Objekte sind transient (kein kumulatives Wachstum), aber der Loop hält die Entsperr-Mechanik über viele Zyklen mit ständigen Neuallokationen aktiv.

**Fix:** Nach einem Dismiss (Negative-Button/Cancel/Timeout) nicht automatisch wieder armen: z.B. `lastDismiss`-Zeitstempel merken und ON_RESUME innerhalb eines Zeitfensters ignorieren, oder Cancel/Timeout nur noch manuell über den „Entsperren"-Button erlauben statt im Observer automatisch.

### `app/src/main/java/com/tabslify/tabs/authenticator/AuthenticatorTabContent.kt:399`  —  BiometricPrompt wird bei Verlassen der Komposition nie gecancelt — Callback hält Compose-States + Activity

- **Kategorie:** misc
- **Schweregrad:** low  |  **Confidence:** medium

**Beschreibung:** `val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() { ... })` (Zeile 399) + `prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))` (Zeile 452) laufen innerhalb der `LaunchedEffect(shouldShowPrompt)` (Zeile 186) ohne jede Lebenszyklus-Anbindung: Wird AuthenticatorTab gewechselt (in PrivateTabslifyApp.kt wird der Tab per `when(selectedMenuItem)` disposed), bleibt der laufende Prompt samt `AuthenticationCallback` — der über die `onSuccess`/`onError`-Closures die Compose-`MutableState`-Objekte (`isAuthenticated`, `shouldShowPrompt`, `showError`, `errorMessage`) und die `activity` hält — im FragmentManager der Activity bis zum Prompt-Ende bzw. Activity-Ende bestehen. Ein `cancelAuthentication()` existiert nirgends. Bei wiederholtem Öffnen + Prompt-Start + Tab-Wechsel ohne Abschluss bleibt so pro Vorgang ein Callback-Objekt mit gefangenen States erhalten.

**Fix:** Den BiometricPrompt in einem remember-State ablegen und in einem `DisposableEffect(Unit) { onDispose { prompt?.cancelAuthentication() } }` beim Verlassen der Komposition canceln sowie `shouldShowPrompt = false` setzen.
## Szenario 8


### `app/src/main/java/com/tabslify/tabs/GalleryTab.kt:111`  —  Unbegrenzter thumbnailCache hält vollauflösende Video-Frames jeder je gescrollten Kachel

- **Kategorie:** bitmap
- **Schweregrad:** high  |  **Confidence:** high

**Beschreibung:** Zitat: `val thumbnailCache = remember { mutableStateMapOf<String, Bitmap>() }` (Z.111). Jedes Video, das beim langen Scrollen durch das LazyVerticalGrid komponiert wird, wird über `produceState` (Z.229-248) mit `getVideoFirstFrame(mediaItem.uri, context)` (Z.90-100) verarbeitet. Diese Funktion liefert via `retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)` einen Frame in VOLLER Videoauflösung (kein setScaledFrame/Downsampling; 1080p ≈ 8 MB, 4K ≈ 33 MB). In Z.243 wird er mit `thumbnailCache[mediaItem.uri] = bmp` dauerhaft in die SnapshotStateMap geschrieben. Die Map hat keine Eviction-Grenze, kein recycle() und kein onDispose-Cleanup; sie wächst während einer langen Scroll-Session um jeden jemals gesehenen Video-Frame (100 Videos ≈ 800 MB → OOM-Risiko). Zusätzlich wird jedes dieser Full-Res-Bitmaps noch via `AsyncImage(model = it)` (Z.250-256) an Coil übergeben, das den vollen Frame (Bitmap-Models werden nicht runterskaliert) ein zweites Mal als Coil-Memory-Cache-Eintrag hält (Doppelbelegung).

**Fix:** Frame vor dem Cachen herunterskalieren (z.B. `retriever.getScaledFrameAtTime(...)` bzw. Bitmap auf ~256-512 px skalieren), die Map durch einen begrenzten `LruCache<String, Bitmap>` mit fester MB-Grenze ersetzen oder ganz auf Coil mit DiskCache setzen. In `DisposableEffect(Unit) { onDispose { thumbnailCache.values.forEach { it.recycle() }; thumbnailCache.clear() } }` aufräumen.

### `app/src/main/java/com/tabslify/tabs/GalleryTab.kt:92`  —  MediaMetadataRetriever wird bei Exception nie freigegeben – natives Resource-Leak pro fehlerhafter Video-Kachel

- **Kategorie:** media
- **Schweregrad:** medium  |  **Confidence:** high

**Beschreibung:** Zitat: `val retriever = MediaMetadataRetriever()` (Z.92); `val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC); retriever.release()` (Z.94-95). `release()` steht nur im try-Zweig. Wirft `setDataSource` (Z.93) oder `getFrameAtTime` (Z.94) eine Exception (häufig bei Content-URIs ohne Berechtigung, beschädigten oder kurzen Videos), wird der catch-Zweig (Z.97-99) erreicht und der native MediaMetadataRetriever-Handle samt Frameressourcen nie freigegeben. Beim langen Scrollen durch viele Videos akkumuliert so jeder fehlgeschlagene Frame-Extrakt einen nativen Handle und ggf. ICodec-/Binder-Ressourcen, bis der Prozess endet.

**Fix:** Release in einem finally ausführen: `val retriever = MediaMetadataRetriever(); try { retriever.setDataSource(context, uri.toUri()); return retriever.getFrameAtTime(0, OPTION_CLOSEST_SYNC) } catch (_: Exception) { null } finally { retriever.release() }`.

### `app/src/main/java/com/tabslify/tabs/GalleryTab.kt:229`  —  produceState wird durch eigenen Cache-Write neu gestartet; IO-Dekodierung läuft nach Kachel-Dispose als Phantom-Task weiter

- **Kategorie:** coroutine
- **Schweregrad:** medium  |  **Confidence:** high

**Beschreibung:** Zitat: `val thumbnail by produceState(thumbnailCache[mediaItem.uri], mediaItem.uri) { if (value == null) { value = withContext(Dispatchers.IO) { ... saveThumbnailToCache(context, mediaItem.uri, bmp); thumbnailCache[mediaItem.uri] = bmp ... } } }` (Z.229-248). Der initialValue-Key liest die SnapshotStateMap `thumbnailCache`, und der Producer schreibt genau dieselbe Map (Z.243) → nach jedem Cache-Write wird der Key ungleich und Compose startet den Producer pro Kachel ein zweites Mal (zusätzlicher Coroutine-Job pro Video). Der Producer-Body (`getVideoFirstFrame` = blockierender MediaMetadataRetriever-Call + Disk-Write in `saveThumbnailToCache`) ist nicht kooperativ abbrechbar: Scrollt die Kachel aus dem Grid, wird der Job gecancelt, aber der laufende IO-Block läuft bis zum Ende weiter, allokiert ein temporäres Full-Res-Bitmap (~8-33 MB) und schreibt es noch in die tab-weite Map – Preloads, die nach dem Entsorgen der Kachel weiterlaufen. Bei schnellem Scrollen durch hunderte Videos stapeln sich so viele 'Geister'-IO-Tasks gleichzeitig.

**Fix:** Nicht die Map als produceState-Key verwenden (nur `mediaItem.uri` als Key, den Cache-Read nur als initialValue), im IO-Block vor jedem Schritt `currentCoroutineContext().ensureActive()` prüfen und den Map-/Disk-Write nur ausführen, wenn der Job noch aktiv ist. Alternativ die Videothumbnails ausschließlich über Coil laden, das Cancellation und Cache-Größenbegrenzung nativ behandelt.

### `app/src/main/java/com/tabslify/quiethoursnotificationhelper/Gallery.kt:290`  —  Statische galleryImages-Liste (bis 5000 Einträge) bleibt prozesslebenslang im Service-Companion

- **Kategorie:** static-cache
- **Schweregrad:** low  |  **Confidence:** high

**Beschreibung:** Zitat: `galleryImages = images` (Z.290) nach `if (images.size >= 5000) break` (Z.286). `galleryImages` ist ein prozessweites Companion-`var` von `QuietHoursNotificationService` (Deklaration QuietHoursNotificationService.kt:309 `var galleryImages: List<GalleryImage> = emptyList()`). Beim Bild-Loop über die Notification (prev/next, `showNextGalleryImage`/`showPreviousGalleryImage` Z.576-608, ausgelöst über ACTION_NEXT/PREV_GALLERY_IMAGE, Service Z.713-721) wird die komplette Liste inkl. aller Content-URIs über die gesamte Prozesslaufzeit stark gehalten; geleert wird sie nur beim Neuladen (Z.243), beim Notification-Dismiss (BroadcastReceiver.kt:111) oder in onDestroy (QuietHoursNotificationService.kt:1200). Jeder `ACTION_SHOW_GALLERY` (Service Z.709) baut die Liste synchron auf dem Main-Thread neu auf (blockierende MediaStore-Query auf dem Main-Looper → Jank/ANR beim Loopen).

**Fix:** Die Galerie-Daten nicht im Service-Companion prozessweit halten – nach Stoppen/Schließen der Galerie immer `galleryImages = emptyList()` erzwingen oder in einen kurzlebigen Scope (ViewModel/Datenklasse) verschieben. Die MediaStore-Query und die Bitmap-Dekodierung in `showGalleryImage`/`showDeleteConfirmation` auf Dispatchers.IO auslagern, statt sie im Main-Thread von onStartCommand auszuführen.

### `app/src/main/java/com/tabslify/tabs/GalleryTab.kt:238`  —  Video-Thumbnail-Disk-Cache video_thumbnails wird geschrieben, aber nie gelesen und nie geräumt (Disk-Leak)

- **Kategorie:** misc
- **Schweregrad:** low  |  **Confidence:** high

**Beschreibung:** Zitat: `saveThumbnailToCache(context, mediaItem.uri, bmp)` (Z.238). Die Funktion (OtherBucketTab.kt:1145-1163) schreibt pro Video ein JPEG in `context.cacheDir/video_thumbnails` mit Dateinamen `videoPath.hashCode().toString() + ".jpg"`. Ein Grep über GalleryTab.kt zeigt: `loadThumbnailFromCache` (OtherBucketTab.kt:1165) wird im GalleryTab nirgends aufgerufen – die geschriebenen Dateien werden nie wieder gelesen und nie geräumt. Über viele Sessions (jeder Tab-Besuch extrahiert und schreibt jedes Video erneut) wächst das Verzeichnis unbegrenzt (Disk-Leak) und jede Kachel schreibt doppelt (Disk-Write plus RAM-Bitmap).

**Fix:** Im GalleryTab zuerst `loadThumbnailFromCache` versuchen (vermeidet Re-Extraktion und Doppel-Writes) und den Disk-Cache begrenzen/leeren (z.B. Dateianzahl limitieren oder beim Tab-Exit alte Dateien löschen), oder die Disk-Persistenz im GalleryTab ganz entfernen und nur Coil (mit eigenem DiskCache) nutzen.
## Szenario 9


### `app/src/main/java/com/tabslify/tabs/exploretab/ExploreLocationTracker.kt:118`  —  Unmanaged CoroutineScope im Singleton wird nie gecancelt

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** medium

**Beschreibung:** `private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())` (Z. 118) im `object ExploreLocationTracker` wird nie abgebrochen — `scope.cancel()` existiert in der Datei nicht. Pro Szenario-Zyklus startet dieser Scope Jobs: `evaluateCurrentLocation` (Z. 265, bei jedem `start()` durch Tab-Öffnen), `logEvent` (Z. 365) und der `ingest`-Job im LocationCallback (Z. 549). Diese halten während ihrer Ausführung `ExploreRepository` (Z. 367/551, inkl. Room-DB-Singleton) und Application-Context. Die Einzeljobs sind kurzlebig, aber der Scope hat keine Lebenszyklus-Kopplung an Service oder Tab — ein künftiger Job, der eine Activity oder UI-Referenz captured, würde ohne Abbruchmöglichkeit permanent retained werden.

**Fix:** Scope an den Service-Lebenszyklus koppeln: in `onServiceStarted` neu erzeugen und in `onServiceDestroyed` `scope.cancel()` aufrufen, oder den vorhandenen `serviceScope`/`appScope` aus `Tabslify` (Application) verwenden statt einen eigenen unmanaged Scope zu erzeugen.

### `app/src/main/java/com/tabslify/tabs/exploretab/ExploreLocationTracker.kt:611`  —  stop()/onServiceDestroyed() räumen Geofence- und Standort-Listener nicht explizit ab

- **Kategorie:** receiver
- **Schweregrad:** low  |  **Confidence:** medium

**Beschreibung:** `registerGeofence` (Z. 198-199: `LocationServices.getGeofencingClient(context).addGeofences(request, geofencePendingIntent(context))`) wird bei jedem `start()` registriert, aber `removeGeofences` existiert im gesamten Repo nicht (per Grep verifiziert). `stop()` (Z. 611-628) ruft nur `appCtx.stopService(Intent(appCtx, ExploreForegroundService::class.java))` (Z. 617) und verlässt sich auf den Service-Destroy-Pfad; `onServiceDestroyed` (Z. 589-609) entfernt zwar `removeLocationUpdates(it)` (Z. 594) und `stopActivityRecognitionUpdates` (Z. 598), aber nur wenn der Service tatsächlich lief. Der Geofence-PendingIntent (`ExploreGeofenceReceiver`) bleibt damit nach jedem Stopp dauerhaft beim System registriert und hält den Receiver/App-Prozess bei jeder Transition wach. Im Szenario wird bei jedem Öffnen der Karte `ExploreLocationTracker.start(ctx)` über den ON_RESUME-Observer erneut aufgerufen (ExploreTabContent.kt Z. 137-144) — die Geofence wird per requestId zwar ersetzt, aber nie entfernt; beim Schließen des Tabs läuft der Standort-Listener (ForegroundService) unabhängig von der Karte weiter.

**Fix:** In `stop()` und `onServiceDestroyed()` explizit `LocationServices.getGeofencingClient(appCtx).removeGeofences(listOf(GEOFENCE_ID))` aufrufen und `stopActivityRecognitionUpdates`/`removeLocationUpdates` auch in `stop()` ausführen statt nur über den Service-Destroy-Pfad. Falls der Tracker beim Schließen des Karten-Tabs nicht weiterlaufen soll: `ExploreLocationTracker.stop(...)` im onDispose des Tabs aufrufen.

### `app/src/main/java/com/tabslify/tabs/exploretab/ExploreTabContent.kt:91`  —  MapView-Retention: starke Referenz in ungekeytem remember, Teardown nur via onDetach() im onDispose

- **Kategorie:** map
- **Schweregrad:** low  |  **Confidence:** low

**Beschreibung:** Die MapView wird in einem ungekeyten `remember` stark gehalten (`var mapView by remember { mutableStateOf<MapView?>(null) }`, Z. 91) und der Abbau hängt ausschließlich an `onDispose { lifecycleOwner.lifecycle.removeObserver(observer); mapView?.onDetach() }` (Z. 150-153). osmdroids `onDetach()` leert Overlays und Tile-Cache, beendet aber den internen Tile-Downloader-Executor/HandlerThread der `MapTileProviderBase` nicht garantiert; da die AndroidView weder `onRelease` noch ein explizites Teardown des Tile-Providers registriert, kann bei fehlendem Thread-Stop pro Zyklus eine Karten-Thread-Ressource samt Context-Bindung erhalten bleiben. Die Tab-Shell entfernt die Composition bei Tab-Wechsel (PrivateTabslifyApp.kt Z. 622, `else -> selectedMenuItem.content(setGesturesEnabled)`), sodass onDispose zuverlässig läuft — ein definitiver Heap-Leak ist nur bei nicht beendetem osmdroid-Downloader gegeben (ohne Library-Quelle nicht verifizierbar).

**Fix:** MapView-Teardown verstärken: im onDispose zusätzlich `mapView.tileProvider.clearTileCache()` und `mapView.overlays.clear()` aufrufen, die AndroidView um `onRelease` ergänzen und die Referenz nicht im ungekeyten `remember`-State aufbewahren, sondern direkt in der Factory-Lambda kapseln; bei häufigerem Öffnen eine wiederverwendete MapView-Instanz erwägen.

### `app/src/main/java/com/tabslify/tabs/exploretab/ExploreTabContent.kt:324`  —  osmdroid-Tile-Cache wächst unbegrenzt; pro Tab-Öffnen neue MapView ohne Cache-Limit/Cleanup

- **Kategorie:** map
- **Schweregrad:** low  |  **Confidence:** medium

**Beschreibung:** Die AndroidView-Factory erzeugt pro Tab-Öffnen eine frische osmdroid-MapView: `Configuration.getInstance().userAgentValue = context.packageName` (Z. 323) und `MapView(context).apply { setTileSource(TileSourceFactory.MAPNIK); ... }` (Z. 324-330). Es wird nirgends im Projekt eine Cache-Größe/Eviction konfiguriert (kein `Configuration.getInstance().cacheMapTileOOMHandling`, kein `setTileCache`-Limit, kein Disk-Cache-Cleanup). osmdroid persistiert heruntergeladene Tiles via `SqlTileWriter` dauerhaft im App-Verzeichnis (`osmdroid.sqlite`); beim Bewegen der Karte in neue Bereiche wächst dieser Disk-Cache über beliebig viele Öffnen/Move/Schließen-Zyklen ohne Räumung. Der In-Memory-TileCache (Default ~40 Bitmap-Tiles) wird nur über `mapView?.onDetach()` (Z. 152) im onDispose geleert; die MapView selbst wird pro Zyklus neu allokiert (Factory, Z. 307).

**Fix:** osmdroid-Cache begrenzen/kontrollieren: `Configuration.getInstance()`-Cache-Einstellungen setzen, in onDispose zusätzlich `mapView.tileProvider.clearTileCache()` aufrufen und die `osmdroid`-Tile-Verzeichnisse (bzw. `osmdroid.sqlite`) periodisch aufräumen (z. B. im ExploreWorker oder per Altersgrenze).
## Szenario 10


### `app/src/main/java/com/tabslify/core/activities/Tabslify.kt:184`  —  Rhino-Ausführung ohne Interrupt/Timeout: hängendes Script hält Context, Scope, AppBridge und IO-Thread dauerhaft

- **Kategorie:** js-engine
- **Schweregrad:** medium  |  **Confidence:** medium

**Beschreibung:** In `executeJs` (Z. 159-191) ist der normale Zyklus leak-frei (`val cx = RhinoContext.enter()` Z. 160 wird per `finally { RhinoContext.exit() }` Z. 189 balanciert, `val scope = cx.initStandardObjects()` Z. 175 wird pro Aufruf neu erzeugt und nach Return GC-fähig — kein wachsender Global-Scope). Aber es gibt keinerlei Interrupt-Fähigkeit: weder ein `ContextFactory` mit `observeInstructionCount` noch `setInstructionObserverThreshold`, und `val result = cx.evaluateString(scope, code, "remote", 1, null)` (Z. 184) läuft ohne Timeout. Ein remote-gespeichertes Script (via `fetchAndRun`, Supabase) mit Endlosschleife blockiert den Dispatchers.IO-Thread dauerhaft; `RhinoContext.exit()` wird nie erreicht, sodass RhinoContext, der komplette Standard-Scope, `val bridge = AppBridge(appContext)` (Z. 178) samt `RhinoContext.javaToJS(bridge, scope)`-Wrapper (Z. 179) und der Code-String retained bleiben. Jede weitere FCM-`script_name`-Nachricht startet über `Tabslify.serviceScope.launch { fetchAndRun(...) }` (MyFirebaseMessagingService.kt:68-73) eine weitere hängende Coroutine auf dem begrenzten IO-Pool — über viele Zyklen kumulieren belegte Threads und gehaltener Heap.

**Fix:** Einen `ContextFactory` mit überschriebenem `observeInstructionCount` verwenden und `cx.setInstructionObserverThreshold(...)` setzen; die Ausführung auf einen separaten Thread mit hartem Timeout begrenzen (`Future.get(timeout)` bzw. `cx.interrupt`) und die Exception am `catch (e: Exception)` (Z. 186) nicht verschlucken; zusätzlich in `fetchAndRun` ein Gesamt-Timeout und ein Concurrency-Limit (z. B. Semaphore) einbauen, damit hängende Ausführungen nicht unbegrenzt kumulieren.

### `app/src/main/java/com/tabslify/services/MyFirebaseMessagingService.kt:68`  —  FCM-script_name-Jobs auf nie gecanceltem serviceScope halten die Service-Instanz fest

- **Kategorie:** coroutine
- **Schweregrad:** medium  |  **Confidence:** high

**Beschreibung:** `Tabslify.serviceScope.launch { try { fetchAndRun(scriptName, applicationContext) } catch (_: Exception) {} }` (Z. 68-73): Der Zugriff auf `applicationContext` (ein Member von `ContextWrapper`) im launch-Lambda captured implizit `this@MyFirebaseMessagingService`, weil der Lambda-Receiver (`CoroutineScope`) kein `applicationContext` hat. `serviceScope` (Tabslify.kt:42-44) ist app-weit, `SupervisorJob`, und wird nie gecancelt. Jede Push-Nachricht mit `script_name` (Z. 67) pinnt damit die (vom System ggf. bereits beendete) Service-Instanz für die gesamte Dauer von Script-Download + Rhino-Ausführung (Z. 70); parallele Nachrichten stapeln beliebig viele Jobs auf dem geteilten Scope. Kombiniert mit einem hängenden Script (executeJs ohne Timeout, Tabslify.kt:184) wird die Instanz dauerhaft gehalten und die Jobs akkumulieren.

**Fix:** Vor dem `launch` den Kontext in eine lokale Variable ziehen (`val appContext = applicationContext`), sodass das Lambda nicht mehr die Service-Instanz, sondern nur noch den App-Context captured; den Job als Feld speichern und in einem `onDestroy()` der Service canceln bzw. ein Concurrency-Limit für parallele script_name-Ausführungen einbauen.

### `app/src/main/java/com/tabslify/core/activities/Tabslify.kt:178`  —  AppBridge/executeJs/fetchAndRun halten den übergebenen Context stark — latente Activity-Retention

- **Kategorie:** activity-context
- **Schweregrad:** low  |  **Confidence:** low

**Beschreibung:** `class AppBridge(private val androidContext: Context)` (Z. 143) speichert den übergebenen Context als Feld, `suspend fun fetchAndRun(scriptName: String, context: Context)` (Z. 132) und `fun executeJs(code: String, appContext: Context)` (Z. 159) akzeptieren jede Context-Instanz, und `RhinoContext.javaToJS(bridge, scope)` (Z. 179) hält die Bridge samt Context über den NativeJavaObject-Wrapper in der Scope-Property `Tabslify` (Z. 181). Der einzige produktive Aufrufer übergibt aktuell `applicationContext` (`MyFirebaseMessagingService.kt:70`) — daher besteht heute kein realer Leak. Da `fetchAndRun`/`executeJs` öffentliche Top-Level-Funktionen sind und bei hängendem Script der Scope samt Bridge dauerhaft retained bleibt (siehe js-engine-Finding), würde eine übergebene Activity dort über die gesamte (ggf. unbegrenzte) Ausführungsdauer gepinnt.

**Fix:** Intern auf den App-Kontext normalisieren: in `executeJs` bzw. `AppBridge` bzw. am Anfang von `fetchAndRun` `context.applicationContext` verwenden, oder den Parametertyp auf `Application` beschränken, sodass nie ein Activity-Kontext in den JS-Scope gelangen kann.
## Szenario 11


### `app/src/main/java/com/tabslify/tabs/audiorecordertab/AudioRecorderTab.kt:210`  —  Tab-Exit released den MediaPlayer ohne Nulling/isPlaying-Rücksetzung — freigegebene Instanz bleibt im sitzungsweiten ViewModel

- **Kategorie:** media
- **Schweregrad:** high  |  **Confidence:** high

**Beschreibung:** Zeile 210-214: `DisposableEffect(Unit) { onDispose { vm.mediaPlayer?.release() } }`. Das `AudioRecorderTabViewModel` ist per `viewModel()` Activity-scoped (Single-Activity-Shell, `PrivateTabslifyApp.kt:293`) und lebt die gesamte Sitzung. Beim Verlassen des Tabs wird nur `release()` aufgerufen — das Feld `vm.mediaPlayer` (ViewModel.kt:43 `var mediaPlayer by mutableStateOf<MediaPlayer?>(null)`) bleibt nicht-null und `isPlaying` bleibt true. Beim erneuten Öffnen läuft `LaunchedEffect(vm.isPlaying) { vm.updatePos() }` (Zeile 206) erneut; `updatePos()` (ViewModel.kt:61-70) ruft `it.isPlaying` auf der freigegebenen Instanz auf → `IllegalStateException` (App-Crash in der Composition-Coroutine). Auch `onPlayPause()` (ViewModel.kt:99-118) ruft `start()`/`pause()` auf der freigegebenen Instanz auf → `IllegalStateException`. Die tote MediaPlayer-Referenz wird über die gesamte App-Sitzung im ViewModel gehalten und der Zustand (isPlaying=true, Position) ist inkonsistent. Es ist ein Einzelobjekt (kein kumulatives Heap-Wachstum), aber ein echter Retained-Object-/Zustandsfehler mit Crash auf dem Wiederbetreten-Pfad.

**Fix:** Im onDispose `vm.onStop()` aufrufen statt nur `release()` — das stoppt und released den Player, nullt das Feld und setzt `isPlaying = false` (siehe ViewModel.kt:120-126). Alternativ in onDispose zusätzlich `vm.mediaPlayer = null` und `vm.isPlaying = false` setzen.

### `app/src/main/java/com/tabslify/tabs/audiorecordertab/AudioForegroundService.kt:179`  —  Playback-Capture: AudioRecord/MediaCodec/MediaMuxer leaken bei Exceptions außerhalb des try-Catch (z.B. encoder.start())

- **Kategorie:** media
- **Schweregrad:** medium  |  **Confidence:** medium

**Beschreibung:** `record` (AudioRecord, Zeile 145-154), `encoder` (MediaCodec, 156-166) und `muxer` (MediaMuxer, 168) sind lokale Variablen in `startPlaybackCapture`. Sie werden NUR im `SecurityException`-Catch (171-178) und im `finally` der Coroutine `runPlaybackCaptureLoop` (210-236) freigegeben. `encoder.start()` (Zeile 179) sowie MediaMuxer-Konstruktor/`configure` liegen außerhalb jeder Aufräumlogik; `onDestroy` (320-346) kennt nur `recorder` und `mediaProjection`, nicht record/encoder/muxer. Wirft `encoder.start()` (oder die Erzeugung) eine Nicht-Security-Exception, propagiert sie an `onStartCommand`'s catch → `stopSelf()`, aber der bereits gestartete `AudioRecord` (captured weiter System-Audio), der Codec und der Muxer mit offener Datei werden nie gestoppt/freigegeben — die nativen Ressourcen bleiben bis zum Prozessende belegt. Bei wiederholten fehlgeschlagenen Playback-Capture-Zyklen (z.B. Codec-/Encoder-Fehler) akkumulieren sich diese nativen Resourcen.

**Fix:** Die Instanzen als Felder halten und in `onDestroy` stoppen/freigeben, oder den gesamten Bereich ab `record = AudioRecord.Builder()...build()` (Zeile 145) in `try/finally` kapseln, sodass record/encoder/muxer bei jeder Exception freigegeben werden.

### `app/src/main/java/com/tabslify/tabs/audiorecordertab/ViewModel.kt:157`  —  onShareDirect: MediaPlayer wird bei prepare()-Exception nicht freigegeben

- **Kategorie:** media
- **Schweregrad:** low  |  **Confidence:** medium

**Beschreibung:** Zeile 155-159: `val mp = MediaPlayer(); mp.setDataSource(file.absolutePath); mp.prepare(); val dur = mp.duration.toFloat(); mp.release()`. Wirft `prepare()` (z.B. bei korrupter/unvollständiger m4a-Datei oder fehlender Datei) eine `IOException`, wird `mp.release()` übersprungen und der MediaPlayer (inkl. nativer Ressourcen) bleibt bis zum GC/Finalizer gehalten. Bei wiederholtem Teilen defekter Dateien können sich so MediaPlayer-Native-Ressourcen anhäufen.

**Fix:** `mp.release()` in einen `finally`-Block ziehen (bzw. `mp.use { ... }` verwenden), sodass die Instanz auch bei `prepare()`-Fehler freigegeben wird.
## Szenario 12


### `app/src/main/java/com/tabslify/tabs/fitnesstab/PoseAnalyzer.kt:87`  —  Pro Frame zwei RGBA-Bitmaps erzeugt, keine wird recycelt

- **Kategorie:** bitmap
- **Schweregrad:** medium  |  **Confidence:** high

**Beschreibung:** In `analyze()` (Kamera-Callback, ~30 fps über die ganze Session) werden pro Frame zwei vollflächige Bitmaps allokiert: Z.87 `val raw = imageProxy.toBitmap()` und Z.92 `bitmap = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)`, danach Z.104 `detector.detectAsync(BitmapImageBuilder(rotated).build(), timestamp)`. Weder `raw` (direkt nach dem Kopieren nicht mehr benötigt) noch die gedrehte Kopie `rotated` werden je recycelt (kein einziger `recycle()`-Aufruf in der Datei). Bei 1280x720 RGBA sind das ~3,7 MB pro Bitmap, also ~7,4 MB Allokation pro Frame und mehrere hundert MB/s bei 30 fps. Über eine 30-minütige Session sind das ~100.000 Bitmaps mit hunderten GB Gesamtallokation. Die Objekte sind zwar GC-fähig (daher kein klassischer Retained-Leak), aber der Dauer-Allokationsdruck erzeugt GC-Thrashing, spürbare Jank in `onFrame`/`PoseOverlay` und unter Speicherdruck OOM-Risiko; zusaetzlich haelt MediaPipe in LIVE_STREAM waehrend der laufenden Inferenz das MPImage (inkl. `rotated`-Bitmap) fest.

**Fix:** `raw` unmittelbar nach `Bitmap.createBitmap(raw, ...)` mit `raw.recycle()` freigeben. `rotated` nach Abschluss der Inferenz recyceln, z.B. im `setResultListener { result, input -> ... }` ueber `(input.container as? BitmapContainer)?.bitmap?.recycle()` (nur wenn der Input wirklich eine Bitmap ist). Alternativ eine wiederverwendete Bitmap/Pipeline einfuehren: einmalig einen Bitmap-Puffer der Zielgroesse anlegen und pro Frame per Canvas darin rendern statt `toBitmap()` + `createBitmap(...)` zu allokieren.
## Szenario 13


### `app/src/main/java/com/tabslify/tabs/GmailTab.kt:204`  —  Realtime-Channel 'emails-tab' wird beim Tab-Verlassen nie entfernt – WebSocket + Heartbeat/Reconnect laufen prozesslebenslang weiter

- **Kategorie:** listener
- **Schweregrad:** medium  |  **Confidence:** high

**Beschreibung:** Im einzigen Realtime-Nutzer der App wird die Subscription in LaunchedEffect(Unit) erzeugt und nie wieder freigegeben:

    LaunchedEffect(Unit) {
        reload()
        try {
            val channel = Config.client.channel("emails-tab")   // Zeile 204
            channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "emails" }
                .onEach { ... }.launchIn(this)                    // Zeile 231
            channel.subscribe()                                    // Zeile 232
        } catch (_: Exception) {}
    }

Config.client ist ein Prozess-Singleton (Config.kt:54) mit install(Realtime) ohne Config-Block. In supabase-kt 3.7.0 legt RealtimeImpl.channel() den Channel in der internen Map `_subscriptions` (topic-keyed) ab; `subscribe()` verbindet den WebSocket und der Channel bleibt SUBSCRIBED, bis removeChannel()/removeAllChannels()/close() aufgerufen wird. Ein Grep ueber den gesamten App-Code zeigt KEINEN einzigen removeChannel-/disconnect-Aufruf. Beim Verlassen des Tabs wird nur der LaunchedEffect-Scope gecancelt – das entfernt lediglich den Flow-Callback (awaitClose in postgresChangeFlow) und die PostgresChange-Config, NICHT die Channel-Subscription. Dadurch: (1) bleibt die WebSocket-Verbindung samt Heartbeat-Job, Message-Job und Reconnect-/Rejoin-Loop (RealtimeImpl.reconnect/connect/rejoinChannels) fuer die gesamte Prozesslebenszeit aktiv, da `disconnectOnNoSubscriptions=true` nie greift (subscriptions ist nie leer); (2) das komplette RealtimeSubsystem inkl. RealtimeChannelImpl bleibt ueber den Config-Singleton permanent im Speicher; (3) funktionaler Folgefehler: beim erneuten Tab-Besuch liefert client.channel("emails-tab") denselben, noch SUBSCRIBED Channel zurueck, sodass postgresChangeFlow mit 'You cannot call postgresChangeFlow after joining the channel' wirft und Echtzeit-Updates dauerhaft ausfallen.

**Fix:** Channel-Lebenszyklus explizit an die Composition koppeln: z.B. den Channel in `remember` halten und bei Dispose per `channel.removeChannel()` (bzw. `Config.client.realtime.removeChannel(channel)`) im finally/DisposableEffect entfernen, oder die Subscription komplett in eine Coroutine legen, die bei Dispose gecancelt wird und deren finally den Channel entfernt. So wird der WebSocket freigegeben, sobald der Tab verlassen wird, und beim naechsten Besuch sauber neu subscribiert.

### `app/src/main/java/com/tabslify/core/functions/ErrorInsert.kt:18`  —  errorInsert startet pro Fehler eine unbegrenzte Coroutine auf dem gemeinsamen serviceScope – Sturm aus parallelen Functions-HTTP-Calls bei längerem Supabase-Ausfall

- **Kategorie:** coroutine
- **Schweregrad:** low  |  **Confidence:** medium

**Beschreibung:** errorInsert() erzeugt bei JEDEM Aufruf ohne Begrenzung, Queue oder Coalescing eine neue Coroutine auf dem globalen Singleton-Scope:

    serviceScope.launch {                              // Zeile 18
        try {
            Config.client.functions.invoke(...)        // Zeile 20: Netzwerkaufruf, Ktor-Timeout 30s
        } catch (e: Exception) { println(...) }
    }

serviceScope (Tabslify.kt:42, Dispatchers.IO + SupervisorJob) wird von der CoroutineExceptionHandler-basierten Fehlerberichtskette gespeist (Tabslify.kt:61 uncaught-exception-Handler, Tabslify.kt:77 coroutineExceptionHandler) sowie von ~30 Stellen in Services/ViewModels/Tabs. Bei einem anhaltenden Supabase-Ausfall erzeugt jede fehlschlagende Operation (z.B. safeCall-Fehler) einen weiteren errorInsert-Aufruf, der seinerseits einen request gegen denselben unerreichbaren Endpunkt startet; waehrend des 30s-Ktor-Timeouts stapeln sich viele konkurrierend blockierte Coroutinen samt Closure-Referenzen (Fehlertext, launch-Block) auf den IO-Workern. Diese laufen nach dem Timeout ab (transient, KEIN permanenter Speicherzuwachs ueber Zyklen, keine Selbstverstaerkung, weil der Fehler im catch geschluckt wird), aber es gibt keine Obergrenze fuer parallele in-flight HTTP-Requests.

**Fix:** errorInsert mit begrenzter Nebenlaeufigkeit absichern: z.B. eigene aufrufer-entkoppelte, bounded Queue (Channel(100) + einzelner Consumer-Coroutine auf serviceScope), deduplizieren/coalescen identischer Fehler (serviceName+message+severity in kurzem Zeitfenster), oder zumindest einen Semaphore/Mutex, damit ein Fehlersturm nicht Hunderte paralleler Functions-Calls auf den Supabase-Endpunkt feuert.
## Szenario 14


### `app/src/main/java/com/tabslify/core/ui/PrivateTabslifyApp.kt:652`  —  Fullscreen-Video-CustomView wird beim Browser-Schließen nicht geräumt

- **Kategorie:** webview
- **Schweregrad:** medium  |  **Confidence:** medium

**Beschreibung:** `onShowCustomView` (Zeile 649) hängt den Video-View per `decor.addView(view, ...)` (Zeile 652) an das DecorView und speichert `customView`/`customViewCallback` nur als lokale Felder des anonymen WebChromeClient. Schließt der Nutzer den Browser, während ein Video im Fullscreen läuft, läuft nur der Pfad Zeile 847-855 (`webView.stopLoading(); webView.onPause(); webView.destroy()`), ohne `onHideCustomView()` bzw. `customViewCallback.onCustomViewHidden()` aufzurufen. Der Video-SurfaceView bleibt im DecorView hängen (hält Surface-/Video-Buffer), die Orientierung bleibt auf `SCREEN_ORIENTATION_SENSOR_LANDSCAPE` gesperrt, und bei erneutem Browser-Öffnen wird ein weiterer CustomView addiert.

**Fix:** Den WebChromeClient-CustomView-State nach außen reichen (z.B. in ein remember-Held-Objekt) und in der onDispose des WebView-Blocks prüfen: wenn customView != null, `decor.removeView(customView)` + `customViewCallback?.onCustomViewHidden()` + `activity.requestedOrientation = UNSPECIFIED`, bevor `webView.destroy()` läuft.

### `app/src/main/java/com/tabslify/core/ui/PrivateTabslifyApp.kt:763`  —  Download-Receiver überlebt Browser-Session / Leak bei nie abschließendem Download

- **Kategorie:** receiver
- **Schweregrad:** medium  |  **Confidence:** medium

**Beschreibung:** Der anonyme `BroadcastReceiver` (Zeile 763, `val receiver = object : BroadcastReceiver()`) wird pro enqueued Download am Activity-Context registriert und meldet sich NUR in seinem eigenen `onReceive` ab (Zeile 769 `ctx.unregisterReceiver(this)`). Er ist weder an die WebView noch an den `DisposableEffect(isFullScreen)`-Lifecycle gebunden: Wird der Browser geschlossen (WebView.destroy() in Zeile 852) oder erreicht ein Download nie einen Endzustand (z.B. dauerhaft PAUSED bei fehlendem Netz/WiFi), bleibt der Receiver auf der Activity registriert und hält die Closure (`dm`, `filename`, `downloadId`, äußeres `context`) fest. Über viele Öffnen/Schließen-Zyklen mit mehreren Downloads, die nie fertig werden, akkumulieren die Registrationen.

**Fix:** Receiver am Activity-Lifecycle (bzw. in der onDispose des WebView-Blocks) abmelden, sobald der Browser verlassen wird. Idealerweise den Receiver pro Download in einer map[downloadId] verwalten und bei jedem terminalen Download-Status (auch FAILED/PAUSED/removed) sowie beim WebView.onDispose abmelden.

### `app/src/main/java/com/tabslify/core/ui/PrivateTabslifyApp.kt:815`  —  Download-BroadcastReceiver doppelt registriert

- **Kategorie:** receiver
- **Schweregrad:** medium  |  **Confidence:** high

**Beschreibung:** Innerhalb von `setDownloadListener` wird derselbe anonyme `BroadcastReceiver` zweimal mit identischem Filter registriert: Zeile 809 `context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)` und Zeile 815 exakt dieselbe Zeile als Kopie. Abhängig vom Android-Registrierungsmechanismus führt das zu einem doppelten onReceive-Aufruf, bei dem der zweite `ctx.unregisterReceiver(this)` eine IllegalArgumentException wirft (und der Datei-Move danach übersprungen wird), oder zu einer hängenden zweiten Registration, die nie abgemeldet wird.

**Fix:** Den duplizierten `registerReceiver`-Block (Zeilen 815-819) komplett entfernen. Vor dem `registerReceiver` zusätzlich prüfen, ob bereits ein Receiver für diese downloadId aktiv ist, und den Receiver in die onDispose des WebView-Blocks auslagern.

### `app/src/main/java/com/tabslify/core/ui/PrivateTabslifyApp.kt:860`  —  webViewState hält zerstörte WebView + WebViewClient/WebChromeClient fest

- **Kategorie:** webview
- **Schweregrad:** low  |  **Confidence:** high

**Beschreibung:** `var webViewState by remember { mutableStateOf<WebView?>(null) }` (Zeile 393) wird nur in `update = { webViewState = it }` (Zeile 860) gesetzt, nie auf null zurückgesetzt. Verlässt der Nutzer den Fullscreen, ruft der DisposableEffect `webView.destroy()` (Zeile 852) auf, aber `webViewState` referenziert die zerstörte WebView weiter — zusammen mit den anonymen `WebViewClient`/`WebChromeClient`-Objekten, die `context` (die MainActivity) und die `isFullScreen`-State-Captures festhalten. Die Referenz bleibt bis zum nächsten Browser-Öffnen (das sie überschreibt) bzw. für die gesamte App-Session bestehen. Es akkumuliert zwar nicht, aber ein toter WebView-Objektgraph inkl. Activity-Bindung bleibt permanent im Speicher.

**Fix:** In der onDispose des WebView-Bereichs nach `webView.destroy()` zusätzlich `webViewState = null` setzen (und die BackHandler-Pfade Zeile 476/917 nur auf den Live-WebView zugreifen lassen, da Methoden auf einer destroyed WebView IllegalStateException werfen).

### `app/src/main/java/com/tabslify/tabs/RemoteDesktopTab.kt:204`  —  reuseBitmapRef hält letzten RGB_565-Bitmap über Sessions hinweg fest

- **Kategorie:** bitmap
- **Schweregrad:** low  |  **Confidence:** high

**Beschreibung:** `private val reuseBitmapRef = AtomicReference<Bitmap?>(null)` (Zeile 204) wird in `disconnect()` (Zeile 538) nie geleert/gercycled; nur `_currentFrame.value = null` (Zeile 545) wird gesetzt. Der ViewModel wird über `viewModel()` (Zeile 567) am Activity-ViewModelStore gescoped und lebt damit für die gesamte App-Session. Nach jeder Remote-Desktop-Sitzung bleibt das letzte dekodierte Fullscreen-Bitmap (RGB_565, ~2 Bytes/Pixel, z.B. 1080x2400 ≈ 5 MB nativer Heap) fest in `reuseBitmapRef` gepinnt, auch wenn der Nutzer die Sitzung längst beendet hat. Es akkumuliert nicht (nur das letzte Bitmap), aber der native Speicher wird bis App-Ende nie freigegeben.

**Fix:** In `disconnect()` zusätzlich `reuseBitmapRef.getAndSet(null)?.let { if (!it.isRecycled) it.recycle() }` aufrufen (bzw. zumindest die Referenz auf null setzen), damit das letzte Frame-Bitmap nach Session-Ende freigegeben wird.

### `app/src/main/java/com/tabslify/tabs/RemoteDesktopTab.kt:549`  —  MulticastLock doppelt freigegeben (Race beim schnellen Tab-Verlassen)

- **Kategorie:** misc
- **Schweregrad:** low  |  **Confidence:** low

**Beschreibung:** `disconnect()` (Zeile 549: `multicastLock?.release()`) und der `finally`-Block des Discovery-Coroutines (Zeile 320: `multicastLock?.release()`) geben denselben MulticastLock frei. Beim schnellen Verlassen des RemoteDesktop-Tabs während laufender Discovery kann beides den Feldwert vor dem jeweiligen `= null` lesen, sodass `release()` zweimal aufgerufen wird; bei `setReferenceCounted(true)` (Zeile 251) wirft der zweite Aufruf `RuntimeException("Under-locked MulticastLock")` und kann den Tab-Schließ-Pfad crashen. (Kein Memory-Leak, aber eine Ressourcen-Doppelfreigabe im Öffnen/Schließen-Zyklus.)

**Fix:** Das Release idempotent machen, z.B. `val lock = multicastLock; multicastLock = null; lock?.release()` in BOTH Stellen, sodass nur eine Seite den Lock tatsächlich freigibt; alternativ den Release aus dem `finally` entfernen und ausschließlich in `disconnect()` freigeben.