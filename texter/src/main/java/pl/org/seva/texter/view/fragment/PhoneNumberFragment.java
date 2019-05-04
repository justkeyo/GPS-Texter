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

package pl.org.seva.texter.view.fragment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import androidx.databinding.DataBindingUtil;
import android.os.Bundle;
import android.provider.ContactsContract;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.core.content.ContextCompat;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import pl.org.seva.texter.R;
import pl.org.seva.texter.databinding.FragmentNumberBinding;

public class PhoneNumberFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final int CONTACTS_QUERY_ID = 0;
    private static final int DETAILS_QUERY_ID = 1;

    private Toast toast;

    private final static String[] FROM_COLUMNS = {
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
    };

    private static final String[] CONTACTS_PROJECTION = {  // SELECT
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
    };

    private static final String CONTACTS_SELECTION =  // FROM
            ContactsContract.Contacts.HAS_PHONE_NUMBER + " = ?";

    private static final String CONTACTS_SORT =  // ORDER_BY
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY;

    private static final String[] DETAILS_PROJECTION = {  // SELECT
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
    };

    private static final String DETAILS_SORT =  // ORDER_BY
            ContactsContract.CommonDataKinds.Phone._ID;

    private static final String DETAILS_SELECTION =  // WHERE
            ContactsContract.Data.LOOKUP_KEY + " = ?";



    // The column index for the LOOKUP_KEY column
    private static final int CONTACT_KEY_INDEX = 1;
    private static final int CONTACT_NAME_INDEX = 2;
    private static final int DETAILS_NUMBER_INDEX = 1;

    private final static int[] TO_IDS = {
            android.R.id.text1
    };

    private boolean contactsEnabled;
    private String contactKey;
    private String contactName;

    private SimpleCursorAdapter adapter;
    private EditText number;

    @Nullable
    @Override
    public View onCreateView
            (LayoutInflater inflater,
             @Nullable ViewGroup container,
             @Nullable Bundle savedInstanceState) {
        FragmentNumberBinding binding =
                DataBindingUtil.inflate(inflater, R.layout.fragment_number, container, false);
        number = binding.number;

        contactsEnabled = ContextCompat.checkSelfPermission(
                getActivity(),
                Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED;

        ListView contacts = binding.contacts;
        if (!contactsEnabled) {
            contacts.setVisibility(View.GONE);
        }
        else {
            contacts.setOnItemClickListener(this::onItemClick);
            adapter = new SimpleCursorAdapter(
                    getActivity(),
                    R.layout.item_contact,
                    null,
                    FROM_COLUMNS,
                    TO_IDS,
                    0);
            contacts.setAdapter(adapter);
        }

        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(CONTACTS_QUERY_ID, null, this);
    }

    @Override
    public String toString() {
        return number.getText().toString();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (!contactsEnabled) {
            return null;
        }
        switch (id) {
            case CONTACTS_QUERY_ID:
                String[] contactsSelectionArgs = {"1",};
                return new CursorLoader(
                        getActivity(),
                        ContactsContract.Contacts.CONTENT_URI,
                        CONTACTS_PROJECTION,
                        CONTACTS_SELECTION,
                        contactsSelectionArgs,
                        CONTACTS_SORT);
            case DETAILS_QUERY_ID:
                String[] detailsSelectionArgs = { contactKey, };
                return new CursorLoader(
                        getActivity(),
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        DETAILS_PROJECTION,
                        DETAILS_SELECTION,
                        detailsSelectionArgs,
                        DETAILS_SORT);
            default:
                return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        switch (loader.getId()) {
            case CONTACTS_QUERY_ID:
                adapter.swapCursor(data);
                break;
            case DETAILS_QUERY_ID:
                final List<String> numbers = new ArrayList<>();
                while (data.moveToNext()) {
                    String n = data.getString(DETAILS_NUMBER_INDEX);
                    if (!numbers.contains(n)) {
                        numbers.add(n);
                    }
                }
                data.close();
                if (numbers.size() == 1) {
                    this.number.setText(numbers.get(0));
                }
                else if (numbers.isEmpty()) {
                    toast = Toast.makeText(
                            getContext(),
                            R.string.no_number,
                            Toast.LENGTH_SHORT);
                    toast.show();
                }
                else {
                    String[] items = new String[numbers.size()];
                    numbers.toArray(items);
                    new AlertDialog.Builder(getActivity()).
                            setItems(items, (dialog, which) -> {
                                dialog.dismiss();
                                number.setText(numbers.get(which));
                            }).
                            setTitle(contactName).
                            setCancelable(true).
                            setNegativeButton(
                                    android.R.string.cancel,
                                    (dialog, which) -> dialog.dismiss()).show();
                }
                break;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        switch (loader.getId()) {
            case CONTACTS_QUERY_ID:
                adapter.swapCursor(null);
                break;
            case DETAILS_QUERY_ID:
                break;
        }
    }

    private void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (toast != null) {
            toast.cancel();
        }
        Cursor cursor = ((SimpleCursorAdapter) parent.getAdapter()).getCursor();
        cursor.moveToPosition(position);
        contactKey = cursor.getString(CONTACT_KEY_INDEX);
        contactName = cursor.getString(CONTACT_NAME_INDEX);

        getLoaderManager().restartLoader(DETAILS_QUERY_ID, null, this);
    }

    public void setNumber(String number) {
        this.number.setText(number);
    }
}
