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
 */

package pl.org.seva.texter.mock

import javax.inject.Singleton

import dagger.Module
import dagger.Provides
import pl.org.seva.texter.source.LocationSource
import pl.org.seva.texter.presenter.SmsSender
import pl.org.seva.texter.presenter.SmsHistory
import pl.org.seva.texter.source.ActivityRecognitionSource
import pl.org.seva.texter.presenter.Timer
import pl.org.seva.texter.presenter.ZoneCalculator

@Module
internal class MockTexterModule {

    @Provides
    @Singleton
    fun provideGpsManager(timer: Timer): LocationSource {
        return MockLocationSource(timer)
    }

    @Provides
    @Singleton
    fun provideSmsManager(locationSource: LocationSource, smsHistory: SmsHistory, zoneCalculator: ZoneCalculator): SmsSender {
        return MockSmsSender(locationSource, smsHistory, zoneCalculator)
    }

    @Provides
    @Singleton
    fun provideActivityRecognitionSource() : ActivityRecognitionSource {
        return MockActivityRecognitionSource()
    }
}
