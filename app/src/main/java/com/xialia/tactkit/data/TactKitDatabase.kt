package com.xialia.tactkit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xialia.tactkit.data.dao.WorkbenchCardDao
import com.xialia.tactkit.data.model.WorkbenchCardEntity

@Database(
  entities = [WorkbenchCardEntity::class],
  version = 2,
  exportSchema = false,
)
abstract class TactKitDatabase : RoomDatabase() {

  abstract fun workbenchCardDao(): WorkbenchCardDao

  companion object {
    @Volatile private var INSTANCE: TactKitDatabase? = null

    val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "ALTER TABLE workbench_cards ADD COLUMN audioDurationMs INTEGER NOT NULL DEFAULT 0"
          )
          db.execSQL("ALTER TABLE workbench_cards ADD COLUMN audioFilePath TEXT")
        }
      }

    fun getInstance(context: Context): TactKitDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                TactKitDatabase::class.java,
                "tactkit_workbench.db",
              )
              .addMigrations(MIGRATION_1_2)
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}
