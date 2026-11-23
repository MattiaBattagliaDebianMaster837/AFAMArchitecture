package com.afam.galleria.ui.glass

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.afam.galleria.ui.theme.Ios
import com.afam.galleria.ui.theme.IosType
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow as GlassShadow

/**
 * Sfondo da cui il vetro "legge" ciò che sta dietro. Viene fornito dai [GlassBox]
 * ai soli elementi sovrapposti, mai al contenuto campionato (eviterebbe ricorsioni).
 */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** false = effetto ridotto (niente sfocatura/rifrazione), per telefoni meno potenti. */
val LocalGlassEffects = staticCompositionLocalOf { true }

/** true se il dispositivo supporta sfocatura in tempo reale (Android 12+). */
val glassBlurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Contenitore Liquid Glass: [content] viene registrato in un livello grafico e gli elementi
 * di [overlay] lo usano per sfocarlo e rifrangerlo. Il contenuto viene disegnato una volta sola.
 */
@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    val enabled = LocalGlassEffects.current && glassBlurSupported
    if (!enabled) {
        Box(modifier) {
            content()
            CompositionLocalProvider(LocalGlassBackdrop provides null) { overlay() }
        }
        return
    }
    val backdrop = rememberLayerBackdrop()
    Box(modifier) {
        Box(Modifier.matchParentSize().layerBackdrop(backdrop)) {
            CompositionLocalProvider(LocalGlassBackdrop provides null) { content() }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) { overlay() }
    }
}

/** Schermata intera con sfondo e controlli di vetro sovrapposti. */
@Composable
fun GlassScreen(
    background: Color,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    GlassBox(
        modifier = Modifier.fillMaxSize(),
        content = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(background),
                content = content,
            )
        },
        overlay = overlay,
    )
}

/** Colore della superficie di vetro in base al tema. */
@Composable
fun glassSurface(prominent: Boolean = false): Color {
    val c = Ios.colors
    return when {
        prominent -> c.accent.copy(alpha = 0.88f)
        c.isDark -> Color(0xFF1E1E21).copy(alpha = 0.42f)
        else -> Color.White.copy(alpha = 0.42f)
    }
}

/** Superficie di ripiego quando la sfocatura non è disponibile: più coprente per restare leggibile. */
@Composable
fun glassFallback(prominent: Boolean = false): Color {
    val c = Ios.colors
    return when {
        prominent -> c.accent
        c.isDark -> Color(0xF0262629)
        else -> Color(0xF2F8F8FA)
    }
}

/**
 * Applica il materiale Liquid Glass: vibranza, leggera sfocatura, rifrazione ai bordi,
 * riflesso speculare e ombra morbida. Ricade su una superficie traslucida se non supportato.
 */
@Composable
fun Modifier.glass(
    shape: CornerBasedShape,
    prominent: Boolean = false,
    tint: Color? = null,
    blurRadius: Dp = 4.dp,
    refraction: Boolean = true,
    shadow: Boolean = true,
): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val c = Ios.colors
    if (backdrop == null) {
        val fallback = tint?.copy(alpha = maxOf(tint.alpha, 0.9f)) ?: glassFallback(prominent)
        val edge = if (c.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
        return this
            .then(if (shadow) Modifier.shadow(10.dp, shape, clip = false, ambientColor = Color(0x22000000), spotColor = Color(0x33000000)) else Modifier)
            .clip(shape)
            .background(fallback)
            .border(0.5.dp, edge, shape)
    }
    val density = LocalDensity.current
    val surface = tint ?: glassSurface(prominent)
    val blurPx = with(density) { blurRadius.toPx() }
    val refractionHeight = with(density) { 14.dp.toPx() }
    val refractionAmount = with(density) { 28.dp.toPx() }
    val shapeBlock = remember(shape) { { shape } }
    val effects: BackdropEffectScope.() -> Unit = remember(blurPx, refraction, refractionHeight, refractionAmount) {
        {
            vibrancy()
            blur(blurPx)
            if (refraction) {
                val height = minOf(refractionHeight, size.minDimension / 3f)
                lens(height, refractionAmount, depthEffect = true)
            }
        }
    }
    val drawSurface: DrawScope.() -> Unit = remember(surface) { { drawRect(surface) } }
    val shadowBlock: (() -> GlassShadow?)? = remember(shadow, c.isDark) {
        if (shadow) {
            val s = GlassShadow(
                radius = 18.dp,
                offset = DpOffset(0.dp, 4.dp),
                color = Color.Black.copy(alpha = if (c.isDark) 0.35f else 0.12f),
            )
            ({ s })
        } else null
    }
    val highlightBlock: () -> Highlight? = remember(c.isDark) {
        val h = if (c.isDark) Highlight(alpha = 0.6f) else Highlight.Default
        ({ h })
    }
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = shapeBlock,
        effects = effects,
        highlight = highlightBlock,
        shadow = shadowBlock,
        onDrawSurface = drawSurface,
    )
}

/** Effetto "pressione" dei controlli di vetro: si ingrandiscono leggermente, come su iOS 26. */
@Composable
private fun Modifier.glassPress(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 1.08f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "glassPress",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Pulsante circolare di vetro con un'icona. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = Ios.colors.label,
    prominent: Boolean = false,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .glassPress(source)
            .size(size)
            .glass(CircleShape, prominent = prominent)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription,
            tint = if (prominent) Color.White else if (enabled) tint else tint.copy(alpha = 0.35f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Pulsante a capsula di vetro con testo (es. "Seleziona", "Fine"). */
@Composable
fun GlassTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    color: Color = Ios.colors.label,
    enabled: Boolean = true,
    height: Dp = 40.dp,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .glassPress(source)
            .height(height)
            .glass(RoundedCornerShape(50), prominent = prominent)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = IosType.subhead.copy(fontWeight = FontWeight.SemiBold),
            color = when {
                prominent -> Color.White
                enabled -> color
                else -> color.copy(alpha = 0.35f)
            },
            maxLines = 1,
        )
    }
}

/** Capsula di vetro che raggruppa più controlli (barre degli strumenti). */
@Composable
fun GlassCapsule(
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .height(height)
            .glass(RoundedCornerShape(50))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Capsula di vetro per le azioni di una barra: se non contiene nulla non disegna niente
 * (evita livelli grafici di dimensione zero).
 */
@Composable
fun GlassActions(
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    content: @Composable RowScope.() -> Unit,
) {
    var width by remember { mutableIntStateOf(0) }
    Box(modifier) {
        if (width > 0) {
            Box(
                Modifier
                    .matchParentSize()
                    .glass(RoundedCornerShape(50))
            )
        }
        Row(
            Modifier
                .height(height)
                .onSizeChanged { width = it.width },
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Pannello di vetro ad angoli arrotondati (menu, schede, fogli). */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    tint: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.glass(RoundedCornerShape(cornerRadius), tint = tint, blurRadius = 12.dp, refraction = true),
        content = content,
    )
}
