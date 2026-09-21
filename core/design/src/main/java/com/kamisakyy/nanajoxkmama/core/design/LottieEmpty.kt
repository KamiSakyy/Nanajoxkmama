package com.kamisakyy.nanajoxkmama.core.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/** Lottie-powered empty-state illustration (6.x). */
@Composable
fun LottieEmpty(asset: String = "empty_search.json", modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(asset))
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)
    Box(modifier.size(160.dp)) {
        LottieAnimation(composition = composition, progress = { progress }, modifier = Modifier.size(160.dp))
    }
}
