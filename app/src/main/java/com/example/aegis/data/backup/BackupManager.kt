package com.example.aegis.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.example.aegis.data.db.AegisDatabase
import com.example.aegis.data.db.entity.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AegisDatabase,
) {

    /**
     * Exports all health data as JSON + document files, then AES-256-GCM encrypts the result.
     * No DB file copying — purely DAO-based so WAL/passphrase issues don't apply.
     */
    suspend fun createBackupBlob(passphrase: String): ByteArray = withContext(Dispatchers.IO) {
        val patient    = db.patientDao().getPatientOnce()
        val conditions = db.conditionDao().getAllOnce()
        val meds       = db.medicationDao().getAllOnce()
        val allergies  = db.allergyDao().getAllOnce()
        val documents  = db.documentDao().getAllOnce()
        val visits     = db.visitLogDao().getAllOnce()
        val filesDir   = context.filesDir.absolutePath

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            if (patient != null)
                zip.text("patient.json", patientToJson(patient).toString())

            zip.text("conditions.json", JSONArray().also { a ->
                conditions.forEach { a.put(conditionToJson(it)) }
            }.toString())

            zip.text("medications.json", JSONArray().also { a ->
                meds.forEach { a.put(medicationToJson(it)) }
            }.toString())

            zip.text("allergies.json", JSONArray().also { a ->
                allergies.forEach { a.put(allergyToJson(it)) }
            }.toString())

            zip.text("documents.json", JSONArray().also { a ->
                documents.forEach { a.put(documentToJson(it, filesDir)) }
            }.toString())

            zip.text("visits.json", JSONArray().also { a ->
                visits.forEach { a.put(visitToJson(it)) }
            }.toString())

            // Document files stored as files/<relative-path-under-filesDir>
            documents.forEach { doc ->
                val file = File(doc.localPath)
                if (file.exists()) {
                    val rel = doc.localPath.toRelative(filesDir)
                    zip.putNextEntry(ZipEntry("files/$rel"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }

        BackupCrypto.encrypt(baos.toByteArray(), passphrase)
    }

    /**
     * Decrypts the blob, parses JSON, clears all tables in a transaction, re-inserts all data,
     * and copies document files back. Room's passphrase is unaffected — no DB file copying.
     */
    suspend fun restoreFromBlob(blob: ByteArray, passphrase: String) = withContext(Dispatchers.IO) {
        val plaintext = BackupCrypto.decrypt(blob, passphrase)

        // Parse everything into memory first — DB untouched until all parsing succeeds
        val textEntries = mutableMapOf<String, String>()
        val fileEntries = mutableMapOf<String, ByteArray>()
        ZipInputStream(plaintext.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val bytes = zip.readBytes()
                if (entry.name.startsWith("files/"))
                    fileEntries[entry.name.removePrefix("files/")] = bytes
                else
                    textEntries[entry.name] = String(bytes, Charsets.UTF_8)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val filesDir = context.filesDir.absolutePath

        // Atomic: clear all tables then re-insert
        db.withTransaction {
            db.visitLogDao().deleteAll()
            db.documentDao().deleteAll()
            db.allergyDao().deleteAll()
            db.medicationDao().deleteAll()
            db.conditionDao().deleteAll()
            db.patientDao().deleteAll()

            textEntries["patient.json"]?.let {
                db.patientDao().upsert(patientFromJson(JSONObject(it)))
            }
            textEntries["conditions.json"]?.let {
                JSONArray(it).forEach { j -> db.conditionDao().insert(conditionFromJson(j)) }
            }
            textEntries["medications.json"]?.let {
                JSONArray(it).forEach { j -> db.medicationDao().insert(medicationFromJson(j)) }
            }
            textEntries["allergies.json"]?.let {
                JSONArray(it).forEach { j -> db.allergyDao().insert(allergyFromJson(j)) }
            }
            textEntries["documents.json"]?.let {
                JSONArray(it).forEach { j -> db.documentDao().insert(documentFromJson(j, filesDir)) }
            }
            textEntries["visits.json"]?.let {
                JSONArray(it).forEach { j -> db.visitLogDao().insert(visitFromJson(j)) }
            }
        }

        // Copy document files
        fileEntries.forEach { (rel, bytes) ->
            val dest = File(context.filesDir, rel)
            dest.parentFile?.mkdirs()
            dest.writeBytes(bytes)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun ZipOutputStream.text(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun String.toRelative(base: String): String =
        if (startsWith("$base/")) removePrefix("$base/") else File(this).name

    private inline fun JSONArray.forEach(action: (JSONObject) -> Unit) {
        for (i in 0 until length()) action(getJSONObject(i))
    }

    // ── JSON serializers ──────────────────────────────────────────────────────

    private fun patientToJson(p: PatientEntity) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("bloodType", p.bloodType)
        put("dateOfBirth", p.dateOfBirth ?: JSONObject.NULL)
        put("gender", p.gender ?: JSONObject.NULL)
        put("emergencyContactName", p.emergencyContactName ?: JSONObject.NULL)
        put("emergencyContactPhone", p.emergencyContactPhone ?: JSONObject.NULL)
        put("updatedAt", p.updatedAt)
    }

    private fun patientFromJson(j: JSONObject) = PatientEntity(
        id = j.getInt("id"),
        name = j.getString("name"),
        bloodType = j.optString("bloodType", "Unknown"),
        dateOfBirth = if (j.isNull("dateOfBirth")) null else j.getLong("dateOfBirth"),
        gender = if (j.isNull("gender")) null else j.optString("gender"),
        emergencyContactName = if (j.isNull("emergencyContactName")) null else j.optString("emergencyContactName"),
        emergencyContactPhone = if (j.isNull("emergencyContactPhone")) null else j.optString("emergencyContactPhone"),
        updatedAt = j.getLong("updatedAt"),
    )

    private fun conditionToJson(c: ConditionEntity) = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("diagnosedAt", c.diagnosedAt ?: JSONObject.NULL)
        put("isActive", c.isActive)
        put("notes", c.notes)
    }

    private fun conditionFromJson(j: JSONObject) = ConditionEntity(
        id = j.getLong("id"),
        name = j.getString("name"),
        diagnosedAt = if (j.isNull("diagnosedAt")) null else j.getLong("diagnosedAt"),
        isActive = j.getBoolean("isActive"),
        notes = j.optString("notes", ""),
    )

    private fun medicationToJson(m: MedicationEntity) = JSONObject().apply {
        put("id", m.id)
        put("name", m.name)
        put("dosage", m.dosage)
        put("frequency", m.frequency)
        put("startDate", m.startDate ?: JSONObject.NULL)
        put("endDate", m.endDate ?: JSONObject.NULL)
        put("prescribedBy", m.prescribedBy)
        put("isActive", m.isActive)
        put("notes", m.notes)
    }

    private fun medicationFromJson(j: JSONObject) = MedicationEntity(
        id = j.getLong("id"),
        name = j.getString("name"),
        dosage = j.getString("dosage"),
        frequency = j.getString("frequency"),
        startDate = if (j.isNull("startDate")) null else j.getLong("startDate"),
        endDate = if (j.isNull("endDate")) null else j.getLong("endDate"),
        prescribedBy = j.optString("prescribedBy", ""),
        isActive = j.getBoolean("isActive"),
        notes = j.optString("notes", ""),
    )

    private fun allergyToJson(a: AllergyEntity) = JSONObject().apply {
        put("id", a.id)
        put("substance", a.substance)
        put("severity", a.severity)
        put("reaction", a.reaction)
    }

    private fun allergyFromJson(j: JSONObject) = AllergyEntity(
        id = j.getLong("id"),
        substance = j.getString("substance"),
        severity = j.optString("severity", "Unknown"),
        reaction = j.optString("reaction", ""),
    )

    private fun documentToJson(d: DocumentEntity, filesDir: String) = JSONObject().apply {
        put("id", d.id)
        put("fileName", d.fileName)
        put("documentType", d.documentType)
        put("mimeType", d.mimeType)
        put("localPathRel", d.localPath.toRelative(filesDir))
        put("uploadedAt", d.uploadedAt)
        put("documentDate", d.documentDate ?: JSONObject.NULL)
        put("conditionId", d.conditionId ?: JSONObject.NULL)
        put("providerName", d.providerName)
        put("tags", d.tags)
        put("ocrText", d.ocrText ?: JSONObject.NULL)
        put("extractedFields", d.extractedFields ?: JSONObject.NULL)
        put("thumbnailPath", d.thumbnailPath?.toRelative(filesDir) ?: JSONObject.NULL)
        put("extractionState", d.extractionState)
    }

    private fun documentFromJson(j: JSONObject, filesDir: String) = DocumentEntity(
        id = j.getLong("id"),
        fileName = j.getString("fileName"),
        documentType = j.optString("documentType", "UNKNOWN"),
        mimeType = j.optString("mimeType", ""),
        localPath = "$filesDir/${j.getString("localPathRel")}",
        uploadedAt = j.getLong("uploadedAt"),
        documentDate = if (j.isNull("documentDate")) null else j.getLong("documentDate"),
        conditionId = if (j.isNull("conditionId")) null else j.getLong("conditionId"),
        providerName = j.optString("providerName", ""),
        tags = j.optString("tags", ""),
        ocrText = if (j.isNull("ocrText")) null else j.optString("ocrText"),
        extractedFields = if (j.isNull("extractedFields")) null else j.optString("extractedFields"),
        thumbnailPath = if (j.isNull("thumbnailPath")) null else "$filesDir/${j.optString("thumbnailPath")}",
        extractionState = j.optString("extractionState", "PENDING"),
    )

    private fun visitToJson(v: VisitLogEntity) = JSONObject().apply {
        put("id", v.id)
        put("visitDate", v.visitDate)
        put("doctorName", v.doctorName)
        put("clinicName", v.clinicName)
        put("diagnosis", v.diagnosis)
        put("prescription", v.prescription)
        put("notes", v.notes)
        put("followUpDate", v.followUpDate ?: JSONObject.NULL)
        put("linkedDocumentIds", v.linkedDocumentIds)
        put("conditionTags", v.conditionTags)
    }

    private fun visitFromJson(j: JSONObject) = VisitLogEntity(
        id = j.getLong("id"),
        visitDate = j.getLong("visitDate"),
        doctorName = j.optString("doctorName", ""),
        clinicName = j.optString("clinicName", ""),
        diagnosis = j.optString("diagnosis", ""),
        prescription = j.optString("prescription", ""),
        notes = j.optString("notes", ""),
        followUpDate = if (j.isNull("followUpDate")) null else j.getLong("followUpDate"),
        linkedDocumentIds = j.optString("linkedDocumentIds", ""),
        conditionTags = j.optString("conditionTags", ""),
    )
}
