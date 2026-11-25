/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieAnimatable
import com.airbnb.lottie.compose.rememberLottieComposition
import io.github.proify.lyricon.app.util.AnimationEmoji

@Composable
fun EmojiInfiniteQueuePlayer(
    modifier: Modifier = Modifier,
    names: List<String> = listOf("Neutral-face", "Expressionless", "Mouth-none")
) {
    var currentIndex by remember { mutableIntStateOf(0) }

    val compositions = remember(names) {
        names.map {
            LottieCompositionSpec.Asset(AnimationEmoji.getAssetsFile(it))
        }
    }.map { spec ->
        rememberLottieComposition(spec)
    }

    val currentComposition = compositions[currentIndex].value
    val animatable = rememberLottieAnimatable()

    LaunchedEffect(currentComposition) {
        currentComposition ?: return@LaunchedEffect

        animatable.animate(
            composition = currentComposition,
            iterations = 10,
            initialProgress = 0f
        )

        currentIndex = (currentIndex + 1) % compositions.size
    }

    if (currentComposition != null) {
        LottieAnimation(
            composition = currentComposition,
            progress = animatable::progress,
            modifier = modifier
        )
    }
}