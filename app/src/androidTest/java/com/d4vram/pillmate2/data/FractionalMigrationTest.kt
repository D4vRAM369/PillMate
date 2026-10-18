package com.d4vram.pillmate2.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.d4vram.pillmate2.data.local.PillMateDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FractionalMigrationTest {
    @Test fun version15RetainsHistoryAndAcceptsHalfUnits() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "fractional-migration-test.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `medications` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `dose` TEXT NOT NULL, `type` TEXT NOT NULL, `colorArgb` INTEGER NOT NULL, `emoji` TEXT NOT NULL, `takenToday` INTEGER NOT NULL, `dailyGoal` INTEGER NOT NULL, `lastTaken` TEXT, `sortOrder` INTEGER NOT NULL, `scheduleTime` TEXT, `scheduleDays` TEXT NOT NULL DEFAULT '', `reminderEnabled` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL DEFAULT 0, `photoPath` TEXT DEFAULT NULL, `usePhotoInstead` INTEGER NOT NULL DEFAULT 0, `parentMedicationId` TEXT DEFAULT NULL, PRIMARY KEY(`id`))""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `history` (`id` TEXT NOT NULL, `medicationId` TEXT NOT NULL, `medicationName` TEXT NOT NULL, `time` TEXT NOT NULL, `date` TEXT NOT NULL, `colorArgb` INTEGER NOT NULL, `emoji` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `notes` TEXT NOT NULL, `timestampIso` TEXT NOT NULL, `dose` TEXT, `audioNoteUri` TEXT, PRIMARY KEY(`id`))""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `voice_notes` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, `durationMillis` INTEGER NOT NULL, `encryptedFileName` TEXT NOT NULL, `noteSaltBase64` TEXT NOT NULL, `noteIvBase64` TEXT NOT NULL, `fileSizeBytes` INTEGER NOT NULL, `category` TEXT NOT NULL, `tags` TEXT NOT NULL, PRIMARY KEY(`id`))""")
                        db.execSQL("INSERT INTO medications (id,name,dose,type,colorArgb,emoji,takenToday,dailyGoal,lastTaken,sortOrder) VALUES ('m','Example','20 mg','Pastilla',0,'',2,0,NULL,0)")
                        db.execSQL("INSERT INTO history (id,medicationId,medicationName,time,date,colorArgb,emoji,quantity,notes,timestampIso,dose,audioNoteUri) VALUES ('h','m','Example','10:00','2026-01-01',0,'',2,'note','2026-01-01T10:00','20 mg','audio')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        helper.writableDatabase
        helper.close()
        val database = Room.databaseBuilder(context, PillMateDatabase::class.java, name)
            .addMigrations(PillMateDatabase.MIGRATION_15_16, PillMateDatabase.MIGRATION_16_17, PillMateDatabase.MIGRATION_17_18).build()
        try {
            val old = database.historyDao().getAllHistory().single()
            assertEquals(2.0, old.quantity, 0.0)
            assertEquals("note", old.notes)
            assertEquals("audio", old.audioNoteUri)
            assertEquals(2.0, database.medicationDao().getMedicationById("m")!!.takenToday, 0.0)
            database.historyDao().insert(old.copy(quantity = 0.5))
            assertEquals(0.5, database.historyDao().getById("h")!!.quantity, 0.0)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
