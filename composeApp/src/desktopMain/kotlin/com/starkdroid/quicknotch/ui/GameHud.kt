package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.generated.resources.Res
import com.starkdroid.quicknotch.generated.resources.game_hud_cpu
import com.starkdroid.quicknotch.generated.resources.game_hud_fps
import com.starkdroid.quicknotch.generated.resources.game_hud_gpu
import com.starkdroid.quicknotch.generated.resources.game_hud_net
import com.starkdroid.quicknotch.generated.resources.game_hud_ping
import com.starkdroid.quicknotch.generated.resources.game_hud_ping_value
import com.starkdroid.quicknotch.generated.resources.game_hud_summary
import com.starkdroid.quicknotch.generated.resources.game_hud_unknown
import com.starkdroid.quicknotch.model.GameMetrics
import com.starkdroid.quicknotch.system.GameSession
import com.starkdroid.quicknotch.system.SystemSnapshot
import org.jetbrains.compose.resources.stringResource

/** One readout: a big value over a small caps caption. */
private data class HudCell(
    val label: String,
    val value: String,
    /** False when the OS could not answer; drawn dim instead of inventing a number. */
    val known: Boolean,
    /** Load metrics tint at the same threshold the System page's rings use. */
    val alert: Boolean = false
)

/** Load metrics turn amber at this level, matching `LevelRing`'s high threshold. */
private const val HudAlertPercent = 85

private val HudSidePadding = 10.dp
private val HudDividerHeight = 14.dp

/**
 * The gaming quickview: a read-only strip ordered frames, loads, network.
 * A missing reading drops its cell instead of showing a dash.
 */
@Composable
internal fun GameHud(
    game: GameSession,
    system: SystemSnapshot?,
    fps: Int?,
    metrics: GameMetrics,
    transparent: Boolean
) {
    val unknown = stringResource(Res.string.game_hud_unknown)
    val fpsLabel = stringResource(Res.string.game_hud_fps)
    val cpuLabel = stringResource(Res.string.game_hud_cpu)
    val gpuLabel = stringResource(Res.string.game_hud_gpu)
    val pingLabel = stringResource(Res.string.game_hud_ping)
    val netLabel = stringResource(Res.string.game_hud_net)
    val pingText = system?.pingMs?.let { stringResource(Res.string.game_hud_ping_value, it) }

    // Only the cells the user asked for, and only where the OS has a real
    // number: a selected metric with no reading still drops its cell rather
    // than inventing one.
    val cells = listOfNotNull(
        if (metrics.fps) fpsCell(fps, fpsLabel) else null,
        if (metrics.cpu) cpuCell(system, cpuLabel) else null,
        if (metrics.gpu) gpuCell(system, gpuLabel) else null,
        if (metrics.ping) pingCell(pingText, unknown, pingLabel) else null,
        if (metrics.net) netCell(system, unknown, netLabel) else null
    )
    val summary = stringResource(
        Res.string.game_hud_summary,
        game.name,
        cells.joinToString(", ") { "${it.label} ${it.value}" }
    )
    Row(
        modifier = Modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) { contentDescription = summary }
            .padding(horizontal = HudSidePadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        cells.forEachIndexed { index, cell ->
            // Transparent mode is metrics only: no hairlines at all.
            if (index > 0 && !transparent) HudDivider()
            HudCellView(cell, transparent, Modifier.weight(1f))
        }
    }
}

private fun fpsCell(fps: Int?, label: String): HudCell? =
    fps?.let { HudCell(label, it.toString(), known = true) }

private fun cpuCell(system: SystemSnapshot?, label: String): HudCell? =
    system?.cpuPercent?.let {
        HudCell(label, "$it%", known = true, alert = it >= HudAlertPercent)
    }

private fun gpuCell(system: SystemSnapshot?, label: String): HudCell? {
    val gpu = system?.gpuPercent
    return if (system?.gpuAvailable == true && gpu != null) {
        HudCell(label, "$gpu%", known = true, alert = gpu >= HudAlertPercent)
    } else {
        null
    }
}

private fun pingCell(value: String?, unknown: String, label: String): HudCell =
    HudCell(label, value ?: unknown, known = value != null)

private fun netCell(system: SystemSnapshot?, unknown: String, label: String): HudCell =
    HudCell(
        label,
        system?.let { systemRate(it.downBytesPerSec) } ?: unknown,
        known = system != null
    )

@Composable
private fun HudCellView(cell: HudCell, transparent: Boolean, modifier: Modifier = Modifier) {
    val shadow = hudShadow(transparent)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = cell.value,
            color = when {
                !cell.known -> Color.White.copy(alpha = 0.45f)
                cell.alert -> LoadHighTint
                else -> Color.White.copy(alpha = 0.96f)
            },
            style = TextStyle(shadow = shadow),
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Text(
            text = cell.label,
            color = Color.White.copy(alpha = 0.5f),
            style = TextStyle(shadow = shadow),
            fontSize = 6.sp,
            lineHeight = 7.sp,
            letterSpacing = 0.4.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * A hairline between cells, drawn only on a solid body. Transparent mode drops
 * every rule, so the strip reads as numbers floating over the game.
 */
@Composable
private fun HudDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(HudDividerHeight)
            .background(Color.White.copy(alpha = 0.12f))
    )
}

/**
 * Text shadow only in transparent mode: on a solid or blurred body the island
 * already guarantees contrast, and a shadow there would just smear the glyphs.
 */
private fun hudShadow(transparent: Boolean): Shadow? =
    if (transparent) Shadow(Color.Black.copy(alpha = 0.92f), Offset(0f, 1f), 5f) else null
