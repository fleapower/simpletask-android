package nl.mpcjanssen.simpletask.task

import nl.mpcjanssen.simpletask.util.addInterval
import java.util.*
import java.util.regex.Pattern

class Task(text: String, defaultPrependedDate: String? = null) {
    var tokens: ArrayList<TToken>

    init {
        tokens = parse(text)
        defaultPrependedDate?.let {
            if (createDate == null) {
                createDate = defaultPrependedDate
            }
        }
    }

    constructor (text: String) : this(text, null)

    fun update(rawText: String) {
        tokens = parse(rawText)
    }

    private inline fun <reified T> getFirstToken(): T? {
        tokens.filterIsInstance<T>().forEach {
            return it
        }
        return null
    }

    private inline fun <reified T : TToken> upsertToken(newToken: T) {

        if (getFirstToken<T>() == null) {
            tokens.add(newToken)
        } else {
            tokens.forEachIndexed { idx, item ->
                if (item is T) tokens[idx] = newToken
            }
        }

    }

    var id: String = UUID.randomUUID().toString()

    val text: String
        get() {
            return tokens.joinToString(" ") { it.text }
        }

    val extensions: List<Pair<String, String>>
        get() {
            return tokens.asSequence().filter { it is KeyValueToken }.map {
                val token = it as KeyValueToken
                Pair(token.key, token.valueStr)
            }.toList()
        }

    val completionDate: String?
        get() = getFirstToken<CompletedDateToken>()?.value

    var uuid: String?
        get() = getFirstToken<UUIDToken>()?.valueStr
        set(uuid) {
            uuid?.let { upsertToken(UUIDToken(it)) }
        }

    var createDate: String?
        get() = getFirstToken<CreateDateToken>()?.value
        set(newDate) {
            val temp = ArrayList<TToken>()
            if (tokens.isNotEmpty() && (tokens.first() is CompletedToken)) {
                temp.add(tokens[0])
                tokens.removeAt(0)
                if (tokens.isNotEmpty() && tokens.first() is CompletedDateToken) {
                    temp.add(tokens.first())
                    tokens.removeAt(0)
                }
            }
            if (tokens.isNotEmpty() && tokens[0] is PriorityToken) {
                temp.add(tokens.first())
                tokens.removeAt(0)
            }
            if (tokens.isNotEmpty() && tokens[0] is CreateDateToken) {
                tokens.removeAt(0)
            }
            newDate?.let {
                temp.add(CreateDateToken(newDate))
            }
            temp.addAll(tokens)
            tokens = temp
        }

    var dueDate: String?
        get() = getFirstToken<DueDateToken>()?.valueStr
        set(dateStr) {
            if (dateStr.isNullOrEmpty()) {
                tokens.removeWhen { it is DueDateToken }
            } else {
                upsertToken(DueDateToken(dateStr))
            }
        }

    var thresholdDate: String?
        get() = getFirstToken<ThresholdDateToken>()?.valueStr
        set(dateStr) {
            if (dateStr.isNullOrEmpty()) {
                tokens.removeWhen { it is ThresholdDateToken }
            } else {
                upsertToken(ThresholdDateToken(dateStr))
            }
        }

    var priority: Priority
        get() = getFirstToken<PriorityToken>()?.value ?: Priority.NONE
        set(prio) {
            when {
                prio == Priority.NONE -> tokens.removeWhen { it is PriorityToken }
                tokens.any { it is PriorityToken } -> upsertToken(PriorityToken(prio.fileFormat))
                else -> tokens.add(0, PriorityToken(prio.fileFormat))
            }
        }

    val recurrencePattern: String?
        get() = getFirstToken<RecurrenceToken>()?.valueStr

    val tags: SortedSet<String>?
        get() {
            tokens.filterIsInstance<TagToken>().run {
                return if (isNotEmpty()) {
                    map { it.value }.toSortedSet()
                } else {
                    null
                }
            }
        }

    val lists: SortedSet<String>?
        get() {
            tokens.filterIsInstance<ListToken>().run {
                return if (isNotEmpty()) {
                    map { it.value }.toSortedSet()
                } else {
                    null
                }
            }
        }

    val links: Set<String>
        get() {
            return tokens.asSequence().filter { it is LinkToken }.map { it.text }.toSet()
        }

