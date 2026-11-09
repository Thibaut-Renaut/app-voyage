package corsica.universita.journalvoyage.ui.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import corsica.universita.journalvoyage.BuildConfig
import corsica.universita.journalvoyage.JournalContainer
import corsica.universita.journalvoyage.R
import corsica.universita.journalvoyage.data.Etape
import java.util.Locale

fun Context.shareEtape(etape: Etape) {
    val text = buildString {
        append(etape.titre).append("\n\n").append(etape.description)
        if (etape.lat != null && etape.lng != null) {
            append("\n\n").append(String.format(Locale.ROOT, "https://maps.google.com/?q=%.5f,%.5f", etape.lat, etape.lng))
        }
    }
    val uris = etape.photos.map { JournalContainer.photoStore.uriFor(it) }
    val send = when (uris.size) {
        0 -> Intent(Intent.ACTION_SEND).apply { type = "text/plain" }
        1 -> Intent(Intent.ACTION_SEND).apply { type = "image/jpeg"; putExtra(Intent.EXTRA_STREAM, uris[0]) }
        else -> Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "image/jpeg"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)) }
    }.apply {
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, etape.titre)
        if (uris.isNotEmpty()) {
            clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    startActivity(Intent.createChooser(send, getString(R.string.share_title)))
}

fun Context.openMap(lat: Double, lng: Double, label: String) {
    val uri = Uri.parse(String.format(Locale.ROOT, "geo:%.6f,%.6f?q=%.6f,%.6f(%s)", lat, lng, lat, lng, Uri.encode(label)))
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.no_map_app, Toast.LENGTH_SHORT).show()
    }
}

fun Context.openPwa() {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://${BuildConfig.PWA_HOST}/")).apply {
        selector = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
    }
    startActivity(view)
}

fun Context.copyToken(token: String?) {
    if (token == null) {
        Toast.makeText(this, R.string.token_unavailable, Toast.LENGTH_LONG).show()
        return
    }
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("FCM", token))
    Toast.makeText(this, R.string.token_copied, Toast.LENGTH_SHORT).show()
}
