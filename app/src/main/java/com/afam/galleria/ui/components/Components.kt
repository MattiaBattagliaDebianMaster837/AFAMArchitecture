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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Map
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afam.galleria.ui.glass.GlassActions
import com.afam.galleria.ui.glass.GlassIconButton
import com.afam.galleria.ui.glass.GlassTextButton
import com.afam.galleria.ui.glass.glass
import com.afam.galleria.ui.theme.Ios
import com.afam.galleria.ui.theme.IosType

val SystemGray = Color(0xFF8E8E93)

/** Altezza occupata dalla barra delle schede fluttuante (esclusa la barra di navigazione). */
val TabBarHeight = 76.dp

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
    MAP("Mappa", Icons.Rounded.Map),
    SEARCH("Cerca", Icons.Rounded.Search),
}

/**
 * Barra delle schede Liquid Glass: capsula fluttuante con un indicatore che scorre
 * sotto la scheda attiva e un pulsante Cerca separato, come su iOS 26.
 */
@Composable
fun GlassTabBar(selected: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    val c = Ios.colors
    val tabs = remember { MainTab.entries.filter { it != MainTab.SEARCH } }
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, bottom = 8.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(62.dp)
                .glass(RoundedCornerShape(50))
                .padding(4.dp)
        ) {
            val itemWidth = maxWidth / tabs.size
            val index = tabs.indexOf(selected)
            val x by animateDpAsState(
                itemWidth * index.coerceAtLeast(0),
                spring(dampingRatio = 0.72f, stiffness = 520f),
                label = "tab",
            )
            if (index >= 0) {
                Box(
                    Modifier
                        .offset(x = x)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(if (c.isDark) Color.White.copy(alpha = 0.13f) else Color.Black.copy(alpha = 0.07f))
                )
            }
            Row(Modifier.fillMaxSize()) {
                tabs.forEach { tab ->
                    val active = tab == selected
                    val tint = if (active) c.accent else c.label
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .plainClickable { onSelect(tab) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(tab.icon, contentDescription = tab.title, tint = tint, modifier = Modifier.size(24.dp))
                        Text(
                            tab.title,
                            style = IosType.caption2.copy(fontSize = 10.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium),
                            color = tint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        val searchActive = selected == MainTab.SEARCH
        GlassIconButton(
            Icons.Rounded.Search,
            "Cerca",
            { onSelect(MainTab.SEARCH) },
            size = 62.dp,
            iconSize = 26.dp,
            tint = if (searchActive) c.accent else c.label,
        )
    }
}

/**
 * Barra di navigazione delle schermate interne in stile iOS 26: pulsante indietro di vetro,
 * azioni raccolte in una capsula di vetro, titolo che compare scorrendo.
 * [background], [showDivider] e [backLabel] sono mantenuti per compatibilità.
 */
@Composable
fun IosNavBar(
    title: String,
    modifier: Modifier = Modifier,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    titleVisible: Boolean = true,
    background: Color = Ios.colors.background,
    showDivider: Boolean = true,
    contentColor: Color = Ios.colors.label,
    accent: Color = Ios.colors.accent,
    actions: @Composable RowScope.() -> Unit = {},
) {
    @Suppress("UNUSED_VARIABLE") val unused = backLabel to showDivider
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier.fillMaxWidth()) {
        // Sfumatura "scroll edge" sotto i controlli, per la leggibilità.
        Box(
            Modifier
                .fillMaxWidth()
                .height(statusTop + 76.dp)
                .background(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = 0.92f),
                        0.6f to background.copy(alpha = 0.55f),
                        1f to Color.Transparent,
                    )
                )
        )
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 12.dp)
        ) {
            if (onBack != null) {
                GlassIconButton(
                    Icons.Rounded.ChevronLeft,
                    "Indietro",
                    onBack,
                    modifier = Modifier.align(Alignment.CenterStart),
                    iconSize = 30.dp,
                    tint = accent,
                )
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
            GlassActions(Modifier.align(Alignment.CenterEnd), content = actions)
        }
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
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

/** Capsula di vetro con testo, usata sopra le foto (es. "Seleziona" nella Libreria). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    GlassTextButton(text, onClick, modifier, prominent = highlighted, height = 38.dp)
}

@Composable
fun PillIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassIconButton(icon, contentDescription, onClick, modifier, size = 38.dp, iconSize = 20.dp)
}

/** Controllo segmentato a capsula di vetro (Anni / Mesi / Giorni / Tutte le foto). */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ios.colors
    BoxWithConstraints(
        modifier
            .height(42.dp)
            .glass(RoundedCornerShape(50))
            .padding(4.dp)
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * selected, spring(dampingRatio = 0.75f, stiffness = 560f), label = "segment")
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(if (c.isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.85f))
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
            .padding(start = 16.dp, end = 4.dp, top = 20.dp, bottom = 8.dp),
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
                .clip(RoundedCornerShape(22.dp))
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
            .heightIn(min = 50.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
        }
        Box(Modifier.weight(1f).heightIn(min = 50.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
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

/** Barra degli strumenti in basso: capsula di vetro fluttuante (selezione, visualizzatore...). */
@Composable
fun BottomToolbar(
    modifier: Modifier = Modifier,
    background: Color = Color.Transparent,
    showDivider: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    @Suppress("UNUSED_VARIABLE") val unused = background to showDivider
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 8.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .glass(RoundedCornerShape(50))
                .plainClickable { }
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

@Suppress("unused")
private val CircleShapeRef = CircleShape
