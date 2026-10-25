package io.github.jqssun.airplay

import org.junit.Assert.*
import org.junit.Test

class ReceiverEntryTest {
    private val connecting = ReceiverSessionSnapshot(sessionId = "one", connections = 1)
    private val mirror = connecting.copy(mirroring = true)
    private val disconnected = connecting.copy(connections = 0)
    private fun automatic() = ReceiverEntry(createdAutomatically = true, automaticSessionId = "one")
    private fun ReceiverEntry.screen(snapshot: ReceiverSessionSnapshot?) = screen(snapshot, true, false)

    @Test fun coldAutomaticWaitsBeforeServiceBinding() {
        assertEquals(ReceiverScreen.WAITING, automatic().screen(null))
        assertFalse(automatic().automaticEnded(null))
    }

    @Test fun automaticHandshakeNeverShowsPages() {
        assertEquals(ReceiverScreen.WAITING, automatic().screen(connecting))
        assertEquals(ReceiverScreen.MIRROR, automatic().screen(mirror))
    }

    @Test fun warmManualActivityUsesAutomaticSurfaceWithoutChangingOwnership() {
        val entry = ReceiverEntry(false).receiveAutomatic("one", connecting)
        assertFalse(entry.createdAutomatically)
        assertEquals(ReceiverScreen.WAITING, entry.screen(connecting))
        assertEquals(ReceiverScreen.MIRROR, entry.screen(mirror))
        assertEquals(ReceiverScreen.PAGES, entry.screen(disconnected))
        assertNull(entry.reconcile(disconnected).automaticSessionId)
    }

    @Test fun onlyAutomaticOwnerFinishesAfterConfirmedDisconnect() {
        assertEquals(ReceiverScreen.FINISH, automatic().screen(disconnected))
        assertEquals(ReceiverScreen.PAGES, ReceiverEntry(false).screen(disconnected))
    }

    @Test fun staleWarmIntentIsIgnored() {
        val manual = ReceiverEntry(false)
        assertEquals(manual, manual.receiveAutomatic("old", connecting))
        assertEquals(ReceiverScreen.FINISH, automatic().screen(connecting.copy(sessionId = "new")))
    }

    @Test fun staleColdIntentNeverReopensOverview() {
        assertEquals(ReceiverScreen.WAITING, automatic().screen(null))
        assertEquals(ReceiverScreen.FINISH, automatic().screen(ReceiverSessionSnapshot()))
    }

    @Test fun duplicateIntentCannotUndoManualExit() {
        val entry = automatic().exitFullscreen(mirror)
        assertEquals(entry, entry.receiveAutomatic("one", mirror))
        assertEquals(ReceiverScreen.PAGES, entry.screen(mirror))
    }

    @Test fun delayedFirstAutomaticIntentCannotUndoEarlierManualExit() {
        val entry = ReceiverEntry(false).exitFullscreen(mirror).receiveAutomatic("one", mirror)
        assertEquals(ReceiverScreen.PAGES, entry.screen(mirror))
        assertFalse(entry.createdAutomatically)
    }

    @Test fun newSessionResetsManualExit() {
        val next = connecting.copy(sessionId = "two")
        val entry = automatic().exitFullscreen(mirror).receiveAutomatic("two", next)
        assertEquals(ReceiverScreen.WAITING, entry.screen(next))
    }

    @Test fun exitBeforeBindingStillStaysWindowedForThatSession() {
        val entry = automatic().exitFullscreen(null)
        assertEquals(ReceiverScreen.PAGES, entry.screen(connecting))
        assertEquals(ReceiverScreen.PAGES, entry.screen(mirror))
    }

    @Test fun pinPrecedesAllMediaBranches() {
        for (snapshot in listOf(null, connecting, mirror, connecting.copy(videoActive = true), connecting.copy(videoPending = true), connecting.copy(audioOnly = true))) {
            assertEquals(ReceiverScreen.PIN, automatic().screen(snapshot, true, true))
        }
    }

    @Test fun dismissingPinRestoresCurrentMirroringChoice() {
        assertEquals(ReceiverScreen.MIRROR, automatic().screen(mirror, true, false))
        assertEquals(ReceiverScreen.PAGES, automatic().exitFullscreen(mirror).screen(mirror, true, false))
    }

    @Test fun videoAndAudioKeepTheirExistingScreens() {
        assertEquals(ReceiverScreen.VIDEO, automatic().screen(connecting.copy(videoActive = true)))
        assertEquals(ReceiverScreen.VIDEO, automatic().screen(connecting.copy(videoPending = true)))
        assertEquals(ReceiverScreen.PAGES, automatic().screen(connecting.copy(audioOnly = true)))
        assertEquals(ReceiverScreen.PAGES, automatic().screen(connecting.copy(videoEnded = true)))
    }

    @Test fun mirrorStoppingWithLiveAudioReturnsToAudioPage() {
        val audio = mirror.withMirrorRunning(false)
        assertEquals(1, audio.connections)
        assertTrue(audio.audioOnly)
        assertFalse(audio.mirroring)
        assertEquals(ReceiverScreen.PAGES, automatic().screen(audio))
        val preparingMirror = audio.withMirrorRunning(true)
        assertFalse(preparingMirror.audioOnly)
        assertFalse(preparingMirror.mirroring)
        assertEquals(ReceiverScreen.WAITING, automatic().screen(preparingMirror))
    }

    @Test fun autoFullscreenPreferenceCanDisableAutomaticSurface() {
        assertEquals(ReceiverScreen.PAGES, automatic().screen(null, false, false))
        assertEquals(ReceiverScreen.PAGES, automatic().screen(mirror, false, false))
        assertEquals(ReceiverScreen.VIDEO, automatic().screen(connecting.copy(videoActive = true), false, false))
    }

    @Test fun manualIdleFullscreenSurvivesInitialZeroAndEndsAfterActualSession() {
        val entry = ReceiverEntry(false).enterFullscreen(ReceiverSessionSnapshot())
        assertEquals(ReceiverScreen.MIRROR, entry.reconcile(ReceiverSessionSnapshot()).screen(ReceiverSessionSnapshot()))
        val connected = entry.reconcile(connecting)
        assertEquals(ReceiverScreen.PAGES, connected.reconcile(disconnected).screen(disconnected))
    }

    @Test fun manualIntentDoesNotConvertAutomaticOwnership() {
        val entry = automatic().receiveManual(mirror)
        assertTrue(entry.createdAutomatically)
        assertNull(entry.automaticSessionId)
        assertEquals(ReceiverScreen.PAGES, entry.screen(mirror))
        assertEquals(ReceiverScreen.PAGES, entry.screen(disconnected))
    }

    @Test fun videoDoesNotLeaveManualMirroringFullscreenBehind() {
        val entry = ReceiverEntry(false).enterFullscreen(mirror)
            .reconcile(connecting.copy(videoActive = true))
        assertFalse(entry.manualFullscreen)
        assertEquals(ReceiverScreen.PAGES, entry.screen(connecting.copy(videoEnded = true)))
    }
}
