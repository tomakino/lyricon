/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("SpellCheckingInspection")

package io.github.proify.lyricon.app.activity.lyric.pkg.sheet

/**
 * 2025年国内外音乐App完整包名列表
 * 包含主流、小众、Lite版本等
 */

// ROM系统厂商音乐App
val romMusicApps: Array<String> = arrayOf(
    // 小米/红米
    "com.miui.player",                        // 小米音乐(MIUI音乐)
    "com.xiaomi.music",                       // 小米音乐(新版)

    // 华为/荣耀
    "com.android.mediacenter",                // 华为音乐(音乐)
    "com.huawei.music",                       // 华为音乐
    "com.hihonor.music",                      // 荣耀音乐

    // OPPO/一加
    "com.heytap.music",                       // OPPO音乐(欢太音乐)
    "com.oppo.music",                         // OPPO音乐(旧版)
    "com.oneplus.music",                      // 一加音乐

    // vivo/iQOO
    "com.android.BBKMusic",                   // vivo音乐
    "com.vivo.music",                         // vivo音乐(新版)
    "com.iqoo.music",                         // iQOO音乐

    // 三星
    "com.samsung.android.app.music",          // 三星音乐
    "com.sec.android.app.music",              // 三星音乐(旧版)

    // 索尼
    "com.sonyericsson.music",                 // 索尼音乐
    "com.sony.music",                         // 索尼Walkman

    // 魅族
    "com.meizu.media.music",                  // 魅族音乐
    "com.android.music",                      // 魅族音乐(Flyme)

    // 联想/摩托罗拉
    "com.lenovo.music",                       // 联想音乐
    "com.motorola.music",                     // 摩托罗拉音乐

    // 努比亚/中兴
    "cn.nubia.music.preset",                  // 努比亚音乐
    "com.zte.music",                          // 中兴音乐

    // 真我realme
    "com.realme.music",                       // realme音乐

    // 其他国产品牌
    "com.smartisanos.music",                  // 锤子音乐
    "com.yulong.android.music",               // 酷派音乐
    "com.gionee.music",                       // 金立音乐
    "com.transsion.music",                    // 传音音乐

    // 国际品牌
    "com.google.android.music",               // Google Play Music(已停运)
    "com.htc.music",                          // HTC音乐
    "com.lge.music",                          // LG音乐
    "com.asus.music",                         // 华硕音乐
    "com.nokia.music",                        // 诺基亚音乐
)

// 运营商定制版音乐App
val carrierCustomizedApps: Array<String> = arrayOf(
    // 中国移动
    "com.cmcc.migumusic",                     // 咪咕音乐(移动定制)
    "cmccwm.mobilemusic.activity",            // 咪咕音乐(移动版)

    // 中国联通
    "com.unicom.music",                       // 沃音乐
    "com.chinaunicom.music",                  // 联通音乐

    // 中国电信
    "com.chinatelecom.music",                 // 天翼音乐
    "com.ct.music",                           // 电信音乐

    // 国际运营商
    "com.verizon.music",                      // Verizon定制音乐
    "com.att.music",                          // AT&T定制音乐
    "com.tmobile.music",                      // T-Mobile定制音乐
)

