package corsica.universita.journalvoyage.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

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

@Serializable
data class EchoResponse(val url: String = "", val json: EtapeDto? = null)

interface SyncApi {
    @POST("post")
    suspend fun push(@Body body: EtapeDto): EchoResponse
}
