/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package io.github.proify.lyricon.app.util

object ShellUtils {
    fun execCmd(
        commands: Array<String>?,
        isRoot: Boolean = false,
        isNeedResultMsg: Boolean = false
    ): CommandResult {
        if (commands.isNullOrEmpty()) return CommandResult(-1, "", "")

        return try {
            val p = ProcessBuilder(if (isRoot) "su" else "sh").start()
            p.outputStream.bufferedWriter().use { w ->
                commands.forEach { if (it.isNotEmpty()) w.write("$it\n") }
                w.write("exit\n")
            }
            val code = p.waitFor()
            if (isNeedResultMsg) {
                val out = p.inputStream.bufferedReader().readText().trim()
                val err = p.errorStream.bufferedReader().readText().trim()
                CommandResult(code, out, err)
            } else {
                CommandResult(code, "", "")
            }
        } catch (e: Exception) {
            CommandResult(-1, "", e.message ?: "Unknown Error")
        }
    }

    fun execCmd(
        command: String,
        isRoot: Boolean = false,
        isNeedResultMsg: Boolean = false
    ): CommandResult =
        execCmd(arrayOf(command), isRoot, isNeedResultMsg)

    fun execCmd(
        commands: List<String>?,
        isRoot: Boolean = false,
        isNeedResultMsg: Boolean = false
    ): CommandResult =
        execCmd(commands?.toTypedArray(), isRoot, isNeedResultMsg)

    data class CommandResult(
        val result: Int,
        val successMsg: String,
        val errorMsg: String
    )
}