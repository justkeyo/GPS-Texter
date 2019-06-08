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

package pl.org.seva.texter.presenter.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import pl.org.seva.texter.R;
import pl.org.seva.texter.model.DistanceZone;
import pl.org.seva.texter.presenter.source.LocationSource;
import pl.org.seva.texter.view.activity.SettingsActivity;
import pl.org.seva.texter.model.Sms;

@Singleton
public class SmsSender {

    @Inject protected SmsCache smsCache;
    @Inject protected LocationSource locationSource;
    @Inject protected ZoneCalculator zoneCalculator;

    private static final String TEXT_KEY = "pl.org.seva.texter.Text";
    private static final String DISTANCE_KEY = "pl.org.seva.texter.Distance";
    private static final String MINUTES_KEY = "pl.org.seva.texter.Minutes";
    private static final String DIRECTION_KEY = "pl.org.seva.texter.Direction";
    private static final String SPEED_KEY = "pl.org.seva.texter.Speed";

	private static final String SENT = "SMS_SENT";
    private static final String DELIVERED = "SMS_DELIVERED";

	private SharedPreferences preferences;
    private String speedUnit;
    private WeakReference<Context> weakContext;

	private final android.telephony.SmsManager smsManager;

    private final PublishSubject<Object> smsSendingSubject;
    private final PublishSubject<Object> smsSentSubject;

    private double lastSentDistance;
	
	private boolean initialized;

    private Sms lastSentLocation;
    private DistanceZone zone;


    @Inject
    protected SmsSender() {
		smsManager = android.telephony.SmsManager.getDefault();
        smsSendingSubject = PublishSubject.create();
        smsSentSubject = PublishSubject.create();
	}

	public void init(final Context context, String speedUnit) {
		if (initialized) {
			return;
		}
        this.speedUnit = speedUnit;
		preferences = PreferenceManager.getDefaultSharedPreferences(context);
        weakContext = new WeakReference<>(context);
		initialized = true;
	}

    public void onDistanceChanged() {
        double distance = locationSource.getDistance();
        double speed = locationSource.getSpeed();

        long time = System.currentTimeMillis();
        int direction = 0; // alternatively (int) Math.signum(this.distance - distance);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        int minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60;
        minutes += calendar.get(Calendar.MINUTE);

        Sms location = new Sms();
        location.setDistance(distance);
        location.setDirection(direction);
        location.setTime(minutes);
        location.setSpeed(speed);

        synchronized (this) {
            DistanceZone zone = zoneCalculator.calculateZone(distance);
            if (this.zone == null) {
                this.zone = zone;
            }
            else if (zone.getMin() != this.zone.getMin() &&
                    zone.getCounter() >= Constants.SMS_TRIGGER &&
                    zone.getDelay() >= Constants.TIME_IN_ZONE) {
                if (this.zone.getMin() > zone.getMin()) {
                    direction = -1;
                }
                else {
                    direction = 1;
                }
                location.setDirection(direction);  // calculated specifically for calculateZone border

                if ((direction == 1 ? zone.getMin() : zone.getMax()) <= getMaxSentDistance()) {
                    send(location);
                }
                this.zone = zone;
            }
        }
    }

    public synchronized void resetZones() {
        zoneCalculator.clearCache();
        zone = null;
    }

	public Observable<Object> smsSendingListener() {
        return smsSendingSubject.hide();
    }

    public Observable<Object> smsSentListener() {
        return smsSentSubject.hide();
    }

	private String getPhoneNumber() {
		String numberStr = preferences.getString(SettingsActivity.SMS_NUMBER, "");
		return numberStr.length() > 0 ? numberStr : "0";
	}

    protected int getMaxSentDistance() {
        String numberStr = preferences.getString(SettingsActivity.MAXIMUM_DISTANCE, "");
        return numberStr.length() > 0 ? Integer.valueOf(numberStr) : 0;
    }

