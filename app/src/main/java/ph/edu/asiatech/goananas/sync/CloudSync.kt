package ph.edu.asiatech.goananas.sync

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import ph.edu.asiatech.goananas.BuildConfig
import ph.edu.asiatech.goananas.data.AppDatabase
import ph.edu.asiatech.goananas.data.PineappleClass
import ph.edu.asiatech.goananas.data.SettingsStore
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Cloud part of the app (Firebase). Everything here is skipped when the group has not
 * added google-services.json, so the app always works offline.
 *
 * Cloud Firestore structure
 *   users/{uid}                     name, role, farmCode, fcmToken, createdAt
 *   farms/{farmCode}/scans/{uuid}   one document per diagnosis (see SyncWorker)
 * Cloud Storage
 *   farms/{farmCode}/scans/{uuid}.jpg
 */
object CloudSync {
    fun isEnabled(context: Context): Boolean =
        BuildConfig.FIREBASE_ENABLED && FirebaseApp.getApps(context).isNotEmpty()

    fun currentUserId(context: Context): String? =
        if (isEnabled(context)) FirebaseAuth.getInstance().currentUser?.uid else null

    fun currentEmail(context: Context): String? =
        if (isEnabled(context)) FirebaseAuth.getInstance().currentUser?.email else null

    suspend fun register(context: Context, email: String, password: String, name: String, role: String, farmCode: String) {
        val result = FirebaseAuth.getInstance().createUserWithEmailAndPassword(email.trim(), password).await()
        val uid = result.user?.uid ?: error("Registration failed")
        FirebaseFirestore.getInstance().collection("users").document(uid).set(
            mapOf("name" to name.trim(), "role" to role, "farmCode" to farmCode.trim().uppercase(), "email" to email.trim(), "createdAt" to System.currentTimeMillis()),
        ).await()
        refreshToken(context)
    }

    /** signs in and returns the saved profile (name, role, farm code) */
    suspend fun signIn(context: Context, email: String, password: String): Triple<String, String, String> {
        val result = FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password).await()
        val uid = result.user?.uid ?: error("Sign-in failed")
        val doc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
        refreshToken(context)
        return Triple(doc.getString("name") ?: "", doc.getString("role") ?: "", doc.getString("farmCode") ?: "")
    }

    fun signOut(context: Context) { if (isEnabled(context)) FirebaseAuth.getInstance().signOut() }

    private fun refreshToken(context: Context) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { saveToken(context, it) }
    }

    fun saveToken(context: Context, token: String) {
        if (!isEnabled(context)) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid).set(mapOf("fcmToken" to token), SetOptions.merge())
    }

    /** uploads unsynced scans as soon as the phone has internet */
    fun requestSync(context: Context) {
        if (!isEnabled(context)) return
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("sync_scans", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!CloudSync.isEnabled(ctx)) return Result.success()
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()
        val settings = SettingsStore(ctx).current()
        val farm = settings.farmCode.ifBlank { "UNASSIGNED" }
        val dao = AppDatabase.get(ctx).scanDao()
        val db = FirebaseFirestore.getInstance()
        val storage = FirebaseStorage.getInstance()

        return try {
            for (scan in dao.unsynced()) {
                var url = scan.remoteImageUrl
                if (url == null) {
                    val file = File(scan.imagePath)
                    if (file.exists()) {
                        val ref = storage.reference.child("farms/$farm/scans/${scan.uuid}.jpg")
                        ref.putFile(Uri.fromFile(file)).await()
                        url = ref.downloadUrl.await().toString()
                    }
                }
                val cls = PineappleClass.fromLabel(scan.predictedLabel)
                val doc = mapOf(
                    "uuid" to scan.uuid,
                    "userId" to user.uid,
                    "userName" to settings.name,
                    "role" to settings.role,
                    "farmCode" to farm,
                    "block" to scan.block,
                    "plantPart" to scan.plantPart,
                    "predictedLabel" to scan.predictedLabel,
                    "predictedClass" to cls.displayName,
                    "isHealthy" to cls.isHealthy,
                    "confidence" to scan.confidence.toDouble(),
                    "probabilities" to PineappleClass.modelOrder.map { it.label }.zip(scan.probabilityList.map { it.toDouble() }).toMap(),
                    "recommendedAction" to cls.recommendedAction,
                    "inferenceMs" to scan.inferenceMs,
                    "notes" to scan.notes,
                    "status" to scan.status,
                    "createdAt" to scan.createdAt,
                    "recheckAt" to scan.recheckAt,
                    "imageUrl" to url,
                    "updatedAt" to System.currentTimeMillis(),
                )
                db.collection("farms").document(farm).collection("scans").document(scan.uuid).set(doc, SetOptions.merge()).await()
                dao.update(scan.copy(synced = true, remoteImageUrl = url))
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
