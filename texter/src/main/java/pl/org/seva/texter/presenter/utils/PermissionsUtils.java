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

import android.content.pm.PackageManager;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

@Singleton
public class PermissionsUtils {

    public static final int PERMISSION_ACCESS_FINE_LOCATION_REQUEST = 0;
    public static final int PERMISSION_READ_CONTACTS_REQUEST = 1;

    private final PublishSubject<String> permissionGrantedSubject = PublishSubject.create();
    private final PublishSubject<String> permissionDeniedSubject = PublishSubject.create();

    private final List<String> rationalesShown = new ArrayList<>();

    @Inject
    PermissionsUtils() {
    }

    public Observable<String> permissionGrantedListener() {
        return permissionGrantedSubject.hide();
    }

    public boolean isRationaleNeeded(String permission) {
        return !rationalesShown.contains(permission);
    }

    @SuppressWarnings("SameParameterValue")
    public void onRationaleShown(String permission) {
        if (isRationaleNeeded(permission)) {
            rationalesShown.add(permission);
        }
    }

    public Observable<String> permissionDeniedListener() {
        return permissionDeniedSubject.hide();
    }

    private void onPermissionGranted(String permission) {
        permissionGrantedSubject.onNext(permission);
        permissionGrantedSubject.onComplete();
    }

    private void onPermissionDenied(String permission) {
        permissionDeniedSubject.onNext(permission);
        permissionDeniedSubject.onComplete();
    }

    public void onRequestPermissionsResult(
            @NonNull String permissions[],
            @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            for (String permission : permissions) {
                onPermissionGranted(permission);
            }
        }
        else {
            for (String permission : permissions) {
                onPermissionDenied(permission);
            }
        }
    }
}
