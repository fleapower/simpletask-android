package nl.mpcjanssen.simpletask.task

import android.app.Activity
import android.content.Intent
import android.os.CountDownTimer
import android.os.SystemClock
import android.util.Log
import nl.mpcjanssen.simpletask.*
import nl.mpcjanssen.simpletask.remote.FileStore
import nl.mpcjanssen.simpletask.remote.IFileStore
import nl.mpcjanssen.simpletask.util.*
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

/**
 * Implementation of the in memory representation of the Todo list
 * uses an ActionQueue to ensure modifications and access of the underlying todo list are
 * sequential. If this is not done properly the result is a likely ConcurrentModificationException.

 * @author Mark Janssen
 */
class TodoList(val config: Config) {
    private var timer: CountDownTimer? = null
    private var mLists: MutableList<String>? = null
    private var mTags: MutableList<String>? = null
    private var todoItems = emptyList<Task>().toMutableList()
    val pendingEdits = HashSet<Task>()
    internal val tag = TodoList::class.java.simpleName

    init {
        config.todoList?.let { todoItems.addAll(it.asSequence()) }
    }

    fun add(items: List<Task>, atEnd: Boolean) {
        Log.d(tag, "Add task ${items.size} atEnd: $atEnd")
        val updatedItems = items.map { item ->
            Interpreter.onAddCallback(item) ?: item
        }
        if (atEnd) {
            todoItems.addAll(updatedItems)
        } else {
            todoItems.addAll(0, updatedItems)
        }

    }

    fun add(t: Task, atEnd: Boolean) {
        add(listOf(t), atEnd)
    }

    fun removeAll(tasks: List<Task>) {
        Log.d(tag, "Remove")
        pendingEdits.removeAll(tasks)
        todoItems.removeAll(tasks)

    }

    fun size(): Int {
        return todoItems.size
    }

    val priorities: ArrayList<Priority>
        get() {
            val res = HashSet<Priority>()
            todoItems.forEach {
                res.add(it.priority)
            }
            val ret = ArrayList(res)
            ret.sort()
            return ret
        }

    val contexts: List<String>
        get() {
            val lists = mLists
            if (lists != null) {
                return lists
            }
            val res = HashSet<String>()
            todoItems.forEach { t ->
                t.lists?.let { res.addAll(it) }

            }
            val newLists = res.toMutableList()
            mLists = newLists
            return newLists
        }

    val projects: List<String>
        get() {
            val tags = mTags
            if (tags != null) {
                return tags
            }
            val res = HashSet<String>()
            todoItems.forEach { t ->
                t.tags?.let { res.addAll(it) }
            }
            val newTags = res.toMutableList()
            mTags = newTags
            return newTags
        }

    fun uncomplete(items: List<Task>) {
        Log.d(tag, "Uncomplete")
        items.forEach {
            it.markIncomplete()
        }
    }

    fun complete(tasks: List<Task>, keepPrio: Boolean, extraAtEnd: Boolean) {
        Log.d(tag, "Complete")
        for (task in tasks) {
            val extra = task.markComplete(todayAsString)
            if (extra != null) {
                if (extraAtEnd) {
                    todoItems.add(extra)
                } else {
                    todoItems.add(0, extra)
                }
            }
            if (!keepPrio) {
                task.priority = Priority.NONE
            }
        }

    }

    fun prioritize(tasks: List<Task>, prio: Priority) {
        Log.d(tag, "Complete")
        tasks.map { it.priority = prio }
    }

    fun defer(deferString: String, tasks: List<Task>, dateType: DateType) {
        Log.d(tag, "Defer")
        tasks.forEach {
            when (dateType) {
                DateType.DUE -> it.deferDueDate(deferString, todayAsString)
                DateType.THRESHOLD -> it.deferThresholdDate(deferString, todayAsString)
            }
        }
    }

    fun update(org: Collection<Task>, updated: List<Task>, addAtEnd: Boolean) {
        val smallestSize = org.zip(updated) { orgTask, updatedTask ->
            val idx = todoItems.indexOf(orgTask)
            if (idx != -1) {
                updatedTask.id = orgTask.id
                todoItems[idx] = updatedTask
            } else {
                todoItems.add(updatedTask)
            }
            1
        }.size
        removeAll(org.toMutableList().drop(smallestSize))
        add(updated.toMutableList().drop(smallestSize), addAtEnd)
    }

    val selectedTasks: List<Task>
        get() {
            return todoItems.toList().filter { it.selected }
        }

    val fileFormat: String = todoItems.toList().joinToString(separator = "\n", transform = {
        it.inFileFormat(config.useUUIDs)
    })

