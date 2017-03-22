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

package pl.org.seva.texter.manager;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.maps.model.LatLng;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import pl.org.seva.texter.activity.SettingsActivity;
import pl.org.seva.texter.preference.HomeLocationPreference;
import pl.org.seva.texter.utils.Calculator;
import pl.org.seva.texter.utils.Constants;

@Singleton
public class GpsManager implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener {

    @SuppressWarnings("WeakerAccess")
    @Inject protected LastLocationManager lastLocationManager;
    @SuppressWarnings("WeakerAccess")
    @Inject protected TimerManager timerManager;

    @SuppressWarnings("WeakerAccess")
    @Inject public GpsManager() {
    }

    private static final String TAG = GpsManager.class.getSimpleName();

    private static final double ACCURACY_THRESHOLD = 100.0;  // [m]

    /** Minimal distance (in meters) that will be counted between two subsequent updates. */
    private static final float MIN_DISTANCE = 5.0f;

    private static final int SIGNIFICANT_TIME_LAPSE = 1000 * 60 * 2;

    private SharedPreferences preferences;

    private GoogleApiClient googleApiClient;
    private LocationRequest locationRequest;

    private final PublishSubject<Object> distanceSubject = PublishSubject.create();
    private final PublishSubject<Object> homeChangedSubject = PublishSubject.create();
    private final PublishSubject<Object> providerEnabledSubject = PublishSubject.create();
    private final PublishSubject<Object> providerDisabledSubject = PublishSubject.create();
    private final PublishSubject<Object> locationChangedSubject = PublishSubject.create();

    private boolean initialized;
    private boolean connected;
    private boolean paused;

    private double homeLat;
    private double homeLon;
    private long time;

    /**
     * @return minimum time between two subsequent updates from one provider (in millis)
     */
    @SuppressWarnings("SameReturnValue")
    private long getUpdateFrequency() {
        return Constants.LOCATION_UPDATE_FREQUENCY;
    }

    private void requestLocationUpdates(Context context) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {
            googleApiClient.connect();
        }
    }

    public void onHomeLocationChanged() {
        updateDistance();
        String homeLocation = preferences.
                getString(SettingsActivity.HOME_LOCATION, Constants.DEFAULT_HOME_LOCATION);
        homeLat = HomeLocationPreference.parseLatitude(homeLocation);
        homeLon = HomeLocationPreference.parseLongitude(homeLocation);
        if (lastLocationManager.getLocation() != null) {
            lastLocationManager.setDistance(Calculator.calculateDistance(
                    homeLat,
                    homeLon,
                    lastLocationManager.getLocation().getLatitude(),
                    lastLocationManager.getLocation().getLongitude()));
        }
        homeChangedSubject.onNext(0);
    }

    /**
     * Initializes the GPS.
     *
     * Actions taken depend on whether the app possesses the permission. If not, has to be
     * called again.
     *
     * @param activity the calling activity
     * @return true if actions requiring the permission have been performed
     */
    public boolean init(Activity activity) {
        boolean granted = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED;
        if (initialized) {
            return granted;
        }
        preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(activity)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        activity.getApplicationContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                locationSettingsChanged();
            }
        }, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));

        onHomeLocationChanged();

        if (granted) {
            requestLocationUpdates(activity);
            initialized = true;
        }

        return granted;
    }

    public Observable<Object> distanceChangedListener() {
        return distanceSubject.hide();
    }

    public Observable<Object> homeChangedListener() {
        return homeChangedSubject.hide();
    }

    public Observable<Object> locationChangedListener() {
        return locationChangedSubject.hide();
    }

    public Observable<Object> providerEnabledListener() {
        return providerEnabledSubject.hide();
    }

    public Observable<Object> providerDisabledListener() {
        return providerDisabledSubject.hide();
    }

    private static boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > SIGNIFICANT_TIME_LAPSE;
        boolean isSignificantlyOlder = timeDelta < -SIGNIFICANT_TIME_LAPSE;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        }
        else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        }
        else if (isNewer && !isSignificantlyLessAccurate) {
            return true;
        }
        return false;
    }

    private double getHomeLat() {
        return homeLat;
    }

    private double getHomeLng() {
        return homeLon;
    }

    public LatLng getHomeLatLng() {
        return new LatLng(getHomeLat(), getHomeLng());
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location.getAccuracy() >= ACCURACY_THRESHOLD) {
            return;
        }
        if (!GpsManager.isBetterLocation(location, lastLocationManager.getLocation())) {
            return;
        }
        timerManager.reset();
        long time = System.currentTimeMillis();
        lastLocationManager.setSpeed(
                calculateSpeedOrReturnZero(lastLocationManager.getLocation(), location, time - this.time));
        lastLocationManager.setLocation(location);
        lastLocationManager.setDistance(calculateCurrentDistance());  // distance in kilometres
        this.time = time;
        distanceSubject.onNext(0);
        locationChangedSubject.onNext(0);
    }

    private void updateDistance() {
        if (lastLocationManager.getLocation() == null) {
            return;
        }
        lastLocationManager.setDistance(calculateCurrentDistance());
    }

    private double calculateCurrentDistance() {
        return Calculator.calculateDistance(  // distance in kilometres
                lastLocationManager.getLocation().getLatitude(),
                lastLocationManager.getLocation().getLongitude(),
                getHomeLat(),
                getHomeLng());
    }

    private double calculateSpeedOrReturnZero(Location loc1, Location loc2, long time) {
        if (loc1 == null || loc2 == null || this.time == 0 || time == 0 ||
                loc1.getLatitude() == loc2.getLatitude() &&
                        loc1.getLongitude() == loc2.getLongitude()) {
            return 0.0;
        }
        if (time == 0.0) {
            return 0.0;
        }
        return Calculator.calculateSpeed(loc1, loc2, time);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        connected = true;

        if (lastLocationManager.getLocation() == null) {
            //noinspection MissingPermission
            lastLocationManager.setLocation(LocationServices.FusedLocationApi.getLastLocation(googleApiClient));
        }

        long updateFrequency = getUpdateFrequency();
        LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(updateFrequency)
                .setSmallestDisplacement(MIN_DISTANCE);

        //noinspection MissingPermission
        LocationServices.FusedLocationApi
                .requestLocationUpdates(googleApiClient, locationRequest, this);
        callProviderListener();
    }

    public void callProviderListener() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);
        PendingResult<LocationSettingsResult> pendingResult =
                LocationServices.SettingsApi.checkLocationSettings(
                        googleApiClient,
                        builder.build());
        pendingResult.setResultCallback(locationSettingsResult -> {
            connected = locationSettingsResult.getLocationSettingsStates().isLocationUsable();
            if (connected) {
                providerEnabledSubject.onNext(0);
            }
            else {
                providerDisabledSubject.onNext(0);
            }
        });
    }

    public void pauseUpdates() {
        if (paused || googleApiClient == null) {
            return;
        }
        Log.d(TAG, "Pause updates.");
        LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);

        paused = true;
    }

    public void resumeUpdates(Context context) {
        if (!paused || ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Log.d(TAG, "Resume updates.");
        //noinspection MissingPermission
        LocationServices.FusedLocationApi.
                requestLocationUpdates(googleApiClient, locationRequest, this);

        paused = false;
    }

    private void locationSettingsChanged() {
        if (locationRequest == null) {
            return;
        }

        callProviderListener();
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }
}
