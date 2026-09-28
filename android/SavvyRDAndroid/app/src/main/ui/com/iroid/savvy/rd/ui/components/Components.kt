package com.iroid.savvy.rd.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iroid.savvy.rd.ui.theme.SavvyType
import com.iroid.savvy.rd.ui.theme.savvyColors

val SheetCorner = 48.dp
val CardShape = RoundedCornerShape(28.dp)

/**
 * Brick-style frame: the screen is a sheet whose bottom corners curve up, and the
 * nav bar sits underneath it on the darker canvas.
 */
@Composable
fun CurvedSheetFrame(bottomBar: @Composable () -> Unit, content: @Composable () -> Unit) {
    val c = savvyColors
    Column(Modifier.fillMaxSize().background(c.canvas)) {
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(bottomStart = SheetCorner, bottomEnd = SheetCorner), ambientColor = c.shadow, spotColor = c.shadow)
                .clip(RoundedCornerShape(bottomStart = SheetCorner, bottomEnd = SheetCorner))
                .background(c.sheet)
        ) { content() }
        Box(Modifier.navigationBarsPadding()) { bottomBar() }
    }
}

/** Text-only tab bar with a small square marker under the selected tab. */
@Composable
fun TextTabBar(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = savvyColors
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        tabs.forEachIndexed { i, label ->
            val on = i == selected
            val color by animateColorAsState(if (on) c.ink else c.inkSoft.copy(alpha = 0.75f), label = "tab")
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable { onSelect(i) }.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, style = SavvyType.nav, color = color)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(6.dp).background(if (on) c.ink else Color.Transparent, RoundedCornerShape(1.dp)))
            }
        }
    }
}

/**
 * Sub-screen frame, like Brick's "Add schedule": a sheet with rounded top corners over
 * the canvas, a round back button and a centred title. [bottom] is pinned (primary action).
 */
@Composable
fun ModalPage(
    title: String,
    onBack: () -> Unit,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = savvyColors
    Box(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding()) {
        Column(
            Modifier.padding(top = 10.dp).fillMaxSize()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(c.sheet)
        ) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, Modifier.align(Alignment.CenterStart))
                Text(title, style = SavvyType.bodyMedium.copy(fontSize = 17.sp), color = c.ink, modifier = Modifier.align(Alignment.Center))
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().let { if (scroll) it.verticalScroll(rememberScrollState()) else it }
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
                Spacer(Modifier.height(16.dp))
            }
            if (bottom != null) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { bottom() }
            } else Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val c = savvyColors
    Box(
        modifier.size(size).clip(CircleShape).background(c.cardStrong).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = c.ink, modifier = Modifier.size(22.dp)) }
}

/** A rounded group of rows with hairline dividers, like the Brick settings cards. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = savvyColors
    Column(modifier.fillMaxWidth().clip(CardShape).background(c.card).padding(horizontal = 22.dp), content = content)
}

@Composable
fun RowDivider() = HorizontalDivider(color = savvyColors.line, thickness = 1.dp)

@Composable
fun SettingRow(
    title: String,
    icon: ImageVector? = null,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color? = null,
    chevron: Boolean = true,
    titleColor: Color? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = savvyColors
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null && enabled) it.clickable(onClick = onClick) else it }.padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = titleColor ?: c.ink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = SavvyType.bodyMedium, color = (titleColor ?: c.ink).copy(alpha = if (enabled) 1f else 0.45f))
            if (subtitle != null) Text(subtitle, style = SavvyType.caption, color = c.inkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) Text(value, style = SavvyType.body, color = valueColor ?: c.inkSoft, modifier = Modifier.padding(start = 8.dp))
        trailing?.invoke(this)
        if (chevron && onClick != null) {
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(22.dp).clip(CircleShape).background(c.cardStrong), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.ink, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Label on the left, value on the right, in its own pill: the Brick form field. */
@Composable
fun FieldPill(label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit) {
    val c = savvyColors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.card)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = SavvyType.body, color = c.inkSoft)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val c = savvyColors
    Row(modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = SavvyType.body, color = c.inkSoft, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = SavvyType.caption, color = c.inkSoft)
    }
}

enum class PillStyle { SOLID, SOFT, OUTLINE, DANGER }

/** Full-width pill button. SOLID = navy (Brick's black "Save"), OUTLINE = Brick's "Tap or hold". */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.SOLID,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = 60.dp,
) {
    val c = savvyColors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (bg, fg) = when (style) {
        PillStyle.SOLID -> c.primary to c.onPrimary
        PillStyle.SOFT -> c.cardStrong to c.ink
        PillStyle.OUTLINE -> c.card to c.ink
        PillStyle.DANGER -> c.dangerSoft to c.danger
    }
    Box(
        modifier.fillMaxWidth().height(height).scale(if (pressed) 0.98f else 1f)
            .clip(CircleShape).background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .let { if (style == PillStyle.OUTLINE) it.border(1.dp, c.line, CircleShape) else it }
            .clickable(interaction, indication = androidx.compose.material3.ripple(), enabled = enabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(color = fg, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        else Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = fg.copy(alpha = if (enabled) 1f else 0.5f), modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
            Text(text, style = SavvyType.bodyMedium.copy(fontSize = 17.sp), color = fg.copy(alpha = if (enabled) 1f else 0.5f))
        }
    }
}

/** Small rounded chip, used for durations, modes and filters. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = savvyColors
    val bg by animateColorAsState(if (selected) c.primary else c.card, label = "chip")
    val fg by animateColorAsState(if (selected) c.onPrimary else c.ink, label = "chipText")
    Box(
        modifier.clip(CircleShape).background(bg).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = SavvyType.label, color = fg) }
}

/** Round letter toggle (Brick's repeat days); used for durations etc. */
@Composable
fun CircleToggle(text: String, selected: Boolean, onClick: () -> Unit, size: Dp = 44.dp) {
    val c = savvyColors
    Box(
        Modifier.size(size).clip(CircleShape).background(if (selected) c.primary else c.cardStrong).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = SavvyType.label, color = if (selected) c.onPrimary else c.inkSoft) }
}

@Composable
fun SavvySwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = savvyColors
    Switch(checked, onChange, colors = SwitchDefaults.colors(
        checkedThumbColor = c.onPrimary, checkedTrackColor = c.primary, uncheckedThumbColor = c.inkSoft,
        uncheckedTrackColor = c.cardStrong, uncheckedBorderColor = c.line,
    ))
}

/** Rounded status pill at the top of a tab, like Brick's "0h 0m today". */
@Composable
fun TopPill(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val c = savvyColors
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(c.card)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Soft informational callout. */
@Composable
fun Callout(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tone: Color? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = savvyColors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(tone ?: c.icySoft)
            .let { if (onAction != null) it.clickable(onClick = onAction) else it }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = c.ink, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)) }
        Text(text, style = SavvyType.caption.copy(fontSize = 14.sp), color = c.ink, modifier = Modifier.weight(1f))
        if (action != null) Text(action, style = SavvyType.label, color = c.ink, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    val c = savvyColors
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = SavvyType.title, color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = SavvyType.body, color = c.inkSoft, textAlign = TextAlign.Center)
    }
}

val TabContentPadding = PaddingValues(horizontal = 20.dp)
