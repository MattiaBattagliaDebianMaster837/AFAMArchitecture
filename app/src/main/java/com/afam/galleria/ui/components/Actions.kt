package com.afam.galleria.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.afam.galleria.GalleryViewModel
import com.afam.galleria.data.MediaItem
import com.afam.galleria.ui.theme.Ios
import com.afam.galleria.ui.theme.IosType
import com.afam.galleria.util.ShareUtil

/** Restituisce un'azione che elimina gli elementi chiedendo conferma in stile iOS. */
@Composable
fun rememberDeleteAction(vm: GalleryViewModel): (List<MediaItem>, () -> Unit) -> Unit {
    val overlay = LocalOverlay.current
    return remember(vm, overlay) {
        { items: List<MediaItem>, onDone: () -> Unit ->
            if (items.isNotEmpty()) {
                if (vm.canManageMedia()) {
                    val label = when {
                        items.size > 1 -> "Elimina ${items.size} elementi"
                        items[0].isVideo -> "Elimina video"
                        else -> "Elimina foto"
                    }
                    overlay.actionSheet(
                        message = if (items.size == 1) {
                            "Questo elemento verrà spostato in Eliminati di recente."
                        } else {
                            "Questi elementi verranno spostati in Eliminati di recente."
                        },
                        actions = listOf(SheetAction(label, destructive = true) {
                            vm.trash(items) { ok -> if (ok) onDone() }
                        }),
                    )
                } else {
                    vm.trash(items) { ok -> if (ok) onDone() }
                }
            }
        }
    }
}

/** Barra inferiore della modalità di selezione: Condividi · N selezionati · Altro · Elimina. */
@Composable
fun SelectionToolbar(
    selection: SelectionState,
    source: List<MediaItem>,
    vm: GalleryViewModel,
    favorites: Set<Long>,
    modifier: Modifier = Modifier,
    albumId: String? = null,
    hiddenMode: Boolean = false,
) {
    val context = LocalContext.current
    val overlay = LocalOverlay.current
    val delete = rememberDeleteAction(vm)
    val selected = remember(selection.selected, source) { selection.items(source) }
    val enabled = selected.isNotEmpty()
    val c = Ios.colors
    BottomToolbar(modifier) {
        BarIconButton(Icons.Rounded.IosShare, "Condividi", { ShareUtil.share(context, selected) }, enabled = enabled)
        Text(
            when (selected.size) {
                0 -> "Seleziona elementi"
                1 -> "1 elemento selezionato"
                else -> "${selected.size} elementi selezionati"
            },
            style = IosType.subhead.copy(fontWeight = FontWeight.SemiBold),
            color = c.label,
        )
        Row {
            BarIconButton(Icons.Rounded.MoreHoriz, "Altro", {
                val allFavorite = selected.all { it.id in favorites }
                val actions = buildList {
                    add(SheetAction(if (allFavorite) "Rimuovi dai preferiti" else "Aggiungi ai preferiti") {
                        vm.setFavorite(selected, !allFavorite)
                        selection.exit()
                    })
                    add(SheetAction("Aggiungi ad album") {
                        overlay.pickAlbum(selected)
                        selection.exit()
                    })
                    if (albumId != null) {
                        add(SheetAction("Rimuovi dall'album", destructive = true) {
                            vm.removeFromAlbum(albumId, selected)
                            selection.exit()
                        })
                    }
                    if (selected.size == 1) {
                        add(SheetAction("Duplica") {
                            vm.duplicate(selected[0])
                            selection.exit()
                        })
                    }
                    add(SheetAction(if (hiddenMode) "Mostra" else "Nascondi") {
                        vm.setHidden(selected, !hiddenMode)
                        selection.exit()
                    })
                }
                overlay.actionSheet(actions = actions)
            }, enabled = enabled)
            BarIconButton(Icons.Outlined.Delete, "Elimina", { delete(selected) { selection.exit() } }, enabled = enabled)
        }
    }
}
