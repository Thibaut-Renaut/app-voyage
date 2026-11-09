package corsica.universita.journalvoyage.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import corsica.universita.journalvoyage.ui.etape.EtapeDetailScreen
import corsica.universita.journalvoyage.ui.etape.EtapeEditScreen
import corsica.universita.journalvoyage.ui.trajet.TrajetScreen
import corsica.universita.journalvoyage.ui.trajet.TrajetsSection
import corsica.universita.journalvoyage.ui.voyage.VoyageScreen
import corsica.universita.journalvoyage.ui.voyages.VoyageEditScreen
import corsica.universita.journalvoyage.ui.voyages.VoyagesScreen
import kotlinx.serialization.Serializable

// Les écrans : une clé typée par écran, ses arguments dedans. Sérialisables : le backstack entier est
// sauvegardé à la rotation ET à la mort du process (rememberNavBackStack).
@Serializable data object VoyagesList : NavKey
@Serializable data class VoyageEdit(val id: Long = 0) : NavKey
@Serializable data class VoyageDetail(val id: Long) : NavKey
@Serializable data class EtapeEdit(val voyageId: Long, val sharedText: String? = null, val sharedImage: String? = null) : NavKey
@Serializable data class EtapeDetail(val id: Long) : NavKey
@Serializable data class TrajetDetail(val id: Long) : NavKey

/**
 * Navigation 3 : naviguer = ajouter une clé ; revenir = retirer la dernière.
 * Les deux décorateurs donnent à CHAQUE entrée son état sauvegardé et ses ViewModels : le ViewModel
 * d'un formulaire meurt quand on quitte le formulaire (onCleared), pas à la rotation.
 */
@Composable
fun JournalNavigation(appViewModel: AppViewModel) {
    val backStack = rememberNavBackStack(VoyagesList)

    // Les intents reçus par l'Activity (notification, partage, App Link) deviennent des navigations.
    LaunchedEffect(Unit) {
        appViewModel.navigation.collect { key ->
            if (key == VoyagesList) {
                while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            } else if (backStack.lastOrNull() != key) {
                backStack.add(key)
            }
        }
    }

    /**
     * Retirer CETTE clé, seulement si elle est au sommet. Un double-tap sur « Retour » (ou la barre d'espace
     * sur un bouton qui a le focus) arrive une 2ᵉ fois sur l'écran qui part, encore affiché pendant
     * l'animation : un removeLastOrNull() aveugle retirerait alors la racine → pile vide → crash.
     */
    fun pop(key: NavKey) {
        if (backStack.size > 1 && backStack.last() == key) backStack.removeAt(backStack.lastIndex)
    }

    /** Et à l'aller : un double-tap sur une carte n'empile pas deux fois le même écran. */
    fun push(key: NavKey) {
        if (backStack.lastOrNull() != key) backStack.add(key)
    }

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<VoyagesList> {
                VoyagesScreen(
                    onOpen = { push(VoyageDetail(it)) },
                    onCreate = { push(VoyageEdit()) },
                )
            }
            entry<VoyageEdit> { key ->
                VoyageEditScreen(voyageId = key.id, onDone = { pop(key) })
            }
            entry<VoyageDetail> { key ->
                VoyageScreen(
                    voyageId = key.id,
                    onBack = { pop(key) },
                    onEdit = { push(VoyageEdit(key.id)) },
                    onAddEtape = { push(EtapeEdit(key.id)) },
                    onOpenEtape = { push(EtapeDetail(it)) },
                    extraContent = { TrajetsSection(voyageId = key.id, onOpen = { push(TrajetDetail(it)) }) },
                )
            }
            entry<EtapeEdit> { key ->
                EtapeEditScreen(key, onDone = { pop(key) })
            }
            entry<EtapeDetail> { key ->
                EtapeDetailScreen(etapeId = key.id, onBack = { pop(key) })
            }
            entry<TrajetDetail> { key ->
                TrajetScreen(trajetId = key.id, onBack = { pop(key) })
            }
        },
    )
}
