package corsica.universita.journalvoyage.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import corsica.universita.journalvoyage.JournalContainer
import corsica.universita.journalvoyage.notif.Notifications
import retrofit2.HttpException
import java.io.IOException

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        Log.d(TAG, "SyncWorker démarré (tentative ${runAttemptCount + 1})")
        val count = JournalContainer.repository.syncPending()
        Log.d(TAG, "SyncWorker : $count étape(s) envoyée(s)")
        if (count > 0) Notifications.showSyncDone(applicationContext, count)
        Result.success(workDataOf("count" to count))
    } catch (e: IOException) {
        Log.w(TAG, "SyncWorker : réseau en échec, nouvel essai plus tard — ${e.message}")
        if (runAttemptCount < 5) Result.retry() else Result.failure()
    } catch (e: HttpException) {
        // 429 et 5xx sont transitoires ; un autre 4xx échouerait à nouveau.
        if ((e.code() == 429 || e.code() >= 500) && runAttemptCount < 5) {
            Log.w(TAG, "SyncWorker : serveur indisponible (${e.code()}), nouvel essai plus tard")
            Result.retry()
        } else {
            Log.w(TAG, "SyncWorker : le serveur a refusé (${e.code()}), abandon")
            Result.failure()
        }
    }

    private companion object {
        const val TAG = "Journal"
    }
}
