package com.xialia.tactkit.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.xialia.tactkit.MainActivity
import com.xialia.tactkit.R

object PomodoroNotificationHelper {

  const val CHANNEL_ID = "tactkit_pomodoro_live"
  const val NOTIFICATION_ID = 4091

  fun ensureChannel(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (nm.getNotificationChannel(CHANNEL_ID) == null) {
      val channel =
        NotificationChannel(
            CHANNEL_ID,
            "专注计时与实时进度",
            NotificationManager.IMPORTANCE_DEFAULT,
          )
          .apply {
            description = "显示番茄钟与倒计时的实时计时进度条"
            setSound(null, null)
            enableVibration(false)
          }
      nm.createNotificationChannel(channel)
    }
  }

  fun canPostLiveUpdates(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 36) return false
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false)
  }

  fun postTimerNotification(
    context: Context,
    title: String,
    phaseLabel: String,
    remainingSeconds: Int,
    totalSeconds: Int,
    isRunning: Boolean,
    isFocusPhase: Boolean,
  ) {
    ensureChannel(context)
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val launchIntent =
      PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val safeTotal = totalSeconds.coerceAtLeast(1)
    val elapsed = (safeTotal - remainingSeconds).coerceIn(0, safeTotal)
    val mm = remainingSeconds / 60
    val ss = remainingSeconds % 60
    val timeChip = String.format("%02d:%02d", mm, ss)

    val notification: Notification =
      if (Build.VERSION.SDK_INT >= 36) {
        buildAndroid16LiveUpdateNotification(
          context = context,
          title = title,
          phaseLabel = phaseLabel,
          timeChip = timeChip,
          elapsedSeconds = elapsed,
          totalSeconds = safeTotal,
          remainingSeconds = remainingSeconds,
          isRunning = isRunning,
          isFocusPhase = isFocusPhase,
          contentIntent = launchIntent,
        )
      } else {
        NotificationCompat.Builder(context, CHANNEL_ID)
          .setSmallIcon(R.mipmap.ic_launcher)
          .setContentTitle("$title · $timeChip")
          .setContentText(phaseLabel)
          .setOngoing(isRunning)
          .setOnlyAlertOnce(true)
          .setSilent(true)
          .setProgress(safeTotal, elapsed, false)
          .setContentIntent(launchIntent)
          .build()
      }

    runCatching { nm.notify(NOTIFICATION_ID, notification) }
  }

  fun cancelTimerNotification(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.cancel(NOTIFICATION_ID)
  }

  @androidx.annotation.RequiresApi(36)
  private fun buildAndroid16LiveUpdateNotification(
    context: Context,
    title: String,
    phaseLabel: String,
    timeChip: String,
    elapsedSeconds: Int,
    totalSeconds: Int,
    remainingSeconds: Int,
    isRunning: Boolean,
    isFocusPhase: Boolean,
    contentIntent: PendingIntent,
  ): Notification {
    val accentHex = if (isFocusPhase) Color.parseColor("#3B6AF5") else Color.parseColor("#2E8B57")
    val progressStyle =
      Notification.ProgressStyle()
        .setStyledByProgress(true)
        .setProgress(elapsedSeconds)
        .setProgressSegments(
          listOf(
            Notification.ProgressStyle.Segment(totalSeconds).setColor(accentHex),
          )
        )

    val extras =
      Bundle().apply {
        putBoolean("android.requestPromotedOngoing", true)
      }

    val builder =
      Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText("$phaseLabel · 剩余 $timeChip")
        .setShortCriticalText(timeChip)
        .setOngoing(isRunning)
        .setOnlyAlertOnce(true)
        .setStyle(progressStyle)
        .addExtras(extras)
        .setContentIntent(contentIntent)

    if (isRunning && remainingSeconds > 0) {
      builder
        .setWhen(System.currentTimeMillis() + remainingSeconds * 1000L)
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
    }

    return builder.build()
  }
}