    val phoneNumbers: Set<String>
        get() {
            return tokens.asSequence().filter { it is PhoneToken }.map { it.text }.toSet()
        }
    val mailAddresses: Set<String>
        get() {
            return tokens.asSequence().filter { it is MailToken }.map { it.text }.toSet()
        }
    var selected: Boolean = false
    val alphaParts: String = showParts { it.isAlpha() }

    fun removeTag(tag: String) {
        tokens.removeWhen {
            ((it is TagToken) && it.value == tag)
        }
    }

    fun removeList(list: String) {
        tokens.removeWhen {
            ((it is ListToken) && (it.value == list))
        }
    }

    fun markComplete(dateStr: String): Task? {
        if (!this.isCompleted()) {
            val textWithoutCompletedInfo = text
            tokens.addAll(0, listOf(CompletedToken(true), CompletedDateToken(dateStr)))
            val pattern = recurrencePattern
            if (pattern != null) {
                var deferFromDate = ""
                if (recurrencePattern?.contains("+") == false) {
                    deferFromDate = completionDate ?: ""
                }
                val newTask = Task(textWithoutCompletedInfo)
                if (newTask.dueDate != null) {
                    newTask.deferDueDate(pattern, deferFromDate)
                }
                if (newTask.thresholdDate != null) {
                    newTask.deferThresholdDate(pattern, deferFromDate)
                }
                if (!createDate.isNullOrEmpty()) {
                    newTask.createDate = dateStr
                }
                return newTask
            }
        }
        return null
    }

    fun markIncomplete() {
        tokens.removeWhen {
            when (it) {
                is CompletedDateToken -> true
                is CompletedToken -> true
                else -> false
            }
        }
    }

    private fun deferDate(deferString: String, deferFromDate: String?): String? {
        if (MATCH_SINGLE_DATE.reset(deferString).matches()) {
            return deferString
        }
        if (deferString == "") {
            return null
        }
        val newDate = addInterval(deferFromDate, deferString)
        return newDate?.format(DATE_FORMAT)
    }

    fun deferThresholdDate(deferString: String, deferFromDate: String) {

        val oldDate: String? = if (deferFromDate.isEmpty()) {
            thresholdDate
        } else {
            deferFromDate
        }
        thresholdDate = deferDate(deferString, oldDate)
    }

    fun deferDueDate(deferString: String, deferFromDate: String) {
        val oldDate: String? = if (deferFromDate.isEmpty()) {
            dueDate
        } else {
            deferFromDate
        }
        dueDate = deferDate(deferString, oldDate)
    }

    fun inFileFormat(useUUIDs: Boolean): String {
        if (useUUIDs && text.isNotBlank() && uuid == null) {
            uuid = UUID.randomUUID().toString()
        }
        return text
    }

    fun inFuture(today: String, createIsThreshold: Boolean): Boolean {
        val date = thresholdDate ?: if (createIsThreshold) createDate else null
        date?.let {
            return date > today
        }
        return false
    }

    fun isHidden(): Boolean {
        return getFirstToken<HiddenToken>()?.value == true
    }

    fun isCompleted(): Boolean {
        return getFirstToken<CompletedToken>() != null
    }

    fun showParts(filter: (TToken) -> Boolean): String {
        return tokens.asSequence().filter(filter).joinToString(" ") { it.text }
    }

    fun getHeader(sort: String, empty: String, createIsThreshold: Boolean): String {
        when {
            sort.contains("by_context") -> {
                lists?.run {
                    if (isNotEmpty()) {
                        return first()
                    }
                }
                return empty
            }

            sort.contains("by_project") -> {
                tags?.run {
                    if (isNotEmpty()) {
                        return first()
                    }
                }
                return empty
            }

            sort.contains("by_threshold_date") -> return if (createIsThreshold) {
                thresholdDate ?: createDate ?: empty
            } else {
                thresholdDate ?: empty
            }

            sort.contains("by_prio") -> return priority.code
            sort.contains("by_due_date") -> return dueDate ?: empty
            else -> return ""
        }
    }

    // Adds the task to list listName
    // If the task is already on that list, it does nothing
    fun addList(listName: String) {
        listName.split(Regex("\\s+")).forEach {
            if (lists?.contains(it) != true) {
                appendToken(ListToken("@$it"))
            }
        }
    }

    // Tags the task with tag
    // If the task already has te tag, it does nothing
    fun addTag(tagName: String) {
        tagName.split(Regex("\\s+")).forEach {
            if (tags?.contains(it) != true) {
                appendToken(TagToken("+$it"))
            }
        }
    }

