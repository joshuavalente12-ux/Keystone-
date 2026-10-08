package androidx.compose.runtime.snapshots

class SnapshotStateMap<K, V>(private val map: LinkedHashMap<K, V> = LinkedHashMap()) : MutableMap<K, V> by map

class SnapshotStateList<T>(private val list: ArrayList<T> = ArrayList()) : MutableList<T> by list
