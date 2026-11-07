package com.starkdroid.quicknotch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starkdroid.quicknotch.generated.resources.Res
import com.starkdroid.quicknotch.generated.resources.cpu
import com.starkdroid.quicknotch.generated.resources.gpu
import com.starkdroid.quicknotch.generated.resources.net_down
import com.starkdroid.quicknotch.generated.resources.net_up
import com.starkdroid.quicknotch.generated.resources.ram
import com.starkdroid.quicknotch.generated.resources.storage
import com.starkdroid.quicknotch.generated.resources.system_metric_cpu
import com.starkdroid.quicknotch.generated.resources.system_metric_down
import com.starkdroid.quicknotch.generated.resources.system_metric_gpu
import com.starkdroid.quicknotch.generated.resources.system_metric_ram
import com.starkdroid.quicknotch.generated.resources.system_metric_storage
import com.starkdroid.quicknotch.generated.resources.system_metric_up
import com.starkdroid.quicknotch.generated.resources.system_open_monitor
import com.starkdroid.quicknotch.generated.resources.system_reading
import com.starkdroid.quicknotch.generated.resources.system_reading_unknown
import com.starkdroid.quicknotch.generated.resources.system_unknown
import com.starkdroid.quicknotch.system.SystemSnapshot
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.log10
import kotlin.math.roundToInt

/** Ring diameter on the System page — a hair larger than the pill's 22dp rings. */
private val SystemRingSize = 34.dp

/** Everything one ring tile needs, resolved before the row is laid out. */
private data class SystemTileSpec(
    val icon: DrawableResource,
    /** Caption under the value: CPU, GPU, Memory, … */
    val name: String,
    /** The live value as drawn: "23%", "12.4 M/s", or the placeholder dash. */
    val value: String,
    /** Full accessibility reading; "GPU: unavailable" instead of a fake number. */
    val description: String,
    /** 0-100 sweep, or null when the OS reported nothing this tick. */
    val level: Int?,
    /** Load metrics warm to [LoadHighTint] as they saturate; network does not. */
    val alert: Boolean
)

/** Starts the poller once. A manual place seeds the position so the first
 * fetch skips the IP lookup. */
@Composable
internal fun SystemNotchPage(
    snapshot: SystemSnapshot?,
    interactive: Boolean = false,
    onOpenMonitor: () -> Unit = {}
) {
    val tiles = systemTiles(snapshot)
    val openLabel = stringResource(Res.string.system_open_monitor)
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxSize()
            // The whole page is the target, not the individual rings: a click
            // anywhere on the row means "show me the processes behind this".
            // Installed only while the page is the active, expanded one — a
            // disabled clickable still occupies the hit path and would swallow
            // the wheel from the page beneath (see DockContent's app tiles).
            .then(
                if (interactive) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClickLabel = openLabel,
                        role = Role.Button,
                        onClick = onOpenMonitor
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = DockLayout.ContentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        tiles.forEach { spec ->
            SystemMetricTile(spec = spec, modifier = Modifier.weight(1f))
        }
    }
}

/** Resolves the live readings into the tiles this tick actually draws. */
@Composable
private fun systemTiles(snapshot: SystemSnapshot?): List<SystemTileSpec> {
    val unknown = stringResource(Res.string.system_unknown)
    val cpu = snapshot?.cpuPercent
    val gpu = snapshot?.gpuPercent
    val ram = snapshot?.ramPercent
    val storage = snapshot?.storagePercent
    val down = snapshot?.downBytesPerSec ?: 0L
    val up = snapshot?.upBytesPerSec ?: 0L

    return buildList {
        add(percentTile(Res.drawable.cpu, Res.string.system_metric_cpu, cpu, unknown))
        if (snapshot?.gpuAvailable == true) {
            add(percentTile(Res.drawable.gpu, Res.string.system_metric_gpu, gpu, unknown))
        }
        add(percentTile(Res.drawable.ram, Res.string.system_metric_ram, ram, unknown))
        add(percentTile(Res.drawable.storage, Res.string.system_metric_storage, storage, unknown))
        add(rateTile(Res.drawable.net_down, Res.string.system_metric_down, down))
        add(rateTile(Res.drawable.net_up, Res.string.system_metric_up, up))
    }
}