    private fun appendToken(token: TToken, trimWhiteSpace: Boolean = true) {
        while (trimWhiteSpace && tokens.isNotEmpty() && tokens.last() is WhiteSpaceToken) {
            tokens.removeLast()
        }
        tokens.add(token)
    }

    companion object {
        var TAG = "Task"
        const val DATE_FORMAT = "YYYY-MM-DD"
        private val MATCH_LIST = Pattern.compile("@(\\S+)").matcher("")
        private val MATCH_TAG = Pattern.compile("\\+(\\S+)").matcher("")
        private val MATCH_HIDDEN = Pattern.compile("[Hh]:([01])").matcher("")
        private val MATCH_UUID =
            Pattern.compile("[Uu][Uu][Ii][Dd]:([A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12})")
                .matcher("")
        private val MATCH_DUE = Pattern.compile("[Dd][Uu][Ee]:(\\d{4}-\\d{2}-\\d{2})").matcher("")
        private val MATCH_THRESHOLD = Pattern.compile("[Tt]:(\\d{4}-\\d{2}-\\d{2})").matcher("")
        private val MATCH_RECURRENCE =
            Pattern.compile("[Rr][Ee][Cc]:((\\+?)\\d*[dDwWmMyYbB])").matcher("")
        private val MATCH_EXT = Pattern.compile("(.+):(.+)").matcher("")
        private val MATCH_PRIORITY = Regex("\\(([A-Z])\\)")
        private val MATCH_SINGLE_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}").matcher("")
        private val MATCH_PHONE_NUMBER = Pattern.compile("[+]?[0-9,#()-]{4,}").matcher("")
        private val MATCH_URI = Pattern.compile("[a-z]+://(\\S+)").matcher("")
        private val MATCH_MAIL = Pattern.compile(
            "[a-zA-Z0-9\\+\\._%\\-]{1,256}" + "@" + "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" + "(" + "\\." + "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" + ")+"
        ).matcher("")

        // fun parse(text: String): ArrayList<TToken> {
        //     val tokens = ArrayList<TToken>()
        //     tokens.add(TextToken(text))
        //     return tokens
        // }

        fun parse(text: String): ArrayList<TToken> {
            synchronized(this) {
                var lexemes = text.lex()
                val tokens = ArrayList<TToken>()

                if (lexemes.take(1) == listOf("x")) {
                    tokens.add(CompletedToken(true))
                    lexemes = lexemes.drop(1)
                    var nextToken = lexemes.getOrElse(0) { "" }
                    MATCH_SINGLE_DATE.reset(nextToken).apply {
                        if (matches()) {
                            tokens.add(CompletedDateToken(lexemes.first()))
                            lexemes = lexemes.drop(1)
                            nextToken = lexemes.getOrElse(0) { "" }
                            MATCH_SINGLE_DATE.reset(nextToken).apply {
                                if (matches()) {
                                    tokens.add(CreateDateToken(lexemes.first()))
                                    lexemes = lexemes.drop(1)
                                }
                            }
                        }
                    }
                }

                var nextToken = lexemes.getOrElse(0) { "" }
                MATCH_PRIORITY.matchEntire(nextToken)?.let {
                    tokens.add(PriorityToken(nextToken))
                    lexemes = lexemes.drop(1)
                }

                nextToken = lexemes.getOrElse(0) { "" }
                MATCH_SINGLE_DATE.reset(nextToken).apply {
                    if (matches()) {
                        tokens.add(CreateDateToken(lexemes.first()))
                        lexemes = lexemes.drop(1)
                    }
                }

                lexemes.forEach { lexeme ->
                    MATCH_LIST.reset(lexeme).apply {
                        if (matches()) {
                            tokens.add(ListToken(lexeme))
                            return@forEach
                        }
                    }
                    MATCH_TAG.reset(lexeme).apply {
                        if (matches()) {
                            tokens.add(TagToken(lexeme))
                            return@forEach
                        }
                    }
                    MATCH_HIDDEN.reset(lexeme).apply {
                        if (matches()) {
                            group(1)?.let { tokens.add(HiddenToken(it)) }
                            return@forEach
                        }
                    }
                    // Match phone numbers before tags to support +31.....
                    // This will make tags which can also be interpreted as phone numbers not possible
                    MATCH_PHONE_NUMBER.reset(lexeme).apply {
                        if (matches()) {
                            tokens.add(PhoneToken(lexeme))
                            return@forEach
                        }
                    }

                    MATCH_DUE.reset(lexeme).apply {
                        if (matches()) {
                            group(1)?.let { tokens.add(DueDateToken(it)) }
                            return@forEach
                        }
                    }
                    MATCH_THRESHOLD.reset(lexeme).apply {
                        if (matches()) {
                            group(1)?.let { tokens.add(ThresholdDateToken(it)) }
                            return@forEach
                        }
                    }
                    MATCH_RECURRENCE.reset(lexeme).apply {
                        if (matches()) {
                            group(1)?.let { tokens.add(RecurrenceToken(it)) }
                            return@forEach
                        }
                    }
                    MATCH_UUID.reset(lexeme).apply {
                        if (matches()) {
                            group(1)?.let { tokens.add(UUIDToken(it)) }
                            return@forEach
                        }
                    }
                    MATCH_URI.reset(lexeme).apply {
                        if (matches()) {
                            tokens.add(LinkToken(lexeme))
                            return@forEach
                        }
                    }
                    MATCH_MAIL.reset(lexeme).apply {
                        if (matches()) {
                            tokens.add(MailToken(lexeme))
                            return@forEach
                        }
                    }
                    MATCH_EXT.reset(lexeme).apply {
                        if (matches()) {
                            val extName = group(1)
                            val extValue = group(2)
                            if (extName != null && extValue != null) {
                                tokens.add(ExtToken(extName, extValue))
                            }
                            return@forEach
                        }
                    }
                    if (lexeme.isBlank()) {
                        tokens.add(WhiteSpaceToken(lexeme))
                    } else {
                        tokens.add(TextToken(lexeme))
                    }
                }
                return tokens
            }
        }
    }
}

