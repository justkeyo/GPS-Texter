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

package pl.org.seva.texter.view.fragment

import android.arch.lifecycle.LifecycleFragment
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.support.v7.widget.RecyclerView
import com.github.salomonbrys.kodein.conf.KodeinGlobalAware
import com.github.salomonbrys.kodein.instance
import kotlinx.android.synthetic.main.fragment_history.*

import pl.org.seva.texter.R
import pl.org.seva.texter.presenter.SmsHistory
import pl.org.seva.texter.view.adapter.HistoryAdapter
import pl.org.seva.texter.presenter.SmsSender

class HistoryFragment: LifecycleFragment(), KodeinGlobalAware {

    val smsHistory: SmsHistory = instance()
    val smsSender: SmsSender = instance()

    private lateinit var adapter: HistoryAdapter
    private var scrollToBottom: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createSubscription()
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler_view.setHasFixedSize(true)
        recycler_view.layoutManager = LinearLayoutManager(context)
        adapter = HistoryAdapter(activity, smsHistory.list)
        recycler_view.adapter = adapter
        recycler_view.addItemDecoration(HistoryAdapter.DividerItemDecoration(activity))
        recycler_view.clearOnScrollListeners()
        recycler_view.addOnScrollListener(OnScrollListener())
        scrollToBottom = true
    }

    private fun createSubscription() {
         smsSender.addSmsSentListener(lifecycle) { update() }
    }

    override fun onResume() {
        super.onResume()
        update()
    }

    private fun update() {
        adapter.notifyDataSetChanged()
        if (scrollToBottom) {
            recycler_view.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private inner class OnScrollListener : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (recyclerView === recycler_view) {
                scrollToBottom = recyclerView.computeVerticalScrollOffset() ==
                        recyclerView.computeVerticalScrollRange() - recyclerView.computeVerticalScrollExtent()
            }
        }
    }

    companion object {

        fun newInstance(): HistoryFragment {
            return HistoryFragment()
        }
    }
}
