package corsica.universita.journalvoyage.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import corsica.universita.journalvoyage.JournalContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Le ViewModel de l'Activity : il transforme les intents reçus en navigations.
 * Un Channel (et non un état) : une navigation se consomme une fois. Bufferisé : un intent reçu
 * avant que l'écran n'écoute (démarrage à froid) attend d'être lu.
 */
class AppViewModel : ViewModel() {
    private val repository = JournalContainer.repository
    private val settings = JournalContainer.settings

    private val _navigation = Channel<NavKey>(Channel.BUFFERED)
    val navigation: Flow<NavKey> = _navigation.receiveAsFlow()

    fun openEtape(id: Long) = _navigation.trySend(EtapeDetail(id))
    fun openVoyage(id: Long) = _navigation.trySend(VoyageDetail(id))
    fun openTrajet(id: Long) = _navigation.trySend(TrajetDetail(id))
    fun openHome() = _navigation.trySend(VoyagesList)

    /** Un texte ou une photo partagés depuis une autre app : une nouvelle étape, dans le voyage actif. */
    fun receiveShare(text: String?, imageUri: String?) {
        viewModelScope.launch {
            val active = settings.activeVoyageId.first()?.takeIf { repository.getVoyage(it) != null }
            val voyageId = active ?: repository.latestVoyageId()
            Log.d("Journal", "Partage reçu → nouvelle étape dans le voyage $voyageId")
            if (voyageId == null) _navigation.send(VoyagesList) // aucun voyage : l'utilisateur en crée un d'abord
            else _navigation.send(EtapeEdit(voyageId, sharedText = text, sharedImage = imageUri))
        }
    }
}
