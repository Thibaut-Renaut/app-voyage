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

class AppViewModel : ViewModel() {
    private val _navigation = Channel<NavKey>(Channel.BUFFERED)
    val navigation: Flow<NavKey> = _navigation.receiveAsFlow()

    fun openEtape(id: Long) = _navigation.trySend(EtapeDetail(id))
    fun openTrajet(id: Long) = _navigation.trySend(TrajetDetail(id))
    fun openHome() = _navigation.trySend(Home)

    fun openVoyage(id: Long) {
        viewModelScope.launch {
            JournalContainer.settings.setActiveVoyage(id)
            _navigation.send(Home)
        }
    }

    fun receiveShare(text: String?, imageUri: String?) {
        Log.d("Journal", "Partage reçu → formulaire de l'accueil")
        JournalContainer.pendingShare.value = SharedContent(text, imageUri)
        _navigation.trySend(Home)
    }
}
