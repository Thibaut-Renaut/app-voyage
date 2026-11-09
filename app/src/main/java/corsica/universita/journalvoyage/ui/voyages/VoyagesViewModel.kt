package corsica.universita.journalvoyage.ui.voyages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import corsica.universita.journalvoyage.JournalContainer
import corsica.universita.journalvoyage.data.JournalRepository
import corsica.universita.journalvoyage.data.Voyage
import corsica.universita.journalvoyage.data.local.Settings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VoyagesState(
    val voyages: List<Voyage> = emptyList(),
    val pending: Int = 0,
    val activeId: Long? = null,
    val consent: Boolean? = false, // false tant que DataStore n'a pas répondu : pas de bandeau qui clignote
    val fcmToken: String? = null,
)

/** L'état de l'accueil, assemblé à partir de Room (voyages, badge) et de DataStore (voyage actif, consentement, jeton). */
class VoyagesViewModel(repository: JournalRepository, private val settings: Settings) : ViewModel() {

    // WhileSubscribed(5 s) : la requête Room survit à une rotation (l'écran se réabonne en moins de 5 s).
    val state: StateFlow<VoyagesState> = combine(
        repository.voyages, repository.pendingCount, settings.activeVoyageId, settings.analyticsConsent, settings.fcmToken,
    ) { voyages, pending, active, consent, token -> VoyagesState(voyages, pending, active, consent, token) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoyagesState())

    fun setConsent(value: Boolean) {
        viewModelScope.launch { settings.setAnalyticsConsent(value) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { VoyagesViewModel(JournalContainer.repository, JournalContainer.settings) }
        }
    }
}
