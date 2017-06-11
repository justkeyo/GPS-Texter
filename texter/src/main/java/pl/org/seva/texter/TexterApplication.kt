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

package pl.org.seva.texter

import android.content.Intent
import android.content.pm.PackageManager
import android.support.multidex.MultiDexApplication
import pl.org.seva.texter.presenter.dagger.DaggerGraph

import javax.inject.Inject

import pl.org.seva.texter.presenter.dagger.Graph
import pl.org.seva.texter.presenter.source.ActivityRecognitionSource
import pl.org.seva.texter.presenter.source.LocationSource
import pl.org.seva.texter.presenter.service.TexterService

open class TexterApplication : MultiDexApplication() {

    @Inject
    lateinit var locationSource: LocationSource
    @Inject
    lateinit var activityRecognitionSource: ActivityRecognitionSource

    lateinit var graph: Graph

    private var isServiceRunning = false

    override fun onCreate() {
        super.onCreate()
        graph = createGraph()
        graph.inject(this)
        locationSource.initPreferences(this)
        activityRecognitionSource.initWithContext(this)
    }

    open fun hardwareCanSendSms(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }



    protected open fun createGraph(): Graph {
        return DaggerGraph.create()
    }

    fun startService() {
        if (isServiceRunning) {
            return
        }
        startService(Intent(baseContext, TexterService::class.java))
        isServiceRunning = true
    }

    open fun stopService() {
        if (!isServiceRunning) {
            return
        }
        stopService(Intent(baseContext, TexterService::class.java))
        isServiceRunning = false
    }
}