    private void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        Context context = weakContext.get();
        if (context != null) {
            context.registerReceiver(receiver, filter);
        }
    }

    private void unregisterReceiver(BroadcastReceiver receiver) {
        Context context = weakContext.get();
        if (context != null) {
            context.unregisterReceiver(receiver);
        }
    }

	private void registerBroadcastReceiver(String id) {
        // When the SMS has been sent.
        registerReceiver(new SmsSentReceiver(), new IntentFilter(SENT + id));
	}
	
	public void send(Sms model) {
        if (!isTextingEnabled()) {
            return;
        }
        if (this.lastSentLocation != null && this.lastSentLocation.equals(model)) {
            return;
        }
        this.lastSentLocation = model;

        if (!isCorrectPhoneNumberSet()) {
            return;
        }
        checkInit();
        double distance = model.getDistance();
        @SuppressLint("DefaultLocale")
        String distanceStr = String.format("%.2f", distance) + model.getSign();
        StringBuilder smsBuilder = new StringBuilder(distanceStr + " km");
        if (isSpeedIncluded()) {
            String speedStr = StringUtils.getSpeedString(model.getSpeed(), speedUnit);
            smsBuilder.append(speedStr.startsWith("0 ") ? "" : ", " + speedStr);
        }
        if (isTimeIncluded()) {
            Calendar now = Calendar.getInstance();
            String minuteStr = Integer.toString(now.get(Calendar.MINUTE));
            if (minuteStr.length() == 1) {
                minuteStr = "0" + minuteStr;
            }
            String timeStr = now.get(Calendar.HOUR_OF_DAY) + ":" + minuteStr;
            smsBuilder.append(" (").append(timeStr).append(")");
        }
        if (isLocationIncluded()) {
            smsBuilder.append(" ").append(locationSource.getLocationUrl());
        }
        @SuppressLint("DefaultLocale")
        String intentDistanceStr = String.format("%.1f", distance) + model.getSign() + " km";
        String smsStr = smsBuilder.toString();
        send(smsStr, intentDistanceStr, model);
        lastSentDistance = distance;
    }

    protected boolean isCorrectPhoneNumberSet() {
        return !getPhoneNumber().equals("0");
    }

    public double getLastSentDistance() {
        return lastSentDistance;
    }

    private void send(String text, String intentText, Sms location) {
        String id = UUID.randomUUID().toString();
		Intent sentIntent = new Intent(SENT + id);
		sentIntent.putExtra(TEXT_KEY, intentText);
        sentIntent.putExtra(DISTANCE_KEY, location.getDistance());
        sentIntent.putExtra(MINUTES_KEY, location.getMinutes());
        sentIntent.putExtra(DIRECTION_KEY, location.getDirection());
        sentIntent.putExtra(SPEED_KEY, location.getSpeed());

		Intent deliveredIntent = new Intent(DELIVERED + id);
		deliveredIntent.putExtra(TEXT_KEY, intentText);

        smsSendingSubject.onNext(0);

        Context context = weakContext.get();
        if (context != null) {
            PendingIntent sentPI = PendingIntent.getBroadcast(context, 0, sentIntent, 0);
            PendingIntent deliveredPI = PendingIntent.getBroadcast(context, 0, deliveredIntent, 0);
            registerBroadcastReceiver(id);
            try {
                sendTextMessage(text, sentPI, deliveredPI);
            }
            catch (SecurityException ignore) {
                // Ignore, as may indicate the app has no permission to send SMS.
            }
        }
	}

	protected void sendTextMessage(
	        String text,
            PendingIntent sentIntent,
            PendingIntent deliveredIntent) throws SecurityException {
        smsManager.sendTextMessage(getPhoneNumber(), null, text, sentIntent, deliveredIntent);
    }
	
	private void checkInit() {
		if (!initialized) {
			throw new IllegalStateException("SMS not initialized");
		}
	}

    private boolean isSpeedIncluded() {
        return preferences.getBoolean(SettingsActivity.INCLUDE_SPEED, false);
    }

    private boolean isLocationIncluded() {
        return preferences.getBoolean(SettingsActivity.INCLUDE_LOCATION, false);
    }

    private boolean isTimeIncluded() {
        return preferences.getBoolean(SettingsActivity.INCLUDE_TIME, false);
    }

    public boolean isTextingEnabled() {
        return preferences.getBoolean(SettingsActivity.SMS_ENABLED, false);
    }

    public boolean needsPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    private class SmsSentReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String text = intent.getStringExtra(TEXT_KEY);
            Sms location = new Sms().
                    setDistance(intent.getDoubleExtra(DISTANCE_KEY, 0.0)).
                    setTime(intent.getIntExtra(MINUTES_KEY, 0)).
                    setDirection(intent.getIntExtra(DIRECTION_KEY, 0)).
                    setSpeed(intent.getDoubleExtra(SPEED_KEY, 0.0));
            switch (getResultCode())
            {
                case Activity.RESULT_OK:
                    StringBuilder sentBuilder = new StringBuilder(context.getString(R.string.sent));
                    int length = Toast.LENGTH_SHORT;
                    if (text != null) {
                        sentBuilder.append(": ").append(text);
                        length = Toast.LENGTH_SHORT;
                    }
                    Toast.makeText(context, sentBuilder.toString(), length).show();
                    if (location != null) {
                        smsCache.add(location);
                        smsSentSubject.onNext(0);
                    }
                    break;
                case android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    Toast.makeText(
                            context,
                            context.getString(R.string.generic_failure),
                            Toast.LENGTH_SHORT).show();
                    break;
                case android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE:
                    Toast.makeText(
                            context,
                            context.getString(R.string.no_service),
                            Toast.LENGTH_SHORT).show();
                    break;
                case android.telephony.SmsManager.RESULT_ERROR_NULL_PDU:
                    Toast.makeText(context, "Null PDU", Toast.LENGTH_SHORT).show();
                    break;
                case android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF:
                    Toast.makeText(
                            context,
                            context.getString(R.string.radio_off),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
            unregisterReceiver(this);
        }
    }
}
