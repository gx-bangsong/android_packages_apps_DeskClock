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

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.deskclock.R;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the horizontal carousel of quick timer chips shown on the timer creation screen.
 * The last chip is always an "add" chip. The adapter implements
 * {@link QuickTimerRepository.QuickTimerListener} so it can be registered directly with the
 * repository to stay in sync with the persisted presets.
 */
public final class QuickTimerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
        implements QuickTimerRepository.QuickTimerListener {

    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_ADD = 1;

    /** Callbacks for chip interactions. */
    public interface OnQuickTimerClickListener {
        void onQuickTimerClick(QuickTimer quickTimer);

        void onAddQuickTimerClick();

        void onQuickTimerLongClick(QuickTimer quickTimer);
    }

    private final Context mContext;
    private final OnQuickTimerClickListener mListener;
    private final List<QuickTimer> mQuickTimers = new ArrayList<>();

    public QuickTimerAdapter(Context context, OnQuickTimerClickListener listener) {
        mContext = context;
        mListener = listener;
    }

    /** @param quickTimers the presets to display; may be null */
    public void setQuickTimers(List<QuickTimer> quickTimers) {
        mQuickTimers.clear();
        if (quickTimers != null) {
            mQuickTimers.addAll(quickTimers);
        }
        notifyDataSetChanged();
    }

    @Override
    public void onQuickTimersChanged(List<QuickTimer> quickTimers) {
        setQuickTimers(quickTimers);
    }

    @Override
    public int getItemCount() {
        return mQuickTimers.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position < mQuickTimers.size() ? VIEW_TYPE_ITEM : VIEW_TYPE_ADD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(mContext)
                .inflate(R.layout.quick_timer_item, parent, false);
        return viewType == VIEW_TYPE_ITEM
                ? new QuickTimerViewHolder(view)
                : new AddViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof QuickTimerViewHolder) {
            ((QuickTimerViewHolder) holder).bind(mQuickTimers.get(position));
        } else if (holder instanceof AddViewHolder) {
            ((AddViewHolder) holder).bind();
        }
    }

    private static String formatDuration(long durationMillis) {
        return DateUtils.formatElapsedTime(durationMillis / 1000L);
    }

    private final class QuickTimerViewHolder extends RecyclerView.ViewHolder {
        private final Chip mChip;

        QuickTimerViewHolder(@NonNull View itemView) {
            super(itemView);
            mChip = itemView.findViewById(R.id.quick_timer_chip);
        }

        void bind(QuickTimer quickTimer) {
            final String text = TextUtils.isEmpty(quickTimer.label)
                    ? formatDuration(quickTimer.duration)
                    : mContext.getString(R.string.quick_timer_label_format,
                            quickTimer.label, formatDuration(quickTimer.duration));
            mChip.setText(text);
            mChip.setContentDescription(text);
            mChip.setOnClickListener(v -> mListener.onQuickTimerClick(quickTimer));
            mChip.setOnLongClickListener(v -> {
                mListener.onQuickTimerLongClick(quickTimer);
                return true;
            });
        }
    }

    private final class AddViewHolder extends RecyclerView.ViewHolder {
        private final Chip mChip;

        AddViewHolder(@NonNull View itemView) {
            super(itemView);
            mChip = itemView.findViewById(R.id.quick_timer_chip);
        }

        void bind() {
            mChip.setText("+");
            mChip.setContentDescription(mContext.getString(R.string.add_quick_timer_title));
            mChip.setOnClickListener(v -> mListener.onAddQuickTimerClick());
            mChip.setOnLongClickListener(null);
        }
    }
}
