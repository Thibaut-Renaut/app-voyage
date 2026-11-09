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
import corsica.universita.journalvoyage.ui.home.HomeScreen
import corsica.universita.journalvoyage.ui.trajet.TrajetScreen
import kotlinx.serialization.Serializable

// Les écrans. La PWA tient sur une page (Home) ; l'app y ajoute deux écrans natifs :
// le détail d'une étape (partage, carte) et un trajet GPS. Clés sérialisables : le backstack
// est sauvegardé à la rotation ET à la mort du process (rememberNavBackStack).
@Serializable data object Home : NavKey
@Serializable data class EtapeDetail(val id: Long) : NavKey
@Serializable data class TrajetDetail(val id: Long) : NavKey

/**
 * Navigation 3 : naviguer = ajouter une clé ; revenir = retirer la dernière.
 * Les deux décorateurs donnent à CHAQUE entrée son état sauvegardé et ses ViewModels.
 */
@Composable
fun JournalNavigation(appViewModel: AppViewModel) {
    val backStack = rememberNavBackStack(Home)

    /** Retirer CETTE clé, seulement si elle est au sommet (un double-tap sur Retour ne vide pas la pile). */
    fun pop(key: NavKey) {
        if (backStack.size > 1 && backStack.last() == key) backStack.removeAt(backStack.lastIndex)
    }

    /** Et à l'aller : un double-tap n'empile pas deux fois le même écran. */
    fun push(key: NavKey) {
        if (backStack.lastOrNull() != key) backStack.add(key)
    }

    // Les intents reçus par l'Activity (notification, partage, App Link) deviennent des navigations.
    LaunchedEffect(Unit) {
        appViewModel.navigation.collect { key ->
            if (key == Home) {
                while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            } else {
                push(key)
            }
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<Home> {
                HomeScreen(onOpenEtape = { push(EtapeDetail(it)) }, onOpenTrajet = { push(TrajetDetail(it)) })
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
