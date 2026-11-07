package com.starkdroid.quicknotch.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

// The collapsed live-activity elements and their detailed now-playing page
// counterparts share these keys, so Compose morphs each pair's bounds instead
// of cutting between the two surfaces when the notch opens or closes.

internal const val NowPlayingDiscKey = "nowPlayingDisc"
internal const val NowPlayingTitleKey = "nowPlayingTitle"
internal const val NowPlayingVolumeKey = "nowPlayingVolume"
internal const val NowPlayingBatteryKey = "nowPlayingBattery"

/**
 * Must be sharedElement, not sharedBounds: sharedBounds keeps both copies
 * visible, so the pill and page double-draw during the morph.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SharedTransitionScope.nowPlayingSharedElement(
    key: String,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = Modifier.sharedElement(
    state = rememberSharedContentState(key),
    animatedVisibilityScope = animatedVisibilityScope,
    boundsTransform = object : BoundsTransform {
        override fun transform(initialBounds: Rect, targetBounds: Rect) = sharedBoundsSpring()
    }
)
