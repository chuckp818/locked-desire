package com.lockeddesire.app.notify

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
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lockeddesire.app.LockedDesireApp
import com.lockeddesire.app.MainActivity
import com.lockeddesire.app.R
import com.lockeddesire.app.data.NotificationSettings
import com.lockeddesire.app.data.TimeUtil
import com.lockeddesire.app.data.now
import com.lockeddesire.app.engine.ChallengeEngine
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object Notifier {
    private const val CH_SOUND = "ld_sound"
    private const val CH_VIBRATE = "ld_vibrate"
    private const val CH_SILENT = "ld_silent"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH_SOUND, "Hinweise mit Ton", NotificationManager.IMPORTANCE_DEFAULT).apply { enableVibration(true) })
        nm.createNotificationChannel(NotificationChannel(CH_VIBRATE, "Hinweise mit Vibration", NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null, null); enableVibration(true) })
        nm.createNotificationChannel(NotificationChannel(CH_SILENT, "Stille Hinweise", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null); enableVibration(false) })
    }

    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun typeEnabled(n: NotificationSettings, type: String): Boolean = when (type) {
        "CHALLENGE" -> n.nChallenge; "PHASE" -> n.nPhase; "CHASTITY" -> n.nChastity; "DAILY" -> n.nDaily
        "FITNESS" -> n.nFitness; "RECORD" -> n.nRecord; "XP" -> n.nXp; "PAUSE" -> n.nPause
        else -> true
    }

    private fun discreetText(type: String): String = when (type) {
        "CHALLENGE", "DAILY" -> "Du hast eine neue Nachricht."
        "FITNESS" -> "Zeit für deine Erinnerung."
        else -> "Es gibt Neuigkeiten in deiner App."
    }

    /** Zeigt eine Benachrichtigung, sofern Einstellungen, Ruhezeiten und Berechtigung es erlauben. */
    fun post(context: Context, n: NotificationSettings, type: String, text: String, route: String = "dashboard", ignoreQuiet: Boolean = false) {
        if (!n.enabled || !typeEnabled(n, type) || !canNotify(context)) return
        if (!ignoreQuiet && ChallengeEngine.isQuiet(TimeUtil.minuteOfDay(now()), n.quietStart, n.quietEnd)) return
        val channel = if (n.sound) CH_SOUND else if (n.vibration) CH_VIBRATE else CH_SILENT
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", route)
        }
        val pi = PendingIntent.getActivity(context, type.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(if (n.discreet) "LD" else "Locked Desire")
            .setContentText(if (n.discreet) discreetText(type) else text)
            .setVisibility(if (n.discreet) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pi)
        try { NotificationManagerCompat.from(context).notify(type.hashCode(), builder.build()) } catch (_: SecurityException) { }
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<TickWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("ld_tick", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun targetPerDay(n: NotificationSettings): Int = when (n.frequency) {
        0 -> 2; 1 -> 4; 2 -> 7; else -> n.customPerDay.coerceIn(1, 12)
    }.coerceAtMost(n.maxPerDay)
}

/**
 * Läuft alle 15 Minuten über den WorkManager und respektiert damit die
 * Hintergrundbeschränkungen von Android (Doze, Akkusparen).
 */
class TickWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val repo = (applicationContext as LockedDesireApp).repo
            repo.ensureSeeded()
            val n = repo.notif()
            val ctx = applicationContext

            repo.tickDungeon().forEach { (type, text) -> Notifier.post(ctx, n, type, text, "dungeon24") }

            val cs = repo.db.chastityDao().activeNow()
            if (cs != null && cs.goalAt > 0 && !cs.goalNotified && cs.pausedAt == 0L && now() >= cs.goalAt) {
                repo.db.chastityDao().update(cs.copy(goalNotified = true))
                Notifier.post(ctx, n, "CHASTITY", "Keuschheitsziel erreicht. Du entscheidest, wie es weitergeht.", "chastity")
            }

            val minute = TimeUtil.minuteOfDay(now())
            val quiet = ChallengeEngine.isQuiet(minute, n.quietStart, n.quietEnd)
            val today = TimeUtil.dayNumber()
            var settings = n
            if (!quiet && n.nDaily && n.lastDailyDay != today) {
                settings = settings.copy(lastDailyDay = today)
                Notifier.post(ctx, n, "DAILY", "Deine Tagesaufgabe ist verfügbar.", "taskRoulette")
            }
            if (!quiet && n.nFitness && n.lastFitnessDay != today && minute >= 17 * 60) {
                settings = settings.copy(lastFitnessDay = today)
                Notifier.post(ctx, n, "FITNESS", "Fitness-Erinnerung: Zeit für ein paar Wiederholungen.", "fitness")
            }
            if (settings != n) repo.db.profileDao().save(settings)

            if (n.spontaneous && !quiet) {
                val target = Notifier.targetPerDay(n)
                val sent = repo.offersToday()
                if (sent < target) {
                    // Wache Minuten bis zur Ruhezeit, verteilt auf 15-Minuten-Fenster
                    val awakeLeft = if (n.quietStart > minute) n.quietStart - minute else (1440 - minute + n.quietStart) % 1440
                    val slots = (awakeLeft / 15).coerceAtLeast(1)
                    val chance = ((target - sent).toDouble() / slots).coerceIn(0.03, 1.0)
                    if (Random.nextDouble() < chance) {
                        repo.offerSpontaneous()?.let { Notifier.post(ctx, n, "CHALLENGE", it, "chat") }
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.success()
        }
    }
}
