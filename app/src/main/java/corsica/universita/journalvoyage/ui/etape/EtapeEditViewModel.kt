package corsica.universita.journalvoyage.ui.etape

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import corsica.universita.journalvoyage.JournalContainer
import corsica.universita.journalvoyage.data.location.Fix
import corsica.universita.journalvoyage.ui.EtapeEdit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class EtapeForm(
    val titre: String = "",
    val description: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val photos: List<String> = emptyList(),
)

/**
 * Le formulaire « Ajouter une étape ». Tout le brouillon est dans le SavedStateHandle (même les chemins
 * des photos déjà prises) : rotation, mort du process en arrière-plan — rien ne se perd.
 */
class EtapeEditViewModel(private val key: EtapeEdit, private val saved: SavedStateHandle) : ViewModel() {
    private val repository = JournalContainer.repository
    private val photoStore = JournalContainer.photoStore
    private val locationSource = JournalContainer.locationSource

    val form: StateFlow<EtapeForm> = combine(
        saved.getStateFlow(TITRE, ""),
        saved.getStateFlow(DESC, ""),
        saved.getStateFlow<Double?>(LAT, null),
        saved.getStateFlow<Double?>(LNG, null),
        saved.getStateFlow(PHOTOS, arrayListOf<String>()),
    ) { t, d, lat, lng, photos -> EtapeForm(t, d, lat, lng, photos) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EtapeForm())

    /** Le dernier relevé : sa précision (« à 12 m près »), précise ou approximative. Pas besoin de le sauver. */
    private val _fix = MutableStateFlow<Fix?>(null)
    val fix: StateFlow<Fix?> = _fix.asStateFlow()

    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

    private var savedOk = false

    init {
        // Contenu partagé par une autre app : traité UNE fois (le drapeau est dans le Bundle).
        if (saved.get<Boolean>(SHARE_HANDLED) != true) {
            saved[SHARE_HANDLED] = true
            key.sharedText?.let { text ->
                saved[TITRE] = text.lineSequence().first().take(60)
                saved[DESC] = text
            }
            key.sharedImage?.let { uri -> viewModelScope.launch { photoStore.import(Uri.parse(uri))?.let(::addPhoto) } }
        }
    }

    fun setTitre(v: String) { saved[TITRE] = v }
    fun setDescription(v: String) { saved[DESC] = v }

    fun locate() {
        viewModelScope.launch {
            _locating.value = true
            val fix = locationSource.current()
            _locating.value = false
            if (fix == null) {
                Log.w(TAG, "Position indisponible (localisation désactivée ?)")
                return@launch
            }
            Log.d(TAG, "Position : ${fix.lat}, ${fix.lng} à ${fix.accuracy} m près (${if (fix.precise) "précise" else "approximative"})")
            _fix.value = fix
            saved[LAT] = fix.lat
            saved[LNG] = fix.lng
        }
    }

    fun clearLocation() {
        saved[LAT] = null
        saved[LNG] = null
        _fix.value = null
    }

    fun newPhotoFile(): File = photoStore.newFile()

    fun addPhoto(path: String) {
        saved[PHOTOS] = ArrayList(form.value.photos + path)
    }

    fun importPhotos(uris: List<Uri>) {
        viewModelScope.launch { uris.forEach { uri -> photoStore.import(uri)?.let(::addPhoto) } }
    }

    fun removePhoto(path: String) {
        saved[PHOTOS] = ArrayList(form.value.photos - path)
        JournalContainer.appScope.launch { photoStore.delete(listOf(path)) }
    }

    fun save(onDone: () -> Unit) {
        val f = form.value
        if (f.titre.isBlank() || f.description.isBlank()) return
        viewModelScope.launch {
            val id = repository.addEtape(key.voyageId, f.titre.trim(), f.description.trim(), f.lat, f.lng, f.photos)
            Log.d(TAG, "Étape $id enregistrée (${f.photos.size} photo(s)) — envoi confié à WorkManager")
            JournalContainer.analytics.etapeCreated(photos = f.photos.size, withLocation = f.lat != null)
            savedOk = true
            onDone()
        }
    }

    /** Formulaire abandonné (retour) : les photos prises pour ce brouillon ne restent pas orphelines sur le disque. */
    override fun onCleared() {
        if (!savedOk) {
            val photos = form.value.photos
            if (photos.isNotEmpty()) JournalContainer.appScope.launch { photoStore.delete(photos) }
        }
    }

    companion object {
        private const val TAG = "Journal"
        private const val TITRE = "titre"
        private const val DESC = "description"
        private const val LAT = "lat"
        private const val LNG = "lng"
        private const val PHOTOS = "photos"
        private const val SHARE_HANDLED = "shareHandled"

        fun factory(key: EtapeEdit): ViewModelProvider.Factory = viewModelFactory {
            initializer { EtapeEditViewModel(key, createSavedStateHandle()) }
        }
    }
}
