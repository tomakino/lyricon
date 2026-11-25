/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose.custom.bonsai.core.node.extension

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal interface SelectableNode {

    val isSelected: Boolean

    var isSelectedState: Boolean

    var onToggleSelected: (Boolean) -> Unit

    fun setSelected(isSelected: Boolean)
}

internal class SelectableNodeHandler : SelectableNode {

    override val isSelected: Boolean
        get() = isSelectedState

    override var isSelectedState: Boolean by mutableStateOf(false)

    override var onToggleSelected: (Boolean) -> Unit by mutableStateOf({})

    override fun setSelected(isSelected: Boolean) {
        onToggleSelected(isSelected)
    }
}