    fun notifyTasklistChanged(
        todoFile: File,
        save: Boolean,
        refreshMainUI: Boolean = true,
        forceKeepSelection: Boolean = false
    ) {
        Log.d(tag, "Notified changed")
        if (save) {
            save(FileStore, todoFile, eol = config.eol)
        }
        if (selectedTasks.isEmpty()) {
            broadcastRefreshSelection(TodoApplication.app.localBroadCastManager)
        } else if (!config.hasKeepSelection && !forceKeepSelection) {
            clearSelection()
        }
        mLists = null
        mTags = null
        if (refreshMainUI) {
            broadcastTasklistChanged(TodoApplication.app.localBroadCastManager)
        } else {
            broadcastRefreshWidgets(TodoApplication.app.localBroadCastManager)
        }
    }

    private fun startAddTaskActivity(act: Activity, prefill: String) {
        Log.d(tag, "Start add/edit task activity")
        val intent = Intent(act, AddTask::class.java)
        intent.putExtra(Constants.EXTRA_PREFILL_TEXT, prefill)
        act.startActivity(intent)
    }

    fun getMultiComparator(filter: Query, caseSensitive: Boolean): MultiComparator {
        val sorts = filter.getSort(config.defaultSorts)
        return MultiComparator(
            sorts,
            TodoApplication.app.today,
            caseSensitive,
            filter.createIsThreshold,
            filter.luaModule
        )
    }

    fun getSortedTasks(filter: Query, caseSensitive: Boolean): Pair<List<Task>, Int> {
        Log.d(tag, "Getting sorted and filtered tasks")
        val start = SystemClock.elapsedRealtime()
        val comp = getMultiComparator(filter, caseSensitive)
        val listCopy = todoItems.toList()
        val taskCount = listCopy.size
        val itemsToSort = if (comp.fileOrder) {
            listCopy
        } else {
            listCopy.reversed()
        }
        val sortedItems = itemsToSort.sortedWith(comp.comparator)
        val result = filter.applyFilter(sortedItems, showSelected = true)
        val end = SystemClock.elapsedRealtime()
        Log.d(tag, "Sorting and filtering tasks took ${end - start} ms")
        return Pair(result, taskCount)

    }

    fun reload(reason: String = "") {
        FileStoreActionQueue.add("Reload") {
            Log.d(tag, "Reload: $reason")

            val todoFile = config.todoFile
            if (config.changesPending && FileStore.isOnline) {
                Log.i(tag, "Not loading, changes pending")
                Log.i(tag, "Saving instead of loading")
                save(FileStore, todoFile, eol = config.eol)
            } else {
                reloadaction(todoFile)
            }

        }
    }

    private fun reloadaction(file: File) {
        Log.d(tag, "Executing reloadaction")
        broadcastFileSyncStart(TodoApplication.app.localBroadCastManager)
        val needSync = FileStore.needSync(file)
        if (needSync) {
            Log.i(tag, "Remote version is different, sync")
            try {
                val items = FileStore.loadTasksFromFile(file)

                val newTodoItems = items.map { Task(it) }.toMutableList()
                synchronized(todoItems) {
                    Log.d(tag, "Fill todolist with ${items.size} items")
                    todoItems = newTodoItems
                    config.todoList = todoItems.toList()
                }
                // Update cache
                // Backup
                FileStoreActionQueue.add("Backup") {
                    Backupper.backup(file, items)
                }
                notifyTasklistChanged(file, save = false, refreshMainUI = true)
            } catch (e: Exception) {
                Log.e(tag, "TodoList load failed: ${file.path}", e)
                showToastShort(TodoApplication.app, "Loading of todo file failed")
            }
            Log.i(tag, "TodoList loaded from filestore")
        } else {
            Log.i(tag, "Remote version is same, load from cache")
        }
        broadcastFileSyncDone(TodoApplication.app.localBroadCastManager)
    }

