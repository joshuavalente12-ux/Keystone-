package androidx.compose.runtime

import kotlin.reflect.KProperty

/**
 * Just enough of Compose's state holders for the game logic. The web HUD reads these
 * values every frame instead of being recomposed.
 */
interface State<T> {
    val value: T
}

interface MutableState<T> : State<T> {
    override var value: T
}

private class SimpleState<T>(override var value: T) : MutableState<T>

fun <T> mutableStateOf(value: T): MutableState<T> = SimpleState(value)

operator fun <T> State<T>.getValue(thisObj: Any?, property: KProperty<*>): T = value

operator fun <T> MutableState<T>.setValue(thisObj: Any?, property: KProperty<*>, value: T) {
    this.value = value
}

class MutableIntState(var intValue: Int)
class MutableLongState(var longValue: Long)
class MutableFloatState(var floatValue: Float)

fun mutableIntStateOf(value: Int) = MutableIntState(value)
fun mutableLongStateOf(value: Long) = MutableLongState(value)
fun mutableFloatStateOf(value: Float) = MutableFloatState(value)

operator fun MutableIntState.getValue(thisObj: Any?, property: KProperty<*>): Int = intValue
operator fun MutableIntState.setValue(thisObj: Any?, property: KProperty<*>, value: Int) {
    intValue = value
}

operator fun MutableLongState.getValue(thisObj: Any?, property: KProperty<*>): Long = longValue
operator fun MutableLongState.setValue(thisObj: Any?, property: KProperty<*>, value: Long) {
    longValue = value
}

operator fun MutableFloatState.getValue(thisObj: Any?, property: KProperty<*>): Float = floatValue
operator fun MutableFloatState.setValue(thisObj: Any?, property: KProperty<*>, value: Float) {
    floatValue = value
}

fun <K, V> mutableStateMapOf(): androidx.compose.runtime.snapshots.SnapshotStateMap<K, V> =
    androidx.compose.runtime.snapshots.SnapshotStateMap()

fun <T> mutableStateListOf(): androidx.compose.runtime.snapshots.SnapshotStateList<T> =
    androidx.compose.runtime.snapshots.SnapshotStateList()
