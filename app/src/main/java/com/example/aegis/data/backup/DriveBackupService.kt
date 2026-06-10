package com.example.aegis.data.backup

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class DriveFile(val id: String, val name: String, val createdTime: String)

class DriveBackupService(private val okhttp: OkHttpClient) {

    companion object {
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL =
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        private const val FOLDER = "appDataFolder"
        private const val MIME = "application/octet-stream"

        fun getSignInOptions(): GoogleSignInOptions =
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(
                    com.google.android.gms.common.api.Scope(
                        "https://www.googleapis.com/auth/drive.appdata"
                    )
                )
                .build()

        fun getToken(context: Context): String? {
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
            return account.idToken // note: for Drive API we need the OAuth access token
            // Access token is obtained via GoogleAuthUtil in BackupViewModel
        }
    }

    suspend fun upload(accessToken: String, blob: ByteArray, fileName: String): String =
        withContext(Dispatchers.IO) {
            val metadata = JSONObject().apply {
                put("name", fileName)
                put("parents", org.json.JSONArray().put(FOLDER))
            }
            val metaPart = metadata.toString()
                .toRequestBody("application/json; charset=UTF-8".toMediaType())
            val dataPart = blob.toRequestBody(MIME.toMediaType())

            val body = MultipartBody.Builder()
                .setType(MultipartBody.MIXED)
                .addPart(metaPart)
                .addPart(dataPart)
                .build()

            val request = Request.Builder()
                .url(DRIVE_UPLOAD_URL)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(body)
                .build()

            okhttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Upload failed: ${response.code} ${response.body?.string()}")
                val json = JSONObject(response.body!!.string())
                json.getString("id")
            }
        }

    suspend fun listBackups(accessToken: String): List<DriveFile> =
        withContext(Dispatchers.IO) {
            val url = "$DRIVE_FILES_URL?spaces=$FOLDER" +
                "&fields=files(id,name,createdTime)" +
                "&orderBy=createdTime+desc"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            okhttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("List failed: ${response.code}")
                val root = JSONObject(response.body!!.string())
                val files = root.getJSONArray("files")
                List(files.length()) { i ->
                    val f = files.getJSONObject(i)
                    DriveFile(
                        id = f.getString("id"),
                        name = f.getString("name"),
                        createdTime = f.optString("createdTime", ""),
                    )
                }
            }
        }

    suspend fun download(accessToken: String, fileId: String): ByteArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$DRIVE_FILES_URL/$fileId?alt=media")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            okhttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download failed: ${response.code}")
                response.body!!.bytes()
            }
        }

    suspend fun deleteBackup(accessToken: String, fileId: String) =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$DRIVE_FILES_URL/$fileId")
                .addHeader("Authorization", "Bearer $accessToken")
                .delete()
                .build()
            okhttp.newCall(request).execute().close()
        }
}
