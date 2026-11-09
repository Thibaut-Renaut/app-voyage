package corsica.universita.journalvoyage.ui.home

import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import corsica.universita.journalvoyage.JournalContainer
import corsica.universita.journalvoyage.R
import corsica.universita.journalvoyage.data.Etape
import corsica.universita.journalvoyage.data.Voyage
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Les fenêtres (modales) de la PWA : créer, modifier, supprimer, changer de carnet. */
enum class VoyageDialog { None, Create, Edit, Delete, Switch }

/** Le message d'état sous « Ma position » (#geo-status de la PWA). */
sealed interface GeoStatus {
    data object Idle : GeoStatus
    data object Searching : GeoStatus
    data class Found(val lat: Double, val lng: Double, val accuracy: Int, val precise: Boolean) : GeoStatus
    data object Unavailable : GeoStatus
}

/** Un toast : un texte de res/values/strings.xml et ses arguments (le ViewModel ne connaît pas le Context). */
data class Toast(@StringRes val text: Int, val args: List<Any> = emptyList())

data class EtapeForm(
    val titre: String = "",
    val description: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val photos: List<String> = emptyList(),
)

data class VoyageForm(val titre: String = "", val description: String = "", val coverPath: String? = null)

data class HomeState(
    val voyages: List<Voyage> = emptyList(),
    val active: Voyage? = null,
    val etapes: List<Etape> = emptyList(),
    val pending: Int = 0,
)

