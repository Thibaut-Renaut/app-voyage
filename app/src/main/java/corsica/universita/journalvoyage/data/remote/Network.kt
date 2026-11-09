package corsica.universita.journalvoyage.data.remote

import android.content.Context
import android.util.Log
import corsica.universita.journalvoyage.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "Journal"

/**
 * Le client HTTP, un seul par process (construit par JournalContainer).
 * Retrofit décrit, OkHttp transporte : connexions, cache HTTP, intercepteurs, délais.
 */
fun okHttp(context: Context): OkHttpClient =
    OkHttpClient.Builder()
        // Cache HTTP de 10 Mo dans cacheDir/http, piloté par le serveur (Cache-Control, ETag → 304).
        .cache(Cache(File(context.cacheDir, "http"), 10L * 1024 * 1024))
        // Journal dans Logcat : « --> POST … » puis « <-- 200 … » (adb logcat -s Journal).
        .addInterceptor(
            HttpLoggingInterceptor { message -> Log.d(TAG, message) }
                .apply { level = HttpLoggingInterceptor.Level.BASIC },
        )
        // Sans délais, une connexion lente tient une coroutine des minutes.
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

private val json = Json { ignoreUnknownKeys = true } // httpbin renvoie bien d'autres champs

fun syncApi(client: OkHttpClient): SyncApi =
    Retrofit.Builder()
        .baseUrl(BuildConfig.SYNC_BASE_URL) // https://httpbin.org/ (local.properties)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SyncApi::class.java)
