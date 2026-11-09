package corsica.universita.journalvoyage.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [VoyageEntity::class, EtapeEntity::class, PhotoEntity::class, TrajetEntity::class, TrackPointEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun voyageDao(): VoyageDao
    abstract fun etapeDao(): EtapeDao
    abstract fun trajetDao(): TrajetDao

    companion object {
        const val NAME = "journal.db"

        // SQL identique à schemas/…/2.json ; vérifié par MigrationTest.
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trajets ADD COLUMN nom TEXT")
            }
        }

        fun build(context: Context): JournalDatabase =
            Room.databaseBuilder(context.applicationContext, JournalDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