// 国内音乐App
val domesticMusicApps: Array<String> = arrayOf(
    // 主流平台
    "com.tencent.qqmusic",                    // QQ音乐
    "com.tencent.qqmusiclite",                // QQ音乐极速版
    "com.tencent.qqmusicpad",                 // QQ音乐HD
    "com.tencent.qqmusic.tv",                 // QQ音乐TV版
    "com.netease.cloudmusic",                 // 网易云音乐
    "com.netease.cloudmusic.lite",            // 网易云音乐极速版
    "com.netease.cloudmusic.pad",             // 网易云音乐HD
    "com.netease.cloudmusic.tv",              // 网易云音乐TV版
    "cmccwm.mobilemusic",                     // 咪咕音乐
    "cmccwm.mobilemusic.lite",                // 咪咕音乐极速版
    "com.migu.music",                         // 咪咕音乐(新版)
    "com.kugou.android",                      // 酷狗音乐
    "com.kugou.android.lite",                 // 酷狗音乐极速版
    "com.kugou.android.ringtone",             // 酷狗铃声
    "com.kugou.android.hd",                   // 酷狗音乐HD
    "com.kugou.viper",                        // 酷狗音乐概念版
    "com.kuwo.kwmusicapp",                    // 酷我音乐
    "cn.kuwo.player",                         // 酷我音乐(旧)
    "com.kuwo.kwmusicapp.tv",                 // 酷我音乐TV版
    "com.ting.mp3.android",                   // 千千音乐(原百度音乐)
    "fm.xiami.main",                          // 虾米音乐(已停运但可能仍有安装)
    "com.taobao.musicapp",                    // 虾米音乐(淘宝版)

    // 直播/短视频平台音乐
    "com.ss.android.ugc.aweme",               // 抖音
    "com.ss.android.ugc.aweme.lite",          // 抖音极速版
    "com.smile.gifmaker",                     // 快手
    "com.kuaishou.nebula",                    // 快手极速版
    "com.tencent.weishi",                     // 微视

    // 音频平台
    "com.ximalaya.ting.android",              // 喜马拉雅
    "com.ximalaya.ting.android.lite",         // 喜马拉雅极速版
    "com.qingting.fm",                        // 蜻蜓FM
    "com.lizhi.fm",                           // 荔枝FM
    "com.lavaradio.podcast",                  // 荔枝播客
    "tunein.player",                          // TuneIn Radio(国际版在国内也有用户)

    // 小众/特色平台
    "com.syberos.music",                      // 落网
    "com.changba",                            // 唱吧
    "com.duomi.android",                      // 多米音乐
    "com.aspire.mm",                          // 和彩云
    "com.cmcc.cmvideo",                       // 咪咕视频(含音乐)
    "cn.gov.pbc.dcep",                        // 数字人民币(含音乐支付)
)

// 国际主流音乐App
val internationalMainstreamApps: Array<String> = arrayOf(
    // Spotify系列
    "com.spotify.music",                      // Spotify
    "com.spotify.lite",                       // Spotify Lite

    // Apple Music
    "com.apple.android.music",                // Apple Music(Android)

    // YouTube Music
    "com.google.android.apps.youtube.music",  // YouTube Music

    // Amazon Music
    "com.amazon.mp3",                         // Amazon Music
    "com.amazon.music.lite",                  // Amazon Music Lite

    // Deezer
    "deezer.android.app",                     // Deezer

    // Tidal
    "com.aspiro.tidal",                       // Tidal

    // SoundCloud
    "com.soundcloud.android",                 // SoundCloud
    "com.soundcloud.android.go",              // SoundCloud Go

    // Pandora
    "com.pandora.android",                    // Pandora

    // iHeartRadio
    "com.clearchannel.iheartradio.controller", // iHeartRadio

    // Shazam
    "com.shazam.android",                     // Shazam
    "com.shazam.encore.android",              // Shazam Encore
)

// 其他地区特色音乐App
val regionalMusicApps: Array<String> = arrayOf(
    // 日本
    "jp.klab.utapass",                        // うたパス
    "com.linecorp.linemusic.android",         // LINE MUSIC
    "jp.co.recochoku.android.music",          // レコチョク
    "jp.awa",                                 // AWA Music

    // 韩国
    "com.skt.skaf.OA00018282",                // FLO
    "com.nhn.android.music",                  // VIBE
    "com.iloen.melon",                        // Melon
    "com.kakao.android.muzik",                // Kakao Music

    // 印度
    "com.jio.media.jiobeats",                 // JioSaavn
    "com.gaana",                              // Gaana
    "com.hungama.myplay.activity",            // Hungama Music
    "in.wynk.music",                          // Wynk Music

    // 东南亚
    "com.goomazing.jooxnew",                  // JOOX
    "com.kkbox.android",                      // KKBOX

    // 俄罗斯
    "ru.yandex.music",                        // Yandex Music
    "com.vkontakte.android",                  // VK Music

    // 欧洲
    "com.napster.mobile",                     // Napster
    "com.qobuz.music",                        // Qobuz

    // 拉美
    "br.com.palcomp3.mobile",                 // Palco MP3
)

