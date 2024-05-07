package sh.measure.android.performance

import android.system.OsConstants
import androidx.annotation.VisibleForTesting
import sh.measure.android.events.EventProcessor
import sh.measure.android.events.EventType
import sh.measure.android.executors.MeasureExecutorService
import sh.measure.android.logger.LogLevel
import sh.measure.android.logger.Logger
import sh.measure.android.utils.OsSysConfProvider
import sh.measure.android.utils.OsSysConfProviderImpl
import sh.measure.android.utils.ProcProvider
import sh.measure.android.utils.ProcProviderImpl
import sh.measure.android.utils.ProcessInfoProvider
import sh.measure.android.utils.TimeProvider
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

internal const val CPU_TRACKING_INTERVAL_MS = 3000L

internal class CpuUsageCollector(
    private val logger: Logger,
    private val eventProcessor: EventProcessor,
    private val processInfo: ProcessInfoProvider,
    private val timeProvider: TimeProvider,
    private val executorService: MeasureExecutorService,
    private val procProvider: ProcProvider = ProcProviderImpl(),
    private val osSysConfProvider: OsSysConfProvider = OsSysConfProviderImpl(),
) {
    private var prevCpuUsageData: CpuUsageData? = null

    @VisibleForTesting
    var future: Future<*>? = null

    fun register() {
        if (!processInfo.isForegroundProcess()) return
        if (future != null) return
        future = executorService.scheduleAtFixedRate(
            {
                trackCpuUsage()
            },
            0,
            CPU_TRACKING_INTERVAL_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    fun resume() {
        if (future == null) register()
    }

    fun pause() {
        future?.cancel(false)
        future = null
    }

    private fun trackCpuUsage() {
        val (utime, stime, cutime, cstime, startTime) = readStatFile() ?: return
        val numCores = osSysConfProvider.get(OsConstants._SC_NPROCESSORS_CONF).toInt()
        val clockSpeedHz = osSysConfProvider.get(OsConstants._SC_CLK_TCK)
        if (clockSpeedHz <= 0L || numCores <= 0L) return
        val uptime = timeProvider.elapsedRealtime
        val cpuUsageData = CpuUsageData(
            num_cores = numCores,
            clock_speed = clockSpeedHz,
            uptime = uptime,
            utime = utime,
            stime = stime,
            cutime = cutime,
            cstime = cstime,
            start_time = startTime,
            interval_config = CPU_TRACKING_INTERVAL_MS,
        )
        if (prevCpuUsageData?.utime == cpuUsageData.utime && prevCpuUsageData?.stime == cpuUsageData.stime) {
            return
        }
        eventProcessor.track(
            type = EventType.CPU_USAGE,
            timestamp = timeProvider.currentTimeSinceEpochInMillis,
            data = cpuUsageData,
        )
        prevCpuUsageData = cpuUsageData
    }

    private fun readStatFile(): Array<Long>? {
        val pid = processInfo.getPid()
        val file = procProvider.getStatFile(pid)
        return if (file.exists()) {
            val stat = file.readText()
            val statArray = try {
                stat.split(" ")
            } catch (e: Exception) {
                logger.log(LogLevel.Error, "Unable to parse stat file to get CPU usage")
                return null
            }
            return arrayOf(
                /* utime */
                statArray[13].toLong(),
                /* stime */
                statArray[14].toLong(),
                /* cutime */
                statArray[15].toLong(),
                /* cstime */
                statArray[16].toLong(),
                /* start_time */
                statArray[21].toLong(),
            )
        } else {
            logger.log(LogLevel.Error, "Unable to read stat file to get CPU usage")
            null
        }
    }
}
