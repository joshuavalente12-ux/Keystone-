package android.content.res

import com.keystone.rpg.WebResources

class Resources {
    /** Resource id by name, e.g. ("voice_intro_1", "raw"); 0 when the game has no such file. */
    fun getIdentifier(name: String, type: String, pkg: String): Int = WebResources.names["$type/$name"] ?: 0
}

class Configuration(
    val screenWidthDp: Int = 0,
    val screenHeightDp: Int = 0,
) {
    val orientation: Int get() = if (screenWidthDp >= screenHeightDp) ORIENTATION_LANDSCAPE else ORIENTATION_PORTRAIT

    companion object {
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2
    }
}
