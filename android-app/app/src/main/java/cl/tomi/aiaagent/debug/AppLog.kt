package cl.tomi.aiaagent.debug

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Buffer de logs visible en la UI para depuración sin ADB.
 */
object AppLog {
    private const val MAX_ENTRIES = 200

    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        add("$tag", message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        add("W/$tag", message)
    }

    private fun add(tag: String, message: String) {
        val time = dateFormat.format(Date())
        val line = "[$time] $tag: $message"
        _entries.value = (_entries.value + line).takeLast(MAX_ENTRIES)
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
