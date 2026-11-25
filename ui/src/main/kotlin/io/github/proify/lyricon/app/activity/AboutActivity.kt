/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import io.github.proify.lyricon.app.AppConstants
import io.github.proify.lyricon.app.BuildConfig
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.IconActions
import io.github.proify.lyricon.app.compose.custom.miuix.basic.AppBasicComponent
import io.github.proify.lyricon.app.compose.effect.BgEffectBackground
import io.github.proify.lyricon.app.util.AppLangUtils
import io.github.proify.lyricon.app.util.AppThemeUtils
import io.github.proify.lyricon.app.util.launchBrowser
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class AboutActivity : BaseActivity() {
    companion object {
        val ENABLE_NEW_HEADVIEW get() = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AboutContent() }
    }

    @Composable
    private fun AboutContent() {
        val context = LocalContext.current
        val isEnableMonet = AppThemeUtils.isEnableMonet(context)

        val buildTimeFormat =
            Instant
                .ofEpochMilli(BuildConfig.BUILD_TIME)
                .atZone(ZoneId.systemDefault())
                .format(
                    DateTimeFormatter
                        .ofLocalizedDateTime(FormatStyle.MEDIUM)
                        .withLocale(AppLangUtils.getLocale(context))
                )

        AppToolBarListContainer(
            title = stringResource(id = R.string.activity_about),
            canBack = true,
        ) {
            item("head") {
                HeadView(isEnableMonet)
            }

            item("info") {
                Card(
                    modifier =
                        Modifier
                            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 0.dp)
                            .fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp)
                ) {
                    AppBasicComponent(
                        startAction = { IconActions(painterResource(R.drawable.ic_info)) },
                        title = stringResource(id = R.string.item_app_version),
                        summary =
                            stringResource(
                                id = R.string.item_app_version_summary,
                                LyriconApp.packageInfo.versionName ?: BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE.toString(),
                                BuildConfig.BUILD_TYPE
                            )
                    )

                    AppBasicComponent(
                        startAction = { IconActions(painterResource(R.drawable.ic_build)) },
                        title = stringResource(id = R.string.item_build_time),
                        summary = buildTimeFormat
                    )
                }

                Card(
                    modifier =
                        Modifier
                            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 0.dp)
                            .fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    val githubHome = stringResource(id = R.string.github_home)

                    ArrowPreference(
                        startAction = { IconActions(painterResource(R.drawable.ic_github)) },
                        summary = stringResource(id = R.string.item_view_on_github_summary),
                        title = stringResource(id = R.string.item_view_on_github),
                        onClick = {
                            launchBrowser(
                                githubHome,
                            )
                        }
                    )

                    ArrowPreference(
                        startAction = { IconActions(painterResource(R.drawable.ic_license)) },
                        title = stringResource(id = R.string.item_open_source_licenses),
                        summary = stringResource(id = R.string.item_open_source_licenses_summary),
                        onClick = {
                            startActivity(Intent(context, LicensesActivity::class.java))
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun HeadView(isEnableMonet: Boolean) {
        val backdrop = rememberLayerBackdrop()
        val isRuntimeShaderSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val context = LocalContext.current

        Card(
            modifier =
                Modifier
                    .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)
                    .fillMaxWidth(),
            pressFeedbackType = PressFeedbackType.Sink,
        ) {
            val drawable =
                AppCompatResources.getDrawable(
                    context,
                    R.mipmap.ic_launcher
                )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .height(135.dp)
            ) {
                if (isRuntimeShaderSupported && !isEnableMonet && ENABLE_NEW_HEADVIEW) {
                    BgEffectBackground(
                        dynamicBackground = isRuntimeShaderSupported,
                        modifier = Modifier.matchParentSize(),
                        bgModifier = Modifier.layerBackdrop(backdrop),
                        effectBackground = isRuntimeShaderSupported
                    ) {}
                } else {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                (if (isEnableMonet) MiuixTheme.colorScheme.primaryVariant
                                else AppConstants.APP_COLOR).copy(
                                    alpha = 0.4f
                                )
                            )
                    )
                }

                Column(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        modifier = Modifier.size(54.dp),
                        painter = rememberDrawablePainter(drawable),
                        contentDescription = null,
                        tint = null
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MiuixTheme.textStyles.title3,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun AboutContentPreview() {
        AboutContent()
    }
}
