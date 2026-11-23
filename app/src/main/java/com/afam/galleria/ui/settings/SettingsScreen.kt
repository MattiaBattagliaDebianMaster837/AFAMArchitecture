package com.afam.galleria.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afam.galleria.BuildConfig
import com.afam.galleria.GalleryViewModel
import com.afam.galleria.ui.components.GroupedSection
import com.afam.galleria.ui.components.IosNavBar
import com.afam.galleria.ui.components.IosSwitch
import com.afam.galleria.ui.components.ListRow
import com.afam.galleria.ui.components.LocalOverlay
import com.afam.galleria.ui.components.SheetAction
import com.afam.galleria.ui.components.Thumbs
import com.afam.galleria.ui.nav.LocalNavigator
import com.afam.galleria.ui.glass.GlassScreen
import com.afam.galleria.ui.theme.Ios
import com.afam.galleria.util.Dates
import com.afam.galleria.util.ShareUtil

@Composable
fun SettingsScreen(vm: GalleryViewModel) {
    val library by vm.library.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val overlay = LocalOverlay.current
    val context = LocalContext.current
    val c = Ios.colors
    val local = library.local
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var canManage by remember { mutableStateOf(vm.canManageMedia()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canManage = vm.canManageMedia()
    }

    GlassScreen(
        background = c.groupedBackground,
        content = {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = statusTop + 56.dp, bottom = navBottom + 32.dp),
        ) {
            item {
                GroupedSection(header = "Libreria") {
                    ListRow(
                        title = "Più recenti in alto",
                        icon = Icons.AutoMirrored.Outlined.Sort,
                        chevron = false,
                        trailing = { IosSwitch(local.newestFirst) { vm.setNewestFirst(it) } },
                    )
                    ListRow(
                        title = "Colonne della griglia",
                        icon = Icons.Outlined.Apps,
                        value = local.gridColumns.toString(),
                        showDivider = false,
                        onClick = {
                            overlay.actionSheet(
                                title = "Colonne della griglia",
                                message = "Puoi anche pizzicare la griglia con due dita.",
                                actions = listOf(2, 3, 4, 5, 7).map { n ->
                                    SheetAction("$n colonne") { vm.setGridColumns(n) }
                                },
                            )
                        },
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
            item {
                GroupedSection(
                    header = "Aspetto",
                    footer = "Liquid Glass usa sfocatura e rifrazione in tempo reale (Android 12 o successivo). Disattivalo per risparmiare batteria sui telefoni meno recenti.",
                ) {
                    ListRow(
                        title = "Effetto Liquid Glass",
                        icon = Icons.Outlined.AutoAwesome,
                        chevron = false,
                        showDivider = false,
                        trailing = { IosSwitch(local.glassEffects) { vm.setGlassEffects(it) } },
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
            item {
                GroupedSection(
                    header = "Privacy",
                    footer = "Gli album Nascosti ed Eliminati di recente richiedono lo sblocco con impronta, volto o PIN.",
                ) {
                    ListRow(
                        title = "Mostra album Nascosti",
                        icon = Icons.Outlined.VisibilityOff,
                        chevron = false,
                        trailing = { IosSwitch(local.showHiddenAlbum) { vm.setShowHiddenAlbum(it) } },
                    )
                    ListRow(
                        title = "Proteggi con sblocco",
                        icon = Icons.Outlined.Lock,
                        chevron = false,
                        showDivider = false,
                        trailing = { IosSwitch(local.lockPrivate) { vm.setLockPrivate(it) } },
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
            item {
                GroupedSection(
                    header = "Autorizzazioni",
                    footer = "Con «Gestione contenuti multimediali» Galleria può spostare elementi in Eliminati di recente senza la richiesta di conferma di Android.",
                ) {
                    ListRow(
                        title = "Gestione contenuti multimediali",
                        icon = Icons.Outlined.DeleteSweep,
                        value = if (canManage) "Consentita" else "Non consentita",
                        onClick = { ShareUtil.openManageMediaSettings(context) },
                    )
                    ListRow(
                        title = "Accesso a foto e video",
                        icon = Icons.Outlined.PhotoLibrary,
                        onClick = { ShareUtil.openAppSettings(context) },
                    )
                    ListRow(
                        title = "Imposta come galleria predefinita",
                        icon = Icons.Outlined.Folder,
                        showDivider = false,
                        onClick = { ShareUtil.openDefaultAppsSettings(context) },
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
            item {
                GroupedSection(header = "Informazioni") {
                    ListRow(
                        title = "Elementi nella libreria",
                        icon = Icons.Outlined.PhotoLibrary,
                        value = Dates.format(library.all.size),
                        chevron = false,
                    )
                    ListRow(
                        title = "Svuota cache miniature",
                        icon = Icons.Outlined.DeleteSweep,
                        onClick = {
                            Thumbs.clear()
                            vm.message("Cache svuotata")
                        },
                    )
                    ListRow(
                        title = "Versione",
                        icon = Icons.Outlined.Info,
                        value = BuildConfig.VERSION_NAME,
                        chevron = false,
                        showDivider = false,
                    )
                }
            }
        }
        },
        overlay = {
        IosNavBar(title = "Impostazioni", backLabel = "Indietro", onBack = { nav.pop() })
        },
    )
}
