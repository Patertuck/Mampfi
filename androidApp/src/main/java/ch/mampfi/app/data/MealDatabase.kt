package ch.mampfi.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface MealDao {
    @Query("SELECT * FROM mahlzeiten") fun observeAll(): Flow<List<MahlzeitEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<MahlzeitEntity>)
    @Query("DELETE FROM mahlzeiten") suspend fun clear()
}
@Database(entities = [MahlzeitEntity::class], version = 1, exportSchema = false)
abstract class MealDatabase : RoomDatabase() { abstract fun meals(): MealDao }
