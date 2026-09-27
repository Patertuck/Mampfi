package ch.mampfi.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface MealDao {
    @Query("SELECT * FROM mahlzeiten") fun observeAll(): Flow<List<MahlzeitEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<MahlzeitEntity>)
    @Query("DELETE FROM mahlzeiten") suspend fun clear()
}
@Dao interface AwayEntryDao {
    @Query("SELECT * FROM auswaerts_eintraege ORDER BY datum") fun observeAll(): Flow<List<AuswaertsEintragEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<AuswaertsEintragEntity>)
    @Query("DELETE FROM auswaerts_eintraege") suspend fun clear()
}
@Database(entities = [MahlzeitEntity::class, AuswaertsEintragEntity::class], version = 3, exportSchema = false)
abstract class MealDatabase : RoomDatabase() {
    abstract fun meals(): MealDao
    abstract fun awayEntries(): AwayEntryDao
}
