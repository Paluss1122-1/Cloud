package com.tabslify.tabs.mediaplayer

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.tabslify.services.WhatsAppNotificationListener

object SpotifyPlaybackTracker {

    private const val SPOTIFY_PACKAGE = "com.spotify.music"
    private const val MIN_SESSION_MS = 5_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    private var sessionManager: MediaSessionManager? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    private var activeController: MediaController? = null
    private var controllerCallback: MediaController.Callback? = null

    private var currentLabel: String? = null
    private var sessionStartedAt = 0L
    private var accumulatedMs = 0L
    private var playingSinceMs: Long? = null
    private var lastState: PlaybackState? = null

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        val appContext = context.applicationContext
        MediaAnalyticsManager.init(appContext)
        val manager =
            appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                ?: return
        val componentName = ComponentName(appContext, WhatsAppNotificationListener::class.java)

        val listener =
            MediaSessionManager.OnActiveSessionsChangedListener { controllers -> attach(controllers) }

        try {
            manager.addOnActiveSessionsChangedListener(
                listener,
                componentName,
                mainHandler
            )
            attach(manager.getActiveSessions(componentName))
        } catch (_: SecurityException) {
            return
        }

        sessionManager = manager
        sessionsListener = listener
        started = true
    }

    fun stop() {
        detach()
        sessionsListener?.let { sessionManager?.removeOnActiveSessionsChangedListener(it) }
        sessionManager = null
        sessionsListener = null
        started = false
    }

    private fun attach(controllers: List<MediaController>?) {
        val spotify = controllers?.find { it.packageName == SPOTIFY_PACKAGE }
        if (spotify?.sessionToken == activeController?.sessionToken) return

        detach()
        activeController = spotify ?: return

        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                onStateChanged(state)
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                finalizeSession()
                currentLabel = labelFor(metadata)
            }

            override fun onSessionDestroyed() {
                finalizeSession()
                activeController = null
            }
        }
        activeController?.registerCallback(callback, mainHandler)
        controllerCallback = callback

        currentLabel = labelFor(activeController?.metadata)
        onStateChanged(activeController?.playbackState)
    }

    private fun detach() {
        finalizeSession()
        controllerCallback?.let { cb -> activeController?.unregisterCallback(cb) }
        controllerCallback = null
        currentLabel = null
    }

    private fun onStateChanged(state: PlaybackState?) {
        lastState = state
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val now = System.currentTimeMillis()
        if (isPlaying) {
            if (playingSinceMs == null) {
                playingSinceMs = now
                if (sessionStartedAt == 0L) sessionStartedAt = now
            }
        } else {
            playingSinceMs?.let { since -> accumulatedMs += (now - since).coerceAtLeast(0L) }
            playingSinceMs = null
        }
    }

    private fun finalizeSession() {
        val now = System.currentTimeMillis()
        playingSinceMs?.let { since -> accumulatedMs += (now - since).coerceAtLeast(0L) }
        playingSinceMs = null

        val label = currentLabel
        if (label != null && accumulatedMs >= MIN_SESSION_MS) {
            MediaAnalyticsManager.addSession(
                ListenSession(
                    label = label,
                    type = classify(lastState),
                    listenedMs = accumulatedMs,
                    startedAt = sessionStartedAt,
                    source = "spotify"
                )
            )
        }
        accumulatedMs = 0L
        sessionStartedAt = 0L
        lastState = null
    }

    private fun labelFor(metadata: MediaMetadata?): String? {
        metadata ?: return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return null
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        return if (!artist.isNullOrBlank()) "$artist - $title" else title
    }

    private fun classify(state: PlaybackState?): String {
        val hasSkipSecondsAction = state?.customActions?.any { action ->
            val name = action.name?.toString()?.lowercase() ?: ""
            val actionId = action.action.lowercase()
            val mentionsSeconds = listOf("15", "30").any { name.contains(it) || actionId.contains(it) }
            val mentionsSkip = listOf("skip", "forward", "back", "rewind").any {
                name.contains(it) || actionId.contains(it)
            }
            mentionsSeconds && mentionsSkip
        } == true
        return if (hasSkipSecondsAction) "podcast" else "music"
    }
}
