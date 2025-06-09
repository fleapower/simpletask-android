package nl.mpcjanssen.simpletask.drive

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Collections

object DriveSync {
    /**
     * Ensure DriveSync is initialized with the last signed-in account, if available.
     * This allows staying logged in between app restarts.
     */
    fun ensureInitialized(context: Context) {
        if (driveService != null && account != null && credential != null) return
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (lastAccount != null) {
            account = lastAccount
            credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_FILE)
            )
            credential!!.selectedAccount = account!!.account
            val transport = GoogleNetHttpTransport.newTrustedTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()
            driveService = Drive.Builder(transport, jsonFactory, credential)
                .setApplicationName("Simpletask")
                .build()
            Log.i("DriveSync", "DriveSync initialized from last signed-in account.")
        }
    }
    private const val RC_SIGN_IN = 9001
    private var googleSignInClient: GoogleSignInClient? = null
    private var driveService: Drive? = null
    private var account: GoogleSignInAccount? = null
    private var credential: GoogleAccountCredential? = null

    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        if (googleSignInClient == null) {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_FILE))
                .build()
            googleSignInClient = GoogleSignIn.getClient(context, gso)
        }
        return googleSignInClient!!
    }

    fun signIn(activity: Activity) {
        val signInIntent = getGoogleSignInClient(activity).signInIntent
        activity.startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    fun handleSignInResult(context: Context, data: Intent?, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            account = task.result
            credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_FILE)
            )
            credential!!.selectedAccount = account!!.account
            val transport = GoogleNetHttpTransport.newTrustedTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()
            driveService = Drive.Builder(transport, jsonFactory, credential)
                .setApplicationName("Simpletask")
                .build()
            onSuccess()
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun isSignedIn(context: Context): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null
    }

    fun signOut(context: Context, onComplete: () -> Unit) {
        getGoogleSignInClient(context).signOut().addOnCompleteListener { onComplete() }
    }

    fun uploadFile(context: Context, localFile: File, driveFileName: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (driveService == null) {
            onError(Exception("Not signed in to Google Drive"))
            return
        }
        Thread {
            try {
                // Find or create ToDoTxt folder
                val folderResult = driveService!!.files().list()
                    .setQ("name = 'ToDoTxt' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                    .setSpaces("drive")
                    .setFields("files(id, name)")
                    .execute()
                val folders = folderResult.files
                val folderId = if (folders != null && folders.isNotEmpty()) {
                    folders[0].id
                } else {
                    val folderMetadata = com.google.api.services.drive.model.File()
                    folderMetadata.name = "ToDoTxt"
                    folderMetadata.mimeType = "application/vnd.google-apps.folder"
                    val folder = driveService!!.files().create(folderMetadata)
                        .setFields("id")
                        .execute()
                    folder.id
                }
                // Search for an existing file with the same name in ToDoTxt
                val result = driveService!!.files().list()
                    .setQ("name = '${driveFileName.replace("'", "\\'")}' and trashed = false and '${folderId}' in parents")
                    .setSpaces("drive")
                    .setFields("files(id, name)")
                    .execute()
                val files = result.files
                val fileContent = FileInputStream(localFile)
                val mediaContent = com.google.api.client.http.InputStreamContent("text/plain", fileContent)
                if (files != null && files.isNotEmpty()) {
                    // File exists, update it
                    val fileId = files[0].id
                    val updatedFile = com.google.api.services.drive.model.File()
                    updatedFile.name = driveFileName
                    driveService!!.files().update(fileId, updatedFile, mediaContent)
                        .setFields("id")
                        .execute()
                    Log.i("DriveSync", "File updated with ID: $fileId")
                } else {
                    // File does not exist, create it
                    val fileMetadata = com.google.api.services.drive.model.File()
                    fileMetadata.name = driveFileName
                    fileMetadata.parents = listOf(folderId)
                    val file = driveService!!.files().create(fileMetadata, mediaContent)
                        .setFields("id")
                        .execute()
                    Log.i("DriveSync", "File uploaded with ID: ${file.id}")
                }
                fileContent.close()
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }.start()
    }

    fun downloadFile(context: Context, driveFileId: String, localFile: File, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (driveService == null) {
            onError(Exception("Not signed in to Google Drive"))
            return
        }
        Thread {
            try {
                val tempFile = File.createTempFile("download", null, localFile.parentFile)
                try {
                    val outputStream = FileOutputStream(tempFile)
                    driveService!!.files().get(driveFileId).executeMediaAndDownloadTo(outputStream)
                    outputStream.close()
                    // Only replace the original file if download succeeded
                    if (localFile.exists()) localFile.delete()
                    tempFile.renameTo(localFile)
                    Log.i("DriveSync", "File downloaded to: ${localFile.absolutePath}")
                    onSuccess()
                } catch (e: Exception) {
                    tempFile.delete()
                    throw e
                }
            } catch (e: Exception) {
                onError(e)
            }
        }.start()
    }

    /**
     * Two-way sync: if the Drive file is newer, download it and overwrite the local file.
     * If the local file is newer, upload it and overwrite the Drive file.
     * If both are the same, do nothing.
     */
    fun syncTwoWay(
        context: Context,
        localFile: File,
        driveFileName: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit,
        onDriveNewer: (() -> Unit)? = null // called if Drive file is newer
    ) {
        if (driveService == null) {
            onError(Exception("Not signed in to Google Drive"))
            return
        }
        Thread {
            try {
                // Find or create ToDoTxt folder
                val folderResult = driveService!!.files().list()
                    .setQ("name = 'ToDoTxt' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                    .setSpaces("drive")
                    .setFields("files(id, name)")
                    .execute()
                val folders = folderResult.files
                val folderId = if (folders != null && folders.isNotEmpty()) {
                    folders[0].id
                } else {
                    val folderMetadata = com.google.api.services.drive.model.File()
                    folderMetadata.name = "ToDoTxt"
                    folderMetadata.mimeType = "application/vnd.google-apps.folder"
                    val folder = driveService!!.files().create(folderMetadata)
                        .setFields("id")
                        .execute()
                    folder.id
                }
                // Get Drive file metadata in ToDoTxt
                val result = driveService!!.files().list()
                    .setQ("name = '${driveFileName.replace("'", "\\'")}' and trashed = false and '${folderId}' in parents")
                    .setSpaces("drive")
                    .setFields("files(id, name, modifiedTime)")
                    .execute()
                val files = result.files
                val driveFile = files?.firstOrNull()
                val driveModified = driveFile?.modifiedTime?.value ?: 0L
                val localModified = localFile.lastModified()

                if (driveFile != null) {
                    if (driveModified > localModified) {
                        // Drive is newer, download
                        val driveFileId = driveFile.id
                        val tempFile = File.createTempFile("download", null, localFile.parentFile)
                        try {
                            val outputStream = FileOutputStream(tempFile)
                            driveService!!.files().get(driveFileId).executeMediaAndDownloadTo(outputStream)
                            outputStream.close()
                            // Only replace the original file if download succeeded
                            if (localFile.exists()) localFile.delete()
                            tempFile.renameTo(localFile)
                            Log.i("DriveSync", "Drive file was newer, downloaded to local.")
                            onDriveNewer?.invoke()
                        } catch (e: Exception) {
                            tempFile.delete()
                            throw e
                        }
                    } else if (localModified > driveModified) {
                        // Local is newer, upload
                        val fileContent = FileInputStream(localFile)
                        val mediaContent = com.google.api.client.http.InputStreamContent("text/plain", fileContent)
                        val updatedFile = com.google.api.services.drive.model.File()
                        updatedFile.name = driveFileName
                        driveService!!.files().update(driveFile.id, updatedFile, mediaContent)
                            .setFields("id")
                            .execute()
                        fileContent.close()
                        Log.i("DriveSync", "Local file was newer, uploaded to Drive.")
                    } else {
                        Log.i("DriveSync", "Files are in sync, no action taken.")
                    }
                } else {
                    // No Drive file exists, upload local as new
                    val fileMetadata = com.google.api.services.drive.model.File()
                    fileMetadata.name = driveFileName
                    fileMetadata.parents = listOf(folderId)
                    val fileContent = FileInputStream(localFile)
                    val mediaContent = com.google.api.client.http.InputStreamContent("text/plain", fileContent)
                    driveService!!.files().create(fileMetadata, mediaContent)
                        .setFields("id")
                        .execute()
                    fileContent.close()
                    Log.i("DriveSync", "No Drive file, uploaded local file as new.")
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }.start()
    }
}
