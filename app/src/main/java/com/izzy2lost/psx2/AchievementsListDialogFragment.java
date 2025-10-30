package com.izzy2lost.psx2;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Dialog showing the list of achievements for the current game.
 */
public class AchievementsListDialogFragment extends DialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle("Achievements");
        
        View view = createView();
        builder.setView(view);
        builder.setPositiveButton("Close", null);
        
        return builder.create();
    }

    private View createView() {
        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);

        // Load achievements on background thread
        new Thread(() -> {
            try {
                Achievement[] achievements = NativeApp.achievementsGetAchievementList();
                
                requireActivity().runOnUiThread(() -> {
                    if (achievements == null || achievements.length == 0) {
                        TextView emptyText = new TextView(requireContext());
                        emptyText.setText("No achievements found for this game.");
                        emptyText.setPadding(0, 16, 0, 16);
                        layout.addView(emptyText);
                    } else {
                        // Count unlocked
                        int unlockedCount = 0;
                        int totalPoints = 0;
                        int earnedPoints = 0;
                        for (Achievement achievement : achievements) {
                            if (achievement.unlocked) {
                                unlockedCount++;
                                earnedPoints += achievement.points;
                            }
                            totalPoints += achievement.points;
                        }

                        // Summary
                        TextView summary = new TextView(requireContext());
                        summary.setText(String.format("Unlocked: %d / %d (%d / %d points)",
                            unlockedCount, achievements.length, earnedPoints, totalPoints));
                        summary.setTextSize(16);
                        summary.setPadding(0, 0, 0, 16);
                        layout.addView(summary);

                        // Add each achievement
                        for (Achievement achievement : achievements) {
                            layout.addView(createAchievementView(achievement));
                        }
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Error loading achievements: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                });
            }
        }).start();

        scrollView.addView(layout);
        return scrollView;
    }

    private View createAchievementView(Achievement achievement) {
        LinearLayout itemLayout = new LinearLayout(requireContext());
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setPadding(0, 8, 0, 8);

        // Icon placeholder (we'll add image loading later)
        TextView icon = new TextView(requireContext());
        icon.setText(achievement.unlocked ? "🏆" : "🔒");
        icon.setTextSize(32);
        icon.setPadding(0, 0, 16, 0);
        itemLayout.addView(icon);

        // Text content
        LinearLayout textLayout = new LinearLayout(requireContext());
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView title = new TextView(requireContext());
        title.setText(achievement.title + " (" + achievement.points + " pts)");
        title.setTextSize(14);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        textLayout.addView(title);

        TextView description = new TextView(requireContext());
        description.setText(achievement.description);
        description.setTextSize(12);
        description.setAlpha(0.7f);
        textLayout.addView(description);

        if (achievement.unlocked) {
            TextView unlockTime = new TextView(requireContext());
            unlockTime.setText("Unlocked: " + achievement.getUnlockTimeFormatted());
            unlockTime.setTextSize(10);
            unlockTime.setAlpha(0.5f);
            textLayout.addView(unlockTime);
        } else if (achievement.measuredPercent > 0) {
            TextView progress = new TextView(requireContext());
            progress.setText(String.format("Progress: %.1f%%", achievement.measuredPercent));
            progress.setTextSize(10);
            progress.setAlpha(0.5f);
            textLayout.addView(progress);
        }

        itemLayout.addView(textLayout);
        return itemLayout;
    }

    public static AchievementsListDialogFragment newInstance() {
        return new AchievementsListDialogFragment();
    }
}
