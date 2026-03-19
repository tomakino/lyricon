/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.IconActions
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperArrow
import io.github.proify.lyricon.app.compose.preference.InputPreference
import io.github.proify.lyricon.app.compose.preference.InputType
import io.github.proify.lyricon.app.compose.preference.RectInputPreference
import io.github.proify.lyricon.app.compose.preference.SwitchPreference
import io.github.proify.lyricon.app.compose.preference.rememberBooleanPreference
import io.github.proify.lyricon.app.compose.preference.rememberStringPreference
import io.github.proify.lyricon.app.util.LyricPrefs
import io.github.proify.lyricon.app.util.Utils
import io.github.proify.lyricon.app.util.editCommit
import io.github.proify.lyricon.lyric.style.BasicStyle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.extra.SuperSpinner

class BasicLyricStyleActivity : AbstractLyricActivity() {
    private val preferences by lazy { LyricPrefs.basicStylePrefs }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences.registerOnSharedPreferenceChangeListener(this)
        setContent {
            Content()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    @Composable
    private fun Content() {
        val context = LocalContext.current

        AppToolBarListContainer(
            title = stringResource(R.string.activity_base_lyric_style),
            canBack = true
        ) {
            item(key = "location") {
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

                    SuperArrow(
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
                        SpinnerEntry(title = stringResource(R.string.item_base_insertion_before)),
                        SpinnerEntry(title = stringResource(R.string.item_base_insertion_after)),
                    )

                    optionKeys.forEachIndexed { index, key ->
                        if (insertionOrder == key) {
                            selectedIndex.intValue = index
                        }
                    }

                    SuperSpinner(
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

                    RectInputPreference(
                        preferences,
                        "lyric_style_base_margins",
                        stringResource(R.string.item_base_margins),
                        leftAction = {
                            IconActions(painterResource(R.drawable.ic_margin))
                        },
                    )

                    RectInputPreference(
                        preferences,
                        "lyric_style_base_paddings",
                        stringResource(R.string.item_base_paddings),
                        leftAction = {
                            IconActions(painterResource(R.drawable.ic_padding))
                        }
                    )


                    InputPreference(
                        sharedPreferences = preferences,
                        key = "lyric_style_base_width",
                        title = stringResource(R.string.item_base_width),
                        inputType = InputType.DOUBLE,
                        maxValue = 1000.0,
                        leftAction = {
                            IconActions(painterResource(R.drawable.ic_width_normal))
                        },
                    )

                    val dynamicWidthEnabled = rememberBooleanPreference(
                        preferences,
                        "lyric_style_base_dynamic_width_enabled",
                        BasicStyle.Defaults.DYNAMIC_WIDTH_ENABLED
                    )

                    SwitchPreference(
                        sharedPreferences = preferences,
                        key = "lyric_style_base_dynamic_width_enabled",
                        defaultValue = BasicStyle.Defaults.DYNAMIC_WIDTH_ENABLED,
                        title = stringResource(R.string.item_base_dynamic_width),
                        summary = stringResource(R.string.item_base_dynamic_width_summary),
                        startAction = {
                            IconActions(painterResource(R.drawable.ic_visibility_off))
                        },
                    )

                    SwitchPreference(
                        sharedPreferences = preferences,
                        key = "lyric_style_base_dynamic_width_auto_hide_clock",
                        defaultValue = BasicStyle.Defaults.DYNAMIC_WIDTH_AUTO_HIDE_CLOCK,
                        title = stringResource(R.string.item_base_dynamic_width_auto_hide_clock),
                        summary = stringResource(
                            if (dynamicWidthEnabled.value) {
                                R.string.item_base_dynamic_width_auto_hide_clock_summary
                            } else {
                                R.string.item_base_dynamic_width_auto_hide_clock_disabled_summary
                            }
                        ),
                        startAction = {
                            IconActions(painterResource(R.drawable.ic_width_normal))
                        },
                        enabled = dynamicWidthEnabled.value
                    )

                    if (Utils.isHyperOs3OrAbove) {
                        SwitchPreference(
                            sharedPreferences = preferences,
                            key = "lyric_style_base_xiaomi_island_temp_hide_enabled",
                            defaultValue = BasicStyle.Defaults.XIAOMI_ISLAND_TEMP_HIDE_ENABLED,
                            title = stringResource(R.string.item_base_xiaomi_island_temp_hide),
                            summary = stringResource(R.string.item_base_xiaomi_island_temp_hide_summary),
                            startAction = {
                                IconActions(painterResource(R.drawable.ic_visibility_off))
                            },
                        )
                    }
                    if (Utils.isOPlus) {
                        InputPreference(
                            sharedPreferences = preferences,
                            key = "lyric_style_base_width_in_coloros_capsule_mode",
                            title = stringResource(R.string.item_base_width_color_os_capsule),
                            inputType = InputType.DOUBLE,
                            maxValue = 1000.0,
                            leftAction = {
                                IconActions(painterResource(R.drawable.ic_width_normal))
                            },
                        )
                    }

                    SuperArrow(
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
                }
            }

            item(key = "visibility") {
                Card(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                ) {

                    SwitchPreference(
                        preferences,
                        "lyric_style_base_hide_on_lock_screen",
                        defaultValue = BasicStyle.Defaults.HIDE_ON_LOCK_SCREEN,
                        startAction = {
                            IconActions(painterResource(R.drawable.ic_visibility_off))
                        },
                        title = stringResource(R.string.item_base_lockscreen_hidden),
                    )

                    SwitchPreference(
                        preferences,
                        "lyric_style_base_double_tap_switch_clock",
                        defaultValue = BasicStyle.Defaults.DOUBLE_TAP_SWITCH_CLOCK,
                        startAction = {
                            IconActions(painterResource(R.drawable.ic_visibility_off))
                        },
                        title = stringResource(R.string.item_base_double_tap_switch_clock),
                    )

                    HideWhenNoLyric()
                    HideWhenNoUpdate()
                    HideWhenKeywords()
                }
            }

            item("bottom_spacer") {
                Spacer(Modifier.height(16.dp))
            }
        }
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

        InputPreference(
            sharedPreferences = preferences,
            key = "lyric_style_base_no_lyric_hide_timeout",
            title = stringResource(R.string.item_base_timeout_no_lyric),
            inputType = InputType.INTEGER,
            maxValue = 3600000.0,
            summary = hideWhenNoLyricSummary,
            leftAction = { IconActions(painterResource(R.drawable.ic_stop_circle)) },
            isTimeUnit = true,
            formatMultiplier = 1000
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

        InputPreference(
            sharedPreferences = preferences,
            key = "lyric_style_base_no_update_hide_timeout",
            title = stringResource(R.string.item_base_timeout_no_update),
            inputType = InputType.INTEGER,
            maxValue = 3600000.0,
            summary = summary,
            leftAction = { IconActions(painterResource(R.drawable.ic_stop_circle)) },
            isTimeUnit = true,
            formatMultiplier = 1000
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

            InputPreference(
                sharedPreferences = preferences,
                key = "lyric_style_base_keyword_hide_timeout",
                title = stringResource(R.string.item_base_timeout_keyword_match),
                inputType = InputType.INTEGER,
                maxValue = 3600000.0,
                summary = summary,
                leftAction = { IconActions(painterResource(R.drawable.ic_stop_circle)) },
                isTimeUnit = true,
                formatMultiplier = 1000
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

            InputPreference(
                sharedPreferences = preferences,
                key = "lyric_style_base_timeout_hide_keywords",
                title = stringResource(R.string.item_base_filter_keyword_list),
                inputType = InputType.STRING,
                summary = summary,
                leftAction = { IconActions(painterResource(R.drawable.ic_stop_circle)) },
                label = stringResource(R.string.hint_filter_keyword_input)
            )
        }

        SecondsInput()
        RegexInput()
    }

    @Preview(showBackground = true)
    @Composable
    private fun ContentPreview() {
        Content()
    }
}
