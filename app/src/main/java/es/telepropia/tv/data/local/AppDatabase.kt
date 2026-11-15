package es.telepropia.tv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SourceHostEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun hostDao(): SourceHostDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "telepropia.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        // Los hosts dados de alta en F1 no se pierden al actualizar: entran todos como activos.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE source_host ADD COLUMN state TEXT NOT NULL DEFAULT 'ACTIVE'")
            }
        }
    }
}
