package sh.measure.android.storage

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import sh.measure.android.events.EventType
import sh.measure.android.exporter.AttachmentPacket
import sh.measure.android.exporter.EventPacket
import sh.measure.android.fakes.NoopLogger

/**
 * A robolectric integration test for the database implementation. This test creates a real
 * sqlite database.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Config.OLDEST_SDK])
class DatabaseTest {
    private val database =
        DatabaseImpl(InstrumentationRegistry.getInstrumentation().context, NoopLogger())

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `database is created successfully`() {
        val db = database.writableDatabase

        // Sqlite master table contains metadata about all tables in the database
        // with the name of the table in the 'name' column

        // verify events table has been created
        db.query("sqlite_master", null, "type = ?", arrayOf("table"), null, null, null).use {
            it.moveToFirst()
            // first table is android_metadata, skip it.
            it.moveToNext()
            assertEquals(SessionsTable.TABLE_NAME, it.getString(it.getColumnIndex("name")))
            it.moveToNext()
            assertEquals(EventTable.TABLE_NAME, it.getString(it.getColumnIndex("name")))
            it.moveToNext()
            assertEquals(AttachmentTable.TABLE_NAME, it.getString(it.getColumnIndex("name")))
            it.moveToNext()
            assertEquals(EventsBatchTable.TABLE_NAME, it.getString(it.getColumnIndex("name")))
            it.moveToNext()
            assertEquals(
                UserDefinedAttributesTable.TABLE_NAME,
                it.getString(it.getColumnIndex("name")),
            )
        }
    }

    @Test
    fun `inserts event with attachments successfully`() {
        val db = database.writableDatabase

        val attachmentEntity = AttachmentEntity(
            id = "attachment-id",
            type = "test",
            name = "a.txt",
            path = "test-path",
        )
        val event = EventEntity(
            id = "event-id",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = listOf(attachmentEntity),
            serializedAttributes = null,
            serializedAttachments = null,
            attachmentsSize = 0,
            serializedUserDefAttributes = null,
        )

        database.insertSession("session-id-1", 123, 500, true)
        val result = database.insertEvent(event)

        assertTrue(result)
        queryAllEvents(db).use {
            it.moveToFirst()
            assertEventInCursor(event, it)
        }
        queryAttachmentsForEvent(db, event.id).use {
            it.moveToFirst()
            assertAttachmentInCursor(attachmentEntity, event, it)
        }
    }

    @Test
    fun `inserts event without attachments successfully`() {
        val db = database.writableDatabase

        val event = EventEntity(
            id = "event-id",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = emptyList(),
            serializedAttributes = null,
            serializedAttachments = null,
            attachmentsSize = 500,
            serializedUserDefAttributes = null,
        )

        database.insertSession("session-id-1", 123, 500, true)
        val result = database.insertEvent(event)

        assertTrue(result)
        queryAllEvents(db).use {
            it.moveToFirst()
            assertEventInCursor(event, it)
        }
    }

    @Test
    fun `returns false when event insertion fails`() {
        val event = EventEntity(
            id = "event-id",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = emptyList(),
            serializedAttributes = null,
            serializedAttachments = null,
            attachmentsSize = 500,
            serializedUserDefAttributes = null,
        )

        database.insertSession("session-id-1", 123, 500, true)
        database.insertEvent(event)
        // attempt to insert a event with same ID twice, resulting in a failure
        val result = database.insertEvent(event)
        assertEquals(false, result)
        queryAllEvents(database.writableDatabase).use {
            assertEquals(1, it.count)
        }
    }

    @Test
    fun `returns false when event insertion fails due to attachment insertion failure`() {
        val event = EventEntity(
            id = "event-id",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "987",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = listOf(
                AttachmentEntity(
                    id = "attachment-id",
                    type = "test",
                    name = "a.txt",
                    path = "test-path",
                ),
                // insert a attachment with same ID twice, resulting in a failure
                AttachmentEntity(
                    id = "attachment-id",
                    type = "test",
                    name = "a.txt",
                    path = "test-path",
                ),
            ),
            serializedAttributes = null,
            serializedAttachments = null,
            attachmentsSize = 500,
            serializedUserDefAttributes = null,
        )

        val result = database.insertEvent(event)
        assertEquals(false, result)
        queryAllEvents(database.writableDatabase).use {
            assertEquals(0, it.count)
        }
    }

    @Test
    fun `inserts batched events successfully and returns true`() {
        val event1 = EventEntity(
            id = "event-id-1",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = emptyList(),
            serializedAttributes = null,
            attachmentsSize = 500,
            serializedUserDefAttributes = null,
        )

        val event2 = EventEntity(
            id = "event-id-2",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = emptyList(),
            serializedAttributes = null,
            attachmentsSize = 200,
            serializedUserDefAttributes = null,
        )

        database.insertSession("session-id-1", 123, 500, true)
        database.insertEvent(event1)
        database.insertEvent(event2)
        val result = database.insertBatch(listOf(event1.id, event2.id), "batch-id", 1234567890L)
        assertEquals(true, result)

        queryAllBatchedEvents().use {
            assertEquals(2, it.count)
            it.moveToFirst()
            assertBatchedEventInCursor(event1.id, "batch-id", it)
            it.moveToNext()
            assertBatchedEventInCursor(event2.id, "batch-id", it)
        }
    }

    @Test
    fun `does not insert batched events and returns false if insertion fails`() {
        // attempt to insert a event with same ID twice, resulting in a failure
        val result = database.insertBatch(
            listOf("valid-id", "event-id", "event-id"),
            "batch-id",
            987654321L,
        )
        queryAllBatchedEvents().use {
            assertEquals(0, it.count)
        }
        assertEquals(false, result)
    }

    @Test
    fun `returns event IDs to batch, but discards already batched events`() {
        val event1 = EventEntity(
            id = "event-id-1",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "987",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = emptyList(),
            serializedAttributes = null,
            attachmentsSize = 500,
            serializedUserDefAttributes = null,
        )

        val event2 = EventEntity(
            id = "event-id-2",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "987",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = emptyList(),
            serializedAttributes = null,
            attachmentsSize = 200,
            serializedUserDefAttributes = null,
        )

        val batchedEvent = EventEntity(
            id = "event-id-3",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "987",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = emptyList(),
            serializedAttributes = null,
            attachmentsSize = 200,
            serializedUserDefAttributes = null,
        )

        database.insertSession("987", 123, 500, true)
        database.insertEvent(event1)
        database.insertEvent(event2)
        database.insertEvent(batchedEvent)
        val result = database.insertBatch(listOf(batchedEvent.id), "batch-id", 987654321L)
        assertEquals(true, result)

        val eventsToBatch = database.getUnBatchedEventsWithAttachmentSize(100)
        assertEquals(2, eventsToBatch.size)
    }

    @Test
    fun `returns event IDs to batch, but discards events from session that does not need reporting`() {
        val event1 = EventEntity(
            id = "event-id-1",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = emptyList(),
            serializedAttributes = null,
            attachmentsSize = 500,
            serializedUserDefAttributes = null,
        )

        val event2 = EventEntity(
            id = "event-id-2",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = emptyList(),
            serializedAttributes = null,
            attachmentsSize = 200,
            serializedUserDefAttributes = null,
        )

        val event3 = EventEntity(
            id = "event-id-3",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-2",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = emptyList(),
            serializedAttributes = null,
            attachmentsSize = 200,
            serializedUserDefAttributes = null,
        )

        database.insertSession("session-id-1", 123, 500, true)
        database.insertSession("session-id-2", 123, 500, false)
        database.insertEvent(event1)
        database.insertEvent(event2)
        database.insertEvent(event3)

        val eventsToBatch = database.getUnBatchedEventsWithAttachmentSize(100)
        assertEquals(2, eventsToBatch.size)
    }

    @Test
    fun `given a session ID, returns event IDs to batch`() {
        val event1 = EventEntity(
            id = "event-id-1",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = emptyList(),
            serializedAttributes = null,
            attachmentsSize = 500,
            serializedUserDefAttributes = null,
        )

        val event2 = EventEntity(
            id = "event-id-2",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = emptyList(),
            serializedAttributes = null,
            attachmentsSize = 200,
            serializedUserDefAttributes = null,
        )

        val event3 = EventEntity(
            id = "event-id-3",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-2",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = emptyList(),
            serializedAttributes = null,
            attachmentsSize = 200,
            serializedUserDefAttributes = null,
        )

        database.insertSession("session-id-1", 123, 500, true)
        database.insertSession("session-id-2", 123, 500, true)
        database.insertEvent(event1)
        database.insertEvent(event2)
        database.insertEvent(event3)

        val eventsToBatch =
            database.getUnBatchedEventsWithAttachmentSize(100, sessionId = "session-id-1")
        assertEquals(2, eventsToBatch.size)
    }

    @Test
    fun `returns all allowed events for export for all sessions, even if session is not marked for export`() {
        val hotLaunchEvent = EventEntity(
            id = "event-id-1",
            type = EventType.HOT_LAUNCH,
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = emptyList(),
            serializedAttributes = null,
            attachmentsSize = 500,
            serializedUserDefAttributes = null,
        )

        val coldLaunchEvent = EventEntity(
            id = "event-id-2",
            type = EventType.COLD_LAUNCH,
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = emptyList(),
            serializedAttributes = null,
            attachmentsSize = 200,
            serializedUserDefAttributes = null,
        )

        val warmLaunchEvent = EventEntity(
            id = "event-id-3",
            type = EventType.WARM_LAUNCH,
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-2",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = emptyList(),
            serializedAttributes = null,
            attachmentsSize = 200,
            serializedUserDefAttributes = null,
        )

        val nonLaunchEvent = EventEntity(
            id = "event-id-3",
            type = EventType.CLICK,
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = emptyList(),
            serializedAttributes = null,
            attachmentsSize = 200,
            serializedUserDefAttributes = null,
        )

        database.insertSession("session-id-1", 123, 500, false)
        database.insertSession("session-id-2", 123, 500, false)
        database.insertEvent(hotLaunchEvent)
        database.insertEvent(coldLaunchEvent)
        database.insertEvent(warmLaunchEvent)
        database.insertEvent(nonLaunchEvent)

        val eventsToBatch = database.getUnBatchedEventsWithAttachmentSize(
            100,
            // allow all launch event types
            eventTypeExportAllowList = listOf(
                EventType.COLD_LAUNCH,
                EventType.HOT_LAUNCH,
                EventType.WARM_LAUNCH,
            ),
        )
        assertEquals(3, eventsToBatch.size)
    }

    @Test
    fun `returns event packets for given event IDs`() {
        val event1 = EventEntity(
            id = "event-id-1",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = null,
            serializedAttributes = "attributes",
            serializedAttachments = "attachments",
            attachmentsSize = 0,
            serializedUserDefAttributes = null,
        )

        val event2 = EventEntity(
            id = "event-id-2",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            serializedData = "data",
            attachmentEntities = null,
            serializedAttributes = "attributes",
            serializedAttachments = "attachments",
            attachmentsSize = 0,
            serializedUserDefAttributes = null,
        )

        database.insertSession("session-id-1", 123, 500, true)
        database.insertEvent(event1)
        database.insertEvent(event2)

        val eventPackets = database.getEventPackets(listOf(event1.id, event2.id))
        assertEquals(2, eventPackets.size)
        assertEventPacket(event1, eventPackets[0])
        assertEventPacket(event2, eventPackets[1])
    }

    @Test
    fun `returns empty attachment packets if no events contain attachments`() {
        val event1 = EventEntity(
            id = "event-id-1",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = null,
            serializedAttributes = "attributes",
            serializedAttachments = null,
            attachmentsSize = 0,
            serializedUserDefAttributes = null,
        )

        val event2 = EventEntity(
            id = "event-id-2",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            serializedData = "data",
            attachmentEntities = null,
            serializedAttributes = "attributes",
            serializedAttachments = null,
            attachmentsSize = 0,
            serializedUserDefAttributes = null,
        )

        database.insertSession("session-id-1", 123, 500, true)
        database.insertEvent(event1)
        database.insertEvent(event2)

        val attachmentPackets = database.getAttachmentPackets(listOf(event1.id, event2.id))
        assertEquals(0, attachmentPackets.size)
    }

    @Test
    fun `returns attachment packets when events contain attachments`() {
        val attachment1 = AttachmentEntity(
            id = "attachment-id-1",
            type = "test",
            name = "a.txt",
            path = "test-path",
        )

        val attachment2 = AttachmentEntity(
            id = "attachment-id-2",
            type = "test",
            name = "b.txt",
            path = "test-path",
        )

        val event1 = EventEntity(
            id = "event-id-1",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = listOf(attachment1),
            serializedAttributes = "attributes",
            serializedAttachments = null,
            attachmentsSize = 100,
            serializedUserDefAttributes = null,
        )

        val event2 = EventEntity(
            id = "event-id-2",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            serializedData = "data",
            attachmentEntities = listOf(attachment2),
            serializedAttributes = "attributes",
            serializedAttachments = null,
            attachmentsSize = 200,
            serializedUserDefAttributes = null,
        )

        database.insertSession("session-id-1", 123, 500, true)
        database.insertEvent(event1)
        database.insertEvent(event2)

        val attachmentPackets = database.getAttachmentPackets(listOf(event1.id, event2.id))
        assertEquals(2, attachmentPackets.size)
        assertAttachmentPacket(attachment1, attachmentPackets[0])
        assertAttachmentPacket(attachment2, attachmentPackets[1])
    }

    @Test
    fun `returns all batches and it's event IDs`() {
        val event1 = EventEntity(
            id = "event-id-1",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = null,
            serializedAttributes = "attributes",
            serializedAttachments = null,
            attachmentsSize = 100,
            serializedUserDefAttributes = null,
        )

        val event2 = EventEntity(
            id = "event-id-2",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            serializedData = "data",
            attachmentEntities = null,
            serializedAttributes = "attributes",
            serializedAttachments = null,
            attachmentsSize = 200,
            serializedUserDefAttributes = null,
        )

        database.insertSession("session-id-1", 123, 500, true)
        database.insertEvent(event1)
        database.insertEvent(event2)
        database.insertBatch(listOf(event1.id, event2.id), "batch-id-1", 1234567890L)

        assertEquals(1, database.getBatches(2).size)
        assertEquals(2, database.getBatches(2)["batch-id-1"]!!.size)
    }

    @Test
    fun `returns attachment packets for a given event ID`() {
        val attachment = AttachmentEntity(
            id = "attachment-id",
            type = "test",
            name = "a.txt",
            path = "test-path",
        )

        val event = EventEntity(
            id = "event-id",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = listOf(attachment),
            serializedAttributes = "attributes",
            serializedAttachments = null,
            attachmentsSize = 100,
            serializedUserDefAttributes = null,
        )

        database.insertSession("session-id-1", 123, 500, true)
        database.insertEvent(event)

        val attachmentPackets = database.getAttachmentPacket(event.id)
        assertEquals(1, attachmentPackets.size)
        assertAttachmentPacket(attachment, attachmentPackets[0])
    }

    @Test
    fun `deletes events with given event IDs`() {
        val event1 = EventEntity(
            id = "event-id-1",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = null,
            serializedAttributes = "attributes",
            serializedAttachments = null,
            attachmentsSize = 100,
            serializedUserDefAttributes = null,
        )

        val event2 = EventEntity(
            id = "event-id-2",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            serializedData = "data",
            attachmentEntities = null,
            serializedAttributes = "attributes",
            serializedAttachments = null,
            attachmentsSize = 200,
            serializedUserDefAttributes = null,
        )

        database.insertSession("session-id-1", 123, 500, true)
        database.insertEvent(event1)
        database.insertEvent(event2)

        val eventIds = listOf(event1.id, event2.id)
        database.deleteEvents(eventIds)

        queryAllEvents(database.writableDatabase).use {
            assertEquals(0, it.count)
        }
    }

    @Test
    fun `returns all sessions with untracked app exits from sessions table`() {
        database.insertSession("session-id-1", 123, 500, false)
        database.insertSession("session-id-1.1", 123, 500, false)
        database.insertSession("session-id-2", 987, 700, false)
        database.insertSession("session-id-2.2", 987, 700, false)
        database.insertSession("session-with-tracked-app-exit", 9000, 900, false)

        database.updateAppExitTracked(9000)
        val sessions = database.getSessionsWithUntrackedAppExit()

        assertEquals(2, sessions.size)
        assertEquals(2, sessions[123]!!.size)
        assertEquals(2, sessions[987]!!.size)
    }

    @Test
    fun `getOldestSession returns oldest session`() {
        database.insertSession("session-id-1", 123, 500, false)
        database.insertSession("session-id-2", 123, 700, false)
        database.insertSession("session-id-3", 123, 900, false)

        val sessionId = database.getOldestSession()
        assertEquals("session-id-1", sessionId)
    }

    @Test
    fun `getOldestSession returns null when no session exists`() {
        val sessionId = database.getOldestSession()
        assertNull(sessionId)
    }

    @Test
    fun `inserts a new session successfully`() {
        val sessionId = "session-id"
        val pid = 123
        database.insertSession(sessionId, pid, 500, true)

        val db = database.writableDatabase
        db.query(
            SessionsTable.TABLE_NAME,
            null,
            "${SessionsTable.COL_SESSION_ID} = ?",
            arrayOf(sessionId),
            null,
            null,
            null,
        ).use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals(sessionId, it.getString(it.getColumnIndex(SessionsTable.COL_SESSION_ID)))
            assertEquals(pid, it.getInt(it.getColumnIndex(SessionsTable.COL_PID)))
            assertEquals(true, it.getInt(it.getColumnIndex(SessionsTable.COL_NEEDS_REPORTING)) == 1)
        }
    }

    @Test
    fun `sets app exit as tracked in all sessions for a given pid`() {
        val sessionId1 = "session-id-1"
        val sessionId2 = "session-id-2"
        val sessionId3 = "session-id-3"
        val pid = 123
        val untrackedAppExitPid = 9000
        database.insertSession(sessionId1, pid, 500, false)
        database.insertSession(sessionId2, pid, 700, false)
        database.insertSession(sessionId3, untrackedAppExitPid, 900, false)

        database.updateAppExitTracked(pid)

        val db = database.writableDatabase
        db.query(
            SessionsTable.TABLE_NAME,
            null,
            "${SessionsTable.COL_APP_EXIT_TRACKED} = ?",
            arrayOf("1"),
            null,
            null,
            null,
        ).use {
            assertEquals(2, it.count)
            it.moveToFirst()
            assertEquals(sessionId1, it.getString(it.getColumnIndex(SessionsTable.COL_SESSION_ID)))
            it.moveToNext()
            assertEquals(sessionId2, it.getString(it.getColumnIndex(SessionsTable.COL_SESSION_ID)))
        }
    }

    @Test
    fun `marks a session as crashed and sets needs reporting`() {
        val sessionId = "session-id"
        val pid = 123
        database.insertSession(sessionId, pid, 500, false)

        database.markCrashedSession(sessionId)

        val db = database.readableDatabase
        db.query(
            SessionsTable.TABLE_NAME,
            null,
            "${SessionsTable.COL_SESSION_ID} = ?",
            arrayOf(sessionId),
            null,
            null,
            null,
        ).use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals(1, it.getInt(it.getColumnIndex(SessionsTable.COL_CRASHED)))
            assertEquals(1, it.getInt(it.getColumnIndex(SessionsTable.COL_NEEDS_REPORTING)))
        }
    }

    @Test
    fun `marks multiple sessions as crashed and sets needs reporting`() {
        val sessionId1 = "session-id-1"
        database.insertSession(sessionId1, 100, 500, false)
        val sessionId2 = "session-id-2"
        database.insertSession(sessionId2, 101, 600, false)

        database.markCrashedSessions(listOf(sessionId1, sessionId2))

        val db = database.readableDatabase
        // query all session ids from db
        db.query(
            SessionsTable.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            null,
        ).use {
            assertEquals(2, it.count)
            it.moveToFirst()
            assertEquals(1, it.getInt(it.getColumnIndex(SessionsTable.COL_CRASHED)))
            assertEquals(1, it.getInt(it.getColumnIndex(SessionsTable.COL_NEEDS_REPORTING)))
            it.moveToNext()
            assertEquals(1, it.getInt(it.getColumnIndex(SessionsTable.COL_CRASHED)))
            assertEquals(1, it.getInt(it.getColumnIndex(SessionsTable.COL_NEEDS_REPORTING)))
        }
    }

    @Test
    fun `returns session Ids that need reporting`() {
        database.insertSession("session-id-1", 123, 500, true)
        database.insertSession("session-id-2", 123, 500, false)
        val sessions = database.getSessionIds(
            needReporting = true,
            filterSessionIds = emptyList(),
            maxCount = 5,
        )
        assertEquals(1, sessions.size)
    }

    @Test
    fun `returns session Ids that need reporting, but filters given session IDs`() {
        database.insertSession("session-id-1", 123, 500, true)
        database.insertSession("session-id-2", 123, 500, true)
        val sessions = database.getSessionIds(
            needReporting = true,
            filterSessionIds = listOf("session-id-2"),
            maxCount = 5,
        )
        assertEquals(1, sessions.size)
    }

    @Test
    fun `returns session Ids that need reporting, and respects max count`() {
        database.insertSession("session-id-1", 123, 500, true)
        database.insertSession("session-id-2", 123, 500, true)
        val sessions = database.getSessionIds(
            needReporting = true,
            filterSessionIds = emptyList(),
            maxCount = 1,
        )
        assertEquals(1, sessions.size)
    }

    @Test
    fun `deletes sessions with given session IDs`() {
        val sessionId1 = "session-id-1"
        val sessionId2 = "session-id-2"
        database.insertSession(sessionId1, 123, 500, true)
        database.insertSession(sessionId2, 123, 500, true)

        database.deleteSessions(listOf(sessionId1, sessionId2))

        val db = database.writableDatabase
        db.query(
            SessionsTable.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            null,
        ).use {
            assertEquals(0, it.count)
        }
    }

    @Test
    fun `deleting a session also deletes events for the session`() {
        val sessionId1 = "session-id-1"
        database.insertSession(sessionId1, 123, 500, true)
        val event1 = EventEntity(
            id = "event-id-1",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = sessionId1,
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = null,
            serializedAttributes = "attributes",
            serializedAttachments = null,
            attachmentsSize = 100,
            serializedUserDefAttributes = null,
        )
        val eventInsertionSuccess = database.insertEvent(event1)
        assertEquals(true, eventInsertionSuccess)

        database.deleteSessions(listOf(sessionId1))

        val db = database.writableDatabase
        db.query(
            SessionsTable.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            null,
        ).use {
            assertEquals(0, it.count)
        }

        db.query(
            EventTable.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            null,
        ).use {
            assertEquals(0, it.count)
        }
    }

    @Test
    fun `returns all event Ids for given session Ids`() {
        val event = EventEntity(
            id = "event-id",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = emptyList(),
            serializedAttributes = null,
            serializedAttachments = null,
            attachmentsSize = 0,
            serializedUserDefAttributes = null,
        )
        database.insertSession("session-id-1", 123, 500, true)
        database.insertEvent(event)

        val events = database.getEventsForSessions(listOf("session-id-1"))
        assertEquals(1, events.size)
    }

    @Test
    fun `returns all attachment Ids for given event Ids`() {
        val attachment1 = AttachmentEntity(
            id = "attachment-id-1",
            type = "test",
            name = "a.txt",
            path = "test-path",
        )
        val attachment2 = AttachmentEntity(
            id = "attachment-id-2",
            type = "test",
            name = "a.txt",
            path = "test-path",
        )

        val event = EventEntity(
            id = "event-id",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = listOf(attachment1, attachment2),
            serializedAttributes = null,
            serializedAttachments = null,
            attachmentsSize = 0,
            serializedUserDefAttributes = null,
        )

        database.insertSession("session-id-1", 123, 500, true)
        database.insertEvent(event)

        val attachments = database.getAttachmentsForEvents(listOf("event-id"))
        assertEquals(2, attachments.size)
    }

    @Test
    fun `given events exist in db, returns count of all events in events table`() {
        val event1 = EventEntity(
            id = "event-id-1",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-1",
            userTriggered = false,
            filePath = "test-file-path",
            attachmentEntities = null,
            serializedAttributes = "attributes",
            serializedAttachments = null,
            attachmentsSize = 100,
            serializedUserDefAttributes = null,
        )

        val event2 = EventEntity(
            id = "event-id-2",
            type = "test",
            timestamp = "2024-03-18T12:50:12.62600000Z",
            sessionId = "session-id-2",
            userTriggered = false,
            serializedData = "data",
            attachmentEntities = null,
            serializedAttributes = "attributes",
            serializedAttachments = null,
            attachmentsSize = 200,
            serializedUserDefAttributes = null,
        )

        database.insertSession("session-id-1", 123, 500, true)
        database.insertSession("session-id-2", 123, 500, true)
        database.insertEvent(event1)
        database.insertEvent(event2)

        val count = database.getEventsCount()
        assertEquals(2, count)
    }

    @Test
    fun `given no events exist in db, returns 0`() {
        val count = database.getEventsCount()
        assertEquals(0, count)
    }

    private fun assertAttachmentPacket(
        attachment: AttachmentEntity,
        attachmentPacket: AttachmentPacket,
    ) {
        assertEquals(attachment.id, attachmentPacket.id)
        assertEquals(attachment.path, attachmentPacket.filePath)
    }

    private fun queryAllEvents(db: SQLiteDatabase): Cursor {
        return db.query(
            EventTable.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            null,
        )
    }

    private fun queryAllBatchedEvents(): Cursor {
        val db = database.writableDatabase
        return db.query(
            EventsBatchTable.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            null,
        )
    }

    private fun queryAttachmentsForEvent(db: SQLiteDatabase, eventId: String): Cursor {
        return db.query(
            AttachmentTable.TABLE_NAME,
            null,
            "${AttachmentTable.COL_EVENT_ID} = ?",
            arrayOf(eventId),
            null,
            null,
            null,
        )
    }

    /**
     * Asserts that the event in the cursor matches the expected event.
     *
     * @param expectedEvent The expected event.
     * @param cursor The cursor to assert.
     */
    private fun assertEventInCursor(expectedEvent: EventEntity, cursor: Cursor) {
        assertEquals(expectedEvent.id, cursor.getString(cursor.getColumnIndex(EventTable.COL_ID)))
        assertEquals(
            expectedEvent.type,
            cursor.getString(cursor.getColumnIndex(EventTable.COL_TYPE)),
        )
        assertEquals(
            expectedEvent.timestamp,
            cursor.getString(cursor.getColumnIndex(EventTable.COL_TIMESTAMP)),
        )
        assertEquals(
            expectedEvent.sessionId,
            cursor.getString(cursor.getColumnIndex(EventTable.COL_SESSION_ID)),
        )
        assertEquals(
            expectedEvent.serializedData,
            cursor.getString(cursor.getColumnIndex(EventTable.COL_DATA_SERIALIZED)),
        )
        assertEquals(
            expectedEvent.filePath,
            cursor.getString(cursor.getColumnIndex(EventTable.COL_DATA_FILE_PATH)),
        )
        assertEquals(
            expectedEvent.serializedAttributes,
            cursor.getString(cursor.getColumnIndex(EventTable.COL_ATTRIBUTES)),
        )
        assertEquals(
            expectedEvent.attachmentsSize,
            cursor.getLong(cursor.getColumnIndex(EventTable.COL_ATTACHMENT_SIZE)),
        )
    }

    private fun assertAttachmentInCursor(
        attachmentEntity: AttachmentEntity,
        event: EventEntity,
        cursor: Cursor,
    ) {
        assertEquals(
            attachmentEntity.id,
            cursor.getString(cursor.getColumnIndex(AttachmentTable.COL_ID)),
        )
        assertEquals(
            attachmentEntity.type,
            cursor.getString(cursor.getColumnIndex(AttachmentTable.COL_TYPE)),
        )
        assertEquals(
            attachmentEntity.path,
            cursor.getString(cursor.getColumnIndex(AttachmentTable.COL_FILE_PATH)),
        )
        assertEquals(
            attachmentEntity.name,
            cursor.getString(cursor.getColumnIndex(AttachmentTable.COL_NAME)),
        )
        assertEquals(
            event.timestamp,
            cursor.getString(cursor.getColumnIndex(AttachmentTable.COL_TIMESTAMP)),
        )
        assertEquals(
            event.sessionId,
            cursor.getString(cursor.getColumnIndex(AttachmentTable.COL_SESSION_ID)),
        )
        assertEquals(
            event.id,
            cursor.getString(cursor.getColumnIndex(AttachmentTable.COL_EVENT_ID)),
        )
    }

    private fun assertBatchedEventInCursor(
        eventId: String,
        @Suppress("SameParameterValue") batchId: String,
        cursor: Cursor,
    ) {
        assertEquals(
            eventId,
            cursor.getString(cursor.getColumnIndex(EventsBatchTable.COL_EVENT_ID)),
        )
        assertEquals(
            batchId,
            cursor.getString(cursor.getColumnIndex(EventsBatchTable.COL_BATCH_ID)),
        )
    }

    private fun assertEventPacket(event: EventEntity, eventPacket: EventPacket) {
        assertEquals(event.id, eventPacket.eventId)
        assertEquals(event.type, eventPacket.type)
        assertEquals(event.timestamp, eventPacket.timestamp)
        assertEquals(event.sessionId, eventPacket.sessionId)
        assertEquals(event.serializedData, eventPacket.serializedData)
        assertEquals(event.serializedAttributes, eventPacket.serializedAttributes)
        assertEquals(event.serializedAttachments, eventPacket.serializedAttachments)
        assertEquals(event.filePath, eventPacket.serializedDataFilePath)
    }
}
