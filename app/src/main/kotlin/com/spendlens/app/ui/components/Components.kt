package com.spendlens.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendlens.app.AppIcons
import com.spendlens.app.data.Category
import com.spendlens.app.ui.theme.Ic
import com.spendlens.app.ui.theme.LocalStatusColors
import com.spendlens.core.BudgetState
import com.spendlens.core.Cat

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp).semantics { heading() },
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = if (onBack != null) 4.dp else 20.dp, end = 8.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Ic.back, contentDescription = "Back") }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

@Composable
fun IconTile(icon: ImageVector, tint: Color, background: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun CategoryTile(category: Category?, size: Dp = 40.dp) {
    val color = Color(category?.color ?: 0xFF7F8682)
    IconTile(Ic.forCategory(category?.icon ?: "others"), color, color.copy(alpha = 0.13f), size)
}

/**
 * The merchant's real app icon when that app is installed; otherwise the category icon;
 * otherwise the first letter of the merchant name.
 */
@Composable
fun MerchantAvatar(appIcons: AppIcons, packageName: String?, merchantName: String, category: Category?, size: Dp = 42.dp) {
    val bitmap = remember(packageName) { appIcons.get(packageName)?.asImageBitmap() }
    when {
        bitmap != null -> Image(bitmap, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)))
        category != null && category.id != Cat.OTHERS && category.id != Cat.INCOME -> CategoryTile(category, size)
        else -> Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                merchantName.trim().firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun NavRow(icon: ImageVector, title: String, subtitle: String? = null, iconTint: Color? = null, iconBg: Color? = null, titleColor: Color = Color.Unspecified, trailing: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon, iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant, iconBg ?: MaterialTheme.colorScheme.surfaceVariant, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) trailing() else Icon(Ic.chevR, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, icon: ImageVector? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) IconTile(icon, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant, 36.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

@Composable
fun StatusPill(state: BudgetState, modifier: Modifier = Modifier) {
    val sc = LocalStatusColors.current
    val (label, icon) = when (state) {
        BudgetState.ON_TRACK -> "On track" to Ic.check
        BudgetState.HEADS_UP -> "Heads-up" to Ic.alert
        BudgetState.OVER -> "Over budget" to Ic.alert
    }
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(sc.bg(state)).padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = sc.fg(state), modifier = Modifier.size(14.dp))
        Text(label, color = sc.fg(state), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold))
    }
}

/**
 * Budget ring. Past 100% a second, red inner lap shows how far over you are,
 * so overspend is visible at a glance without relying on colour alone (the centre text says it too).
 */
@Composable
fun BudgetRing(fraction: Double, state: BudgetState, centerTop: String, centerBottom: String, size: Dp = 136.dp) {
    val sc = LocalStatusColors.current
    val main = sc.ring(state, MaterialTheme.colorScheme.primary)
    val track = sc.track
    val over = sc.overFg
    Box(Modifier.size(size).clearAndSetSemantics { contentDescription = "$centerTop $centerBottom" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.095f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            val sweep = (fraction.coerceIn(0.0, 1.0) * 360).toFloat()
            if (sweep > 0f) drawArc(main, -90f, sweep, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (fraction > 1.0) {
                val s2 = stroke * 0.55f
                val inner = stroke + s2 * 1.6f
                val innerSize = Size(this.size.width - inner * 2 + s2, this.size.height - inner * 2 + s2)
                val innerSweep = ((fraction - 1.0).coerceIn(0.0, 1.0) * 360).toFloat()
                drawArc(over, -90f, innerSweep, false, topLeft = Offset(inner - s2 / 2, inner - s2 / 2), size = innerSize, style = Stroke(s2, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, style = MaterialTheme.typography.headlineSmall)
            Text(centerBottom, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

data class DonutSlice(val value: Float, val color: Color)

@Composable
fun Donut(slices: List<DonutSlice>, center: String, size: Dp = 116.dp) {
    val track = LocalStatusColors.current.track
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.14f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            val total = slices.sumOf { it.value.toDouble() }.toFloat()
            if (total > 0f) {
                var start = -90f
                slices.forEach { s ->
                    val sweep = s.value / total * 360f
                    drawArc(s.color, start, (sweep - 1.5f).coerceAtLeast(0.5f), false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                    start += sweep
                }
            }
        }
        Text(center, style = MaterialTheme.typography.titleSmall)
    }
}

/** A progress bar with a thin marker showing where an even pace would be today. */
@Composable
fun PaceBar(fraction: Double, pace: Double, color: Color, height: Dp = 10.dp) {
    val track = LocalStatusColors.current.track
    val marker = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(height + 8.dp)) {
        val h = height.toPx()
        val top = (this.size.height - h) / 2
        val r = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2)
        drawRoundRect(track, topLeft = Offset(0f, top), size = Size(this.size.width, h), cornerRadius = r)
        val w = (fraction.coerceIn(0.0, 1.0) * this.size.width).toFloat()
        if (w > 0f) drawRoundRect(color, topLeft = Offset(0f, top), size = Size(maxOf(w, h), h), cornerRadius = r)
        val x = (pace.coerceIn(0.0, 1.0) * this.size.width).toFloat()
        drawLine(marker, Offset(x, 0f), Offset(x, this.size.height), strokeWidth = 2.dp.toPx())
    }
}

@Composable
fun Checkmark(checked: Boolean, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme.primary
    Box(
        modifier.size(24.dp).clip(RoundedCornerShape(7.dp))
            .background(if (checked) c else Color.Transparent)
            .border(2.dp, if (checked) c else MaterialTheme.colorScheme.outline, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Ic.check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun InfoNote(text: String, icon: ImageVector = Ic.info, container: Color = MaterialTheme.colorScheme.surfaceVariant, content: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(container).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = content)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun OneLine(text: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium, color: Color = Color.Unspecified) =
    Text(text, modifier = modifier, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
