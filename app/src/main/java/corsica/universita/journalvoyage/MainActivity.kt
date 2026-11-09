package corsica.universita.journalvoyage

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import corsica.universita.journalvoyage.notif.Notifications
import corsica.universita.journalvoyage.ui.AppViewModel
import corsica.universita.journalvoyage.ui.JournalNavigation
import corsica.universita.journalvoyage.ui.theme.JournalTheme
import kotlinx.coroutines.launch

private const val TAG = "Journal"

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate — Bundle ${if (savedInstanceState == null) "null (premier lancement)" else "présent (recréation)"}")

        // Uniquement au premier lancement : après une rotation, l'intent serait traité deux fois.
        if (savedInstanceState == null) {
            lifecycleScope.launch { JournalContainer.analytics.sessionStarted() }
            handleIntent(intent)
        }

        enableEdgeToEdge()
        setContent {
            JournalTheme {
                JournalNavigation(appViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val etapeId = intent.longOrStringExtra(Notifications.EXTRA_ETAPE_ID)
        val voyageId = intent.longOrStringExtra(Notifications.EXTRA_VOYAGE_ID)
        val trajetId = intent.longOrStringExtra(Notifications.EXTRA_TRAJET_ID)
        val source = intent.getStringExtra(Notifications.EXTRA_SOURCE)
            ?: if (intent.extras?.containsKey("google.message_id") == true) "push" else null
        if (source != null) {
            Log.d(TAG, "Ouvert depuis une notification ($source) → étape $etapeId, voyage $voyageId — extras : ${intent.extras?.keySet()?.joinToString()}")
            JournalContainer.analytics.notificationClicked(source)
        }
        when {
            trajetId != null -> { appViewModel.openTrajet(trajetId); return }
            etapeId != null -> { appViewModel.openEtape(etapeId); return }
            voyageId != null -> { appViewModel.openVoyage(voyageId); return }
        }

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val image = intent.streamExtra()
                Log.d(TAG, "ACTION_SEND reçu : texte=${text != null}, image=$image")
                if (text != null || image != null) appViewModel.receiveShare(text, image?.toString())
            }
            Intent.ACTION_VIEW -> intent.data?.let {
                Log.d(TAG, "ACTION_VIEW reçu (App Link) : $it")
                appViewModel.openHome()
            }
        }
    }

    // Les données personnalisées de la console Firebase arrivent sous forme de String.
    private fun Intent.longOrStringExtra(key: String): Long? {
        if (!hasExtra(key)) return null
        val asLong = getLongExtra(key, Long.MIN_VALUE)
        return if (asLong != Long.MIN_VALUE) asLong else getStringExtra(key)?.toLongOrNull()
    }

    private fun Intent.streamExtra(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") getParcelableExtra(Intent.EXTRA_STREAM)
}
