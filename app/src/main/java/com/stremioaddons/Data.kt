package com.stremioaddons

import androidx.room.*

/** One row per add-on, enabled OR parked. Position = order shown in the grid. */
@Entity(tableName = "slot")
data class Slot(@PrimaryKey val transportUrl: String, val position: Int, val enabled: Boolean, val descriptorJson: String)

@Entity(tableName = "backup")
data class Backup(@PrimaryKey(autoGenerate = true) val id: Long = 0, val ts: Long, val note: String, val json: String)

@Entity(tableName = "profile")
data class Profile(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val json: String)

@Dao
abstract class AddonDao {
    @Query("SELECT * FROM slot ORDER BY position") abstract suspend fun slots(): List<Slot>
    @Query("DELETE FROM slot") abstract suspend fun clearSlots()
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putSlots(s: List<Slot>)
    @Transaction open suspend fun replaceSlots(s: List<Slot>) { clearSlots(); putSlots(s) }

    @Insert abstract suspend fun addBackup(b: Backup)
    @Query("DELETE FROM backup WHERE id NOT IN (SELECT id FROM backup ORDER BY ts DESC LIMIT 30)") abstract suspend fun pruneBackups()
    @Query("SELECT * FROM backup ORDER BY ts DESC") abstract suspend fun backups(): List<Backup>

    @Insert abstract suspend fun addProfile(p: Profile)
    @Query("SELECT * FROM profile ORDER BY name") abstract suspend fun profiles(): List<Profile>
    @Query("DELETE FROM profile WHERE id = :id") abstract suspend fun deleteProfile(id: Long)
}

@Database(entities = [Slot::class, Backup::class, Profile::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() { abstract fun dao(): AddonDao }
