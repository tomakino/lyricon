/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lyricon.app.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import io.github.libxposed.service.XposedService
import io.github.proify.lyricon.app.util.defaultSharedPreferences
import io.github.proify.lyricon.app.BuildConfig
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.LyriconApp.Companion.addXposedServiceStateListener
import io.github.proify.lyricon.app.LyriconApp.Companion.removeXposedServiceStateListener
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.activity.lyric.AiLaboratoryActivity
import io.github.proify.lyricon.app.activity.lyric.BasicLyricStyleActivity
import io.github.proify.lyricon.app.activity.lyric.pkg.PackageStyleActivity
import io.github.proify.lyricon.app.activity.lyric.provider.LyricProviderActivity
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.app.bridge.LyriconBridge
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.EmojiInfiniteQueuePlayer
import io.github.proify.lyricon.app.compose.MaterialPalette
import io.github.proify.lyricon.app.compose.custom.miuix.basic.AppBasicComponent
import io.github.proify.lyricon.app.compose.custom.miuix.extra.OverlayDialog
import io.github.proify.lyricon.app.event.SettingChangedEvent
import io.github.proify.lyricon.app.util.AppThemeUtils
import io.github.proify.lyricon.app.util.Utils
import io.github.proify.lyricon.app.util.collectEvent
import io.github.proify.lyricon.app.util.editCommit
import io.github.proify.lyricon.app.util.restartApp
import io.github.proify.lyricon.common.PackageNames
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults.progressIndicatorColors
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

class MainActivity : BaseActivity(), LyriconApp.XposedServiceStateListener {

    private companion object {
        const val PREF_KEY_LAST_VERSION = "last_version"
        private const val TAG = "MainActivity"
        private const val RESTART_DEBOUNCE_MS = 666L
    }

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleVersionUpdate()

        setContent {
            MainContent(
                model = viewModel,
                onRestartSystemUI = ::restartSystemUI,
                onRestartApp = ::restartApp
            )
        }
        setupEventListeners()

