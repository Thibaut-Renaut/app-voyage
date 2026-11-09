package corsica.universita.journalvoyage.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

@Composable
fun PhotoImage(path: String?, maxPx: Int, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val key = "$path@$maxPx"
    val bitmap by produceState(initialValue = path?.let { cache.get(key) }, key) {
        if (path != null && value == null) {
            value = withContext(Dispatchers.IO) { decode(path, maxPx)?.asImageBitmap() }?.also { cache.put(key, it) }
        }
    }
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription, modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Explore, contentDescription, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun decode(path: String, maxPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    // CameraX enregistre l'orientation dans l'EXIF sans faire pivoter les pixels.
    val degrees = when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return raw
    }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees) }, true)
}

@Composable
fun AssetImage(name: String, contentDescription: String?, modifier: Modifier = Modifier, maxPx: Int = 1080) {
    val context = LocalContext.current
    val key = "asset:$name@$maxPx"
    val bitmap by produceState(initialValue = cache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open(name).use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
                context.assets.open(name).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                    ?.asImageBitmap()
            }?.also { cache.put(key, it) }
        }
    }
    val b = bitmap
    if (b != null) Image(b, contentDescription, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(MaterialTheme.colorScheme.primaryContainer))
}
