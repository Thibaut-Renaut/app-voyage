package corsica.universita.journalvoyage.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * La base SQLite : /data/data/corsica.universita.journalvoyage/databases/journal.db.
 * `version` : le numéro du schéma. `exportSchema` : chaque version est écrite dans app/schemas/…/N.json (dans Git).
 *
 * v1 (1.0) : voyages, etapes, photos
 */
@Database(
    entities = [VoyageEntity::class, EtapeEntity::class, PhotoEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun voyageDao(): VoyageDao
    abstract fun etapeDao(): EtapeDao

    companion object {
        const val NAME = "journal.db"

        fun build(context: Context): JournalDatabase =
            Room.databaseBuilder(context.applicationContext, JournalDatabase::class.java, NAME)
                // Jamais fallbackToDestructiveMigration : la base vit chez l'utilisateur, ses récits avec.
                .build()
    }
}
