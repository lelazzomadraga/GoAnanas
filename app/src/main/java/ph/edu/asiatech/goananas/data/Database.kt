package ph.edu.asiatech.goananas.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** status of a scanned plant after the diagnosis */
object ScanStatus {
    const val OPEN = "Open"            // diseased, waiting for action
    const val RECHECKED = "Rechecked"  // plant was checked again
    const val RESOLVED = "Resolved"    // plant treated or removed
    const val HEALTHY = "Healthy"      // no action needed
}

/**
 * One diagnosis. Stored on the phone first (works without signal) and copied to
 * Cloud Firestore by SyncWorker when the phone is online and Firebase is set up.
 */
@Entity(tableName = "scans", indices = [Index("uuid", unique = true), Index("predictedLabel"), Index("block")])
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val imagePath: String,
    val plantPart: String,
    val block: String,
    val predictedLabel: String,
    val confidence: Float,
    /** probabilities of the six classes in model order, comma-separated */
    val probabilities: String,
    val inferenceMs: Long,
    val notes: String = "",
    val status: String,
    val createdAt: Long,
    val recheckAt: Long? = null,
    val userId: String? = null,
    val synced: Boolean = false,
    val remoteImageUrl: String? = null,
) {
    val pineappleClass: PineappleClass get() = PineappleClass.fromLabel(predictedLabel)
    val probabilityList: List<Float> get() = probabilities.split(",").mapNotNull { it.toFloatOrNull() }
}

data class ClassCount(val predictedLabel: String, val total: Int)
data class BlockCount(val block: String, val total: Int)

@Dao
interface ScanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scan: ScanEntity): Long

    @Update
    suspend fun update(scan: ScanEntity)

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM scans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE id = :id")
    fun observeById(id: Long): Flow<ScanEntity?>

    @Query("SELECT * FROM scans WHERE id = :id")
    suspend fun getById(id: Long): ScanEntity?

    @Query("SELECT predictedLabel, COUNT(*) AS total FROM scans GROUP BY predictedLabel")
    fun observeClassCounts(): Flow<List<ClassCount>>

    @Query("SELECT block, COUNT(*) AS total FROM scans WHERE predictedLabel != 'healthy' AND block != '' GROUP BY block ORDER BY total DESC")
    fun observeAffectedBlocks(): Flow<List<BlockCount>>

    @Query("SELECT * FROM scans WHERE status = 'Open' AND recheckAt IS NOT NULL ORDER BY recheckAt ASC")
    fun observeDueRechecks(): Flow<List<ScanEntity>>

    @Query("SELECT DISTINCT block FROM scans WHERE block != '' ORDER BY block")
    fun observeBlocks(): Flow<List<String>>

    @Query("SELECT * FROM scans WHERE synced = 0")
    suspend fun unsynced(): List<ScanEntity>

    /** diseased scans of the same class in the same block since a given time (used for the outbreak alert) */
    @Query("SELECT COUNT(*) FROM scans WHERE predictedLabel = :label AND block = :block AND createdAt >= :since")
    suspend fun countSimilar(label: String, block: String, since: Long): Int

    @Query("SELECT createdAt FROM scans WHERE createdAt >= :since")
    fun observeTimesSince(since: Long): Flow<List<Long>>
}

@Database(entities = [ScanEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "go_ananas.db")
                .build().also { instance = it }
        }
    }
}
