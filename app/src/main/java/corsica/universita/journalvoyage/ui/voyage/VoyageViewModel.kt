package corsica.universita.journalvoyage.ui.voyage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import corsica.universita.journalvoyage.JournalContainer
import corsica.universita.journalvoyage.data.Etape
import corsica.universita.journalvoyage.data.JournalRepository
import corsica.universita.journalvoyage.data.Voyage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VoyageViewModel(private val voyageId: Long, private val repository: JournalRepository) : ViewModel() {

    val voyage: StateFlow<Voyage?> = repository.observeVoyage(voyageId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val etapes: StateFlow<List<Etape>> = repository.etapes(voyageId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Ouvrir un voyage en fait le voyage actif (la PWA : localStorage « pwa-active-voyage-id »).
        viewModelScope.launch { JournalContainer.settings.setActiveVoyage(voyageId) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteVoyage(voyageId)
            onDone()
        }
    }

    companion object {
        fun factory(voyageId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { VoyageViewModel(voyageId, JournalContainer.repository) }
        }
    }
}
