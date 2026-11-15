package es.telepropia.tv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SourceHostEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun hostDao(): SourceHostDao

    companion object {
        fun build(context: Context): AppDatabase {
            val builder = Room.databaseBuilder(context, AppDatabase::class.java, "telepropia.db")
            return builder.addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        }

        // Los hosts dados de alta en F1 no se pierden al actualizar: entran todos como activos.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE source_host ADD COLUMN state TEXT NOT NULL DEFAULT 'ACTIVE'")
            }
        }

        // SQLite no borra columnas antes de Android 14: hay que recrear la tabla para quitar isPrimary.
        // El SQL calca el que genera Room; el CI compara ambos. El antiguo primario queda el primero.
        internal const val CREATE_V3 = "CREATE TABLE IF NOT EXISTS `source_host_v3` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `baseUrl` TEXT NOT NULL, `label` TEXT, " +
            "`position` INTEGER NOT NULL, `state` TEXT NOT NULL)"

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_V3)
                db.execSQL(
                    "INSERT INTO source_host_v3 (id, baseUrl, label, position, state) " +
                        "SELECT s.id, s.baseUrl, NULL, " +
                        "(SELECT COUNT(*) FROM source_host o WHERE o.isPrimary > s.isPrimary " +
                        "OR (o.isPrimary = s.isPrimary AND o.id < s.id)), s.state FROM source_host s"
                )
                db.execSQL("DROP TABLE source_host")
                db.execSQL("ALTER TABLE source_host_v3 RENAME TO source_host")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_source_host_baseUrl` ON `source_host` (`baseUrl`)"
                )
            }
        }
    }
}
