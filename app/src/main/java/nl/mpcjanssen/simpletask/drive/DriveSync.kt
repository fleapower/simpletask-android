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
                val fileMetadata = com.google.api.services.drive.model.File()
                fileMetadata.name = driveFileName
                val fileContent = FileInputStream(localFile)
                val mediaContent = com.google.api.client.http.InputStreamContent("text/plain", fileContent)
                val file = driveService!!.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
                Log.i("DriveSync", "File uploaded with ID: ${file.id}")
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
                val outputStream = FileOutputStream(localFile)
                driveService!!.files().get(driveFileId).executeMediaAndDownloadTo(outputStream)
                outputStream.close()
                Log.i("DriveSync", "File downloaded to: ${localFile.absolutePath}")
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }.start()
    }
}
