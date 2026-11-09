package corsica.universita.journalvoyage.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * La base SQLite : /data/data/corsica.universita.journalvoyage/databases/journal.db.
 * `version` : le numéro du schéma. `exportSchema` : chaque version est écrite dans app/schemas/…/N.json (dans Git).
 *
 * v1 (1.0) : voyages, etapes, photos
 * v2 (2.0) : + trajets, track_points (l'enregistreur de trajet)
 */
@Database(
    entities = [VoyageEntity::class, EtapeEntity::class, PhotoEntity::class, TrajetEntity::class, TrackPointEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun voyageDao(): VoyageDao
    abstract fun etapeDao(): EtapeDao
    abstract fun trajetDao(): TrajetDao

    companion object {
        const val NAME = "journal.db"

        /**
         * v1 → v2 : deux tables nouvelles, rien ne change pour les anciennes (voyages, étapes, photos intacts).
         * Le SQL est celui que Room attend, recopié de schemas/…/2.json : MigrationTest le vérifie.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trajets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`voyageId` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, " +
                        "`distanceM` REAL NOT NULL DEFAULT 0, " +
                        "FOREIGN KEY(`voyageId`) REFERENCES `voyages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trajets_voyageId` ON `trajets` (`voyageId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `track_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`trajetId` INTEGER NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `altitude` REAL, " +
                        "`accuracy` REAL NOT NULL, `time` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`trajetId`) REFERENCES `trajets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_points_trajetId` ON `track_points` (`trajetId`)")
            }
        }

        fun build(context: Context): JournalDatabase =
            Room.databaseBuilder(context.applicationContext, JournalDatabase::class.java, NAME)
                // Room les enchaîne ; jamais fallbackToDestructiveMigration : la base vit chez l'utilisateur, ses récits avec.
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