/** A 0-100 load tile (CPU/GPU/memory/storage); null draws as a dim dash. */
@Composable
private fun percentTile(
    icon: DrawableResource,
    nameRes: StringResource,
    level: Int?,
    unknown: String
): SystemTileSpec {
    val name = stringResource(nameRes)
    return SystemTileSpec(
        icon = icon,
        name = name,
        value = systemPercent(level, unknown),
        description = systemDescription(name, level?.let { "$it%" }),
        level = level,
        alert = true
    )
}

/** A throughput tile: log-swept ring, exact rate as the label. */
@Composable
private fun rateTile(icon: DrawableResource, nameRes: StringResource, rate: Long): SystemTileSpec {
    val name = stringResource(nameRes)
    return SystemTileSpec(
        icon = icon,
        name = name,
        value = systemRate(rate),
        description = systemDescription(name, systemRate(rate)),
        level = systemRateLevel(rate),
        alert = false
    )
}

/**
 * One ring + value + caption. A metric the OS could not report draws a dim
 * ring at zero with an em-dash value instead of a plausible-looking number;
 * the caption stays, so the row's rhythm never changes.
 */
@Composable
private fun SystemMetricTile(spec: SystemTileSpec, modifier: Modifier = Modifier) {
    val dimmed = spec.level == null
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LevelRing(
            level = spec.level ?: 0,
            icon = painterResource(spec.icon),
            contentDescription = spec.description,
            modifier = Modifier.size(SystemRingSize),
            tint = if (dimmed) LevelRingGlyph.copy(alpha = 0.35f) else LevelRingGlyph,
            ringColor = if (dimmed) LevelRingGlyph.copy(alpha = 0.28f) else LevelRingGlyph,
            highTint = if (spec.alert) LoadHighTint else null
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = spec.value,
            color = Color.White.copy(alpha = if (dimmed) 0.45f else 0.94f),
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Text(
            text = spec.name,
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 7.5.sp,
            lineHeight = 9.sp,
            letterSpacing = 0.4.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

/** "23%", or the placeholder dash when the OS gave no reading. */
private fun systemPercent(value: Int?, unknown: String): String =
    value?.let { "$it%" } ?: unknown

/** Accessibility line: "CPU: 23%" / "GPU: unavailable". */
@Composable
private fun systemDescription(name: String, value: String?): String =
    if (value == null) {
        stringResource(Res.string.system_reading_unknown, name)
    } else {
        stringResource(Res.string.system_reading, name, value)
    }

/**
 * Throughput as a compact label: "12.4 M/s", "820 K/s", "0". One decimal in
 * the M/G bands (where the eye cares) and a whole number in K, built from
 * integer math so a comma-decimal locale never prints "12,4".
 */
internal fun systemRate(bytesPerSec: Long): String {
    val kb = bytesPerSec / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1024.0 -> "${systemRateBand(mb / 1024.0)} G/s"
        mb >= 1.0 -> "${systemRateBand(mb)} M/s"
        kb >= 1.0 -> "${kb.roundToInt()} K/s"
        else -> "0"
    }
}

/**
 * Drops the ".0" so a round rate reads "2 M/s", not "2.0 M/s", and drops the
 * decimal once a band is three digits wide ("999 M/s"): a tile is only ~54dp,
 * so the tenth matters below 100 and costs a clipped label above it.
 */
private fun systemRateBand(value: Double): String {
    val tenths = (value * 10).roundToInt()
    return when {
        tenths >= 1000 -> "${tenths / 10}"
        tenths % 10 == 0 -> "${tenths / 10}"
        else -> "${tenths / 10}.${tenths % 10}"
    }
}

/**
 * 1 KB/s maps to 0 and 1 GB/s to 100, so idle traffic barely moves the arc;
 * the label carries the rate.
 */
internal fun systemRateLevel(bytesPerSec: Long): Int {
    val kb = bytesPerSec / 1024.0
    if (kb <= 1.0) return 0
    val decades = log10(kb) / 6.0 // 1 KB/s .. 1 GB/s = 6 decades
    return (decades * 100).roundToInt().coerceIn(0, 100)
}