// 独立/小众音乐App
val indieMusicApps: Array<String> = arrayOf(
    "com.bandcamp.android",                   // Bandcamp
    "com.audiomack",                          // Audiomack
    "com.mixcloud.player",                    // Mixcloud
    "com.last.fm.android",                    // Last.fm
    "com.anghami",                            // Anghami
    "com.melodysmart",                        // MelodySound
    "com.trebel.music",                       // Trebel Music
    "com.boomplay.android",                   // Boomplay
    "com.jamendo.android.player",             // Jamendo
    "com.reverbnation.android",               // ReverbNation
)

// 播客/电台App
val podcastRadioApps: Array<String> = arrayOf(
    "com.google.android.apps.podcasts",       // Google Podcasts
    "fm.player",                              // Player FM
    "fm.castbox.audiobook.radio.podcast",     // Castbox
    "com.bambuna.podcastaddict",              // Podcast Addict
    "au.com.shiftyjelly.pocketcasts",         // Pocket Casts
    "de.danoeh.antennapod",                   // AntennaPod
    "com.overcast.app",                       // Overcast(如有Android版)
    "com.audible.application",                // Audible
    "com.radio.fmradio",                      // Radio FM
    "tunein.player",                          // TuneIn Radio
    "com.targetspot.radioplayer",             // Radioplayer
)

// 音乐识别/工具App
val musicToolApps: Array<String> = arrayOf(
    "com.shazam.android",                     // Shazam
    "com.soundhound.android",                 // SoundHound
    "com.musixmatch.android.lyrify",          // Musixmatch
    "com.genie.song.recognizer",              // Music Recognizer
)

// 离线/本地音乐播放器
val offlineMusicPlayers: Array<String> = arrayOf(
    "com.maxmpz.audioplayer",                 // Poweramp
    "com.maxmpz.audioplayer.unlock",          // Poweramp Unlocker
    "ch.blinkenlights.android.vanilla",       // Vanilla Music
    "com.clementvale.simple.music.player",    // Simple Music Player
    "com.kabouzeid.gramophone",               // Phonograph
    "com.iven.musicplayergo",                 // Music Player GO
    "com.blackplayer.blackplayer",            // BlackPlayer
    "com.blackplayer.blackplayerex",          // BlackPlayer EX
    "com.retro.musicplayer.music",            // Retro Music
    "player.phonograph.plus",                 // Phonograph Plus
    "com.h6ah4i.android.materialmusicplayer", // Material Music Player
    "com.sas.music.player",                   // AIMP
    "com.neutroncode.mp",                     // Neutron Music Player
    "com.rhmsoft.pulsar",                     // Pulsar Music Player
    "com.musicplayer.mp3player",              // Music Player
    "com.vkontakte.android.audioplayer",      // VK Music Player
)

// 古典音乐专门App
val classicalMusicApps: Array<String> = arrayOf(
    "com.idagio.app",                         // IDAGIO
    "com.primephonic.android",                // Primephonic(已被Apple收购)
    "com.naxos",                              // Naxos Music Library
)

// 高品质/Hi-Res音乐App
val hiresMusicApps: Array<String> = arrayOf(
    "com.qobuz.music",                        // Qobuz
    "com.aspiro.tidal",                       // Tidal HiFi
    "com.mora.app",                           // mora
    "com.onkyo.jp.musicapp",                  // Onkyo Music
)

// DJ/混音App
val djMixingApps: Array<String> = arrayOf(
    "com.mixvibes.remixlive",                 // Remixlive
    "com.djit.djstudio5",                     // DJ Studio
    "com.MWM.Edjing",                         // edjing Mix
    "com.djit.djstudio.pro",                  // DJ Studio Pro
)

// 合并所有包名
val allMusicAppPackages: Array<String> = romMusicApps +
        carrierCustomizedApps +
        domesticMusicApps +
        internationalMainstreamApps +
        regionalMusicApps +
        indieMusicApps +
        podcastRadioApps +
        musicToolApps +
        offlineMusicPlayers +
        classicalMusicApps +
        hiresMusicApps +
        djMixingApps