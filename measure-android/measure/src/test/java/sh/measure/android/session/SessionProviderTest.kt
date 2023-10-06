package sh.measure.android.session

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import sh.measure.android.fakes.FakeIdProvider
import sh.measure.android.fakes.FakePidProvider
import sh.measure.android.fakes.FakeResourceFactory
import sh.measure.android.fakes.FakeTimeProvider

class SessionProviderTest {
    private lateinit var sessionProvider: SessionProvider
    private val idProvider = FakeIdProvider()
    private val timeProvider = FakeTimeProvider()
    private val resourceFactory = FakeResourceFactory()
    private val pidProvider = FakePidProvider()

    @Before
    fun setup() {
        sessionProvider = SessionProvider(
            idProvider = idProvider,
            resourceFactory = resourceFactory,
            timeProvider = timeProvider,
            pidProvider = pidProvider
        )
    }

    @Test
    fun `SessionProvider creates a session and caches it in memory`() {
        val expectedSession = Session(
            idProvider.id, timeProvider.currentTimeSinceEpochInMillis, resourceFactory.resource
        )
        // When
        sessionProvider.createSession()

        // Then
        Assert.assertEquals(sessionProvider.session, expectedSession)
    }
}