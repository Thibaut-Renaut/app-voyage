package corsica.universita.journalvoyage

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import corsica.universita.journalvoyage.data.local.JournalDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La migration v1 → v2, testée sur un appareil (./gradlew connectedDebugAndroidTest) :
 * une base v1 avec un voyage, une étape et une photo est migrée ; Room compare le schéma obtenu
 * à schemas/…/2.json (validateDroppedTables), puis on vérifie que rien n'a été perdu.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), JournalDatabase::class.java)

    @Test
    fun migrate1To2_garde_les_donnees() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO voyages (id, titre, description, coverPath, createdAt) VALUES (1, 'Corse', 'desc', NULL, 1000)")
            execSQL("INSERT INTO etapes (id, voyageId, titre, description, date, lat, lng, synced) VALUES (1, 1, 'Bastia', 'récit', 2000, 42.7, 9.45, 0)")
            execSQL("INSERT INTO photos (id, etapeId, path) VALUES (1, 1, '/data/photo.jpg')")
            close()
        }

        // Exécute MIGRATION_1_2 et valide le schéma obtenu contre 2.json : une colonne ou un index de travers → échec.
        helper.runMigrationsAndValidate(dbName, 2, true, JournalDatabase.MIGRATION_1_2).close()

        // Puis la vraie base, comme l'app l'ouvre (1→2→3 enchaînées) : les données v1 sont là, les nouvelles tables utilisables.
        val db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, JournalDatabase::class.java, dbName)
            .addMigrations(JournalDatabase.MIGRATION_1_2, JournalDatabase.MIGRATION_2_3)
            .build()
        runBlocking {
            val voyages = db.voyageDao().all().first()
            assertEquals(1, voyages.size)
            assertEquals(1, voyages[0].etapesCount)
            val etape = db.etapeDao().observe(1).first()!!
            assertEquals("Bastia", etape.etape.titre)
            assertEquals(1, etape.photos.size)
            assertEquals(1, db.etapeDao().unsynced().size) // l'étape en attente d'envoi l'est toujours
            assertEquals(0, db.trajetDao().forVoyage(1).first().size)
        }
        db.close()
    }

    /** v2 → v3 : la colonne trajets.nom ajoutée ; le trajet et ses points restent, sans nom. */
    @Test
    fun migrate2To3_ajoute_le_nom_des_trajets() {
        val name = "migration-2-3.db"
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO voyages (id, titre, description, coverPath, createdAt) VALUES (1, 'Corse', 'desc', NULL, 1000)")
            execSQL("INSERT INTO trajets (id, voyageId, startedAt, endedAt, distanceM) VALUES (1, 1, 1000, 2000, 1160.0)")
            execSQL("INSERT INTO track_points (id, trajetId, lat, lng, altitude, accuracy, time) VALUES (1, 1, 42.7, 9.45, 4.0, 5.0, 1000)")
            close()
        }
        helper.runMigrationsAndValidate(name, 3, true, JournalDatabase.MIGRATION_2_3).apply {
            query("SELECT nom, distanceM, (SELECT COUNT(*) FROM track_points) FROM trajets WHERE id = 1").use { c ->
                c.moveToFirst()
                assertEquals(true, c.isNull(0))
                assertEquals(1160.0, c.getDouble(1), 0.01)
                assertEquals(1, c.getInt(2))
            }
            execSQL("UPDATE trajets SET nom = 'De Bavella à Piana' WHERE id = 1")
            close()
        }
    }
}