/**
 * La page d'accueil — la PWA tient sur une seule page, l'app aussi : le formulaire « Nouvelle étape de … »,
 * l'itinéraire du carnet actif, la collection de carnets. Ce ViewModel en porte tout l'état :
 * - ce qui vient de Room et de DataStore (carnets, étapes, carnet actif, badge) : observé, jamais copié ;
 * - les brouillons (étape, carnet en cours d'édition, fenêtre ouverte) : dans le SavedStateHandle,
 *   ils survivent à la rotation et à la mort du process.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val saved: SavedStateHandle) : ViewModel() {
    private val repository = JournalContainer.repository
    private val settings = JournalContainer.settings
    private val photoStore = JournalContainer.photoStore
    private val locationSource = JournalContainer.locationSource

    // ---- Les données ----

    /**
     * Le carnet actif : celui gardé dans DataStore (le localStorage « pwa-active-voyage-id » de la PWA)
     * s'il existe encore, sinon le premier carnet — exactement la règle de initVoyages().
     */
    private val activeId: StateFlow<Long?> = combine(repository.voyages, settings.activeVoyageId) { list, savedId ->
        list.firstOrNull { it.id == savedId }?.id ?: list.firstOrNull()?.id
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val etapes: Flow<List<Etape>> = activeId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.etapes(id)
    }

    val state: StateFlow<HomeState> = combine(repository.voyages, activeId, etapes, repository.pendingCount) { voyages, id, etapes, pending ->
        HomeState(voyages, voyages.firstOrNull { it.id == id }, etapes, pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    val consent: StateFlow<Boolean?> = settings.analyticsConsent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val fcmToken: StateFlow<String?> = settings.fcmToken.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** RGPD : le choix de l'utilisateur, gardé dans DataStore ; Analytics l'observe et coupe ou rouvre la collecte. */
    fun setConsent(value: Boolean) {
        viewModelScope.launch { settings.setAnalyticsConsent(value) }
    }

    // ---- Les toasts (showToast de la PWA) ----

    private val _toasts = Channel<Toast>(Channel.BUFFERED)
    val toasts: Flow<Toast> = _toasts.receiveAsFlow()
    private fun toast(@StringRes text: Int, vararg args: Any) = _toasts.trySend(Toast(text, args.toList()))

    // ---- Le formulaire « Nouvelle étape » ----

    val etapeForm: StateFlow<EtapeForm> = combine(
        saved.getStateFlow(E_TITRE, ""),
        saved.getStateFlow(E_DESC, ""),
        saved.getStateFlow<Double?>(E_LAT, null),
        saved.getStateFlow<Double?>(E_LNG, null),
        saved.getStateFlow(E_PHOTOS, arrayListOf<String>()),
    ) { t, d, lat, lng, photos -> EtapeForm(t, d, lat, lng, photos) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EtapeForm())

    private val _geo = MutableStateFlow<GeoStatus>(GeoStatus.Idle)
    val geo: StateFlow<GeoStatus> = _geo.asStateFlow()

    fun setEtapeTitre(v: String) { saved[E_TITRE] = v }
    fun setEtapeDescription(v: String) { saved[E_DESC] = v }

    fun locate() {
        viewModelScope.launch {
            _geo.value = GeoStatus.Searching
            val fix = locationSource.current()
            if (fix == null) {
                _geo.value = GeoStatus.Unavailable
                return@launch
            }
            Log.d(TAG, "Position : ${fix.lat}, ${fix.lng} à ${fix.accuracy} m près")
            saved[E_LAT] = fix.lat
            saved[E_LNG] = fix.lng
            _geo.value = GeoStatus.Found(fix.lat, fix.lng, fix.accuracy.toInt(), fix.precise)
        }
    }

    fun newPhotoFile(): File = photoStore.newFile()

    fun addPhoto(path: String) {
        saved[E_PHOTOS] = ArrayList(etapeForm.value.photos + path)
    }

    fun importPhotos(uris: List<Uri>) {
        viewModelScope.launch { uris.forEach { uri -> photoStore.import(uri)?.let(::addPhoto) } }
    }

    fun removePhoto(path: String) {
        saved[E_PHOTOS] = ArrayList(etapeForm.value.photos - path)
        JournalContainer.appScope.launch { photoStore.delete(listOf(path)) }
    }

    /** Le submit de #form-etape : Room d'abord, l'envoi confié à WorkManager, le formulaire remis à zéro. */
    fun saveEtape(online: Boolean) {
        val f = etapeForm.value
        val voyageId = activeId.value ?: return
        if (f.titre.isBlank() || f.description.isBlank()) return
        viewModelScope.launch {
            val id = repository.addEtape(voyageId, f.titre.trim(), f.description.trim(), f.lat, f.lng, f.photos)
            Log.d(TAG, "Étape $id enregistrée (${f.photos.size} photo(s)) — envoi confié à WorkManager")
            JournalContainer.analytics.etapeCreated(photos = f.photos.size, withLocation = f.lat != null)
            clearEtapeForm()
            _geo.value = GeoStatus.Idle
            toast(if (online) R.string.toast_etape_saved else R.string.toast_etape_offline)
        }
    }

    fun deleteEtape(id: Long) {
        viewModelScope.launch {
            repository.deleteEtape(id)
            toast(R.string.toast_etape_deleted)
        }
    }

    // ---- Les carnets ----

    fun openVoyage(id: Long) {
        viewModelScope.launch {
            settings.setActiveVoyage(id)
            repository.getVoyage(id)?.let { toast(R.string.toast_active_voyage, it.titre) }
        }
    }

    val dialog: StateFlow<VoyageDialog> = saved.getStateFlow(DIALOG, VoyageDialog.None.name)
        .map { VoyageDialog.valueOf(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VoyageDialog.None)

    /** Le carnet visé par la fenêtre ouverte (édition ou suppression). */
    val dialogVoyageId: StateFlow<Long?> = saved.getStateFlow<Long?>(D_VOYAGE, null)

    val voyageForm: StateFlow<VoyageForm> = combine(
        saved.getStateFlow(V_TITRE, ""), saved.getStateFlow(V_DESC, ""), saved.getStateFlow<String?>(V_COVER, null),
    ) { t, d, c -> VoyageForm(t, d, c) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VoyageForm())

    private fun show(d: VoyageDialog) { saved[DIALOG] = d.name }

    fun openCreate() {
        resetVoyageForm()
        show(VoyageDialog.Create)
    }

    fun openEdit(id: Long) {
        viewModelScope.launch {
            val v = repository.getVoyage(id) ?: return@launch
            discardDraftCover()
            saved[D_VOYAGE] = id
            saved[V_TITRE] = v.titre
            saved[V_DESC] = v.description
            saved[V_COVER] = v.coverPath
            saved[V_ORIGINAL] = v.coverPath
            show(VoyageDialog.Edit)
        }
    }

    fun openSwitch() = show(VoyageDialog.Switch)

    /** promptDeleteVoyage() : la PWA refuse de supprimer le dernier carnet. */
    fun askDelete(id: Long) {
        viewModelScope.launch {
            if (repository.voyageCount() <= 1) {
                closeDialog()
                toast(R.string.toast_cannot_delete_last)
                return@launch
            }
            discardDraftCover()
            saved[D_VOYAGE] = id
            show(VoyageDialog.Delete)
        }
    }

    fun closeDialog() {
        if (dialog.value == VoyageDialog.Create || dialog.value == VoyageDialog.Edit) discardDraftCover()
        show(VoyageDialog.None)
    }

    fun setVoyageTitre(v: String) { saved[V_TITRE] = v }
    fun setVoyageDescription(v: String) { saved[V_DESC] = v }

    fun setCover(uri: Uri) {
        viewModelScope.launch {
            val path = photoStore.import(uri) ?: return@launch
            discardDraftCover()
            saved[V_COVER] = path
        }
    }

    fun removeCover() {
        discardDraftCover()
        saved[V_COVER] = null
    }

    fun submitCreate() {
        val f = voyageForm.value
        if (f.titre.isBlank() || f.description.isBlank()) return
        viewModelScope.launch {
            val id = repository.saveVoyage(0, f.titre.trim(), f.description.trim(), f.coverPath)
            settings.setActiveVoyage(id) // le nouveau carnet devient le carnet actif (setActiveVoyage de la PWA)
            saved[V_COVER] = null // enregistrée : ce n'est plus un brouillon à effacer
            show(VoyageDialog.None)
            toast(R.string.toast_voyage_created, f.titre.trim())
        }
    }

    fun submitEdit() {
        val f = voyageForm.value
        val id = dialogVoyageId.value ?: return
        if (f.titre.isBlank() || f.description.isBlank()) return
        viewModelScope.launch {
            repository.saveVoyage(id, f.titre.trim(), f.description.trim(), f.coverPath) // efface l'ancienne photo remplacée
            saved[V_COVER] = null
            show(VoyageDialog.None)
            toast(R.string.toast_voyage_updated, f.titre.trim())
        }
    }

    fun confirmDelete() {
        val id = dialogVoyageId.value ?: return
        viewModelScope.launch {
            val titre = repository.getVoyage(id)?.titre.orEmpty()
            show(VoyageDialog.None)
            repository.deleteVoyage(id) // CASCADE : étapes, photos, trajets ; les fichiers aussi
            // Le carnet actif supprimé : activeId retombe tout seul sur le premier carnet restant.
            toast(R.string.toast_voyage_deleted, titre)
        }
    }

    /**
     * Vider un brouillon = remettre les valeurs par défaut, jamais saved.remove() : remove() détache le
     * StateFlow rendu par getStateFlow(), et les saisies suivantes ne seraient plus émises (le champ resterait vide).
     */
    private fun resetVoyageForm() {
        discardDraftCover()
        saved[V_TITRE] = ""
        saved[V_DESC] = ""
        saved[V_ORIGINAL] = null
        saved[D_VOYAGE] = null
    }

    private fun clearEtapeForm() {
        saved[E_TITRE] = ""
        saved[E_DESC] = ""
        saved[E_LAT] = null
        saved[E_LNG] = null
        saved[E_PHOTOS] = arrayListOf<String>()
    }

    /** Une photo vitrine choisie puis abandonnée (Annuler, remplacée) ne reste pas sur le disque ; l'originale, si. */
    private fun discardDraftCover() {
        val current = saved.get<String?>(V_COVER) ?: return
        if (current != saved.get<String?>(V_ORIGINAL)) {
            JournalContainer.appScope.launch { photoStore.delete(listOf(current)) }
        }
        saved[V_COVER] = null
    }

    // ---- Le partage reçu d'une autre app : il préremplit le formulaire (comme une nouvelle étape) ----

    init {
        viewModelScope.launch {
            JournalContainer.pendingShare.filterNotNull().collect { share ->
                JournalContainer.pendingShare.value = null
                share.text?.let { text ->
                    saved[E_TITRE] = text.lineSequence().first().take(60)
                    saved[E_DESC] = text
                }
                share.imageUri?.let { uri -> photoStore.import(Uri.parse(uri))?.let(::addPhoto) }
                toast(R.string.toast_share_received, state.mapNotNull { it.active }.first().titre)
            }
        }
    }

    /** L'app est fermée (retour depuis l'accueil) : les photos et la photo vitrine des brouillons ne restent pas orphelines. */
    override fun onCleared() {
        val photos = etapeForm.value.photos
        if (photos.isNotEmpty()) JournalContainer.appScope.launch { photoStore.delete(photos) }
        discardDraftCover()
    }

    companion object {
        private const val TAG = "Journal"
        private const val E_TITRE = "etapeTitre"
        private const val E_DESC = "etapeDescription"
        private const val E_LAT = "etapeLat"
        private const val E_LNG = "etapeLng"
        private const val E_PHOTOS = "etapePhotos"
        private const val DIALOG = "dialog"
        private const val D_VOYAGE = "dialogVoyageId"
        private const val V_TITRE = "voyageTitre"
        private const val V_DESC = "voyageDescription"
        private const val V_COVER = "voyageCover"
        private const val V_ORIGINAL = "voyageOriginalCover"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(createSavedStateHandle()) }
        }
    }
}
