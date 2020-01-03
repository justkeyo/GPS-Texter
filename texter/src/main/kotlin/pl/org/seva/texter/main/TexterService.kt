/*
 * Copyright (C) 2017 Wiktor Nizio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * If you like this program, consider donating bitcoin: bc1qncxh5xs6erq6w4qz3a7xl7f50agrgn3w58dsfp
 */

package pl.org.seva.texter.main

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.lifecycle.LifecycleService
import android.content.Context
import pl.org.seva.texter.R
import pl.org.seva.texter.movement.activityRecognition
import pl.org.seva.texter.movement.location
import pl.org.seva.texter.sms.smsSender


class TexterService : LifecycleService() {

    private val notificationBuilder by lazy { createNotificationBuilder() }
    private var activityRecognitionListenersAdded = false

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        startForeground(ONGOING_NOTIFICATION_ID, createOngoingNotification())
        addDistanceListeners()
        addActivityRecognitionListeners()
        location.request()

        return Service.START_STICKY
    }

    private fun addActivityRecognitionListeners() {
        if (activityRecognitionListenersAdded) {
            return
        }
        activityRecognition.addActivityRecognitionListener(
                lifecycle, stationary = ::onDeviceStationary, moving = ::onDeviceMoving)
        activityRecognitionListenersAdded = true
    }

    private fun onDeviceStationary() {
        location.paused = true
        location.removeRequest()
    }

    private fun onDeviceMoving() {
        location.paused = false
        location.request()
    }

    private fun createOngoingNotification(): Notification {
        val mainActivityIntent = Intent(this, MainActivity::class.java)

        val pi = PendingIntent.getActivity(
                this,
                System.currentTimeMillis().toInt(),
                mainActivityIntent,
                0)
        return notificationBuilder
                .setContentTitle(getString(R.string.app_name))
                .setSmallIcon(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                            R.drawable.notification
                        else
                            R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setAutoCancel(false)
                .build()
    }

    private fun createNotificationBuilder() : Notification.Builder =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
            else {
                val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                // The id of the channel.
                val id = NOTIFICATION_CHANNEL_ID
                // The user-visible name of the channel.
                val name = getString(R.string.channel_name)
                // The user-visible description of the channel.
                val description = getString(R.string.channel_description)
                val importance = NotificationManager.IMPORTANCE_LOW
                val mChannel = NotificationChannel(id, name, importance)
                // Configure the notification channel.
                mChannel.description = description
                mNotificationManager.createNotificationChannel(mChannel)
                Notification.Builder(this, id)
            }

    private fun hardwareCanSendSms(): Boolean = (application as TexterApplication).hardwareCanSendSms()

    private fun addDistanceListeners() {
        if (!hardwareCanSendSms()) {
            return
        }
        location.addDistanceListener(lifecycle) { smsSender.onDistanceChanged() }
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "my_channel_01"
        private const val ONGOING_NOTIFICATION_ID = 1
    }
}
