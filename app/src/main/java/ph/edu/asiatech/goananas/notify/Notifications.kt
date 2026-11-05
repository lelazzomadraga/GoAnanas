package ph.edu.asiatech.goananas.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import ph.edu.asiatech.goananas.MainActivity
import ph.edu.asiatech.goananas.R
import ph.edu.asiatech.goananas.data.AppDatabase
import ph.edu.asiatech.goananas.data.ScanStatus
import ph.edu.asiatech.goananas.data.SettingsStore
import ph.edu.asiatech.goananas.sync.CloudSync
import java.util.concurrent.TimeUnit

/** notification channels and the messages the app shows */
object Notifier {
    const val CH_RECHECK = "recheck"
    const val CH_INSPECTION = "inspection"
    const val CH_ALERTS = "alerts"
    const val EXTRA_SCAN_ID = "scan_id"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RECHECK, "Re-check reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Reminds you to check again a plant that was found diseased"
        })
        nm.createNotificationChannel(NotificationChannel(CH_INSPECTION, "Monthly inspection", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Monthly reminder to inspect the pineapple blocks"
        })
        nm.createNotificationChannel(NotificationChannel(CH_ALERTS, "Disease alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Possible outbreaks and messages from the farm owner or technician"
        })
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context, scanId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (scanId != null) putExtra(EXTRA_SCAN_ID, scanId)
        }
        return PendingIntent.getActivity(context, (scanId ?: 0L).toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun post(context: Context, channel: String, id: Int, title: String, text: String, scanId: Long? = null) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, scanId))
            .build()
        try { NotificationManagerCompat.from(context).notify(id, n) } catch (_: SecurityException) { }
    }

    fun recheck(context: Context, scanId: Long, disease: String, block: String) = post(
        context, CH_RECHECK, (1000 + scanId).toInt(), "Time to re-check a plant",
        "$disease was detected${if (block.isNotEmpty()) " in $block" else ""}. Check the plant again and update its status.", scanId,
    )

    fun monthly(context: Context) = post(
        context, CH_INSPECTION, 2, "Monthly pineapple inspection",
        "Walk the rows and scan plants with yellowing, reddish, or wilting leaves, rotting hearts, or damaged fruits.",
    )

    fun outbreak(context: Context, disease: String, block: String, count: Int) = post(
        context, CH_ALERTS, 3000 + (disease + block).hashCode() % 1000, "Possible $disease outbreak",
        "$count plants in $block were found with $disease in the last 14 days. Inform the farm owner or agricultural technician.",
    )

    fun remote(context: Context, title: String, body: String) = post(context, CH_ALERTS, (System.currentTimeMillis() % 100000).toInt(), title, body)
}

/** schedules the background reminders with WorkManager (they survive app restarts and phone reboots) */
object ReminderScheduler {
    private fun recheckName(id: Long) = "recheck_$id"

    fun scheduleRecheck(context: Context, scanId: Long, disease: String, block: String, days: Long) {
        val req = OneTimeWorkRequestBuilder<RecheckWorker>()
            .setInitialDelay(days, TimeUnit.DAYS)
            .setInputData(workDataOf("scan_id" to scanId, "disease" to disease, "block" to block))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(recheckName(scanId), androidx.work.ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelRecheck(context: Context, scanId: Long) = WorkManager.getInstance(context).cancelUniqueWork(recheckName(scanId))

    fun setMonthly(context: Context, on: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!on) { wm.cancelUniqueWork("monthly_inspection"); return }
        val req = PeriodicWorkRequestBuilder<MonthlyInspectionWorker>(30, TimeUnit.DAYS).setInitialDelay(30, TimeUnit.DAYS).build()
        wm.enqueueUniquePeriodicWork("monthly_inspection", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class RecheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong("scan_id", -1)
        val scan = AppDatabase.get(applicationContext).scanDao().getById(id) ?: return Result.success()
        if (scan.status == ScanStatus.OPEN && SettingsStore(applicationContext).current().recheckReminders) {
            Notifier.recheck(applicationContext, id, inputData.getString("disease") ?: scan.pineappleClass.displayName, scan.block)
        }
        return Result.success()
    }
}

class MonthlyInspectionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (SettingsStore(applicationContext).current().monthlyReminder) Notifier.monthly(applicationContext)
        return Result.success()
    }
}

/** receives push notifications sent by the Cloud Function (firebase/functions/index.js) */
class GoAnanasMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "Go Ananas!"
        val body = message.notification?.body ?: message.data["body"] ?: return
        Notifier.remote(applicationContext, title, body)
    }

    override fun onNewToken(token: String) {
        CloudSync.saveToken(applicationContext, token)
    }
}
