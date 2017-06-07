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

import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import javax.inject.Inject

import pl.org.seva.texter.mock.MockSmsSender
import pl.org.seva.texter.view.activity.MainActivity
import pl.org.seva.texter.mock.MockGraph
import pl.org.seva.texter.presenter.utils.SmsSender

import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.matcher.ViewMatchers.isRoot
import junit.framework.Assert.assertEquals
import pl.org.seva.texter.action.DelayAction

@RunWith(AndroidJUnit4::class)
class LocationTest {

    @Inject
    lateinit var smsSender: SmsSender

    // https://stackoverflow.com/questions/29945087/kotlin-and-new-activitytestrule-the-rule-must-be-public
    @get:Rule
    val activityRule = ActivityTestRule(
            MainActivity::class.java,
            true,
            true)

    @Before
    fun setUp() {
        val graph = (activityRule.activity.application as TexterApplication).graph as MockGraph
        graph.inject(this)
    }

    @Test
    fun test() {
        onView(isRoot()).perform(DelayAction.delay(100))
        for (i in 0 until DURATION_IN_SECONDS) {
            onView(isRoot()).perform(DelayAction.delay(1000))
        }
        assertEquals(TestConstants.EXPECTED_MESSAGES_SENT, (smsSender as MockSmsSender).messagesSent)
    }

    companion object {

        private val DURATION_IN_SECONDS = 50
    }
}