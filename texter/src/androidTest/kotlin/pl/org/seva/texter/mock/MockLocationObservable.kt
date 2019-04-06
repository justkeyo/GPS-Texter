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

package pl.org.seva.texter.mock

import android.location.Location

import java.util.concurrent.TimeUnit

import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import pl.org.seva.texter.TestConstants
import pl.org.seva.texter.movement.LocationObservable
import pl.org.seva.texter.settings.HomeLocationPreference
import pl.org.seva.texter.main.Constants

class MockLocationObservable : LocationObservable() {

    private var ticks = -1

    init {
        val defaultHomeLocation = Constants.DEFAULT_HOME_LOCATION
        homeLat = HomeLocationPreference.parseLatitude(defaultHomeLocation)
        homeLng = HomeLocationPreference.parseLongitude(defaultHomeLocation)

        Observable.timer(1, TimeUnit.SECONDS, Schedulers.io())
                .doOnNext {
                    val location = Location(MOCK_PROVIDER_NAME)
                    location.accuracy = 1.0f
                    location.latitude = homeLatLng.latitude + ticks * TestConstants.LATITUDE_STEP
                    location.longitude = homeLatLng.longitude
                    location.time = System.currentTimeMillis()
                    onLocationChanged(location)
                    ticks++
                }
                .repeat()
                .subscribe()
    }

    override fun request() = Unit
    override fun removeRequest() = Unit

    companion object {

        private const val MOCK_PROVIDER_NAME = "Mock provider"
    }
}
