/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.app.bridge.LyriconBridge
import io.github.proify.lyricon.app.compose.MaterialPalette
import io.github.proify.lyricon.app.compose.custom.bonsai.core.node.Node
import io.github.proify.lyricon.app.compose.theme.CurrentThemeConfigs
import io.github.proify.lyricon.app.util.AppThemeUtils
import io.github.proify.lyricon.app.util.AppPrefs
import io.github.proify.lyricon.common.PackageNames
import io.github.proify.lyricon.common.util.ViewTreeNode
import io.github.proify.lyricon.lyric.style.VisibilityRule
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowBottomSheet

class ViewRulesTreeActivity : ViewTreeActivity() {
    internal val viewModel: RuleViewModel by viewModels()

    override fun onDestroy() {
        super.onDestroy()
        clearNormalRules()
    }

    private fun clearNormalRules() {
        AppPrefs.setLyricViewVisibilityRule(
            AppPrefs.getViewVisibilityRule().filterNot { it.mode == VisibilityRule.MODE_NORMAL }
        )
    }

    override fun getToolBarTitle(): String = getString(R.string.activity_view_rules)

    override fun resetSettings() {
        AppPrefs.setLyricViewVisibilityRule(null)
        refreshTreeDisplay()
    }

    override fun createViewModel(): ViewTreeViewModel = @SuppressLint("StaticFieldLeak")
    object : ViewTreeViewModel() {
        override fun handleNodeClick(node: Node<ViewTreeNode>) {
            val nodeId = node.content.id ?: return
            viewModel.openSelection(nodeId)
        }

        override fun getNodeColor(node: ViewTreeNode): Color {
            val rules = AppPrefs.getViewVisibilityRule()
            val hasActiveRule =
                rules.any { it.id == node.id && it.mode != VisibilityRule.MODE_NORMAL }
            return if (hasActiveRule) if (AppThemeUtils.isEnableMonet(application)) CurrentThemeConfigs.primaryContainer else MaterialPalette.Green.Primary
            else Color.Transparent
        }
    }

    private fun highlightView(id: String) {
        LyriconBridge.with(this)
            .to(PackageNames.SYSTEM_UI)
            .key(AppBridgeConstants.REQUEST_HIGHLIGHT_VIEW)
            .payload(Bundle().apply {
                putString("id", id)
            })
            .send()
    }

    @Composable
    override fun OnScaffoldCreated() {
        LaunchedEffect(viewModel.showOptions.value) {
            highlightView(if (viewModel.showOptions.value) viewModel.editId.value else "")
        }

        val currentMode by viewModel.currentMode

        VisibilityRuleBottomSheet(
            show = viewModel.showOptions,
            nodeId = viewModel.editId.value,
            selectedMode = currentMode,
            onModeSelected = { newMode ->
                viewModel.updateRule(newMode)
                refreshTreeDisplay()
            }
        )
    }

    class RuleViewModel : ViewModel() {
        val showOptions: MutableState<Boolean> = mutableStateOf(false)
        val editId: MutableState<String> = mutableStateOf("")

        private val _currentMode = mutableIntStateOf(VisibilityRule.MODE_NORMAL)
        val currentMode: State<Int> = _currentMode

        fun openSelection(id: String) {
            editId.value = id
            val rules = AppPrefs.getViewVisibilityRule()
            _currentMode.intValue = rules.find { it.id == id }?.mode ?: VisibilityRule.MODE_NORMAL
            showOptions.value = true
        }

        fun updateRule(newMode: Int) {
            val rules = AppPrefs.getViewVisibilityRule().toMutableList()
            val existingIndex = rules.indexOfFirst { it.id == editId.value }

            if (existingIndex != -1) {
                rules[existingIndex] = VisibilityRule(id = editId.value, mode = newMode)
            } else {
                rules.add(VisibilityRule(id = editId.value, mode = newMode))
            }

            AppPrefs.setLyricViewVisibilityRule(rules)
            _currentMode.intValue = newMode
            showOptions.value = false
        }
    }

    @Composable
    internal fun VisibilityRuleBottomSheet(
        show: MutableState<Boolean>,
        nodeId: String,
        selectedMode: Int,
        onModeSelected: (Int) -> Unit
    ) {
        val options = remember {
            listOf(
                VisibilityOption(VisibilityRule.MODE_NORMAL, R.string.option_visibility_default),
                VisibilityOption(
                    VisibilityRule.MODE_HIDE_WHEN_PLAYING,
                    R.string.option_visibility_hide_when_playing
                )
            )
        }

        val context = LocalContext.current

        WindowBottomSheet(
            show = show.value,
            modifier = Modifier,
            title = nodeId,
            backgroundColor = MiuixTheme.colorScheme.surface,
            onDismissRequest = { show.value = false },
            insideMargin = DpSize(0.dp, 0.dp),
            content = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .overScrollVertical(),
                )
                {
                    items(options, key = { it.titleRes }) {
                        VisibilityOptionItem(it, selectedMode, onModeSelected, context)
                    }
                }
            })
    }

    @Composable
    private fun VisibilityOptionItem(
        option: VisibilityOption,
        selectedMode: Int,
        onModeSelected: (Int) -> Unit,
        context: Context
    ) {
        Card(
            modifier =
                Modifier
                    .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)
                    .fillMaxWidth()
        ) {
            CheckboxPreference(
                title = stringResource(option.titleRes),
                checked = selectedMode == option.mode,
                onCheckedChange = { isChecked ->
                    if (isChecked && selectedMode != option.mode) {
                        onModeSelected(option.mode)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            (context as? Activity)?.window?.decorView?.performHapticFeedback(
                                HapticFeedbackConstants.TOGGLE_ON
                            )
                        } else {
                            (context as? Activity)?.window?.decorView?.performHapticFeedback(
                                HapticFeedbackConstants.CONTEXT_CLICK
                            )
                        }
                    }
                }
            )
        }
    }

    private data class VisibilityOption(
        val mode: Int,
        val titleRes: Int
    )
}