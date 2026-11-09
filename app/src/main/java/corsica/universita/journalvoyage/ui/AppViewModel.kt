package corsica.universita.journalvoyage.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import corsica.universita.journalvoyage.JournalContainer
import corsica.universita.journalvoyage.SharedContent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Le ViewModel de l'Activity : il transforme les intents reçus en navigations.
 * Un Channel (et non un état) : une navigation se consomme une fois. Bufferisé : un intent reçu
 * avant que l'écran n'écoute (démarrage à froid) attend d'être lu.
 */
class AppViewModel : ViewModel() {
    private val _navigation = Channel<NavKey>(Channel.BUFFERED)
    val navigation: Flow<NavKey> = _navigation.receiveAsFlow()

    fun openEtape(id: Long) = _navigation.trySend(EtapeDetail(id))
    fun openTrajet(id: Long) = _navigation.trySend(TrajetDetail(id))
    fun openHome() = _navigation.trySend(Home)

    /** Un push qui vise un carnet : il devient le carnet actif, l'accueil l'affiche. */
    fun openVoyage(id: Long) {
        viewModelScope.launch {
            JournalContainer.settings.setActiveVoyage(id)
            _navigation.send(Home)
        }
    }

    /**
     * Un texte ou une photo partagés depuis une autre app : ils préremplissent le formulaire
     * « Nouvelle étape de … » du carnet actif, sur l'accueil.
     */
    fun receiveShare(text: String?, imageUri: String?) {
        Log.d("Journal", "Partage reçu → formulaire de l'accueil")
        JournalContainer.pendingShare.value = SharedContent(text, imageUri)
        _navigation.trySend(Home)
    }
}
