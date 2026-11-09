package corsica.universita.journalvoyage.ui.voyages

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import corsica.universita.journalvoyage.JournalContainer
import corsica.universita.journalvoyage.data.JournalRepository
import corsica.universita.journalvoyage.data.PhotoStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VoyageForm(val titre: String = "", val description: String = "", val coverPath: String? = null)

/**
 * Le brouillon du formulaire vit dans le SavedStateHandle : il survit à la rotation ET à la mort du process
 * (l'OS garde le Bundle). Rien n'est écrit dans Room avant « Enregistrer ».
 */
class VoyageEditViewModel(
    private val voyageId: Long,
    private val saved: SavedStateHandle,
    private val repository: JournalRepository,
    private val photoStore: PhotoStore,
) : ViewModel() {

    val form: StateFlow<VoyageForm> = combine(
        saved.getStateFlow(TITRE, ""), saved.getStateFlow(DESC, ""), saved.getStateFlow<String?>(COVER, null),
    ) { t, d, c -> VoyageForm(t, d, c) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VoyageForm())

    private var savedOk = false

    init {
        // Modification : on charge le voyage une seule fois (pas après une mort du process : le brouillon prime).
        if (voyageId != 0L && saved.get<Boolean>(LOADED) != true) {
            viewModelScope.launch {
                repository.getVoyage(voyageId)?.let {
                    saved[TITRE] = it.titre
                    saved[DESC] = it.description
                    saved[COVER] = it.coverPath
                    saved[ORIGINAL_COVER] = it.coverPath
                }
                saved[LOADED] = true
            }
        }
    }

    fun setTitre(v: String) { saved[TITRE] = v }
    fun setDescription(v: String) { saved[DESC] = v }

    fun setCover(uri: Uri) {
        viewModelScope.launch {
            val path = photoStore.import(uri) ?: return@launch
            discardDraftCover()
            saved[COVER] = path
        }
    }

    fun clearCover() {
        discardDraftCover()
        saved[COVER] = null
    }

    fun save(onDone: () -> Unit) {
        val f = form.value
        if (f.titre.isBlank()) return
        viewModelScope.launch {
            val id = repository.saveVoyage(voyageId, f.titre.trim(), f.description.trim(), f.coverPath)
            JournalContainer.settings.setActiveVoyage(id)
            savedOk = true
            onDone()
        }
    }

    /** Une photo choisie puis remplacée (ou abandonnée) ne reste pas sur le disque. L'originale, si : elle est en base. */
    private fun discardDraftCover() {
        val current = saved.get<String?>(COVER)
        if (current != null && current != saved.get<String?>(ORIGINAL_COVER)) {
            // appScope, pas viewModelScope : appelé aussi depuis onCleared, quand viewModelScope est déjà annulé.
            JournalContainer.appScope.launch { photoStore.delete(listOf(current)) }
        }
    }

    /** L'écran est quitté (retour) sans enregistrer : la photo du brouillon est effacée. */
    override fun onCleared() {
        if (!savedOk) discardDraftCover()
    }

    companion object {
        private const val TITRE = "titre"
        private const val DESC = "description"
        private const val COVER = "cover"
        private const val ORIGINAL_COVER = "originalCover"
        private const val LOADED = "loaded"

        fun factory(voyageId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                VoyageEditViewModel(voyageId, createSavedStateHandle(), JournalContainer.repository, JournalContainer.photoStore)
            }
        }
    }
}
