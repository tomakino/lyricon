/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.MaterialPalette
import io.github.proify.lyricon.app.compose.custom.bonsai.core.node.Node
import io.github.proify.lyricon.app.compose.theme.CurrentThemeConfigs
import io.github.proify.lyricon.app.util.AppThemeUtils
import io.github.proify.lyricon.app.util.AppPrefs
import io.github.proify.lyricon.app.util.editCommit
import io.github.proify.lyricon.common.util.ViewTreeNode
import io.github.proify.lyricon.lyric.style.BasicStyle

class AnchorViewTreeActivity : ViewTreeActivity() {
    val preferences: SharedPreferences by lazy { AppPrefs.lyricBasicStylePrefs }
    private var currentAnchor: String = BasicStyle.Defaults.ANCHOR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentAnchor =
            preferences.getString("lyric_style_base_anchor", currentAnchor) ?: currentAnchor
    }

    override fun getToolBarTitle(): String = getString(R.string.activity_anchor)

    @Composable
    override fun OnScaffoldCreated() {
        // no op
    }

    override fun resetSettings() {
        saveAnchorId(BasicStyle.Defaults.ANCHOR)
    }

    internal fun saveAnchorId(id: String) {
        preferences.editCommit { putString("lyric_style_base_anchor", id) }
        currentAnchor = id
        refreshTreeDisplay()

        window.decorView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    override fun createViewModel(): ViewTreeViewModel = @SuppressLint("StaticFieldLeak")
    object : ViewTreeViewModel() {
        override fun handleNodeClick(node: Node<ViewTreeNode>) {
            val value = node.content
            val id = value.id
            if (id.isNullOrBlank() || id == "status_bar" || id == currentAnchor) return
            saveAnchorId(id)
        }

        override fun getNodeColor(node: ViewTreeNode): Color =
            when (node.id) {
                currentAnchor -> if (AppThemeUtils.isEnableMonet(application)) CurrentThemeConfigs.primaryContainer else MaterialPalette.Green.Primary
                else -> Color.Transparent
            }
    }
}