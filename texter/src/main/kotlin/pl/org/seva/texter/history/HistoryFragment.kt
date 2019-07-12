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

package pl.org.seva.texter.history

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_history.*

import pl.org.seva.texter.R
import pl.org.seva.texter.sms.smsSender

class HistoryFragment : Fragment() {

    private lateinit var adapter: HistoryAdapter
    private var scrollToBottom: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createSubscription()
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?): View =
            inflater.inflate(R.layout.fragment_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recycler_view.setHasFixedSize(true)
        recycler_view.layoutManager = LinearLayoutManager(context)
        adapter = HistoryAdapter(requireActivity(), smsHistory.list)
        recycler_view.adapter = adapter
        recycler_view.addItemDecoration(HistoryAdapter.DividerItemDecoration(requireActivity()))
        recycler_view.clearOnScrollListeners()
        recycler_view.addOnScrollListener(OnScrollListener())
        scrollToBottom = true
    }

    private fun createSubscription() = smsSender.addSmsSentListener(lifecycle) { update() }

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

        fun newInstance() = HistoryFragment()
    }
}
