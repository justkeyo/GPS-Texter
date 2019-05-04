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

package pl.org.seva.texter.view.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import androidx.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import pl.org.seva.texter.R;
import pl.org.seva.texter.view.fragment.PhoneNumberFragment;

public class NumberPreference extends DialogPreference {

    private PhoneNumberFragment numberFragment;
    private String number;

    public NumberPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setDialogLayoutResource(R.layout.dialog_number);
        setDialogIcon(null);
    }

    @Override
    protected View onCreateDialogView() {
        View result = super.onCreateDialogView();
        numberFragment = (PhoneNumberFragment)
                ((FragmentActivity) getContext()).
                        getSupportFragmentManager().findFragmentById(R.id.number_fragment);
        numberFragment.setNumber(number);

        return result;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final SavedState myState = new SavedState(superState);

        if (numberFragment != null) {
            number = numberFragment.toString();
            myState.number = number;
            // If called after onSaveInstanceState, throws:
            // java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState
            ((FragmentActivity) getContext()).
                    getSupportFragmentManager().beginTransaction().remove(numberFragment).commit();
            numberFragment = null;
        }

        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        // Check whether we saved the state in onSaveInstanceState
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save the state, so call superclass
            super.onRestoreInstanceState(state);
            return;
        }

        // Cast state to custom BaseSavedState and pass to superclass
        SavedState myState = (SavedState) state;
        number = myState.number;

        super.onRestoreInstanceState(myState.getSuperState());
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return "";
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        number = restorePersistedValue ? getPersistedString("") : defaultValue.toString();
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (numberFragment != null) {
            if (positiveResult) {
                number = numberFragment.toString();
                persistString(number);
            }
            else {
                number = getPersistedString("");
            }
            ((FragmentActivity) getContext()).
                    getSupportFragmentManager().beginTransaction().remove(numberFragment).commit();
        }
        super.onDialogClosed(positiveResult);
    }

    private static class SavedState extends BaseSavedState {
        private String number;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public SavedState(Parcel source) {
            super(source);
            number = source.readString();
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(number);
        }

        // Standard creator object using an instance of this class
        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {

                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}
