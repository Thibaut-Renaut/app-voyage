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

fun okHttp(context: Context): OkHttpClient =
    OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http"), 10L * 1024 * 1024))
        .addInterceptor(
            HttpLoggingInterceptor { message -> Log.d(TAG, message) }
                .apply { level = HttpLoggingInterceptor.Level.BASIC },
        )
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

private val json = Json { ignoreUnknownKeys = true }

fun syncApi(client: OkHttpClient): SyncApi =
    Retrofit.Builder()
        .baseUrl(BuildConfig.SYNC_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SyncApi::class.java)
