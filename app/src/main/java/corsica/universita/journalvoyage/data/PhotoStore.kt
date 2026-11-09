package corsica.universita.journalvoyage.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Les photos, dans filesDir/photos/ — le stockage privé de l'app : aucune permission de stockage,
 * invisible aux autres apps, effacé à la désinstallation. Room ne garde que le chemin.
 * (La PWA, elle, compressait chaque photo en base64 dans IndexedDB, à cause des quotas du navigateur.)
 */
class PhotoStore(private val context: Context) {
    private val dir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    /** Le fichier où CameraX écrira la prochaine photo. */
    fun newFile(prefix: String = "photo"): File = File(dir, "${prefix}_${System.nanoTime()}.jpg")

    /**
     * Copie une image reçue d'ailleurs (sélecteur de photos, partage d'une autre app) dans le stockage privé.
     * L'URI content:// n'est lisible que le temps de l'opération : on garde une copie, pas l'URI.
     */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val file = newFile("import")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: return@withContext null
            file.absolutePath
        } catch (e: Exception) {
            Log.w("Journal", "Import de photo impossible : $uri", e)
            null
        }
    }

    /** Les photos de démonstration (les mêmes que la PWA), copiées depuis les assets au premier lancement. */
    suspend fun fromAsset(name: String): String = withContext(Dispatchers.IO) {
        val file = newFile("seed")
        context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        file.absolutePath
    }

    /** Pour sortir un fichier de l'app (partage) : une URI content:// servie par le FileProvider. */
    fun uriFor(path: String): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))

    /** Supprimer la ligne sans le fichier laisserait un JPEG orphelin dans filesDir. */
    suspend fun delete(paths: List<String>) = withContext(Dispatchers.IO) {
        paths.forEach { File(it).delete() }
    }
}