        addXposedServiceStateListener(this)
        viewModel.startConnectTimeout()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeXposedServiceStateListener(this)
    }

    private fun handleVersionUpdate() {
        val sharedPreferences = defaultSharedPreferences
        val savedVersionCode = sharedPreferences.getLong(PREF_KEY_LAST_VERSION, 0)
        if (savedVersionCode <= 0) {
            sharedPreferences.edit {
                putLong(PREF_KEY_LAST_VERSION, BuildConfig.VERSION_CODE.toLong())
            }
        } else if (savedVersionCode < BuildConfig.VERSION_CODE) {
            viewModel.setWaitingForReboot(true)
        }
    }

    override fun onResume() {
        super.onResume()
        requestSafeModeCheck()
    }

    private fun setupEventListeners() {
        collectEvent<SettingChangedEvent>(state = Lifecycle.State.CREATED) {
            recreate()
        }
    }

    private fun requestSafeModeCheck() {
        lifecycleScope.launch {
            try {
                val response = LyriconBridge.with(this@MainActivity)
                    .to(PackageNames.SYSTEM_UI)
                    .key(AppBridgeConstants.REQUEST_CHECK_SAFE_MODE)
                    .await()

                viewModel.updateSafeMode(response.getBoolean("result"))
            } catch (e: Exception) {
                Log.e(TAG, "IPC 调用失败: ${e.message}", e)
            }
        }
    }

    private fun restartSystemUI() {
        if (viewModel.isWaitingForReboot.value) {
            saveCurrentVersionCode()
            lifecycleScope.launch {
                delay(RESTART_DEBOUNCE_MS)
                viewModel.setWaitingForReboot(false)
            }
        }
        val result = Utils.killSystemUI()
        if (result.result == -1) {
            viewModel.showRestartFailedDialog.value = true
        }
    }

    private fun saveCurrentVersionCode() {
        defaultSharedPreferences.editCommit {
            putLong(PREF_KEY_LAST_VERSION, LyriconApp.versionCode)
        }
    }

    override fun onServiceStateChanged(service: XposedService?) {
        viewModel.isModuleActive.value = service != null
    }

    /**
     * 界面状态管理层
     */
    class MainViewModel : ViewModel() {
        private val _safeMode = mutableStateOf(false)
        val showRestartFailedDialog: MutableState<Boolean> = mutableStateOf(false)
        private val _isWaitingForReboot = mutableStateOf(false)

        val safeMode: State<Boolean> get() = _safeMode
        val isWaitingForReboot: State<Boolean> get() = _isWaitingForReboot

        val showRestartMenu: MutableState<Boolean> = mutableStateOf(false)
        val isModuleActive: MutableState<Boolean> = mutableStateOf(false)
        var isServiceConnecting = mutableStateOf(false)

        private val handler = Handler(Looper.getMainLooper())
        fun startConnectTimeout() {
            isServiceConnecting.value = true
            handler.postDelayed({
                isServiceConnecting.value = false
            }, 2000)
        }

        fun updateSafeMode(isSafe: Boolean) {
            _safeMode.value = isSafe
            LyriconApp.setSafeMode(isSafe)
        }

        fun setWaitingForReboot(waiting: Boolean) {
            _isWaitingForReboot.value = waiting
        }

        val isMonet: Boolean get() = AppThemeUtils.isEnableMonet(LyriconApp.get())
    }

    private interface CardStatus {
        val colors: CardColors
        val content: @Composable ColumnScope.() -> Unit
    }

    private class StatusCard(
        override val colors: CardColors,
        val icon: ImageVector? = null,
        val iconLayout: @Composable (BoxScope.() -> Unit)? = null,
        val title: String,
        val showAnimatedEmoji: Boolean = false,
        val summary: String? = null,
        val rightActions: @Composable (RowScope.() -> Unit)? = null
    ) : CardStatus {

        override val content: @Composable ColumnScope.() -> Unit = {
            AppBasicComponent(
                insideMargin = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp
                ),
                endActions = rightActions,
                startAction = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (iconLayout != null) {
                            iconLayout()
                        } else if (icon != null) {
                            Icon(
                                modifier = Modifier.size(26.dp),
                                imageVector = icon,
                                tint = White,
                                contentDescription = null,
                            )
                        }
                    }
                },
                customTitle = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = White
                        )
                        if (showAnimatedEmoji) {
                            EmojiInfiniteQueuePlayer(
                                modifier = Modifier
                                    .size(19.dp)
                                    .padding(start = 1.dp)
                            )
                        }
                    }
                },
                titleColor = BasicComponentColors(color = White, disabledColor = White),
                summary = summary,
                summaryColor = BasicComponentColors(
                    color = Color(color = 0xAFFFFFFF),
                    disabledColor = White,
                )
            )
        }
    }

    @SuppressLint("ConfigurationScreenWidthHeight")
    @Composable
    fun MainContent(
        model: MainViewModel? = null,
        onRestartSystemUI: () -> Unit = {},
        onRestartApp: () -> Unit = {}
    ) {
        val isTablet = LocalConfiguration.current.screenWidthDp >= 600
        val fallbackShowRestartMenu = remember { mutableStateOf(false) }
        val showRestartMenuState = model?.showRestartMenu ?: fallbackShowRestartMenu

        if (isTablet) {
            TabletMainContent(
                model = model,
                showRestartMenuState = showRestartMenuState,
                onRestartSystemUI = onRestartSystemUI,
                onRestartApp = onRestartApp
            )
        } else {
            PhoneMainContent(
                model = model,
                showRestartMenuState = showRestartMenuState,
                onRestartSystemUI = onRestartSystemUI,
                onRestartApp = onRestartApp
            )
        }
    }

    @Composable
    private fun PhoneMainContent(
        model: MainViewModel?,
        showRestartMenuState: MutableState<Boolean>,
        onRestartSystemUI: () -> Unit,
        onRestartApp: () -> Unit
    ) {
        AppToolBarListContainer(
            title = stringResource(R.string.app_name),
            actions = { TopBarActions(showRestartMenuState, onRestartSystemUI, onRestartApp) },
            scaffoldContent = {
                if (model != null) RestartFailedDialog(showState = model.showRestartFailedDialog)
            }
        ) {
            item("status_card") {
                val cardStatus = determineCardStatus(
                    safeMode = model?.safeMode?.value ?: false,
                    isWaitingForReboot = model?.isWaitingForReboot?.value ?: false,
                    isMonet = model?.isMonet ?: AppThemeUtils.isEnableMonet(LocalContext.current),
                    isModuleActive = model?.isModuleActive?.value ?: false,
                    isServiceConnecting = model?.isServiceConnecting?.value ?: false,
                    onRestartSystemUI = onRestartSystemUI
                )
                StatusCardItem(
                    cardStatus = cardStatus,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                )
            }

            item("style_settings") {
                StyleSettingsCard(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                )
            }
            item("provider_settings") {
                ProviderSettingsCard(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                        .fillMaxWidth()
                )
            }
            item("other_settings") {
                OtherSettingsCard(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                        .fillMaxWidth()
                )
            }
        }
    }

    @Composable
    private fun TabletMainContent(
        model: MainViewModel?,
        showRestartMenuState: MutableState<Boolean>,
        onRestartSystemUI: () -> Unit,
        onRestartApp: () -> Unit
    ) {
        AppToolBarListContainer(
            title = stringResource(R.string.app_name),
            actions = { TopBarActions(showRestartMenuState, onRestartSystemUI, onRestartApp) },
            scaffoldContent = {
                if (model != null) RestartFailedDialog(showState = model.showRestartFailedDialog)
            }
        ) {
            item("status_card") {
                val cardStatus = determineCardStatus(
                    safeMode = model?.safeMode?.value ?: false,
                    isWaitingForReboot = model?.isWaitingForReboot?.value ?: false,
                    isMonet = model?.isMonet ?: AppThemeUtils.isEnableMonet(LocalContext.current),
                    isModuleActive = model?.isModuleActive?.value ?: false,
                    isServiceConnecting = model?.isServiceConnecting?.value ?: false,
                    onRestartSystemUI = onRestartSystemUI
                )
                TabletContentItem {
                    StatusCardItem(
                        cardStatus = cardStatus,
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .padding(bottom = 20.dp)
                    )
                }
            }
            item("primary_settings") {
                TabletContentItem {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        StyleSettingsCard(modifier = Modifier.weight(1f))
                        ProviderSettingsCard(modifier = Modifier.weight(1f))
                    }
                }
            }
            item("other_settings") {
                TabletContentItem {
                    OtherSettingsCard(
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .padding(top = 20.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }

    @Composable
    private fun TabletContentItem(
        content: @Composable () -> Unit
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 900.dp)
            ) {
                content()
            }
        }
    }

    @Composable
    private fun determineCardStatus(
        safeMode: Boolean,
        isWaitingForReboot: Boolean,
        isMonet: Boolean,
        isModuleActive: Boolean,
        isServiceConnecting: Boolean,
        onRestartSystemUI: () -> Unit
    ): CardStatus {
        val isInspectionMode = LocalInspectionMode.current
        val summary = stringResource(
            R.string.module_status_summary,
            LyriconApp.packageInfo.versionName ?: BuildConfig.VERSION_NAME
        )

        if (isInspectionMode) {
            return StatusCard(
                colors = CardColors(MaterialPalette.Green.Primary, White),
                icon = ImageVector.vectorResource(id = R.drawable.ic_android),
                title = "Preview mode"
            )
        }

        if (safeMode) {
            return StatusCard(
                colors = CardColors(MaterialPalette.Red.Hue400, White),
                icon = ImageVector.vectorResource(id = R.drawable.ic_sentiment_dissatisfied),
                title = stringResource(id = R.string.module_status_system_ui_safe_mode),
                summary = summary
            )
        }

        if (isModuleActive) {
            if (isWaitingForReboot) {
                return StatusCard(
                    colors = CardColors(MaterialPalette.Orange.Primary, White),
                    icon = ImageVector.vectorResource(id = R.drawable.ic_info_fill),
                    title = stringResource(id = R.string.module_status_waiting_for_reboot),
                    summary = summary,
                    rightActions = {
                        IconButton(onClick = onRestartSystemUI) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_refresh),
                                contentDescription = stringResource(id = R.string.action_restart),
                                tint = White
                            )
                        }
                    }
                )
            }

            return StatusCard(
                colors = when {
                    isMonet -> CardColors(
                        MiuixTheme.colorScheme.primary,
                        MiuixTheme.colorScheme.onPrimary
                    )

                    else -> CardColors(MaterialPalette.Green.Primary, White)
                },
                icon = ImageVector.vectorResource(id = R.drawable.ic_check_circle),
                title = stringResource(id = R.string.module_status_activated),
                summary = summary,
                showAnimatedEmoji = true
            )
        } else if (isServiceConnecting) {
            return StatusCard(
                colors = CardColors(MaterialPalette.Blue.Primary, White),
                iconLayout = {
                    CircularProgressIndicator(
                        colors = progressIndicatorColors(
                            backgroundColor = Color.Transparent,
                            foregroundColor = White
                        ),
                        size = 20.dp
                    )
                },
                title = stringResource(id = R.string.module_status_connecting),
                summary = summary,
            )
        }

        return StatusCard(
            colors = CardColors(MaterialPalette.Red.Primary, White),
            icon = ImageVector.vectorResource(id = R.drawable.ic_sentiment_dissatisfied),
            title = stringResource(id = R.string.module_status_not_activated),
            summary = summary
        )
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun StatusCardItem(
        cardStatus: CardStatus,
        modifier: Modifier = Modifier
    ) {
        val animatedColors by animateColorAsState(
            targetValue = cardStatus.colors.color,
            label = "card_color",
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            ),
        )

        Card(
            modifier = modifier
                .fillMaxWidth()
                .animateContentSize(),
            insideMargin = PaddingValues(vertical = 7.dp),
            colors = cardStatus.colors.copy(color = animatedColors),
            pressFeedbackType = PressFeedbackType.Sink,
            onClick = {},
            content = cardStatus.content
        )
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun StyleSettingsCard(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        Card(
            modifier = modifier
        ) {
            ArrowPreference(
                startAction = {
                    ColoredIconBox(Modifier, MaterialPalette.Teal.Primary, R.drawable.ic_android)
                },
                title = stringResource(id = R.string.item_basic_settings),
                summary = stringResource(id = R.string.item_summary_basic_settings),
                onClick = {
                    context.startActivity(Intent(context, BasicLyricStyleActivity::class.java))
                }
            )
            ArrowPreference(
                startAction = {
                    ColoredIconBox(
                        Modifier.padding(2.dp),
                        MaterialPalette.Orange.Primary,
                        R.drawable.ic_palette_swatch_variant
                    )
                },
                title = stringResource(id = R.string.item_app_style_manager),
                summary = stringResource(id = R.string.item_summary_app_style_manager),
                onClick = {
                    context.startActivity(Intent(context, PackageStyleActivity::class.java))
                }
            )
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun ProviderSettingsCard(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        Card(
            modifier = modifier
        ) {
            ArrowPreference(
                startAction = {
                    ColoredIconBox(Modifier, MaterialPalette.Blue.Primary, R.drawable.ic_extension)
                },
                title = stringResource(id = R.string.item_lyric_provider_services),
                summary = stringResource(id = R.string.item_summary_lyric_provider_services),
                onClick = {
                    context.startActivity(Intent(context, LyricProviderActivity::class.java))
                }
            )
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun OtherSettingsCard(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        Card(
            modifier = modifier
        ) {
            ArrowPreference(
                startAction = {
                    ColoredIconBox(
                        Modifier,
                        MaterialPalette.Purple.Primary,
                        R.drawable.psychology_24px
                    )
                },
                title = stringResource(id = R.string.item_ai_laboratory),
                summary = stringResource(id = R.string.item_summary_ai_laboratory),
                onClick = {
                    context.startActivity(Intent(context, AiLaboratoryActivity::class.java))
                }
            )

            ArrowPreference(
                startAction = {
                    ColoredIconBox(
                        Modifier,
                        MaterialPalette.BlueGrey.Primary,
                        R.drawable.ic_settings
                    )
                },
                title = stringResource(id = R.string.item_app_settings),
                summary = stringResource(id = R.string.item_summary_app_settings),
                onClick = {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }
            )

            ArrowPreference(
                startAction = {
                    ColoredIconBox(Modifier, MaterialPalette.Green.Primary, R.drawable.ic_info_fill)
                },
                title = stringResource(id = R.string.item_about_app),
                summary = stringResource(id = R.string.item_summary_about_app),
                onClick = {
                    context.startActivity(Intent(context, AboutActivity::class.java))
                }
            )
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun ColoredIconBox(
        modifier: Modifier = Modifier,
        backgroundColor: Color,
        iconRes: Int
    ) {
        val isMonet = AppThemeUtils.isEnableMonet(LocalContext.current)
        val iconSize = if (isMonet) 20.dp else 24.dp
        Box(
            modifier = Modifier
                .padding(end = 16.dp)
                .size(40.dp)
                .let {
                    if (isMonet) {
                        it.background(MiuixTheme.colorScheme.primary, CircleShape)
                    } else {
                        it.background(backgroundColor, CircleShape)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                modifier = modifier.size(iconSize),
                tint = White,
                contentDescription = null
            )
        }
    }

    @Composable
    private fun RestartFailedDialog(showState: MutableState<Boolean>) {
        OverlayDialog(
            title = stringResource(R.string.restart_fail),
            summary = stringResource(R.string.message_app_restart_fail),
            show = showState.value,
            onDismissRequest = { showState.value = false }
        ) {
            TextButton(
                text = stringResource(R.string.ok),
                onClick = { showState.value = false },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    private fun TopBarActions(
        showRestartMenu: MutableState<Boolean>,
        onRestartSystemUI: () -> Unit,
        onRestartApp: () -> Unit
    ) {
        Box(modifier = Modifier.padding(end = 14.dp)) {
            IconButton(onClick = { showRestartMenu.value = true }) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = MiuixIcons.Refresh,
                    contentDescription = stringResource(id = R.string.action_restart),
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }

            RestartMenuPopup(
                showRestartMenu = showRestartMenu,
                onRestartSystemUI = onRestartSystemUI,
                onRestartApp = onRestartApp
            )
        }
    }

    @Composable
    private fun RestartMenuPopup(
        showRestartMenu: MutableState<Boolean>,
        onRestartSystemUI: () -> Unit,
        onRestartApp: () -> Unit
    ) {
        val items = listOf(
            stringResource(R.string.restart_system_ui),
            stringResource(R.string.restart_app)
        )

        OverlayListPopup(
            show = showRestartMenu.value,
            popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
            alignment = PopupPositionProvider.Align.TopEnd,
            enableWindowDim = true,
            onDismissRequest = { showRestartMenu.value = false },
            minWidth = 200.dp,
            content = {
                ListPopupColumn {
                    items.forEachIndexed { index, string ->
                        DropdownImpl(
                            text = string,
                            optionSize = items.size,
                            isSelected = false,
                            onSelectedIndexChange = {
                                if (index == 0) onRestartSystemUI() else onRestartApp()
                                showRestartMenu.value = false
                            },
                            index = index
                        )
                    }
                }
            })
    }

    @Preview(showBackground = true)
    @Composable
    fun MainContentPreview() {
        MainContent()
    }

}
