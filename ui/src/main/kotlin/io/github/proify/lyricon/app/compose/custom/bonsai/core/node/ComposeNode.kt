/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("KotlinConstantConditions")

package io.github.proify.lyricon.app.compose.custom.bonsai.core.node

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import io.github.proify.lyricon.app.compose.custom.bonsai.core.tree.TreeScope

@Composable
fun <T> TreeScope.Leaf(
    backgroundColor: MutableState<Color>,
    content: T,
    customIcon: NodeComponent<T>? = null,
    customName: NodeComponent<T>? = null,
    name: String = content.toString(),
    secondary: String?,
) {
    val (isSelected, setSelected) = rememberSaveable { mutableStateOf(false) }

    ComposeNode<LeafNode<T>, TreeApplier<T>>(
        factory = {
            LeafNode(
                content = content,
                name = name,
                depth = depth,
                iconComponent = customIcon ?: { DefaultNodeIcon(it) },
                nameComponent = customName ?: { DefaultNodeName(it) },
                secondary = secondary,
                backgroundColor = backgroundColor
            )
        },
        update = {
            set(isSelected) { this.isSelectedState = isSelected }
            set(setSelected) { this.onToggleSelected = setSelected }
        }
    )
}

@Composable
fun <T> TreeScope.Branch(
    backgroundColor: MutableState<Color>,
    content: T,
    customIcon: NodeComponent<T>? = null,
    customName: NodeComponent<T>? = null,
    name: String = content.toString(),
    secondary: String?,
    children: @Composable TreeScope.() -> Unit = {},
) {
    val (isSelected, setSelected) = rememberSaveable { mutableStateOf(false) }
    val (isExpanded, setExpanded) = rememberSaveable { mutableStateOf(isExpanded && depth <= expandMaxDepth) }
    val (expandMaxDepth, setExpandMaxDepth) = rememberSaveable { mutableIntStateOf(expandMaxDepth) }

    ComposeNode<BranchNode<T>, TreeApplier<T>>(
        factory = {
            BranchNode(
                content = content,
                name = name,
                depth = depth,
                iconComponent = customIcon ?: { DefaultNodeIcon(it) },
                nameComponent = customName ?: { DefaultNodeName(it) },
                secondary = secondary,
                backgroundColor = backgroundColor
            )
        },
        update = {
            set(isSelected) { this.isSelectedState = isSelected }
            set(setSelected) { this.onToggleSelected = setSelected }
            set(isExpanded) { this.isExpandedState = isExpanded }
            set(setExpanded) {
                this.onToggleExpanded = { isExpanded, maxDepth ->
                    setExpanded(isExpanded)
                    setExpandMaxDepth(maxDepth)
                }
            }
        }
    )

    if (isExpanded && depth <= expandMaxDepth) {
        TreeScope(depth.inc(), isExpanded, expandMaxDepth)
            .children()
    }
}

internal class TreeApplier<T> : AbstractApplier<Node<T>?>(null) {

    val children = mutableStateListOf<Node<T>>()

    override fun insertTopDown(index: Int, instance: Node<T>?) {
        checkNotNull(instance)
        check(current == null)
        children.add(index, instance)
    }

    override fun insertBottomUp(index: Int, instance: Node<T>?) = Unit

    override fun remove(index: Int, count: Int) {
        check(current == null)
        children.removeRange(index, index + count)
    }

    override fun move(from: Int, to: Int, count: Int) {
        check(current == null)
        @Suppress("UNCHECKED_CAST")
        (children as MutableList<Node<T>?>).move(from, to, count)
    }

    override fun onClear() {
        check(current == null)
        children.clear()
    }
}