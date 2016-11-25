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

package pl.org.seva.texter.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.FragmentManager;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import pl.org.seva.texter.R;
import pl.org.seva.texter.databinding.NavigationFragmentBinding;
import pl.org.seva.texter.listeners.IDistanceChangedListener;
import pl.org.seva.texter.listeners.IHomeChangedListener;
import pl.org.seva.texter.listeners.IPermissionGrantedListener;
import pl.org.seva.texter.managers.GPSManager;
import pl.org.seva.texter.managers.PermissionsManager;

/**
 * Created by hp1 on 21-01-2015.
 */
public class NavigationFragment extends Fragment implements
        IDistanceChangedListener, IHomeChangedListener, IPermissionGrantedListener {

    private TextView distanceTextView;
    private GoogleMap map;
    private boolean animateCamera = true;
    private int mapContainerId;
    private MapFragment mapFragment;

    public static NavigationFragment newInstance() {
        return new NavigationFragment();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            final Bundle savedInstanceState) {
        NavigationFragmentBinding binding =
                DataBindingUtil.inflate(inflater, R.layout.navigation_fragment, container, false);
        distanceTextView = binding.distance;
        GPSManager.getInstance().addDistanceChangedListener(this);
        GPSManager.getInstance().addHomeChangedListener(this);
        show(GPSManager.getInstance().getDistance());

        if (savedInstanceState != null) {
            animateCamera = false;
        }
        MapsInitializer.initialize(getActivity().getApplicationContext());
        mapContainerId = binding.mapContainer.getId();

        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();

        FragmentManager fm = getFragmentManager();
        mapFragment = (MapFragment) fm.findFragmentByTag("map");
        if (mapFragment == null) {
            mapFragment = new MapFragment();
            fm.beginTransaction().add(mapContainerId, mapFragment, "map").commit();
        }

        mapFragment.getMapAsync(googleMap -> {
            map = googleMap;
            if (ContextCompat.checkSelfPermission(
                    getActivity(),
                    Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED) {
                map.setMyLocationEnabled(true);
            }
            else {
                PermissionsManager.getInstance().addPermissionGrantedListener(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        NavigationFragment.this);
            }
            LatLng homeLatLng = GPSManager.getInstance().getHomeLatLng();
            updateHomeLocation(homeLatLng);
            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(homeLatLng).zoom(12).build();
            if (animateCamera) {
                map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            }
            else {
                map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            }
            animateCamera = false;
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        GPSManager.getInstance().removeDistanceChangedListener(this);
        GPSManager.getInstance().removeHomeChangedListener(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mapFragment != null) {
            // see http://stackoverflow.com/questions/7575921/illegalstateexception-can-not-perform-this-action-after-onsaveinstancestate-wit#10261449
            getFragmentManager().beginTransaction().remove(mapFragment).commitAllowingStateLoss();
            mapFragment = null;
        }
        super.onSaveInstanceState(outState);
    }

    private void updateHomeLocation(LatLng home) {
        if (map == null || home == null) {
            return;
        }
        MarkerOptions marker =
                new MarkerOptions().position(home).title(StatsFragment.getHomeString());

        // Changing marker icon
        marker.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE));

        // adding marker
        map.clear();
        map.addMarker(marker);
    }

    private void show(double distance) {
        @SuppressLint("DefaultLocale") String distanceStr = String.format("%.3f km", distance);
        if (distance == 0.0) {
            distanceStr = "0 km";
        }
        distanceTextView.setText(distanceStr);
    }

    @Override
    public void onDistanceChanged() {
        show(GPSManager.getInstance().getDistance());
    }

    @Override
    public void onHomeChanged() {
        updateHomeLocation(GPSManager.getInstance().getHomeLatLng());
    }

    @Override
    public void onPermissionGranted(String permission) {
        if (permission.equals(Manifest.permission.ACCESS_FINE_LOCATION) && map != null) {
            try {
                map.setMyLocationEnabled(true);
            }
            catch (SecurityException ex) {
                // won't happen in the permission granted listener
                ex.printStackTrace();
            }
            PermissionsManager.getInstance().
                    removePermissionGrantedListener(Manifest.permission.ACCESS_FINE_LOCATION, this);
        }
    }
}
