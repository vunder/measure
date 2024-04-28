package sh.measure.android.storage

import sh.measure.android.logger.LogLevel
import sh.measure.android.logger.Logger
import java.io.File
import java.io.IOException

internal interface FileStorage {
    /**
     * Writes serialized event data to a file.
     *
     * @return The path of the file if the write was successful, otherwise null.
     */
    fun writeEventData(eventId: String, serializedData: String): String?

    /**
     * Gets a file from the given path.
     *
     * @param path The path of the file to get.
     * @return The file if it exists, otherwise null.
     */
    fun getFile(path: String): File?

    /**
     * Writes an attachment to a file, with the attachment id as the file name.
     *
     * @param attachmentId The attachment id to use as the file name.
     */
    fun writeAttachment(attachmentId: String, bytes: ByteArray): String?
}

private const val MEASURE_DIR = "measure"

internal class FileStorageImpl(
    private val rootDir: String,
    private val logger: Logger,
) : FileStorage {

    override fun writeEventData(eventId: String, serializedData: String): String? {
        val file = createFile(eventId) ?: return null
        try {
            file.writeText(serializedData)
        } catch (e: IOException) {
            logger.log(LogLevel.Error, "Error writing serialized event data to file", e)
            deleteFileIfExists(file)
            return null
        }
        return file.path
    }

    override fun writeAttachment(attachmentId: String, bytes: ByteArray): String? {
        val file = createFile(attachmentId) ?: return null
        return try {
            file.writeBytes(bytes)
            file.path
        } catch (e: IOException) {
            logger.log(LogLevel.Error, "Error writing attachment to file", e)
            deleteFileIfExists(file)
            null
        }
    }

    override fun getFile(path: String): File? {
        val file = File(path)
        return when {
            file.exists() -> file
            else -> null
        }
    }

    private fun createFile(id: String): File? {
        val dirPath = "$rootDir/$MEASURE_DIR"
        val rootDir = File(dirPath)

        // Create directories if they don't exist
        try {
            if (!rootDir.exists()) {
                rootDir.mkdirs()
            }
        } catch (e: SecurityException) {
            logger.log(LogLevel.Error, "Unable to create file with id=$id", e)
            return null
        }

        // Create file with event id as file name
        val filePath = "$dirPath/$id"
        val file = File(filePath)
        try {
            if (!file.exists()) {
                file.createNewFile()
            }
        } catch (e: IOException) {
            logger.log(LogLevel.Error, "Error creating file with id=$id", e)
            return null
        }

        return file
    }

    private fun deleteFileIfExists(file: File) {
        if (file.exists()) {
            file.delete()
        }
    }
}
