package corsica.universita.journalvoyage.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PhotoStore(private val context: Context) {
    private val dir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun newFile(prefix: String = "photo"): File = File(dir, "${prefix}_${System.nanoTime()}.jpg")

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

    suspend fun fromAsset(name: String): String = withContext(Dispatchers.IO) {
        val file = newFile("seed")
        context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        file.absolutePath
    }

    fun uriFor(path: String): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))

    suspend fun delete(paths: List<String>) = withContext(Dispatchers.IO) {
        paths.forEach { File(it).delete() }
    }
}
