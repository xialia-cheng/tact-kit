package com.xialia.tactkit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.xialia.tactkit.data.dao.WorkbenchCardDao
import com.xialia.tactkit.data.model.WorkbenchCardEntity

@Database(
  entities = [WorkbenchCardEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class TactKitDatabase : RoomDatabase() {

  abstract fun workbenchCardDao(): WorkbenchCardDao

  companion object {
    @Volatile private var INSTANCE: TactKitDatabase? = null

    fun getInstance(context: Context): TactKitDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                TactKitDatabase::class.java,
                "tactkit_workbench.db",
              )
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}
