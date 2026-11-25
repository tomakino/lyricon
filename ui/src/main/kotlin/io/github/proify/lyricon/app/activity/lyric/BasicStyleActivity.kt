/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.bridge.AppBridge.LyricGesturePrefs
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.IconActions
import io.github.proify.lyricon.app.compose.preference.DoubleInputPreference
import io.github.proify.lyricon.app.compose.preference.LongInputPreference
import io.github.proify.lyricon.app.compose.preference.PreferenceValueDisplay
import io.github.proify.lyricon.app.compose.preference.RectInputPreference
import io.github.proify.lyricon.app.compose.preference.StringInputPreference
import io.github.proify.lyricon.app.compose.preference.rememberBooleanPreference
import io.github.proify.lyricon.app.compose.preference.rememberStringPreference
import io.github.proify.lyricon.app.util.AppPrefs
import io.github.proify.lyricon.app.util.Utils
import io.github.proify.lyricon.app.util.editCommit
import io.github.proify.lyricon.lyric.style.BasicStyle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

class BasicLyricStyleActivity : AbstractLyricActivity() {
    private val preferences by lazy { AppPrefs.lyricBasicStylePrefs }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Content()
        }
    }

    @Composable
    private fun Content() {
        val context = LocalContext.current

        AppToolBarListContainer(
            title = stringResource(R.string.activity_basic_settings),
            canBack = true
        ) {
            item(key = "base") {
                SmallTitle(
                    text = stringResource(R.string.section_basic),
                    insideMargin = PaddingValues(
                        start = 26.dp,
                        top = 0.dp,
                        end = 26.dp,
                        bottom = 10.dp
                    )
                )
                Card(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                ) {
                    val anchor = rememberStringPreference(
                        preferences,
                        "lyric_style_base_anchor",
                        BasicStyle.Defaults.ANCHOR
                    )

                    ArrowPreference(
                        title = stringResource(R.string.item_base_anchor),
                        startAction = {
                            IconActions(painterResource(R.drawable.ic_locationon))
                        },
                        summary = anchor.value,
                        onClick = {
                            context.startActivity(
                                Intent(context, AnchorViewTreeActivity::class.java)
                            )
                        }
                    )

                    val insertionOrder = preferences.getInt(
                        "lyric_style_base_insertion_order",
                        BasicStyle.Defaults.INSERTION_ORDER
                    )

                    val selectedIndex = remember { mutableIntStateOf(0) }

                    val optionKeys = listOf(
                        BasicStyle.INSERTION_ORDER_BEFORE,
                        BasicStyle.INSERTION_ORDER_AFTER
                    )

                    val options = listOf(
                        DropdownItem(title = stringResource(R.string.item_base_insertion_before)),
                        DropdownItem(title = stringResource(R.string.item_base_insertion_after)),
                    )

                    optionKeys.forEachIndexed { index, key ->
                        if (insertionOrder == key) {
                            selectedIndex.intValue = index
                        }
                    }

                    OverlaySpinnerPreference(
                        startAction = {
                            IconActions(painterResource(R.drawable.ic_stack))
                        },
                        title = stringResource(R.string.item_base_insertion_order),
                        items = options,
                        selectedIndex = selectedIndex.intValue,
                        onSelectedIndexChange = {
                            selectedIndex.intValue = it
                            preferences.editCommit {
                                putInt(
                                    "lyric_style_base_insertion_order",
                                    optionKeys[it]
                                )
                            }
                        }
                    )

                    val statusStrategyKeys = listOf(
                        BasicStyle.STATUS_COLOR_STRATEGY_PRECISE,
                        BasicStyle.STATUS_COLOR_STRATEGY_COMPAT
                    )

                    val statusStrategyOptions = listOf(
                        DropdownItem(
                            title = stringResource(R.string.option_status_color_strategy_precise)
                        ),
                        DropdownItem(
                            title = stringResource(R.string.option_status_color_strategy_compat)
                        ),
                    )

                    val currentStatusStrategy = preferences.getInt(
                        "lyric_style_base_status_color_strategy",
                        BasicStyle.Defaults.STATUS_COLOR_STRATEGY
                    )

                    val selectedStatusStrategyIndex = remember {
                        mutableIntStateOf(
                            statusStrategyKeys.indexOf(currentStatusStrategy).coerceAtLeast(0)
                        )
                    }

                    OverlaySpinnerPreference(
                        startAction = {
                            IconActions(painterResource(R.drawable.ic_palette))
                        },
                        title = stringResource(R.string.item_status_color_strategy),
                        items = statusStrategyOptions,
                        selectedIndex = selectedStatusStrategyIndex.intValue,
                        onSelectedIndexChange = {
                            selectedStatusStrategyIndex.intValue = it
                            preferences.editCommit {
                                putInt(
                                    "lyric_style_base_status_color_strategy",
                                    statusStrategyKeys[it]
                                )
                            }
                        }
                    )

                    RectInputPreference(
                        preferences,
                        "lyric_style_base_margins",
                        stringResource(R.string.item_base_margins),
                        dialogSummary = stringResource(R.string.dialog_summary_base_margins),
                        startAction = {
                            IconActions(painterResource(R.drawable.ic_margin))
                        },
                    )

                    RectInputPreference(
                        preferences,
                        "lyric_style_base_paddings",
                        stringResource(R.string.item_base_paddings),
                        dialogSummary = stringResource(R.string.dialog_summary_base_paddings),
                        startAction = {
                            IconActions(painterResource(R.drawable.ic_padding))
                        }
                    )

                    DoubleInputPreference(
                        preferences = preferences,
                        key = "lyric_style_base_width",
                        title = stringResource(R.string.item_base_width),
                        dialogSummary = stringResource(R.string.dialog_summary_base_width),
                        range = 0.0..8000.0,
                        startAction = {
                            IconActions(painterResource(R.drawable.ic_width_normal))
                        },
                    )
                    DoubleInputPreference(
                        preferences = preferences,
                        key = "lyric_style_base_width_in_landscape",
                        title = stringResource(R.string.item_base_width_in_landscape),
                        dialogSummary = stringResource(R.string.dialog_summary_base_width_in_landscape),
                        range = 0.0..8000.0,
                        startAction = {
                            IconActions(painterResource(R.drawable.ic_width_normal))
                        },
                    )
                    if (Utils.isOPlus) {
                        DoubleInputPreference(
                            preferences = preferences,
                            key = "lyric_style_base_width_in_coloros_capsule_mode",
                            title = stringResource(R.string.item_base_width_color_os_capsule),
                            dialogSummary = stringResource(R.string.dialog_summary_base_width_color_os_capsule),
                            range = 0.0..8000.0,
                            startAction = {
                                IconActions(painterResource(R.drawable.ic_width_normal))
                            },
                        )
                        DoubleInputPreference(
                            preferences = preferences,
                            key = "lyric_style_base_width_in_coloros_capsule_mode_in_landscape",
                            title = stringResource(R.string.item_base_width_color_os_capsule_in_landscape),
                            dialogSummary = stringResource(R.string.dialog_summary_base_width_color_os_capsule_in_landscape),
                            range = 0.0..8000.0,
                            startAction = {
                                IconActions(painterResource(R.drawable.ic_width_normal))
                            },
                        )
                    }

                    ArrowPreference(
                        startAction = {
                            IconActions(painterResource(R.drawable.ic_visibility))
                        },
                        title = stringResource(R.string.item_config_view_rules),
                        onClick = {
                            context.startActivity(
                                Intent(context, ViewRulesTreeActivity::class.java)
                            )
                        }
                    )

                    StringInputPreference(
                        preferences = preferences,
                        key = "lyric_style_base_blocked_words_regex",
                        title = stringResource(R.string.item_base_blocked_words_regex),
                        dialogSummary = stringResource(R.string.dialog_summary_base_blocked_words_regex),
                        startAction = {
                            IconActions(painterResource(R.drawable.ic_visibility_off))
                        }
                    )

                    ChineseConversionPreference()
                }
            }

            item(key = "visibility") {
                SmallTitle(
                    text = stringResource(R.string.section_visibility),
                    insideMargin = PaddingValues(
                        start = 26.dp,
                        top = 16.dp,
                        end = 26.dp,
                        bottom = 10.dp
                    )
                )
                Card(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 0.dp, end = 16.dp)
                        .fillMaxWidth(),
                ) {

                    var isHideOnLockScreenEnabled by rememberBooleanPreference(
                        preferences,
                        "lyric_style_base_hide_on_lock_screen",
                        BasicStyle.Defaults.HIDE_ON_LOCK_SCREEN
                    )
                    SwitchPreference(
                        checked = isHideOnLockScreenEnabled,
                        onCheckedChange = { isHideOnLockScreenEnabled = it },
                        startAction = {
                            IconActions(painterResource(R.drawable.ic_visibility_off))
                        },
                        title = stringResource(R.string.item_base_lockscreen_hidden),
                    )

                    HideWhenNoLyric()
                    HideWhenNoUpdate()
                    HideWhenKeywords()
                }
            }

            item(key = "gesture") {
                SmallTitle(
                    text = stringResource(R.string.section_gesture),
                    insideMargin = PaddingValues(
                        start = 26.dp,
                        top = 16.dp,
                        end = 26.dp,
                        bottom = 10.dp
                    )
                )
                Card(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 0.dp, end = 16.dp)
                        .fillMaxWidth(),
                ) {

                    var gestureEnabled by rememberBooleanPreference(
                        preferences,
                        LyricGesturePrefs.KEY_ENABLED,
                        LyricGesturePrefs.DEFAULT_ENABLED
                    )
                    SwitchPreference(
                        checked = gestureEnabled,
                        onCheckedChange = { gestureEnabled = it },
                        startAction = {
                            IconActions(painterResource(R.drawable.ic_music_note))
                        },
                        title = stringResource(R.string.item_gesture_enable),
                        summary = stringResource(R.string.item_gesture_summary),
                    )

                    var gestureHapticEnabled by rememberBooleanPreference(
                        preferences,
                        LyricGesturePrefs.KEY_HAPTIC,
                        LyricGesturePrefs.DEFAULT_HAPTIC
                    )
                    SwitchPreference(
                        checked = gestureHapticEnabled,
                        onCheckedChange = { gestureHapticEnabled = it },
                        startAction = {
                            IconActions(painterResource(R.drawable.mobile_vibrate_fill1_24px))
                        },
                        title = stringResource(R.string.item_gesture_haptic),
                        summary = stringResource(R.string.item_gesture_haptic_summary),
                    )

                    GestureActionSpinner(
                        key = LyricGesturePrefs.KEY_SWIPE_LEFT,
                        default = LyricGesturePrefs.DEFAULT_SWIPE_LEFT,
                        title = stringResource(R.string.item_gesture_swipe_left),
                        iconRes = R.drawable.ic_gesture_swipe_left
                    )

                    GestureActionSpinner(
                        key = LyricGesturePrefs.KEY_SWIPE_RIGHT,
                        default = LyricGesturePrefs.DEFAULT_SWIPE_RIGHT,
                        title = stringResource(R.string.item_gesture_swipe_right),
                        iconRes = R.drawable.ic_gesture_swipe_right
                    )

                    GestureActionSpinner(
                        key = LyricGesturePrefs.KEY_TAP,
                        default = LyricGesturePrefs.DEFAULT_TAP,
                        title = stringResource(R.string.item_gesture_tap),
                        iconRes = R.drawable.ic_gesture_tap
                    )

                    GestureActionSpinner(
                        key = LyricGesturePrefs.KEY_LONG_PRESS,
                        default = LyricGesturePrefs.DEFAULT_LONG_PRESS,
                        title = stringResource(R.string.item_gesture_long_press),
                        iconRes = R.drawable.ic_gesture_long_press
                    )
                }
            }

            item("bottom_spacer") {
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    /**
     * 手势动作选择器：一个手势对应一个 [OverlaySpinnerPreference]，
     * 可选项为 [LyricGesturePrefs.ACTION_NONE] 等动作常量
     */
    @Composable
    private fun GestureActionSpinner(
        key: String,
        default: Int,
        title: String,
        iconRes: Int
    ) {
        val optionActions = listOf(
            LyricGesturePrefs.ACTION_NONE,
            LyricGesturePrefs.ACTION_TOGGLE_PLAY,
            LyricGesturePrefs.ACTION_PREVIOUS,
            LyricGesturePrefs.ACTION_NEXT,
            LyricGesturePrefs.ACTION_OPEN_CONTROL
        )

        val optionItems = listOf(
            DropdownItem(title = stringResource(R.string.option_gesture_none)),
            DropdownItem(title = stringResource(R.string.option_gesture_toggle_play)),
            DropdownItem(title = stringResource(R.string.option_gesture_previous)),
            DropdownItem(title = stringResource(R.string.option_gesture_next)),
            DropdownItem(title = stringResource(R.string.option_gesture_open_control)),
        )

        val current = preferences.getInt(key, default)
        val selectedIndex = remember(current) {
            mutableIntStateOf(optionActions.indexOf(current).coerceAtLeast(0))
        }

        OverlaySpinnerPreference(
            startAction = {
                IconActions(painterResource(iconRes))
            },
            title = title,
            items = optionItems,
            selectedIndex = selectedIndex.intValue,
            onSelectedIndexChange = {
                selectedIndex.intValue = it
                preferences.editCommit {
                    putInt(key, optionActions[it])
                }
            }
        )
    }

    @Composable
    private fun HideWhenNoLyric() {
        val hideWhenNoLyricAfterSeconds = rememberStringPreference(
            preferences,
            "lyric_style_base_no_lyric_hide_timeout",
            BasicStyle.Defaults.NO_LYRIC_HIDE_TIMEOUT.toString()
        )
        val hideWhenNoLyricAfterSecondsInt = remember(hideWhenNoLyricAfterSeconds.value) {
            hideWhenNoLyricAfterSeconds.value?.toLongOrNull()
                ?: BasicStyle.Defaults.NO_LYRIC_HIDE_TIMEOUT.toLong()
        }
        val hideWhenNoLyricSummary = remember(hideWhenNoLyricAfterSecondsInt) {
            hideWhenNoLyricAfterSecondsInt
        }.let { seconds ->
            if (seconds <= 0) {
                stringResource(R.string.option_timeout_hide_never)
            } else null
        }

        LongInputPreference(
            preferences = preferences,
            key = "lyric_style_base_no_lyric_hide_timeout",
            title = stringResource(R.string.item_base_timeout_no_lyric),
            dialogSummary = stringResource(R.string.dialog_summary_base_timeout_no_lyric),
            range = 0L..3_600_000L,
            summary = { hideWhenNoLyricSummary },
            startAction = { IconActions(painterResource(R.drawable.update_24px)) },
            display = PreferenceValueDisplay.Time(multiplier = 1000)
        )
    }

    @Composable
    private fun HideWhenNoUpdate() {
        val seconds = rememberStringPreference(
            preferences,
            "lyric_style_base_no_update_hide_timeout",
            BasicStyle.Defaults.NO_UPDATE_HIDE_TIMEOUT.toString()
        )
        val secondsInt = remember(seconds.value) {
            seconds.value?.toLong()
                ?: BasicStyle.Defaults.NO_UPDATE_HIDE_TIMEOUT.toLong()
        }
        val summary = remember(secondsInt) {
            secondsInt
        }.let { seconds ->
            if (seconds <= 0) {
                stringResource(R.string.option_timeout_hide_never)
            } else null
        }

        LongInputPreference(
            preferences = preferences,
            key = "lyric_style_base_no_update_hide_timeout",
            title = stringResource(R.string.item_base_timeout_no_update),
            dialogSummary = stringResource(R.string.dialog_summary_base_timeout_no_update),
            range = 0L..3_600_000L,
            summary = { summary },
            startAction = { IconActions(painterResource(R.drawable.update_24px)) },
            display = PreferenceValueDisplay.Time(multiplier = 1000)
        )
    }

    @Composable
    private fun HideWhenKeywords() {
        @Composable
        fun SecondsInput() {

            val seconds = rememberStringPreference(
                preferences,
                "lyric_style_base_keyword_hide_timeout",
                BasicStyle.Defaults.NO_UPDATE_HIDE_TIMEOUT.toString()
            )
            val secondsInt = remember(seconds.value) {
                seconds.value?.toLong()
                    ?: BasicStyle.Defaults.KEYWORD_HIDE_TIMEOUT.toLong()
            }
            val summary = remember(secondsInt) {
                secondsInt
            }.let { seconds ->
                if (seconds <= 0) {
                    stringResource(R.string.option_timeout_hide_never)
                } else null
            }

            LongInputPreference(
                preferences = preferences,
                key = "lyric_style_base_keyword_hide_timeout",
                title = stringResource(R.string.item_base_timeout_keyword_match),
                dialogSummary = stringResource(R.string.dialog_summary_base_timeout_keyword_match),
                range = 0L..3_600_000L,
                summary = { summary },
                startAction = { IconActions(painterResource(R.drawable.update_24px)) },
                display = PreferenceValueDisplay.Time(multiplier = 1000)
            )
        }

        @Composable
        fun RegexInput() {
            val keywords by rememberStringPreference(
                preferences,
                "lyric_style_base_timeout_hide_keywords",
                if (BasicStyle.Defaults.KEYWORD_HIDE_MATCH.isEmpty()) null
                else BasicStyle.Defaults.KEYWORD_HIDE_MATCH.joinToString()
            )
            val summary = keywords

            StringInputPreference(
                preferences = preferences,
                key = "lyric_style_base_timeout_hide_keywords",
                title = stringResource(R.string.item_base_filter_keyword_list),
                summary = summary,
                dialogSummary = stringResource(R.string.dialog_summary_base_filter_keyword_list),
                startAction = { IconActions(painterResource(R.drawable.regular_expression_24px)) },
                label = stringResource(R.string.hint_filter_keyword_input)
            )
        }

        SecondsInput()
        RegexInput()
    }

    @Suppress("unused")
    @Composable
    private fun ChineseConversionPreference() {
        // 读取当前保存的模式，默认为 OFF (0)
        val currentMode = preferences.getInt(
            "lyric_style_base_chinese_conversion_mode",
            BasicStyle.Defaults.CHINESE_CONVERSION_MODE
        )

        // 定义常量与 UI 索引的映射关系
        val modeOptions = listOf(
            BasicStyle.CHINESE_CONVERSION_OFF,
            BasicStyle.CHINESE_CONVERSION_SIMPLIFIED,
            BasicStyle.CHINESE_CONVERSION_TRADITIONAL
        )

        val selectedIndex = remember(currentMode) {
            val index = modeOptions.indexOf(currentMode)
            mutableIntStateOf(if (index != -1) index else 0)
        }

        val entries = listOf(
            DropdownItem(title = stringResource(R.string.item_base_chinese_conv_off)),
            DropdownItem(title = stringResource(R.string.item_base_chinese_conv_simplified)),
            DropdownItem(title = stringResource(R.string.item_base_chinese_conv_traditional)),
        )

        OverlaySpinnerPreference(
            startAction = {
                IconActions(painterResource(R.drawable.translate_24px))
            },
            title = stringResource(R.string.item_base_chinese_conversion),
            items = entries,
            selectedIndex = selectedIndex.intValue,
            onSelectedIndexChange = { index ->
                selectedIndex.intValue = index
                preferences.editCommit {
                    putInt("lyric_style_base_chinese_conversion_mode", modeOptions[index])
                }
            }
        )
    }

    @Preview(showBackground = true)
    @Composable
    private fun ContentPreview() {
        Content()
    }
}
