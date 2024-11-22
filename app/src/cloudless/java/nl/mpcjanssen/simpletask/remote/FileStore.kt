package nl.mpcjanssen.simpletask.remote

import android.os.*
import android.util.Log
import nl.mpcjanssen.simpletask.R
import nl.mpcjanssen.simpletask.TodoApplication
import nl.mpcjanssen.simpletask.util.broadcastAuthFailed
import nl.mpcjanssen.simpletask.util.join
import java.io.File
import java.io.FilenameFilter
import java.util.*
import kotlin.reflect.KClass

object FileStore : IFileStore {
    private const val TAG = "FileStore"

    private var lastSeenRemoteId by TodoApplication.config.StringOrNullPreference(R.string.file_current_version_id)
    private var observer: TodoObserver? = null

    override val isOnline = true

    init {
        Log.i(TAG, "onCreate")
        Log.i(TAG, "Default path: ${getDefaultFile().path}")
        observer = null
    }

    override val isEncrypted: Boolean
        get() = false

    val isAuthenticated: Boolean
        get() {
            val externManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }

            // The WRITE_EXTERNAL_STORAGE permission is implied by the MANAGE_EXTERNAL_STORAGE permission
            return externManager
        }

    override fun loadTasksFromFile(file: File): List<String> {
        if (!isAuthenticated) {
            broadcastAuthFailed(TodoApplication.app.localBroadCastManager)
            return emptyList()
        }

        Log.i(TAG, "Loading tasks")
        val lines = file.readLines()
        Log.i(TAG, "Read ${lines.size} lines from $file")
        setWatching(file)
        lastSeenRemoteId = file.lastModified().toString()

        return lines
    }

    override fun needSync(file: File): Boolean {
        if (!isAuthenticated) {
            broadcastAuthFailed(TodoApplication.app.localBroadCastManager)
            return true
        }

        return lastSeenRemoteId != file.lastModified().toString()
    }

    override fun todoNameChanged() {
        lastSeenRemoteId = ""
    }

    override fun writeFile(file: File, contents: String) {
        if (!isAuthenticated) {
            broadcastAuthFailed(TodoApplication.app.localBroadCastManager)
            return
        }

        Log.i(TAG, "Writing file to  ${file.canonicalPath}")
        file.writeText(contents)
    }

    override fun readFile(file: File, fileRead: (contents: String) -> Unit) {
        if (!isAuthenticated) {
            broadcastAuthFailed(TodoApplication.app.localBroadCastManager)
            return
        }

        Log.i(TAG, "Reading file: ${file.path}")
        val contents: String
        val lines = file.readLines()
        contents = join(lines, "\n")
        fileRead(contents)
    }

    override fun loginActivity(): KClass<*>? {
        return LoginScreen::class
    }

    private fun setWatching(file: File) {
        Log.i(TAG, "Observer: adding folder watcher on ${file.parent}")

        val obs = observer
        if (obs != null && file.canonicalPath == obs.fileName) {
            Log.w(TAG, "Observer: already watching: ${file.canonicalPath}")
            return
        } else if (obs != null) {
            Log.w(TAG, "Observer: already watching different path: ${obs.fileName}")
            obs.ignoreEvents(true)
            obs.stopWatching()
            observer = null
        }

        observer = TodoObserver(file)
        Log.i(TAG, "Observer: modifying done")
    }

    override fun saveTasksToFile(file: File, lines: List<String>, eol: String): File {
        if (!isAuthenticated) {
            broadcastAuthFailed(TodoApplication.app.localBroadCastManager)
            return file
        }

        Log.i(TAG, "Saving tasks to file: ${file.path}")
        val obs = observer
        obs?.ignoreEvents(true)
        writeFile(file, lines.joinToString(eol) + eol)
        obs?.delayedStartListen(1000)

        lastSeenRemoteId = file.lastModified().toString()

        return file
    }

    override fun appendTaskToFile(file: File, lines: List<String>, eol: String) {
        if (!isAuthenticated) {
            broadcastAuthFailed(TodoApplication.app.localBroadCastManager)
            return
        }

        Log.i(TAG, "Appending ${lines.size} tasks to ${file.path}")
        file.appendText(lines.joinToString(eol) + eol)
    }

    override fun logout() {
    }

    override fun getDefaultFile(): File {
        return File(TodoApplication.app.getExternalFilesDir(null), "todo.txt")
    }

    override fun loadFileList(file: File, txtOnly: Boolean): List<FileEntry> {
        if (!isAuthenticated) {
            broadcastAuthFailed(TodoApplication.app.localBroadCastManager)
            return emptyList()
        }

        val result = ArrayList<FileEntry>()

        if (file.canonicalPath == "/") {
            TodoApplication.app.getExternalFilesDir(null)?.let {
                result.add(FileEntry(it, true))
            }
        }

        val filter = FilenameFilter { dir, filename ->
            val sel = File(dir, filename)
            if (!sel.canRead()) false
            else {
                if (sel.isDirectory) {
                    result.add(FileEntry(File(filename), true))
                } else {
                    !txtOnly || filename.lowercase().endsWith(".txt")
                    result.add(FileEntry(File(filename), false))
                }
            }
        }

        // Run the file applyFilter for side effects
        file.list(filter)

        return result
    }

    class TodoObserver(val file: File) : FileObserver(file) {
        private val tag = "FileWatchService"
        val fileName: String = file.canonicalPath
        private var ignoreEvents: Boolean = false
        private val handler: Handler

        private val delayedEnable = Runnable {
            Log.i(tag, "Observer: Delayed enabling events for: $fileName ")
            ignoreEvents(false)
        }

        init {
            this.startWatching()

            Log.i(tag, "Observer: creating observer on: $fileName")
            this.ignoreEvents = false
            this.handler = Handler(Looper.getMainLooper())
        }

        fun ignoreEvents(ignore: Boolean) {
            Log.i(tag, "Observer: observing events on $fileName? ignoreEvents: $ignore")
            this.ignoreEvents = ignore
        }

        override fun onEvent(event: Int, eventPath: String?) {
            if (eventPath != null && eventPath == fileName) {
                Log.d(tag, "Observer event: $fileName:$event")
                if (event == CLOSE_WRITE || event == MODIFY || event == MOVED_TO) {
                    if (ignoreEvents) {
                        Log.i(tag, "Observer: ignored event on: $fileName")
                    } else {
                        Log.i(tag, "File changed {}$fileName")
                        remoteTodoFileChanged()
                    }
                }
            }
        }

        fun delayedStartListen(ms: Int) {
            // Cancel any running timers
            handler.removeCallbacks(delayedEnable)

            // Reschedule
            Log.i(tag, "Observer: Adding delayed enabling to todoQueue")
            handler.postDelayed(delayedEnable, ms.toLong())
        }
    }
}