package sh.measure.android.applaunch

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import sh.measure.android.TestActivity
import sh.measure.android.events.EventType
import sh.measure.android.fakes.FakeEventProcessor
import sh.measure.android.fakes.NoopLogger
import sh.measure.android.utils.AndroidTimeProvider

internal class AppLaunchCollectorTest {

    private val logger = NoopLogger()

    @Test
    fun tracks_cold_launch() {
        val eventProcessor = FakeEventProcessor()
        coldLaunch(eventProcessor)
        Assert.assertEquals(1, eventProcessor.getTrackedEventsByType(EventType.COLD_LAUNCH).size)
    }

    @Test
    fun triggers_cold_launch_listener() {
        val eventProcessor = FakeEventProcessor()
        var invoked = false
        val coldLaunchListener = object : ColdLaunchListener {
            override fun onColdLaunch() {
                invoked = true
            }
        }
        coldLaunch(eventProcessor, coldLaunchListener = coldLaunchListener)
        assertTrue(invoked)
    }

    private fun coldLaunch(
        eventProcessor: FakeEventProcessor,
        coldLaunchListener: ColdLaunchListener = object : ColdLaunchListener {
            override fun onColdLaunch() {}
        },
        savedStateBundle: Bundle? = null,
    ) {
        ActivityScenario.launch(TestActivity::class.java, savedStateBundle).use { scenario ->
            AppLaunchCollector(
                application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application,
                logger = logger,
                eventProcessor = eventProcessor,
                timeProvider = AndroidTimeProvider(),
            ).apply {
                register()
                setColdLaunchListener(listener = coldLaunchListener)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
        }
    }

    @Test
    fun tracks_warm_launch() {
        val eventProcessor = FakeEventProcessor()
        warmLaunch(eventProcessor)
        Assert.assertEquals(1, eventProcessor.getTrackedEventsByType(EventType.WARM_LAUNCH).size)
    }

    @Test
    fun warm_launch_has_saved_state() {
        val eventProcessor = FakeEventProcessor()
        warmLaunch(eventProcessor)
        val data = eventProcessor.getTrackedEventsByType(EventType.WARM_LAUNCH)[0].data
        assertTrue(data is WarmLaunchData)
        assertTrue((data as WarmLaunchData).has_saved_state)
    }

    private fun warmLaunch(eventProcessor: FakeEventProcessor) {
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            AppLaunchCollector(
                application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application,
                logger = logger,
                eventProcessor = eventProcessor,
                timeProvider = AndroidTimeProvider(),
            ).register()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
        }
    }

    @Test
    @Ignore("Unable to reproduce a hot launch")
    fun tracks_hot_launch() {
        val eventProcessor = FakeEventProcessor()
        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            AppLaunchCollector(
                application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application,
                logger = logger,
                eventProcessor = eventProcessor,
                timeProvider = AndroidTimeProvider(),
            ).register()

            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
        }
        Assert.assertEquals(1, eventProcessor.getTrackedEventsByType(EventType.HOT_LAUNCH).size)
    }
}