    private fun save(fileStore: IFileStore, todoFile: File, eol: String) {
        Log.d(tag, "Save: ${todoFile.path}")
        config.changesPending = true
        broadcastUpdateStateIndicator(TodoApplication.app.localBroadCastManager)
        val lines = todoItems.toList().let {
            config.todoList = it
            it.map {
                it.inFileFormat(config.useUUIDs)
            }
        }

        // Update cache
        FileStoreActionQueue.add("Backup") {
            Backupper.backup(todoFile, lines)
        }
        runOnMainThread {
            timer?.apply { cancel() }
            val saveAction = {
                FileStoreActionQueue.add("Save") {
                    broadcastFileSyncStart(TodoApplication.app.localBroadCastManager)
                    try {
                        Log.i(tag, "Saving todo list, size ${lines.size}")
                        val newFile =
                            fileStore.saveTasksToFile(todoFile, lines, eol = eol).canonicalPath

                        if (config.changesPending) {
                            // Remove the red bar
                            config.changesPending = false
                            broadcastUpdateStateIndicator(TodoApplication.app.localBroadCastManager)
                        }
                        if (newFile != todoFile.canonicalPath) {
                            // The file was written under another name.
                            // Usually this means the was a conflict.
                            Log.i(tag, "Filename was changed remotely. New name is: $newFile")
                            showToastLong(
                                TodoApplication.app,
                                "Filename was changed remotely. New name is: $newFile"
                            )
                            TodoApplication.app.switchTodoFile(File(newFile))
                        }
                        // --- Google Drive Sync after successful local save ---
                        try {
                            TodoApplication.app.syncCurrentFileToDrive(TodoApplication.app)
                        } catch (e: Exception) {
                            Log.e(tag, "Drive sync after save failed", e)
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "TodoList save to ${todoFile.path} failed", e)
                        config.changesPending = true
                        if (fileStore.isOnline) {
                            showToastShort(TodoApplication.app, "Saving of todo file failed")
                        }
                    }
                    broadcastFileSyncDone(TodoApplication.app.localBroadCastManager)
                }
            }
            val idleSeconds = TodoApplication.config.idleBeforeSaveSeconds

            timer = object : CountDownTimer(idleSeconds * 1000L, 1000) {
                override fun onFinish() {
                    Log.d(tag, "Executing pending Save")
                    saveAction()
                }

                override fun onTick(p0: Long) {
                    Log.d(tag, "Scheduled save in $p0")
                }
            }.start()

        }
    }

    fun archive(todoFile: File, doneFile: File, tasks: List<Task>, eol: String) {
        Log.d(tag, "Archive ${tasks.size} tasks")

        FileStoreActionQueue.add("Append to file") {
            broadcastFileSyncStart(TodoApplication.app.localBroadCastManager)
            try {
                FileStore.appendTaskToFile(
                    doneFile,
                    tasks.map { it.inFileFormat(useUUIDs = TodoApplication.config.useUUIDs) },
                    eol
                )
                removeAll(tasks)
                notifyTasklistChanged(todoFile, save = true, refreshMainUI = true)
            } catch (e: Exception) {
                Log.e(tag, "Task archiving failed", e)
                showToastShort(TodoApplication.app, "Task archiving failed")
            }
            broadcastFileSyncDone(TodoApplication.app.localBroadCastManager)
        }
    }

    fun moveAbove(other: Task, itemToMove: Task) {
        val oldIndex = todoItems.indexOf(itemToMove)
        todoItems.removeAt(oldIndex)

        val newIndex = todoItems.indexOf(other)
        todoItems.add(newIndex, itemToMove)
    }

    fun moveBelow(other: Task, itemToMove: Task) {
        val oldIndex = todoItems.indexOf(itemToMove)
        todoItems.removeAt(oldIndex)

        val newIndex = todoItems.indexOf(other) + 1
        todoItems.add(newIndex, itemToMove)
    }

    fun isSelected(item: Task): Boolean = item.selected

    fun numSelected(): Int {
        return todoItems.toList().count { it.selected }
    }

    fun selectTasks(items: List<Task>) {
        Log.d(tag, "Select")
        items.forEach { selectTask(it) }
        broadcastRefreshSelection(TodoApplication.app.localBroadCastManager)
    }

    private fun selectTask(item: Task?) {
        item?.selected = true
    }

    private fun unSelectTask(item: Task) {
        item.selected = false
    }

    fun unSelectTasks(items: List<Task>) {
        Log.d(tag, "Unselect")
        items.forEach { unSelectTask(it) }
        broadcastRefreshSelection(TodoApplication.app.localBroadCastManager)

    }

    fun clearSelection() {
        Log.d(tag, "Clear selection")
        todoItems.iterator().forEach { it.selected = false }
        broadcastRefreshSelection(TodoApplication.app.localBroadCastManager)

    }

    fun getTaskIndex(t: Task): Int {
        return todoItems.indexOf(t)
    }

    fun getTaskAt(idx: Int): Task? {
        return todoItems.getOrNull(idx)
    }

    fun getTaskWithId(id: String): Task? {
        return todoItems.find { it.id == id }
    }

    fun each(callback: (Task) -> Unit) {
        todoItems.forEach { callback.invoke(it) }
    }

    fun editTasks(from: Activity, tasks: List<Task>, prefill: String) {
        Log.d(tag, "Edit tasks")
        pendingEdits.addAll(tasks)
        startAddTaskActivity(from, prefill)
    }

    fun clearPendingEdits() {
        Log.d(tag, "Clear selection")
        pendingEdits.clear()
    }
}