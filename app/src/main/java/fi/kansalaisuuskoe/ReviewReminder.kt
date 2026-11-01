package fi.kansalaisuuskoe

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fi.kansalaisuuskoe.data.InterfaceLanguage
import fi.kansalaisuuskoe.domain.ReminderMessage
import fi.kansalaisuuskoe.domain.StudyReminder
import kotlinx.coroutines.flow.first
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Opt-in local study reminder. A daily job reads the device's own learning data and posts at most one
 * notification; nothing leaves the device. The job is realigned to the chosen time whenever the app
 * opens, so a daylight-saving change can only shift it until then.
 */
object ReviewReminder {
    private const val WORK = "study-reminder"
    private const val CHANNEL = "study-reminder"
    private const val NOTIFICATION_ID = 1

    fun schedule(context: Context, enabled: Boolean, minuteOfDay: Int, now: Long = System.currentTimeMillis()) {
        val work = WorkManager.getInstance(context)
        if (!enabled) { work.cancelUniqueWork(WORK); return }
        val request = PeriodicWorkRequestBuilder<ReviewReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(StudyReminder.nextAt(minuteOfDay, now) - now, TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }

    /** Opening the app answers the reminder. */
    fun dismiss(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    fun canNotify(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun post(context: Context, language: InterfaceLanguage, message: ReminderMessage) {
        if (!canNotify(context)) return
        // The worker has no Activity; apply the in-app interface language the same way InterfaceLocale does.
        val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language.tag)) })
        val res = localized.resources
        // Re-registering an existing channel only renames it, so its name follows the interface language.
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, res.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = res.getString(R.string.reminder_channel_description) })
        val (title, text) = when (message) {
            is ReminderMessage.Reviews -> res.getString(R.string.reminder_due_title) to res.getQuantityString(R.plurals.reminder_due_body, message.count, message.count)
            ReminderMessage.NewLesson -> res.getString(R.string.reminder_new_title) to res.getString(R.string.reminder_new_body)
        }
        val open = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
        catch (_: SecurityException) { /* Permission withdrawn between the check and the post. */ }
    }
}

class ReviewReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as CitizenshipApplication).container
        val settings = container.settings.settings.first()
        if (!settings.reminder) return Result.success()
        StudyReminder.message(container.learning.snapshot.first(), settings.studyLanguage, System.currentTimeMillis())
            ?.let { ReviewReminder.post(applicationContext, settings.interfaceLanguage, it) }
        return Result.success()
    }
}
