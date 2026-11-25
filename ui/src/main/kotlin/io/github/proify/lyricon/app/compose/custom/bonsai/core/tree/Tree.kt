/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose.custom.bonsai.core.tree

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import io.github.proify.lyricon.app.compose.custom.bonsai.core.node.Node
import io.github.proify.lyricon.app.compose.custom.bonsai.core.node.TreeApplier
import io.github.proify.lyricon.app.compose.custom.bonsai.core.tree.extension.ExpandableTree
import io.github.proify.lyricon.app.compose.custom.bonsai.core.tree.extension.ExpandableTreeHandler
import io.github.proify.lyricon.app.compose.custom.bonsai.core.tree.extension.SelectableTree
import io.github.proify.lyricon.app.compose.custom.bonsai.core.tree.extension.SelectableTreeHandler

@DslMarker
private annotation class TreeMarker

@Immutable
@TreeMarker
data class TreeScope(
    val depth: Int,
    internal val isExpanded: Boolean = false,
    internal val expandMaxDepth: Int = 0
)

@Stable
class Tree<T> internal constructor(
    val nodes: List<Node<T>>
) : ExpandableTree<T> by ExpandableTreeHandler(nodes),
    SelectableTree<T> by SelectableTreeHandler(nodes)

@SuppressLint("ComposableNaming")
@Composable
fun <T> Tree(
    content: @Composable TreeScope.() -> Unit
): Tree<T> {
    val applier = remember { TreeApplier<T>() }
    val compositionContext = rememberCompositionContext()
    val composition =
        remember(applier, compositionContext) { Composition(applier, compositionContext) }
    composition.setContent { TreeScope(depth = 0).content() }
    return remember(applier) { Tree(applier.children) }
}