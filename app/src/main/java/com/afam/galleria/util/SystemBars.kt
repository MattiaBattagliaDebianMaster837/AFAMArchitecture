package com.afam.galleria.util

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Adatta le icone della barra di stato allo sfondo (chiaro/scuro) e, se richiesto,
 * nasconde le barre di sistema. Al termine ripristina lo stato predefinito.
 */
@Composable
fun SystemBarsEffect(darkBackground: Boolean, hidden: Boolean = false) {
    val view = LocalView.current
    val systemDark = isSystemInDarkTheme()
    if (view.isInEditMode) return
    DisposableEffect(darkBackground, hidden, systemDark) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkBackground
            controller.isAppearanceLightNavigationBars = !darkBackground
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hidden) controller.hide(WindowInsetsCompat.Type.systemBars())
            else controller.show(WindowInsetsCompat.Type.systemBars())
            onDispose {
                controller.isAppearanceLightStatusBars = !systemDark
                controller.isAppearanceLightNavigationBars = !systemDark
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
