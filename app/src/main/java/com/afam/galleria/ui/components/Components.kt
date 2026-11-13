package com.afam.galleria.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afam.galleria.ui.theme.Ios
import com.afam.galleria.ui.theme.IosType

val SystemGray = Color(0xFF8E8E93)

/** Click senza ripple, con l'effetto "attenuato" tipico di iOS quando premuto. */
fun Modifier.iosClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    this
        .graphicsLayer { alpha = if (pressed && enabled) 0.35f else 1f }
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/** Click senza alcun effetto visivo. */
fun Modifier.plainClickable(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Ios.colors.separator) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(color)
    )
}

enum class MainTab(val title: String, val icon: ImageVector) {
    LIBRARY("Libreria", Icons.Rounded.PhotoLibrary),
    FOR_YOU("Per te", Icons.Rounded.AutoAwesome),
    ALBUMS("Album", Icons.Rounded.Collections),
    SEARCH("Cerca", Icons.Rounded.Search),
}

@Composable
fun IosTabBar(selected: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    val c = Ios.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(c.bar)
            .plainClickable { }
    ) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(52.dp)
        ) {
            MainTab.entries.forEach { tab ->
                val active = tab == selected
                val tint = if (active) c.accent else SystemGray
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .plainClickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, contentDescription = tab.title, tint = tint, modifier = Modifier.size(26.dp))
                    Text(tab.title, style = IosType.caption2, color = tint, maxLines = 1)
                }
            }
        }
    }
}

/** Barra di navigazione delle schermate interne, con pulsante "indietro" in stile iOS. */
@Composable
fun IosNavBar(
    title: String,
    modifier: Modifier = Modifier,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    titleVisible: Boolean = true,
    background: Color = Ios.colors.bar,
    showDivider: Boolean = true,
    contentColor: Color = Ios.colors.label,
    accent: Color = Ios.colors.accent,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().background(background)) {
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(44.dp)
        ) {
            if (onBack != null) {
                Row(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .iosClickable(onClick = onBack)
                        .padding(start = 4.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Indietro", tint = accent, modifier = Modifier.size(34.dp))
                    if (backLabel != null) {
                        Text(backLabel, style = IosType.body, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.offset(x = (-4).dp))
                    }
                }
            }
            if (titleVisible) {
                Text(
                    title,
                    style = IosType.headline,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 96.dp),
                )
            }
            Row(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        if (showDivider) Hairline()
    }
}

/** Testo-pulsante nelle barre (es. "Seleziona", "Fine"). */
@Composable
fun BarTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    color: Color = Ios.colors.accent,
    enabled: Boolean = true,
) {
    Text(
        text,
        style = if (bold) IosType.headline else IosType.body,
        color = if (enabled) color else color.copy(alpha = 0.35f),
        maxLines = 1,
        modifier = modifier
            .iosClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

@Composable
fun BarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Ios.colors.accent,
    enabled: Boolean = true,
    size: Dp = 26.dp,
) {
    Box(
        modifier
            .size(44.dp)
            .iosClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(size))
    }
}

/** Capsula semitrasparente usata sopra le foto (es. "Seleziona" nella Libreria). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val c = Ios.colors
    val bg = if (highlighted) c.accent else if (c.isDark) Color(0xCC2C2C2E) else Color(0xB8E5E5EA)
    val fg = if (highlighted) Color.White else c.label
    Box(
        modifier
            .clip(CircleShape)
            .background(bg)
            .iosClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IosType.subhead.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1)
    }
}

@Composable
fun PillIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ios.colors
    val bg = if (c.isDark) Color(0xCC2C2C2E) else Color(0xB8E5E5EA)
    Box(
        modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(bg)
            .iosClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = c.label, modifier = Modifier.size(20.dp))
    }
}

/** Controllo segmentato a capsula (Anni / Mesi / Giorni / Tutte le foto). */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ios.colors
    val bg = if (c.isDark) Color(0xEB252527) else Color(0xEBEEEEF0)
    val indicator = if (c.isDark) Color(0xFF5B5B5F) else Color.White
    BoxWithConstraints(
        modifier
            .height(36.dp)
            .shadow(8.dp, CircleShape, clip = false, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
            .clip(CircleShape)
            .background(bg)
            .padding(3.dp)
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * selected, spring(dampingRatio = 0.8f, stiffness = 600f), label = "segment")
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxHeight()
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(indicator)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .plainClickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = IosType.footnote.copy(fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium),
                        color = c.label,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier, color: Color = Ios.colors.label) {
    Text(
        text,
        style = IosType.largeTitle,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 16.dp),
    )
}

/** Intestazione di sezione: titolo grande + azione opzionale a destra ("Vedi tutti"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = Ios.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = IosType.title2, color = c.label, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            BarTextButton(action, onAction)
        }
    }
}

/** Gruppo di righe con angoli arrotondati (stile "inset grouped"). */
@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ios.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (header != null) {
            Text(
                header.uppercase(),
                style = IosType.footnote,
                color = c.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp, top = 8.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.card),
            content = content,
        )
        if (footer != null) {
            Text(
                footer,
                style = IosType.footnote,
                color = c.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
        }
    }
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Ios.colors.accent,
    value: String? = null,
    titleColor: Color = Ios.colors.label,
    chevron: Boolean = true,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Ios.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
        }
        Box(Modifier.weight(1f).heightIn(min = 48.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = IosType.body, color = titleColor, modifier = Modifier.weight(1f))
                if (value != null) {
                    Text(value, style = IosType.body, color = c.secondaryLabel, maxLines = 1)
                }
                if (trailing != null) trailing()
                if (chevron && onClick != null) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = c.tertiaryLabel,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            if (showDivider) Hairline(Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = Ios.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = c.green,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = c.fill,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** Barra degli strumenti in basso (selezione, visualizzatore...). */
@Composable
fun BottomToolbar(
    modifier: Modifier = Modifier,
    background: Color = Ios.colors.bar,
    showDivider: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(background)
            .plainClickable { }
    ) {
        if (showDivider) Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(50.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            content = content,
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    val c = Ios.colors
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.tertiaryLabel, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = IosType.title3, color = c.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = IosType.subhead, color = c.secondaryLabel, textAlign = TextAlign.Center)
    }
}
