package com.xialia.tactkit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.xialia.tactkit.data.model.WorkbenchCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkbenchCardDao {

  @Query("SELECT * FROM workbench_cards ORDER BY pinned DESC, updatedAt DESC")
  fun observeAllCards(): Flow<List<WorkbenchCardEntity>>

  @Query("SELECT * FROM workbench_cards WHERE id = :id LIMIT 1")
  suspend fun getCardById(id: Long): WorkbenchCardEntity?

  @Query("SELECT * FROM workbench_cards WHERE type = 'CHECKLIST' ORDER BY pinned DESC, updatedAt DESC LIMIT 1")
  suspend fun getTopChecklist(): WorkbenchCardEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCard(card: WorkbenchCardEntity): Long

  @Update
  suspend fun updateCard(card: WorkbenchCardEntity)

  @Query("DELETE FROM workbench_cards WHERE id = :id")
  suspend fun deleteCardById(id: Long)

  @Query("SELECT COUNT(*) FROM workbench_cards")
  suspend fun countCards(): Int
}
