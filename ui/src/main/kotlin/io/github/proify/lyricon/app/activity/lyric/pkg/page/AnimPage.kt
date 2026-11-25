/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric.pkg.page

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.IconActions
import io.github.proify.lyricon.app.compose.custom.miuix.basic.ScrollBehavior
import io.github.proify.lyricon.app.compose.preference.rememberBooleanPreference
import io.github.proify.lyricon.app.compose.preference.rememberStringPreference
import io.github.proify.lyricon.lyric.style.AnimStyle
import io.github.proify.lyricon.lyric.view.yoyo.YoYoPresets
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun AnimPage(
    scrollBehavior: ScrollBehavior,
    sharedPreferences: SharedPreferences
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        val registry = YoYoPresets.registry
        val keys = registry.keys.toList()
        var selectedId by rememberStringPreference(
            sharedPreferences, "lyric_style_anim_id",
            AnimStyle.Defaults.ID
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {

            item("enable") {
                Card(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                ) {
                    var enable by rememberBooleanPreference(
                        sharedPreferences,
                        "lyric_style_anim_enable",
                        AnimStyle.Defaults.ENABLE
                    )

                    SwitchPreference(
                        checked = enable,
                        onCheckedChange = {
                            enable = it
                        },
                        startAction = {
                            IconActions(painterResource(R.drawable.masked_transitions_24px))
                        },
                        title = stringResource(R.string.item_logo_enable),
                    )
                }
            }

            item("speed") {
                var currentSpeed by rememberStringPreference(
                    sharedPreferences,
                    "lyric_style_anim_speed",
                    AnimStyle.Defaults.SPEED
                )
                val speedOptions = listOf(
                    stringResource(R.string.option_anim_speed_fast),
                    stringResource(R.string.option_anim_speed_normal),
                    stringResource(R.string.option_anim_speed_slow)
                )
                val speedValues = listOf("fast", "normal", "slow")
                var selectedSpeedIndex by remember(currentSpeed) {
                    mutableIntStateOf(
                        speedValues.indexOf(currentSpeed).takeIf { it >= 0 } ?: 1
                    )
                }

                Card(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                ) {
                    OverlayDropdownPreference(
                        startAction = {
                            IconActions(painterResource(R.drawable.ic_speed))
                        },
                        title = stringResource(R.string.item_anim_speed),
                        items = speedOptions,
                        selectedIndex = selectedSpeedIndex,
                        onSelectedIndexChange = {
                            selectedSpeedIndex = it
                            currentSpeed = speedValues[it]
                        }
                    )
                }
            }

            item("list") {
                Card(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                        .fillMaxWidth(),
                ) {
                    val context = LocalContext.current
                    keys.forEach { key ->
                        CheckboxPreference(
                            title = YoYoTranslates.getLabel(context, key),
                            checked = selectedId == key,
                            onCheckedChange = {
                                selectedId = key
                            }
                        )
                    }
                }
            }
        }
    }
}
