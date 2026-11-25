/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose.custom.bonsai.core.node.extension

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal interface ExpandableNode {

    val isExpanded: Boolean

    var isExpandedState: Boolean

    var onToggleExpanded: (Boolean, Int) -> Unit

    fun setExpanded(isExpanded: Boolean, maxDepth: Int)
}

internal class ExpandableNodeHandler : ExpandableNode {

    override val isExpanded: Boolean
        get() = isExpandedState

    override var isExpandedState: Boolean by mutableStateOf(false)

    override var onToggleExpanded: (Boolean, Int) -> Unit by mutableStateOf({ _, _ -> })

    override fun setExpanded(isExpanded: Boolean, maxDepth: Int) {
        onToggleExpanded(isExpanded, maxDepth)
    }
}
