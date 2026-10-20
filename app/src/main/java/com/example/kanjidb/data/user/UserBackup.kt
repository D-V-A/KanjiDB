package com.example.kanjidb.data.user

import android.util.JsonReader
import android.util.JsonToken
import com.example.kanjidb.ui.LearningState
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader
import java.time.Instant

/** Public file DTOs deliberately do not expose Room entities or its schema version. */
data class BackupState(val character: String, val state: String, val manualPosition: Long)
data class BackupMember(val character: String, val manualIndex: Long)
data class BackupList(val id: Long, val name: String, val manualIndex: Long, val members: List<BackupMember>)
data class FullBackupData(val states: List<BackupState>, val lists: List<BackupList>) {
    val hasData get() = states.isNotEmpty() || lists.isNotEmpty()
    val characters get() = (states.map { it.character } + lists.flatMap { it.members.map { m -> m.character } }).distinct()
    fun missingCharacters(available: Set<String>) = characters.filter { it !in available }
}
data class UserBackup(val appVersionName: String, val appVersionCode: Long, val exportedAt: String,
    val data: FullBackupData)

class BackupException(message: String) : Exception(message)

/** Only full/v1 is supported today; version/type dispatch stays independent of app versions. */
object UserBackupFormat {
    const val INVALID_DATA_FILE = "This data file seems corrupted or invalid. Please check its internal structure."
    const val UNSUPPORTED_FORMAT_VERSION = "This data file uses an unsupported format version."
    fun validateFileName(name: String?) {
        if (name == null || !name.endsWith(".kdb", ignoreCase = true))
            throw BackupException("Choose a .kdb data file.")
    }

    fun encode(backup: UserBackup): String {
        val data = JSONObject().put("states", JSONArray().apply {
            backup.data.states.forEach { put(JSONObject().put("character", it.character)
                .put("state", it.state).put("manualPosition", it.manualPosition)) }
        }).put("lists", JSONArray().apply {
            backup.data.lists.forEach { list -> put(JSONObject().put("id", list.id).put("name", list.name)
                .put("manualIndex", list.manualIndex).put("members", JSONArray().apply {
                    list.members.forEach { put(JSONObject().put("character", it.character).put("manualIndex", it.manualIndex)) }
                })) }
        })
        return JSONObject().put("formatVersion", 1).put("backupType", "full")
            .put("appVersionName", backup.appVersionName).put("appVersionCode", backup.appVersionCode)
            .put("exportedAt", backup.exportedAt).put("data", data).toString(2)
    }

    fun decode(text: String): UserBackup {
        if (text.isBlank()) throw BackupException("This data file is empty.")
        try {
            // JSONObject alone accepts JavaScript-like syntax. Validate strict JSON first,
            // including duplicate keys, trailing content and bounded nesting.
            JsonReader(StringReader(text)).use { reader ->
                strictValue(reader, 0)
                require(reader.peek() == JsonToken.END_DOCUMENT)
            }
            val root = JSONObject(text)
            when (root.integer("formatVersion")) {
                1L -> Unit
                else -> throw BackupException(UNSUPPORTED_FORMAT_VERSION)
            }
            when (root.string("backupType")) {
                "full" -> Unit
                else -> throw BackupException("This data file contains an unsupported data type.")
            }
            val versionName = root.string("appVersionName").also { require(it.isNotBlank()) }
            val versionCode = root.integer("appVersionCode").also { require(it > 0) }
            val date = root.string("exportedAt").also { Instant.parse(it) }
            val payload = root.getJSONObject("data")
            val states = payload.getJSONArray("states").objects().map {
                BackupState(it.character(), it.string("state").also { state ->
                    require(state == "LEARNING" || state == "KNOWN")
                }, it.integer("manualPosition").also { position -> require(position >= 0 && position < Long.MAX_VALUE) })
            }
            require(states.map { it.character }.distinct().size == states.size)
            val names = mutableListOf<CustomListEntity>()
            val lists = payload.getJSONArray("lists").objects().map { list ->
                val id = list.integer("id").also { require(it > 0 && it < Long.MAX_VALUE) }
                val name = list.string("name")
                require(customListNameError(name, names) == null)
                names += CustomListEntity(id, name, 0)
                val members = list.getJSONArray("members").objects().map {
                    BackupMember(it.character(), it.integer("manualIndex").also { index -> require(index >= 0 && index < Long.MAX_VALUE) })
                }
                require(members.map { it.character }.distinct().size == members.size)
                BackupList(id, name, list.integer("manualIndex").also { require(it >= 0 && it < Long.MAX_VALUE) }, members)
            }
            require(lists.map { it.id }.distinct().size == lists.size)
            return UserBackup(versionName, versionCode, date, FullBackupData(states, lists))
        } catch (error: BackupException) {
            throw error
        } catch (_: Exception) {
            throw BackupException(INVALID_DATA_FILE)
        }
    }

    private fun JSONObject.string(key: String): String = get(key).also { require(it is String) } as String
    private fun JSONObject.integer(key: String): Long {
        val value = get(key)
        require(value is Int || value is Long)
        return (value as Number).toLong()
    }
    private fun JSONObject.character(): String = string("character").also {
        require(it.codePointCount(0, it.length) == 1)
        val point = it.codePointAt(0)
        // Do not depend on the OS Unicode version recognizing a newer CJK extension.
        require(point !in 0xD800..0xDFFF && !Character.isISOControl(point) && !Character.isWhitespace(point))
    }
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private fun strictValue(reader: JsonReader, depth: Int) {
        require(depth < 32)
        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                val keys = hashSetOf<String>()
                while (reader.hasNext()) { require(keys.add(reader.nextName())); strictValue(reader, depth + 1) }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                while (reader.hasNext()) strictValue(reader, depth + 1)
                reader.endArray()
            }
            JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> reader.nextNull()
            else -> error("Invalid JSON")
        }
    }
}

internal fun FullBackupData.stateEntities() = states.map {
    UserKanjiStateEntity(it.character, LearningState.valueOf(it.state), it.manualPosition)
}
