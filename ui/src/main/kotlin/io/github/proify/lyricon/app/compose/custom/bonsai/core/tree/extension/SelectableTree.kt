/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose.custom.bonsai.core.tree.extension

import android.annotation.SuppressLint
import io.github.proify.lyricon.app.compose.custom.bonsai.core.node.BranchNode
import io.github.proify.lyricon.app.compose.custom.bonsai.core.node.LeafNode
import io.github.proify.lyricon.app.compose.custom.bonsai.core.node.Node

interface SelectableTree<T> {

    val selectedNodes: List<Node<T>>

    fun toggleSelection(node: Node<T>)

    fun selectNode(node: Node<T>)

    fun unselectNode(node: Node<T>)

    fun clearSelection()
}

internal class SelectableTreeHandler<T>(
    private val nodes: List<Node<T>>
) : SelectableTree<T> {

    override val selectedNodes: List<Node<T>>
        get() = nodes.filter { it.isSelected }

    override fun toggleSelection(node: Node<T>) {
        if (node.isSelected) unselectNode(node)
        else selectNode(node)
    }

    override fun selectNode(node: Node<T>) {
        node.setSelected(true)
    }

    override fun unselectNode(node: Node<T>) {
        node.setSelected(false)
    }

    override fun clearSelection() {
        selectedNodes.forEach { it.setSelected(false) }
    }

    @SuppressLint("MemberExtensionConflict")
    private fun Node<T>.setSelected(isSelected: Boolean) {
        when (this) {
            is LeafNode -> setSelected(isSelected)
            is BranchNode -> setSelected(isSelected)
        }
    }
}
