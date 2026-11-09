package corsica.universita.journalvoyage.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Le format du serveur : exactement le `syncPayload` de la PWA (app.js) — sans les photos,
 * seulement leur nombre. ≠ EtapeEntity, le format du disque : chacun évolue seul.
 */
@Serializable
data class EtapeDto(
    val id: Long,
    val voyageId: Long,
    val titre: String,
    val description: String,
    val lat: Double?,
    val lng: Double?,
    val date: Long,
    val photosCount: Int,
)

/** La réponse de httpbin.org/post : il renvoie en écho le JSON reçu, dans `json`. */
@Serializable
data class EchoResponse(val url: String = "", val json: EtapeDto? = null)

/** L'API, décrite par une interface ; Retrofit génère le code (suspend : hors du main thread). */
interface SyncApi {
    @POST("post")
    suspend fun push(@Body body: EtapeDto): EchoResponse
}
