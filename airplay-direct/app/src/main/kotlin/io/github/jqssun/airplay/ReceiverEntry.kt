package io.github.jqssun.airplay

data class ReceiverSessionSnapshot(
    val sessionId: String? = null,
    val connections: Int = 0,
    val mirroring: Boolean = false,
    val audioOnly: Boolean = false,
    val videoActive: Boolean = false,
    val videoPending: Boolean = false,
    val videoEnded: Boolean = false
) {
    fun withMirrorRunning(running: Boolean) = copy(
        mirroring = running && mirroring,
        audioOnly = !running
    )
}

enum class ReceiverScreen { PAGES, WAITING, MIRROR, VIDEO, PIN, FINISH }

data class ReceiverEntry(
    val createdAutomatically: Boolean,
    val automaticSessionId: String? = null,
    val handledSessionId: String? = automaticSessionId,
    val windowedSessionId: String? = null,
    val manualFullscreen: Boolean = false,
    val manualFullscreenSessionId: String? = null
) {
    fun receiveAutomatic(id: String, snapshot: ReceiverSessionSnapshot?): ReceiverEntry {
        if (id == handledSessionId || snapshot != null && snapshot.sessionId != id) return this
        return copy(automaticSessionId = id, handledSessionId = id, windowedSessionId = windowedSessionId?.takeIf { it == id }, manualFullscreen = false)
    }

    fun receiveManual(snapshot: ReceiverSessionSnapshot?): ReceiverEntry = copy(
        automaticSessionId = null,
        windowedSessionId = snapshot?.sessionId,
        manualFullscreen = false
    )

    fun exitFullscreen(snapshot: ReceiverSessionSnapshot?): ReceiverEntry = copy(
        windowedSessionId = snapshot?.sessionId ?: automaticSessionId,
        manualFullscreen = false
    )

    fun automaticEnded(snapshot: ReceiverSessionSnapshot?): Boolean =
        automaticSessionId != null && snapshot != null &&
            (snapshot.sessionId != automaticSessionId || snapshot.connections == 0)

    fun reconcile(snapshot: ReceiverSessionSnapshot?): ReceiverEntry {
        if (automaticEnded(snapshot) && !createdAutomatically) return copy(automaticSessionId = null, manualFullscreen = false)
        if (manualFullscreen && snapshot != null) {
            if (snapshot.videoActive || snapshot.videoPending ||
                manualFullscreenSessionId != null && (snapshot.sessionId != manualFullscreenSessionId || snapshot.connections == 0)) {
                return copy(manualFullscreen = false, manualFullscreenSessionId = null)
            }
            if (snapshot.connections > 0 && manualFullscreenSessionId == null) return copy(manualFullscreenSessionId = snapshot.sessionId)
        }
        return this
    }

    fun enterFullscreen(snapshot: ReceiverSessionSnapshot?): ReceiverEntry = copy(
        manualFullscreen = true,
        manualFullscreenSessionId = snapshot?.sessionId?.takeIf { snapshot.connections > 0 }
    )

    fun screen(snapshot: ReceiverSessionSnapshot?, autoFullscreen: Boolean, pinVisible: Boolean): ReceiverScreen {
        if (pinVisible) return ReceiverScreen.PIN
        if (automaticEnded(snapshot)) return if (createdAutomatically) ReceiverScreen.FINISH else ReceiverScreen.PAGES
        if (snapshot?.videoActive == true || snapshot?.videoPending == true) return ReceiverScreen.VIDEO
        if (manualFullscreen) return ReceiverScreen.MIRROR
        if (snapshot?.videoEnded == true) return ReceiverScreen.PAGES
        val sessionId = snapshot?.sessionId ?: automaticSessionId
        if (sessionId != null && windowedSessionId == sessionId) return ReceiverScreen.PAGES
        if (snapshot?.mirroring == true && snapshot.connections > 0 && autoFullscreen) return ReceiverScreen.MIRROR
        if (automaticSessionId != null && autoFullscreen && snapshot?.audioOnly != true) return ReceiverScreen.WAITING
        return ReceiverScreen.PAGES
    }

    companion object {
        const val EXTRA_AUTO_CONNECT = "io.github.jqssun.airplay.cleanhome.AUTO_CONNECT"
        const val EXTRA_SESSION_ID = "io.github.jqssun.airplay.cleanhome.SESSION_ID"
    }
}