interface TToken {
    val text: String
    val value: Any?
    fun isAlpha() = false
}

data class CompletedToken(override val value: Boolean) : TToken {
    override val text: String
        get() = if (value) "x" else ""

}

data class PriorityToken(override val text: String) : TToken {
    override val value: Priority
        get() = Priority.toPriority(text.removeSurrounding("(", ")"))

}

data class ListToken(override val text: String) : TToken {
    override val value: String
        get() = text.substring(1)

}

data class TagToken(override val text: String) : TToken {
    override val value: String
        get() = text.substring(1)

}

// Tokens with the same value as text representation
interface StringValueToken : TToken {
    override val value: String
        get() = text
}

data class CreateDateToken(override val text: String) : StringValueToken
data class CompletedDateToken(override val text: String) : StringValueToken
data class TextToken(override val text: String) : StringValueToken {
    override fun isAlpha(): Boolean {
        return true
    }
}

data class WhiteSpaceToken(override val text: String) : StringValueToken {
    override fun isAlpha(): Boolean {
        return true
    }
}

data class MailToken(override val text: String) : StringValueToken {
    override fun isAlpha(): Boolean {
        return true
    }
}

data class LinkToken(override val text: String) : StringValueToken {
    override fun isAlpha(): Boolean {
        return true
    }
}

data class PhoneToken(override val text: String) : StringValueToken {
    override fun isAlpha(): Boolean {
        return true
    }
}

// Key Value tokens
interface KeyValueToken : TToken {
    val key: String
    val valueStr: String
    override val value: Any?
        get() = valueStr
    override val text: String
        get() = "$key:$valueStr"
}

data class DueDateToken(override val valueStr: String) : KeyValueToken {
    override val key = "due"
}

data class ThresholdDateToken(override val valueStr: String) : KeyValueToken {
    override val key = "t"
}

data class RecurrenceToken(override val valueStr: String) : KeyValueToken {
    override val key = "rec"
}

data class HiddenToken(override val valueStr: String) : KeyValueToken {
    override val key = "h"
    override val value: Boolean
        get() = valueStr == "1"
}

data class ExtToken(override val key: String, override val valueStr: String) : KeyValueToken

data class UUIDToken(override val valueStr: String) : KeyValueToken {
    override val key = "uuid"
}

// Extension functions
fun String.lex(): List<String> = this.split(Pattern.compile("[ \\p{Z}]"))

// Extension because ArrayList.filter requires API 24
fun ArrayList<TToken>.removeWhen(body: (TToken) -> Boolean) {
    val iter = this.iterator()
    while (iter.hasNext()) {
        val e = iter.next()
        if (body.invoke(e)) {
            iter.remove()
        }

    }
}