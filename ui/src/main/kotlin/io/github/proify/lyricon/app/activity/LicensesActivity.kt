/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.aboutlibraries.Libs
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.util.launchBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class LicensesActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val licensesState = produceState(
                Libs.Builder()
                    .withJson("{}")
                    .build()
            ) {
                value = withContext(Dispatchers.IO) { loadLicenses() }
            }
            Content(licensesState.value)
        }
    }

    @SuppressLint("DiscouragedApi")
    @OptIn(ExperimentalSerializationApi::class)
    private fun loadLicenses(): Libs {
        val context = this@LicensesActivity
        val id = context.resources.getIdentifier(
            "aboutlibraries", "raw", context.packageName
        )
        val inputStream = context.resources.openRawResource(id)
        val jsonString = inputStream.bufferedReader().use { it.readText() }

        return Libs.Builder()
            .withJson(jsonString)
            .build()

    }

    @Composable
    private fun Content(libs: Libs) {
        val sourceLibraries = libs.libraries

        AppToolBarListContainer(
            title = stringResource(R.string.activity_open_source_licenses),
            canBack = true,
        ) {
            items(
                items = sourceLibraries,
                key = { it.toString() }
            ) { library ->
                val sourceLibrary = remember(library) {
                    library
                }
                val developers = sourceLibrary.developers.joinToString { it.name.orEmpty() }
                val url = sourceLibrary.scm?.url
                val description = sourceLibrary.description
                val year = sourceLibrary.artifactVersion
                val project = sourceLibrary.name
                val licenses = sourceLibrary.licenses

                val indication = LocalIndication.current
                val toolbarColor = MiuixTheme.colorScheme.surface.toArgb()

                val clickableModifier = if (url.isNullOrBlank()) Modifier else Modifier.clickable(
                    indication = indication,
                    interactionSource = null,
                    onClick = {
                        launchBrowser(url, toolbarColor)
                    }
                )

                Card(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(clickableModifier)
                            .padding(
                                horizontal = 16.dp,
                                vertical = 12.dp
                            )
                    ) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                modifier = Modifier
                                    .weight(1f),
                                text = project,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            )

                            if (!year.isNullOrEmpty()) {
                                Text(
                                    modifier = Modifier.padding(start = 16.dp),
                                    text = year,
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }

                        if (developers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = developers,
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        }

                        if (!description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider()

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = description,
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }

                        if (licenses.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = licenses
                                        .filter { it.name.isNotBlank() }
                                        .joinToString { it.name },
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
