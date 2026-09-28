package com.starkdroid.quicknotch.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

// ── Shared-element morph keys ────────────────────────────────────────────────
// The collapsed live-activity elements and their detailed now-playing page
// counterparts share these keys, so Compose morphs each pair's bounds instead
// of cutting between the two surfaces when the notch opens or closes.

internal const val NowPlayingDiscKey = "nowPlayingDisc"
internal const val NowPlayingTitleKey = "nowPlayingTitle"
internal const val NowPlayingVolumeKey = "nowPlayingVolume"
internal const val NowPlayingBatteryKey = "nowPlayingBattery"

/**
 * Flies one quickview element into its expanded-page counterpart (the vinyl
 * disc, the marquee title, the level rings) under one shared moving frame.
 * The pair are different composables, but they render the *same content* at
 * different sizes — exactly the contract of
 * [SharedTransitionScope.sharedElement], which renders ONLY the target content
 * inside the transforming bounds.
 *
 * That single-content guarantee is why this must not be
 * [SharedTransitionScope.sharedBounds]: sharedBounds deliberately keeps BOTH
 * the entering and the exiting content visible inside the travelling frame
 * (its enter/exit only style them), so the pill copy and the page copy
 * double-draw over each other mid-flight — two covers, two titles (the
 * marquees also scroll out of register), rings printed twice — before the
 * morph settles. `sharedElement` suppresses the source and moves the target,
 * so there is always exactly one album cover and one title on screen.
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
