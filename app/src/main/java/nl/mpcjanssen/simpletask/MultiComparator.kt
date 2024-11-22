package nl.mpcjanssen.simpletask

import android.util.Log
import nl.mpcjanssen.simpletask.task.Task
import nl.mpcjanssen.simpletask.util.alfaSort
import java.util.*

class MultiComparator(sorts: ArrayList<String>, today: String, caseSensitve: Boolean, createIsThreshold: Boolean, moduleName: String? = null) {
    var comparator : Comparator<Task> = compareBy { null }

    var fileOrder = true

    // val luaCache: MutableMap<Task, String> = HashMap<Task, String>();

    init {
        label@ for (sort in sorts) {
            val parts = sort.split(Query.SORT_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var reverse = false
            val sortType: String
            if (parts.size == 1) {
                // Support older shortcuts and widgets
                reverse = false
                sortType = parts[0]
            } else {
                sortType = parts[1]
                if (parts[0] == Query.REVERSED_SORT) {
                    reverse = true
                }
            }
            var comp: (Task) -> Comparable<*>
            val lastDate = "9999-99-99"
            when (sortType) {
                "file_order" -> {
                    fileOrder = !reverse
                    break@label
                }
                "by_context" -> comp = { t ->
                    val txt = t.lists?.let { alfaSort(it).firstOrNull() } ?: ""
                    if (caseSensitve) {
                        txt
                    } else {
                        txt.lowercase()
                    }
                }
                "by_project" -> comp = { t ->
                    val txt = t.tags?.let { alfaSort(it).firstOrNull() } ?: ""
                    if (caseSensitve) {
                        txt
                    } else {
                        txt.lowercase()
                    }
                }
                "alphabetical" -> comp = if (caseSensitve) {
                    { it.alphaParts }
                } else {
                    { it.alphaParts.lowercase() }
                }
                "by_prio" -> comp = { it.priority }
                "completed" -> comp = { it.isCompleted() }
                "by_creation_date" -> comp = { it.createDate ?: lastDate }
                "in_future" -> comp = { it.inFuture(today, createIsThreshold) }
                "by_due_date" -> comp = { it.dueDate ?: lastDate }
                "by_threshold_date" -> comp = {
                    val fallback = if (createIsThreshold) it.createDate ?: lastDate else lastDate
                    it.thresholdDate ?: fallback
                }
                "by_completion_date" -> comp = { it.completionDate ?: lastDate }
                "by_lua" -> {
                    if (moduleName == null || !Interpreter.hasOnSortCallback(moduleName)) {
                       continue@label
                    }
                    comp = {
                        Interpreter.onSortCallback(moduleName, it)
                        // luaCache[it] ?: Interpreter.onSortCallback(moduleName, it).also { str ->
                        //     luaCache[it] = str
                        // }
                    }
                }
                else -> {
                    Log.w("MultiComparator", "Unknown sort: $sort")
                    continue@label
                }
            }
            comparator = if (reverse) {
                comparator.thenByDescending(comp)
            } else {
                comparator.thenBy(comp)
            }
        }
    }

}
