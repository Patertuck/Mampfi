package ch.mampfi.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface MealDao {
    @Query("SELECT * FROM mahlzeiten") fun observeAll(): Flow<List<MahlzeitEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<MahlzeitEntity>)
    @Query("DELETE FROM mahlzeiten") suspend fun clear()
    @Transaction suspend fun replaceAll(items: List<MahlzeitEntity>) {
        clear()
        upsertAll(items)
    }
}
@Dao interface AwayEntryDao {
    @Query("SELECT * FROM auswaerts_eintraege ORDER BY datum") fun observeAll(): Flow<List<AuswaertsEintragEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<AuswaertsEintragEntity>)
    @Query("DELETE FROM auswaerts_eintraege") suspend fun clear()
    @Transaction suspend fun replaceAll(items: List<AuswaertsEintragEntity>) {
        clear()
        upsertAll(items)
    }
}
@Entity(tableName = "grocery_items")
data class GroceryItemEntity(@PrimaryKey val id: String, val json: String, val revision: Long, val deleted: Boolean)
@Entity(tableName = "grocery_mutations")
data class GroceryMutationEntity(@PrimaryKey val id: String, val json: String, val createdAt: String)
@Entity(tableName = "grocery_suggestions")
data class GrocerySuggestionEntity(@PrimaryKey val stableKey: String, val name: String, val json: String, val lastUsedAt: String)
@Entity(tableName = "grocery_sync_state")
data class GrocerySyncStateEntity(@PrimaryKey val id: Int = 1, val revision: Long)

@Dao interface GroceryDao {
    @Query("SELECT * FROM grocery_items WHERE deleted = 0") fun observeAll(): Flow<List<GroceryItemEntity>>
    @Query("SELECT * FROM grocery_items") suspend fun all(): List<GroceryItemEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: GroceryItemEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(items: List<GroceryItemEntity>)
    @Query("DELETE FROM grocery_items") suspend fun clearItems()
    @Query("SELECT * FROM grocery_mutations ORDER BY createdAt") suspend fun pending(): List<GroceryMutationEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun enqueue(item: GroceryMutationEntity)
    @Query("DELETE FROM grocery_mutations WHERE id IN (:ids)") suspend fun removeMutations(ids: List<String>)
    @Query("SELECT * FROM grocery_suggestions WHERE lower(name) LIKE '%' || lower(:query) || '%' ORDER BY lastUsedAt DESC LIMIT :limit") suspend fun suggestions(query: String, limit: Int = 12): List<GrocerySuggestionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun rememberSuggestion(item: GrocerySuggestionEntity)
    @Query("SELECT revision FROM grocery_sync_state WHERE id = 1") suspend fun revision(): Long?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setState(state: GrocerySyncStateEntity)
}

@Database(
    entities = [MahlzeitEntity::class, AuswaertsEintragEntity::class, GroceryItemEntity::class, GroceryMutationEntity::class, GrocerySuggestionEntity::class, GrocerySyncStateEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class MealDatabase : RoomDatabase() {
    abstract fun meals(): MealDao
    abstract fun awayEntries(): AwayEntryDao
    abstract fun groceries(): GroceryDao
}
