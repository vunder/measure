package sh.measure.android.appexit

import sh.measure.android.SessionManager
import sh.measure.android.events.EventProcessor
import sh.measure.android.events.EventType
import sh.measure.android.executors.MeasureExecutorService

internal class AppExitCollector(
    private val appExitProvider: AppExitProvider,
    private val ioExecutor: MeasureExecutorService,
    private val eventProcessor: EventProcessor,
    private val sessionManager: SessionManager,
) {
    fun onColdLaunch() {
        trackAppExit()
    }

    private fun trackAppExit() {
        ioExecutor.submit {
            val appExits = appExitProvider.get()
            if (appExits.isNullOrEmpty()) {
                return@submit emptyList<Triple<Int, String, AppExit>>()
            }
            val pidsToSessionsMap: Map<Int, List<String>> = sessionManager.getSessionsForPids()
            val appExitsToTrack = mapAppExitsToSession(pidsToSessionsMap, appExits)
            appExitsToTrack.forEach {
                eventProcessor.track(
                    it.third,
                    // For app exit, the time at which the app exited is more relevant
                    // than the current time.
                    it.third.app_exit_time_ms,
                    EventType.APP_EXIT,
                    sessionId = it.second,
                )
                sessionManager.updateAppExitTracked(pid = it.first)
            }
        }
    }

    private fun mapAppExitsToSession(
        pidToSessionsMap: Map<Int, List<String>>,
        appExits: Map<Int, AppExit>,
    ): List<Triple<Int, String, AppExit>> {
        val result: MutableList<Triple<Int, String, AppExit>> = ArrayList()
        for ((pid, sessionIds) in pidToSessionsMap) {
            if (appExits.containsKey(pid)) {
                val appExit = appExits[pid]
                // assuming last session for the PID to be the one that maps to app exit.
                val lastSessionId = sessionIds.last()
                result.add(Triple(pid, lastSessionId, appExit!!))
            }
        }

        return result
    }
}
