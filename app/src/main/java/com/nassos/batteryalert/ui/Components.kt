package com.nassos.batteryalert.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nassos.batteryalert.AlertConfig
import com.nassos.batteryalert.AlertKind
import com.nassos.batteryalert.R
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val ARC_START = 135f
private const val ARC_SWEEP = 270f

/** Circular 270° gauge showing the level, with dots marking the alert thresholds. */
@Composable
fun BatteryGauge(level: Int, plugged: Boolean, markers: List<Int>, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(level / 100f, tween(1000), label = "level")
    val color by animateColorAsState(
        when {
            level <= 15 -> MaterialTheme.colorScheme.error
            level <= 30 -> Color(0xFFF2A93B)
            else -> MaterialTheme.colorScheme.primary
        },
        label = "color",
    )
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2 + 10.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(track, ARC_START, ARC_SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, ARC_START, ARC_SWEEP * progress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))

            val radius = arcSize.width / 2 + stroke / 2 + 6.dp.toPx()
            markers.forEach { m ->
                val angle = Math.toRadians((ARC_START + ARC_SWEEP * m / 100f).toDouble())
                drawCircle(
                    markerColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat()),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
            )
            Icon(
                painterResource(R.drawable.ic_bolt), null,
                tint = color,
                modifier = Modifier.size(28.dp).alpha(if (plugged) pulse else 0f),
            )
            Text("$level%", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(if (plugged) R.string.charging else R.string.on_battery),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun IconBadge(@DrawableRes icon: Int, container: Color, content: Color) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, tint = content, modifier = Modifier.size(26.dp))
    }
}

@Composable
fun AlertCard(
    kind: AlertKind,
    config: AlertConfig,
    soundTitle: String,
    onChange: (AlertConfig) -> Unit,
    onCommit: () -> Unit,
    onPickSound: () -> Unit,
    onTestSound: () -> Unit,
) {
    val isCharge = kind == AlertKind.CHARGE
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    if (isCharge) R.drawable.ic_bolt else R.drawable.ic_battery_low,
                    if (isCharge) scheme.primaryContainer else scheme.errorContainer,
                    if (isCharge) scheme.onPrimaryContainer else scheme.onErrorContainer,
                )
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(
                        stringResource(if (isCharge) R.string.alert_charge_title else R.string.alert_low_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(if (isCharge) R.string.alert_charge_desc else R.string.alert_low_desc, config.target),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Switch(config.enabled, { onChange(config.copy(enabled = it)); onCommit() })
            }

            AnimatedVisibility(
                config.enabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(R.string.threshold), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${config.target}%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (isCharge) scheme.primary else scheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Slider(
                        value = config.target.toFloat(),
                        onValueChange = { onChange(config.copy(target = it.roundToInt())) },
                        onValueChangeFinished = onCommit,
                        valueRange = kind.range.first.toFloat()..kind.range.last.toFloat(),
                    )
                    Row {
                        Text("${kind.range.first}%", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("${kind.range.last}%", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(16.dp))
                    Surface(
                        onClick = onPickSound,
                        shape = MaterialTheme.shapes.medium,
                        color = scheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_music), null, tint = scheme.primary)
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text(stringResource(R.string.alert_sound), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                                Text(soundTitle, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            }
                            FilledTonalIconButton(onClick = onTestSound, enabled = config.sound != "") {
                                Icon(Icons.Default.PlayArrow, stringResource(R.string.test_sound))
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.vibration), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(config.vibrate, { onChange(config.copy(vibrate = it)); onCommit() })
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
fun SheetOption(@DrawableRes icon: Int?, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary)
            } else {
                Spacer(Modifier.width(24.dp))
            }
            Column(Modifier.padding(start = 20.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

val CardSpacing = Arrangement.spacedBy(12.dp)
