/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.style

import android.content.SharedPreferences
import android.os.Parcelable
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.safeDecode
import io.github.proify.android.extensions.toJson
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Parcelize
data class BasicStyle(
    var anchor: String = Defaults.ANCHOR,
    var insertionOrder: Int = Defaults.INSERTION_ORDER,
    var width: Float = Defaults.WIDTH,
    var widthInColorOSCapsuleMode: Float = Defaults.WIDTH_IN_COLOROS_CAPSULE_MODE,
    var dynamicWidthEnabled: Boolean = Defaults.DYNAMIC_WIDTH_ENABLED,
    var dynamicWidthAutoHideClock: Boolean = Defaults.DYNAMIC_WIDTH_AUTO_HIDE_CLOCK,
    var xiaomiIslandTempHideEnabled: Boolean = Defaults.XIAOMI_ISLAND_TEMP_HIDE_ENABLED,
    var margins: RectF = Defaults.MARGINS,
    var paddings: RectF = Defaults.PADDINGS,
    var visibilityRules: List<VisibilityRule> = Defaults.VISIBILITY_RULES,
    var hideOnLockScreen: Boolean = Defaults.HIDE_ON_LOCK_SCREEN,
    var noLyricHideTimeout: Int = Defaults.NO_LYRIC_HIDE_TIMEOUT,
    var noUpdateHideTimeout: Int = Defaults.NO_UPDATE_HIDE_TIMEOUT,
    var keywordHideTimeout: Int = Defaults.KEYWORD_HIDE_TIMEOUT,
    var keywordHideMatches: List<String> = Defaults.KEYWORD_HIDE_MATCH
) : AbstractStyle(), Parcelable {

    @IgnoredOnParcel
    @Transient
    var keywordsHidePattern: List<Regex>? = mutableListOf()
        get() = if (field == null) {
            val list = keywordHideMatches.mapNotNull {
                try {
                    Regex(it)
                } catch (_: Exception) {
                    null
                }
            }
            field = list
            field
        } else {
            field
        }

    override fun onLoad(preferences: SharedPreferences) {
        anchor =
            preferences.getString("lyric_style_base_anchor", Defaults.ANCHOR) ?: Defaults.ANCHOR
        insertionOrder =
            preferences.getInt("lyric_style_base_insertion_order", Defaults.INSERTION_ORDER)
        width = preferences.getFloat("lyric_style_base_width", Defaults.WIDTH)
        widthInColorOSCapsuleMode = preferences.getFloat(
            "lyric_style_base_width_in_coloros_capsule_mode",
            Defaults.WIDTH_IN_COLOROS_CAPSULE_MODE
        )
        dynamicWidthEnabled = preferences.getBoolean(
            "lyric_style_base_dynamic_width_enabled",
            Defaults.DYNAMIC_WIDTH_ENABLED
        )
        dynamicWidthAutoHideClock = preferences.getBoolean(
            "lyric_style_base_dynamic_width_auto_hide_clock",
            Defaults.DYNAMIC_WIDTH_AUTO_HIDE_CLOCK
        )
        xiaomiIslandTempHideEnabled = preferences.getBoolean(
            "lyric_style_base_xiaomi_island_temp_hide_enabled",
            Defaults.XIAOMI_ISLAND_TEMP_HIDE_ENABLED
        )

        margins = json.safeDecode<RectF>(
            preferences.getString("lyric_style_base_margins", null),
            Defaults.MARGINS
        )
        paddings = json.safeDecode<RectF>(
            preferences.getString("lyric_style_base_paddings", null),
            Defaults.PADDINGS
        )
        visibilityRules = json.safeDecode<MutableList<VisibilityRule>>(
            preferences.getString("lyric_style_base_visibility_rules", null),
            Defaults.VISIBILITY_RULES.toMutableList()
        )
        hideOnLockScreen = preferences.getBoolean(
            "lyric_style_base_hide_on_lock_screen",
            Defaults.HIDE_ON_LOCK_SCREEN
        )

        noLyricHideTimeout = preferences.getInt(
            "lyric_style_base_no_lyric_hide_timeout",
            Defaults.NO_LYRIC_HIDE_TIMEOUT
        )
        noUpdateHideTimeout = preferences.getInt(
            "lyric_style_base_no_update_hide_timeout",
            Defaults.NO_UPDATE_HIDE_TIMEOUT
        )
        keywordHideTimeout = preferences.getInt(
            "lyric_style_base_keyword_hide_timeout",
            Defaults.KEYWORD_HIDE_TIMEOUT
        )

        preferences.getString("lyric_style_base_timeout_hide_keywords", null)
            ?.trim()
            ?.lines()
            .let {
                keywordHideMatches = it ?: emptyList()
                keywordsHidePattern = null
            }
    }

    override fun onWrite(editor: SharedPreferences.Editor) {
        editor.putString("lyric_style_base_anchor", anchor)
        editor.putInt("lyric_style_base_insertion_order", insertionOrder)
        editor.putFloat("lyric_style_base_width", width)
        editor.putFloat("lyric_style_base_width_in_coloros_capsule_mode", widthInColorOSCapsuleMode)
        editor.putBoolean("lyric_style_base_dynamic_width_enabled", dynamicWidthEnabled)
        editor.putBoolean("lyric_style_base_dynamic_width_auto_hide_clock", dynamicWidthAutoHideClock)
        editor.putBoolean(
            "lyric_style_base_xiaomi_island_temp_hide_enabled",
            xiaomiIslandTempHideEnabled
        )
        editor.putString("lyric_style_base_margins", margins.toJson())
        editor.putString("lyric_style_base_paddings", paddings.toJson())
        editor.putString("lyric_style_base_visibility_rules", visibilityRules.toJson())
        editor.putBoolean("lyric_style_base_hide_on_lock_screen", hideOnLockScreen)
        editor.putInt(
            "lyric_style_base_no_lyric_hide_timeout",
            noLyricHideTimeout
        )
        editor.putInt(
            "lyric_style_base_no_update_hide_timeout",
            noUpdateHideTimeout
        )
        editor.putInt(
            "lyric_style_base_keyword_hide_timeout",
            keywordHideTimeout
        )
        editor.putString("lyric_style_base_timeout_hide_keywords", keywordHideMatches.toJson())
    }

    object Defaults {

        const val HIDE_ON_LOCK_SCREEN: Boolean = true
        const val ANCHOR: String = "clock"
        const val INSERTION_ORDER: Int = INSERTION_ORDER_BEFORE
        const val WIDTH: Float = 100f
        const val WIDTH_IN_COLOROS_CAPSULE_MODE: Float = 70f
        const val DYNAMIC_WIDTH_ENABLED: Boolean = false
        const val DYNAMIC_WIDTH_AUTO_HIDE_CLOCK: Boolean = false
        const val XIAOMI_ISLAND_TEMP_HIDE_ENABLED: Boolean = true
        val MARGINS: RectF = RectF()
        val PADDINGS: RectF = RectF()
        val VISIBILITY_RULES: List<VisibilityRule> = emptyList()
        const val NO_LYRIC_HIDE_TIMEOUT: Int = 0
        const val NO_UPDATE_HIDE_TIMEOUT = 0
        const val KEYWORD_HIDE_TIMEOUT: Int = 0
        val KEYWORD_HIDE_MATCH: List<String> = listOf()
    }

    companion object {
        const val INSERTION_ORDER_BEFORE: Int = 0
        const val INSERTION_ORDER_AFTER: Int = 1
    }
}
