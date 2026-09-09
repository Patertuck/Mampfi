package ch.mampfi.server

import java.io.File
import java.sql.DriverManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

object DatabaseBackup {
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")

    fun create(
        databasePath: String,
        backupDirectory: File = defaultDirectory(databasePath),
        reason: String,
        revision: String = System.getenv("APP_REVISION") ?: "unknown",
        retainedDeploymentBackups: Int = 10,
    ): File {
        require(databasePath != ":memory:") { "An in-memory database cannot be backed up" }
        require(retainedDeploymentBackups > 0) { "At least one deployment backup must be retained" }
        check(File(databasePath).isFile) { "Database does not exist at ${File(databasePath).absolutePath}" }
        check(backupDirectory.isDirectory || backupDirectory.mkdirs()) { "Could not create backup directory ${backupDirectory.absolutePath}" }

        val safeReason = reason.safeFilenamePart()
        val safeRevision = revision.safeFilenamePart().take(16).ifBlank { "unknown" }
        val timestamp = LocalDateTime.now().format(timestampFormatter)
        val suffix = UUID.randomUUID().toString().take(8)
        val destination = File(backupDirectory, "mampfi-$safeReason-$timestamp-$safeRevision-$suffix.db")

        try {
            DriverManager.getConnection("jdbc:sqlite:$databasePath").use { source ->
                source.prepareStatement("VACUUM INTO ?").use { statement ->
                    statement.setString(1, destination.absolutePath)
                    statement.execute()
                }
            }
            checkIntegrity(destination)
            if (safeReason == "pre-update") removeExpiredDeploymentBackups(backupDirectory, retainedDeploymentBackups)
            return destination
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    fun defaultDirectory(databasePath: String): File {
        val database = File(databasePath).absoluteFile
        return File(database.parentFile ?: File("."), "backups")
    }

    private fun checkIntegrity(database: File) {
        DriverManager.getConnection("jdbc:sqlite:${database.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA integrity_check").use { result ->
                    check(result.next() && result.getString(1).equals("ok", ignoreCase = true)) {
                        "SQLite integrity check failed for ${database.absolutePath}"
                    }
                }
            }
        }
    }

    private fun removeExpiredDeploymentBackups(directory: File, retained: Int) {
        directory.listFiles { file -> file.isFile && file.name.startsWith("mampfi-pre-update-") && file.extension == "db" }
            .orEmpty()
            .sortedByDescending { it.name }
            .drop(retained)
            .forEach(File::delete)
    }

    private fun String.safeFilenamePart() = lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-')
}
