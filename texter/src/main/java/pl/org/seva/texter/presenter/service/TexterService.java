/*
 * Copyright (C) 2016 Wiktor Nizio
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
 */

package pl.org.seva.texter.presenter.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import javax.inject.Inject;

import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import pl.org.seva.texter.R;
import pl.org.seva.texter.presenter.source.LocationSource;
import pl.org.seva.texter.presenter.utils.SmsSender;
import pl.org.seva.texter.view.activity.MainActivity;
import pl.org.seva.texter.TexterApplication;

public class TexterService extends Service {

    @SuppressWarnings({"WeakerAccess", "CanBeFinal"})
    @Inject
    LocationSource locationSource;
    @SuppressWarnings({"WeakerAccess", "CanBeFinal"})
    @Inject
    SmsSender smsSender;

    private static final int ONGOING_NOTIFICATION_ID = 1;

    private Disposable distanceSubscription = Disposables.empty();

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        ((TexterApplication) getApplication()).getComponent().inject(this);
        Intent mainActivityIntent = new Intent(this, MainActivity.class);

        // Use System.currentTimeMillis() to have a unique ID for the pending intent.
        PendingIntent pIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                mainActivityIntent,
                0);
        Notification n = new Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ?
                        R.drawable.notification :
                        R.mipmap.ic_launcher)
                .setContentIntent(pIntent)
                .setAutoCancel(false)
                .build();
        createDistanceSubscription();
        locationSource.resumeUpdates(this);

        startForeground(ONGOING_NOTIFICATION_ID, n);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        removeDistanceSubscription();
        locationSource.pauseUpdates();
        super.onDestroy();
    }

    private boolean hardwareCanSendSms() {
        return  ((TexterApplication) getApplication()).hardwareCanSendSms();
    }

    private void createDistanceSubscription() {
        distanceSubscription = locationSource
                .distanceChangedListener()
                .filter(__ -> hardwareCanSendSms())
                .subscribe(
                __ -> smsSender.onDistanceChanged());
    }

    private void removeDistanceSubscription() {
        distanceSubscription.dispose();
    }
}
