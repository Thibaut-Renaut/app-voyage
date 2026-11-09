package corsica.universita.journalvoyage.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GpxExporter(private val context: Context) {
    suspend fun save(uri: Uri, gpx: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(gpx.toByteArray()) }
            ?: error("Impossible d'écrire dans $uri")
    }

    suspend fun forSharing(fileName: String, gpx: String): Uri = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "exports").apply { mkdirs() }.resolve(fileName)
        file.writeText(gpx)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
