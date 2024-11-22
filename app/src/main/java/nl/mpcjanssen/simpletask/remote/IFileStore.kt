package nl.mpcjanssen.simpletask.remote

import nl.mpcjanssen.simpletask.TodoApplication
import nl.mpcjanssen.simpletask.util.broadcastFileSync
import java.io.File
import java.io.IOException
import kotlin.reflect.KClass

/**
 * Interface definition of the storage backend used.
 * All functions are run in a background thread and are properly queued to prevent
 * concurrent file store actions.
 * The implementers of this interface should throw exceptions if anything goes wrong. The logic for handling the
 * errors should be implemented in the users of the FileStore
 * Because some FileStores might be accessing the network to do their job,
 * all file related actions should never be performed on the main thread.
 */
 // TODO: Make archiving atomic
interface IFileStore {
    val isEncrypted: Boolean

    @Throws(IOException::class)
    fun loadTasksFromFile(file: File): List<String>

    fun needSync(file: File) : Boolean

    fun todoNameChanged()

    // Save the tasks and return the new remote Filename
    fun saveTasksToFile(file: File, lines: List<String>, eol: String) : File

    // Handle login and logout
    fun loginActivity(): KClass<*>?
    fun logout()

    @Throws(IOException::class)
    fun appendTaskToFile(file: File, lines: List<String>, eol: String)

    fun readFile(file: File, fileRead: (contents: String) -> Unit)
    fun writeFile(file: File, contents: String)

    val isOnline: Boolean

    // Return the default todo.txt path
    fun getDefaultFile(): File

    // Return files and subfolders in path. If txtOnly is true only *.txt files will be returned.
    fun loadFileList(file: File, txtOnly: Boolean): List<FileEntry>

    // Allow the FileStore to signal that the remote
    // todoFile changed. Call this in the filestore code
    // to force file sync
    @Suppress("unused")
    fun remoteTodoFileChanged() {
        broadcastFileSync(TodoApplication.app.localBroadCastManager)
    }

    // Generic special folder names for use in File dialogs
    companion object {
        const val ROOT_DIR = "/"
        const val PARENT_DIR = ".."

    }
}

// Data class to return the lines and version of a remote file.

// Generic file entry class for use in File dialogs
data class FileEntry(val file: File, val isFolder: Boolean)



