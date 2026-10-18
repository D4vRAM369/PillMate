package com.d4vram.pillmate2.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.d4vram.pillmate2.data.local.PillMateDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Camino real de la app diaria: v15 → 16 → 17 → 18, validado contra los esquemas de app/schemas/. */
class MigrationPathTest {
    private val name = "migration-path-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PillMateDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test fun version15To18KeepsHistoryAndRebuildsLastTakenAsIso() {
        helper.createDatabase(name, 15).apply {
            // Datos sintéticos: texto de pantalla congelado como el que guardaba la app
            execSQL("INSERT INTO medications (id,name,dose,type,colorArgb,emoji,takenToday,dailyGoal,lastTaken,sortOrder) VALUES ('a','Example A','10 mg','Pastilla',0,'',3,1,'Hoy 22:25',0)")
            execSQL("INSERT INTO medications (id,name,dose,type,colorArgb,emoji,takenToday,dailyGoal,lastTaken,sortOrder) VALUES ('b','Example B','5 mg','Pastilla',0,'',0,1,'Ayer 08:00',1)")
            execSQL("INSERT INTO history (id,medicationId,medicationName,time,date,colorArgb,emoji,quantity,notes,timestampIso,dose,audioNoteUri) VALUES ('h1','a','Example A','09:00','2026-01-01',0,'',1,'','2026-01-01T09:00','10 mg',NULL)")
            execSQL("INSERT INTO history (id,medicationId,medicationName,time,date,colorArgb,emoji,quantity,notes,timestampIso,dose,audioNoteUri) VALUES ('h2','a','Example A','22:25','2026-03-02',0,'',2,'nota','2026-03-02T22:25','10 mg',NULL)")
            execSQL("INSERT INTO history (id,medicationId,medicationName,time,date,colorArgb,emoji,quantity,notes,timestampIso,dose,audioNoteUri) VALUES ('h3','a','Example A','11:00','2026-02-10',0,'',1,'','2026-02-10T11:00','10 mg',NULL)")
            close()
        }

        val db = helper.runMigrationsAndValidate(
            name, 18, true,
            PillMateDatabase.MIGRATION_15_16,
            PillMateDatabase.MIGRATION_16_17,
            PillMateDatabase.MIGRATION_17_18
        )

        db.query("SELECT id, lastTaken, archived FROM medications ORDER BY id").use { c ->
            c.moveToNext()
            assertEquals("a", c.getString(0))
            assertEquals("2026-03-02T22:25", c.getString(1))
            assertEquals(0, c.getInt(2))
            c.moveToNext()
            assertEquals("b", c.getString(0))
            assertNull(c.getString(1))
        }
        db.query("SELECT COUNT(*), SUM(quantity) FROM history").use { c ->
            c.moveToNext()
            assertEquals(3, c.getInt(0))
            assertEquals(4.0, c.getDouble(1), 0.0)
        }
        db.close()
    }
}
