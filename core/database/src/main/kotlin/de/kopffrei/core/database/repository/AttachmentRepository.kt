package de.kopffrei.core.database.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import de.kopffrei.core.common.model.OwnerType
import de.kopffrei.core.common.time.Ids
import de.kopffrei.core.database.dao.AttachmentDao
import de.kopffrei.core.database.entity.AttachmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anhänge werden in den privaten App-Speicher kopiert (kein Zugriff durch andere Apps, bleiben
 * erhalten, auch wenn die Originaldatei verschwindet).
 */
@Singleton
class AttachmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AttachmentDao,
) {
    private val directory: File
        get() = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun observe(ownerType: OwnerType, ownerId: String): Flow<List<AttachmentEntity>> =
        dao.observeFor(ownerType, ownerId)

    suspend fun getFor(ownerType: OwnerType, ownerId: String): List<AttachmentEntity> =
        dao.getFor(ownerType, ownerId)

    /** Kopiert die Datei hinter [uri] in die App. Gibt null zurück, wenn sie nicht gelesen werden kann. */
    suspend fun addFromUri(ownerType: OwnerType, ownerId: String, uri: Uri): AttachmentEntity? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val displayName = queryDisplayName(uri) ?: "Anhang"
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val id = Ids.newId()
            val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80)
            val target = File(directory, "${id}_$safeName")
            try {
                val input = resolver.openInputStream(uri) ?: return@withContext null
                input.use { source -> target.outputStream().use { sink -> source.copyTo(sink) } }
            } catch (_: IOException) {
                target.delete()
                return@withContext null
            } catch (_: SecurityException) {
                target.delete()
                return@withContext null
            }
            val entity = AttachmentEntity(
                id = id,
                ownerType = ownerType,
                ownerId = ownerId,
                filePath = "$DIR_NAME/${target.name}",
                mimeType = mimeType,
                displayName = displayName,
                sizeBytes = target.length(),
            )
            dao.upsert(entity)
            entity
        }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        val attachment = dao.get(id) ?: return@withContext
        file(attachment).delete()
        dao.delete(id)
    }

    suspend fun removeAllFor(ownerType: OwnerType, ownerId: String) = withContext(Dispatchers.IO) {
        dao.getFor(ownerType, ownerId).forEach { file(it).delete() }
        dao.deleteFor(ownerType, ownerId)
    }

    /** Löscht alle Anhangsdateien (für „Alle Daten löschen“). */
    suspend fun deleteAllFiles() = withContext(Dispatchers.IO) {
        File(context.filesDir, DIR_NAME).deleteRecursively()
        Unit
    }

    fun file(attachment: AttachmentEntity): File = File(context.filesDir, attachment.filePath)

    /** Freigabe-URI zum Öffnen in einer anderen App (über den FileProvider der App). */
    fun shareUri(attachment: AttachmentEntity): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file(attachment))

    private fun queryDisplayName(uri: Uri): String? =
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (_: SecurityException) {
            null
        }

    companion object {
        private const val DIR_NAME = "attachments"
    }
}
