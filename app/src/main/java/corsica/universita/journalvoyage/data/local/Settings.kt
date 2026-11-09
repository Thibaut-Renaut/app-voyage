package corsica.universita.journalvoyage.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

private val KEY_ACTIVE_VOYAGE = longPreferencesKey("activeVoyageId")
private val KEY_FCM_TOKEN = stringPreferencesKey("fcmToken")
private val KEY_CONSENT = booleanPreferencesKey("analyticsConsent")
private val KEY_FIRST_ETAPE_LOGGED = booleanPreferencesKey("firstEtapeLogged")
private val KEY_LAST_SESSION_DAY = longPreferencesKey("lastSessionDay")

class Settings(context: Context) {
    private val store = context.applicationContext.settingsStore

    val activeVoyageId: Flow<Long?> = store.data.map { it[KEY_ACTIVE_VOYAGE] }
    suspend fun setActiveVoyage(id: Long) = store.edit { it[KEY_ACTIVE_VOYAGE] = id }

    val fcmToken: Flow<String?> = store.data.map { it[KEY_FCM_TOKEN] }
    suspend fun saveFcmToken(token: String) = store.edit { it[KEY_FCM_TOKEN] = token }

    val analyticsConsent: Flow<Boolean?> = store.data.map { it[KEY_CONSENT] }
    suspend fun setAnalyticsConsent(value: Boolean) = store.edit { it[KEY_CONSENT] = value }

    suspend fun claimFirstEtape(): Boolean = claimOnce(KEY_FIRST_ETAPE_LOGGED)

    suspend fun swapSessionDay(today: Long): Long? {
        var previous: Long? = null
        store.edit {
            previous = it[KEY_LAST_SESSION_DAY]
            it[KEY_LAST_SESSION_DAY] = today
        }
        return previous
    }

    private suspend fun claimOnce(key: Preferences.Key<Boolean>): Boolean {
        if (store.data.first()[key] == true) return false
        var claimed = false
        store.edit {
            if (it[key] != true) {
                it[key] = true
                claimed = true
            }
        }
        return claimed
    }
}
