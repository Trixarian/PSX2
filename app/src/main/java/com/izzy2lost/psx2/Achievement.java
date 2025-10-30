package com.izzy2lost.psx2;

/**
 * Represents a single achievement from RetroAchievements.
 */
public class Achievement {
    public final int id;
    public final String title;
    public final String description;
    public final String badgeName;
    public final int points;
    public final boolean unlocked;
    public final long unlockTime;
    public final String measuredProgress;
    public final float measuredPercent;
    public final int state;
    public final float rarity;
    public final float rarityHardcore;

    public Achievement(int id, String title, String description, String badgeName,
                      int points, boolean unlocked, long unlockTime,
                      String measuredProgress, float measuredPercent, int state,
                      float rarity, float rarityHardcore) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.badgeName = badgeName;
        this.points = points;
        this.unlocked = unlocked;
        this.unlockTime = unlockTime;
        this.measuredProgress = measuredProgress;
        this.measuredPercent = measuredPercent;
        this.state = state;
        this.rarity = rarity;
        this.rarityHardcore = rarityHardcore;
    }

    /**
     * Get the badge URL for this achievement.
     * @param locked Whether to get the locked or unlocked badge
     */
    public String getBadgeUrl(boolean locked) {
        return String.format("https://media.retroachievements.org/Badge/%s%s.png",
            badgeName, locked ? "_lock" : "");
    }

    /**
     * Get a formatted string for the unlock time.
     */
    public String getUnlockTimeFormatted() {
        if (unlockTime == 0) return "Not unlocked";
        return new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US)
            .format(new java.util.Date(unlockTime * 1000));
    }
}
