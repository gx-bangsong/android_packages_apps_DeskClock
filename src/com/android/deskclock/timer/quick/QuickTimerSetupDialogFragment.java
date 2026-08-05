/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.deskclock.timer.quick;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.android.deskclock.R;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Dialog that lets the user create a new quick timer preset by entering an optional label and a
 * duration (hours, minutes and seconds). The dialog refuses to close while the input is invalid
 * (non-numeric fields, out-of-range fields or a zero-length duration).
 */
public final class QuickTimerSetupDialogFragment extends DialogFragment {

    /** Callback invoked when a valid preset has been entered. */
    public interface QuickTimerSetupListener {
        void onQuickTimerCreated(long durationMillis, String label);
    }

    private QuickTimerSetupListener mListener;

    public void setListener(QuickTimerSetupListener listener) {
        mListener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_quick_timer_setup, null);

        final TextInputLayout hoursLayout = view.findViewById(R.id.quick_timer_hours_layout);
        final TextInputLayout minutesLayout = view.findViewById(R.id.quick_timer_minutes_layout);
        final TextInputLayout secondsLayout = view.findViewById(R.id.quick_timer_seconds_layout);
        final EditText labelEdit = view.findViewById(R.id.quick_timer_label);
        final EditText hoursEdit = hoursLayout.getEditText();
        final EditText minutesEdit = minutesLayout.getEditText();
        final EditText secondsEdit = secondsLayout.getEditText();

        final AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_quick_timer_title)
                .setView(view)
                .setPositiveButton(R.string.done, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    final long duration = QuickTimerDuration.toMillis(
                            textOf(hoursEdit), textOf(minutesEdit), textOf(secondsEdit));
                    if (duration == QuickTimerDuration.INVALID) {
                        showInputError(hoursLayout, minutesLayout, secondsLayout);
                        return;
                    }
                    if (mListener != null) {
                        mListener.onQuickTimerCreated(duration, textOf(labelEdit));
                    }
                    dialog.dismiss();
                }));
        return dialog;
    }

    private static String textOf(EditText editText) {
        return editText == null ? "" : editText.getText().toString();
    }

    private void showInputError(TextInputLayout hoursLayout, TextInputLayout minutesLayout,
            TextInputLayout secondsLayout) {
        final String error = getString(R.string.quick_timer_invalid_duration);
        setError(hoursLayout, error);
        setError(minutesLayout, error);
        setError(secondsLayout, error);
    }

    private static void setError(TextInputLayout layout, String error) {
        if (layout != null) {
            layout.setError(error);
        }
    }
}
