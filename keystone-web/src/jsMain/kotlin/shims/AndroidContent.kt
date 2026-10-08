package android.content

import kotlinx.browser.window

/**
 * Where the web build keeps saved data. Defaults to the browser's localStorage (or memory
 * when that is blocked, e.g. in a sandboxed frame); the host can swap in another store,
 * such as the player's account on the website.
 */
interface KeyValueStore {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)
    fun keys(): List<String>
}

object WebStore : KeyValueStore {
    private val memory = HashMap<String, String>()
    var backing: KeyValueStore? = null

    private val local = runCatching { window.localStorage.also { it.getItem("probe") } }.getOrNull()

    override fun get(key: String): String? {
        backing?.let { return it.get(key) }
        return local?.getItem(key) ?: memory[key]
    }

    override fun set(key: String, value: String) {
        backing?.let { return it.set(key, value) }
        val l = local
        if (l != null) runCatching { l.setItem(key, value) }.onFailure { memory[key] = value } else memory[key] = value
    }

    override fun remove(key: String) {
        backing?.let { return it.remove(key) }
        if (local != null) local.removeItem(key) else memory.remove(key)
    }

    override fun keys(): List<String> {
        backing?.let { return it.keys() }
        if (local == null) return memory.keys.toList()
        return (0 until local.length).mapNotNull { local.key(it) }
    }
}

interface SharedPreferences {
    val all: Map<String, *>
    fun getString(key: String, def: String?): String?
    fun getInt(key: String, def: Int): Int
    fun getLong(key: String, def: Long): Long
    fun getFloat(key: String, def: Float): Float
    fun getBoolean(key: String, def: Boolean): Boolean
    fun contains(key: String): Boolean
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun commit(): Boolean
        fun apply()
    }
}

/** One named preferences file, stored as "name/key" entries in [WebStore]. */
private class WebPrefs(name: String) : SharedPreferences {
    private val prefix = "keystone:$name/"

    private fun raw(key: String) = WebStore.get(prefix + key)

    override val all: Map<String, *>
        get() = WebStore.keys().filter { it.startsWith(prefix) }.associate { it.removePrefix(prefix) to WebStore.get(it) }

    override fun getString(key: String, def: String?) = raw(key) ?: def
    override fun getInt(key: String, def: Int) = raw(key)?.toIntOrNull() ?: def
    override fun getLong(key: String, def: Long) = raw(key)?.toLongOrNull() ?: def
    override fun getFloat(key: String, def: Float) = raw(key)?.toFloatOrNull() ?: def
    override fun getBoolean(key: String, def: Boolean) = raw(key)?.toBooleanStrictOrNull() ?: def
    override fun contains(key: String) = raw(key) != null

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = LinkedHashMap<String, String?>()
        private var wipe = false

        override fun putString(key: String, value: String?) = also { changes[key] = value }
        override fun putInt(key: String, value: Int) = also { changes[key] = value.toString() }
        override fun putLong(key: String, value: Long) = also { changes[key] = value.toString() }
        override fun putFloat(key: String, value: Float) = also { changes[key] = value.toString() }
        override fun putBoolean(key: String, value: Boolean) = also { changes[key] = value.toString() }
        override fun remove(key: String) = also { changes[key] = null }
        override fun clear() = also {
            wipe = true
            changes.clear()
        }

        override fun commit(): Boolean {
            if (wipe) for (k in WebStore.keys()) if (k.startsWith(prefix)) WebStore.remove(k)
            for ((k, v) in changes) if (v == null) WebStore.remove(prefix + k) else WebStore.set(prefix + k, v)
            wipe = false
            changes.clear()
            return true
        }

        override fun apply() {
            commit()
        }
    }
}

open class Context {
    open val applicationContext: Context get() = this
    open val packageName: String get() = "com.keystone.rpg"
    val resources = android.content.res.Resources()

    fun getString(id: Int): String = com.keystone.rpg.WebResources.strings[id] ?: ""

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = WebPrefs(name)

    companion object {
        const val MODE_PRIVATE = 0
    }
}

open class ContextWrapper(val baseContext: Context) : Context()
