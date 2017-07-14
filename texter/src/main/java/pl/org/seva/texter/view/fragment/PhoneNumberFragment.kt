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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.ContactsContract
import android.support.v4.app.DialogFragment
import android.support.v4.app.LoaderManager
import android.support.v4.content.ContextCompat
import android.support.v4.content.CursorLoader
import android.support.v4.content.Loader
import android.support.v4.widget.SimpleCursorAdapter
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast

import java.util.ArrayList

import pl.org.seva.texter.R
import pl.org.seva.texter.Constants
import pl.org.seva.texter.view.activity.SettingsActivity

class PhoneNumberFragment : DialogFragment(), LoaderManager.LoaderCallbacks<Cursor> {

    private var toast: Toast? = null

    private var contactsEnabled: Boolean = false
    private lateinit var contactKey: String
    private var contactName: String? = null

    private var adapter: SimpleCursorAdapter? = null
    private lateinit var number: EditText

    @SuppressLint("InflateParams")
    private fun phoneNumberDialogView(inflater: LayoutInflater) : View {

        val v = inflater.inflate(R.layout.fragment_number, null)

        number = v.findViewById<EditText>(R.id.number)

        contactsEnabled = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

        val contacts = v.findViewById<ListView>(R.id.contacts)
        if (!contactsEnabled) {
            contacts.visibility = View.GONE
        } else {
            contacts.onItemClickListener = AdapterView.OnItemClickListener {
                parent, _, position, _ -> this.onItemClick(parent, position) }
            adapter = SimpleCursorAdapter(
                    activity,
                    R.layout.item_contact, null,
                    FROM_COLUMNS,
                    TO_IDS,
                    0)
            contacts.adapter = adapter
        }

        return v
    }

    private val persistedString: String
        get() = PreferenceManager.getDefaultSharedPreferences(activity)
                .getString(SettingsActivity.PHONE_NUMBER, Constants.DEFAULT_PHONE_NUMBER)

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        // Get the layout inflater
        val inflater = activity.layoutInflater

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(phoneNumberDialogView(inflater))
                // Add action buttons
                .setPositiveButton(android.R.string.ok) { dialog, _ -> onOkPressedInDialog(dialog) }
                .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
        setNumber(persistedString)
        return builder.create()
    }

    private fun onOkPressedInDialog(d: DialogInterface) {
        persistString(number.text.toString())
        d.dismiss()
    }

    private fun persistString(`val`: String) {
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
                .putString(SettingsActivity.PHONE_NUMBER, `val`).apply()
    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        loaderManager.initLoader(CONTACTS_QUERY_ID, null, this)
    }

    override fun toString(): String {
        return number.text.toString()
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        if (!contactsEnabled) {
            return null
        }
        when (id) {
            CONTACTS_QUERY_ID -> {
                val contactsSelectionArgs = arrayOf("1")
                return CursorLoader(
                        activity,
                        ContactsContract.Contacts.CONTENT_URI,
                        CONTACTS_PROJECTION,
                        CONTACTS_SELECTION,
                        contactsSelectionArgs,
                        CONTACTS_SORT)
            }
            DETAILS_QUERY_ID -> {
                val detailsSelectionArgs = arrayOf(contactKey)
                return CursorLoader(
                        activity,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        DETAILS_PROJECTION,
                        DETAILS_SELECTION,
                        detailsSelectionArgs,
                        DETAILS_SORT)
            }
            else -> return null
        }
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor) {
        when (loader.id) {
            CONTACTS_QUERY_ID -> adapter!!.swapCursor(data)
            DETAILS_QUERY_ID -> {
                val numbers = ArrayList<String>()
                while (data.moveToNext()) {
                    val n = data.getString(DETAILS_NUMBER_INDEX)
                    if (!numbers.contains(n)) {
                        numbers.add(n)
                    }
                }
                data.close()
                if (numbers.size == 1) {
                    this.number.setText(numbers[0])
                } else if (numbers.isEmpty()) {
                    toast = Toast.makeText(
                            context,
                            R.string.no_number,
                            Toast.LENGTH_SHORT)
                    toast!!.show()
                } else {
                    val items = numbers.toTypedArray()
                    AlertDialog.Builder(activity).setItems(items) { dialog, which ->
                        dialog.dismiss()
                        number.setText(numbers[which])
                    }.setTitle(contactName).setCancelable(true).setNegativeButton(
                            android.R.string.cancel)
                            { dialog, _ -> dialog.dismiss() }.show()
                }
            }
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        when (loader.id) {
            CONTACTS_QUERY_ID -> adapter!!.swapCursor(null)
            DETAILS_QUERY_ID -> {
            }
        }
    }

    private fun onItemClick(parent: AdapterView<*>, position: Int) {
        toast?.cancel()
        val cursor = (parent.adapter as SimpleCursorAdapter).cursor
        cursor.moveToPosition(position)
        contactKey = cursor.getString(CONTACT_KEY_INDEX)
        contactName = cursor.getString(CONTACT_NAME_INDEX)

        loaderManager.restartLoader(DETAILS_QUERY_ID, null, this)
    }

    fun setNumber(number: String?) {
        this.number.setText(number)
    }

    companion object {

        private val CONTACTS_QUERY_ID = 0
        private val DETAILS_QUERY_ID = 1

        private val FROM_COLUMNS = arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)

        private val CONTACTS_PROJECTION = arrayOf( // SELECT
                ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.HAS_PHONE_NUMBER)

        private val CONTACTS_SELECTION = // FROM
                ContactsContract.Contacts.HAS_PHONE_NUMBER + " = ?"

        private val CONTACTS_SORT = // ORDER_BY
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY

        private val DETAILS_PROJECTION = arrayOf( // SELECT
                ContactsContract.CommonDataKinds.Phone._ID, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.LABEL)

        private val DETAILS_SORT = // ORDER_BY
                ContactsContract.CommonDataKinds.Phone._ID

        private val DETAILS_SELECTION = // WHERE
                ContactsContract.Data.LOOKUP_KEY + " = ?"


        // The column index for the LOOKUP_KEY column
        private val CONTACT_KEY_INDEX = 1
        private val CONTACT_NAME_INDEX = 2
        private val DETAILS_NUMBER_INDEX = 1

        private val TO_IDS = intArrayOf(android.R.id.text1)
    }
}
