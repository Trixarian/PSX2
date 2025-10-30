package com.izzy2lost.psx2;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.view.Surface;
import java.io.File;
import java.lang.ref.WeakReference;

public class NativeApp {
	static {
		try {
			System.loadLibrary("emucore");
			hasNoNativeBinary = false;
		} catch (UnsatisfiedLinkError e) {
			hasNoNativeBinary = true;
		}
	}

	public static boolean hasNoNativeBinary;


	protected static WeakReference<Context> mContext;
	public static Context getContext() {
		return mContext.get();
	}

	public static void initializeOnce(Context context) {
		mContext = new WeakReference<>(context);
		File externalFilesDir = context.getExternalFilesDir(null);
		if (externalFilesDir == null) {
			externalFilesDir = context.getDataDir();
		}
		initialize(externalFilesDir.getAbsolutePath(), android.os.Build.VERSION.SDK_INT);
	}

    public static native void initialize(String path, int apiVer);
    public static native String getGameTitle(String path);
    public static native String getGameTitleFromUri(String gameUri);
	public static native String getGameSerial();
	public static native float getFPS();

	public static native String getPauseGameTitle();
	public static native String getPauseGameSerial();

	public static native void setPadVibration(boolean isonoff);
	public static native void setPadButton(int index, int range, boolean iskeypressed);
	public static native void resetKeyStatus();

	public static native void setAspectRatio(int type);
	public static native void speedhackLimitermode(int value);
	public static native void speedhackEecyclerate(int value);
	public static native void speedhackEecycleskip(int value);

	public static native void renderUpscalemultiplier(float value);
	public static native void renderMipmap(int value);
	public static native void renderHalfpixeloffset(int value);
	public static native void renderGpu(int value);
	public static native void renderPreloading(int value);

	// HUD/OSD visibility toggle
	public static native void setHudVisible(boolean visible);
	
	// Widescreen and interlacing patches
	    public static native void setWidescreenPatches(boolean enabled);
    public static native void setNoInterlacingPatches(boolean enabled);
    
    // Texture loading options for texture packs
    public static native void setLoadTextures(boolean enabled);
    public static native void setAsyncTextureLoading(boolean enabled);
    public static native void setPrecacheTextureReplacements(boolean enabled);
    public static native void setBlendingAccuracy(int level);
    
    // Shade Boost (brightness/contrast/saturation)
    public static native void setShadeBoost(boolean enabled);
    public static native void setShadeBoostBrightness(int brightness);
    public static native void setShadeBoostContrast(int contrast);
    public static native void setShadeBoostSaturation(int saturation);

    // Apply multiple settings in one atomic batch (safer live updates)
    public static native void applyGlobalSettingsBatch(int renderer,
                                                       float upscaleMultiplier,
                                                       int aspectRatio,
                                                       int blendingAccuracy,
                                                       boolean widescreenPatches,
                                                       boolean noInterlacingPatches,
                                                       boolean loadTextures,
                                                       boolean asyncTextureLoading,
                                                       boolean hudVisible);
    
    // Apply per-game settings (subset) in one batch
    public static native void applyPerGameSettingsBatch(int renderer,
                                                        float upscaleMultiplier,
                                                        int blendingAccuracy,
                                                        boolean widescreenPatches,
                                                        boolean noInterlacingPatches,
                                                        boolean enablePatches,
                                                        boolean enableCheats);

    // Query current runtime renderer from the core (reflects global/per-game)
    public static native int getCurrentRenderer();

    // Per-game settings
    public static native void saveGameSettings(String filename, int blendingAccuracy, int renderer, 
                                              int resolution, boolean widescreenPatches, 
                                              boolean noInterlacingPatches, boolean enablePatches, 
                                              boolean enableCheats);
    public static native void saveGameSettingsToPath(String fullPath, int blendingAccuracy, int renderer, 
                                                     int resolution, boolean widescreenPatches, 
                                                     boolean noInterlacingPatches, boolean enablePatches, 
                                                     boolean enableCheats);
    public static native void deleteGameSettings(String filename);
    public static native String getGameSerial(String gameUri);
    public static native String getGameCrc(String gameUri);
    public static native String getCurrentGameSerial();
    
    // Synchronization object for CDVD operations to prevent crashes
    private static final Object CDVD_LOCK = new Object();
    
    // Synchronized wrapper for getGameSerial to prevent CDVD race conditions
    public static String getGameSerialSafe(String gameUri) {
        synchronized (CDVD_LOCK) {
            try {
                return getGameSerial(gameUri);
            } catch (Exception e) {
                return "";
            }
        }
    }
    
    // Synchronized wrapper for getGameTitleFromUri to prevent CDVD race conditions
    public static String getGameTitleFromUriSafe(String gameUri) {
        synchronized (CDVD_LOCK) {
            try {
                return getGameTitleFromUri(gameUri);
            } catch (Exception e) {
                return "";
            }
        }
    }
    
    // Synchronized wrapper for getGameCrc to prevent CDVD race conditions
    public static String getGameCrcSafe(String gameUri) {
        synchronized (CDVD_LOCK) {
            try {
                return getGameCrc(gameUri);
            } catch (Exception e) {
                return "";
            }
        }
    }

    // Get list of saves on a memory card
    // Returns array of strings in format "filename|size|isDirectory"
    public static native String[] getMemoryCardSaves(String memcardPath);

    // RetroAchievements native methods
    public static native boolean achievementsIsActive();
    public static native boolean achievementsIsHardcoreMode();
    public static native boolean achievementsHasActiveGame();
    public static native String achievementsGetGameTitle();
    public static native int achievementsGetGameId();
    public static native String achievementsGetRichPresence();
    public static native void achievementsLogin(String username, String password);
    public static native void achievementsLogout();
    public static native void achievementsInitialize();
    public static native void achievementsShutdown();
    public static native Achievement[] achievementsGetAchievementList();
    public static native void achievementsSetHardcoreMode(boolean enabled);

	public static native void onNativeSurfaceCreated();
	public static native void onNativeSurfaceChanged(Surface surface, int w, int h);
	public static native void onNativeSurfaceDestroyed();

    public static native boolean runVMThread(String path);

	public static native void pause();
	public static native void resume();
	public static native boolean isPaused();
	public static native void shutdown();

	public static native boolean saveStateToSlot(int slot);
	public static native boolean loadStateFromSlot(int slot);
	public static native String getGamePathSlot(int slot);
	public static native byte[] getImageSlot(int slot);

	// Call jni
    public static int openContentUri(String uriString) {
        Context _context = getContext();
        if(_context != null) {
            ContentResolver _contentResolver = _context.getContentResolver();
            try {
                ParcelFileDescriptor filePfd = _contentResolver.openFileDescriptor(Uri.parse(uriString), "r");
                if (filePfd != null) {
                    return filePfd.detachFd();  // Take ownership of the fd.
                }
            } catch (Exception ignored) {}
        }
        return -1;
    }

    // Indicates whether a SAF Data Root has been selected by the user.
    public static boolean hasSafDataRoot() {
        return SafManager.getDataRootUri(getContext()) != null;
    }

    // Open a SAF content Uri with the requested mode ("r", "w", or "rw"). Returns a detached FD or -1.
    public static int openContentUriMode(String uriString, String mode) {
        Context _context = getContext();
        if(_context != null) {
            ContentResolver _contentResolver = _context.getContentResolver();
            try {
                ParcelFileDescriptor filePfd = _contentResolver.openFileDescriptor(Uri.parse(uriString), mode);
                if (filePfd != null) {
                    return filePfd.detachFd();
                }
            } catch (Exception ignored) {}
        }
        return -1;
    }

    // Resolve a child document Uri within the SAF Data Root.
    // subdir: e.g., "gamesettings", filename: e.g., "SLUS-12345.ini". If create is true, creates file.
    public static String resolveSafChildUri(String subdir, String filename, boolean create) {
        Uri root = SafManager.getDataRootUri(getContext());
        if (root == null) return null;
        try {
            androidx.documentfile.provider.DocumentFile df;
            if (create) {
                df = SafManager.createChild(getContext(), new String[]{subdir}, filename, "application/octet-stream");
            } else {
                df = SafManager.getChild(getContext(), new String[]{subdir}, filename);
            }
            return (df != null) ? df.getUri().toString() : null;
        } catch (Throwable ignored) { }
        return null;
    }

    // Resolve a file path relative to the SAF Data Root. Accepts nested paths like
    // "textures/SLUS-12345/replacements/subdir/file.png". If create is true, creates the file.
    public static String resolveSafPathUri(String relativePath, boolean create) {
        if (relativePath == null) return null;
        Uri root = SafManager.getDataRootUri(getContext());
        if (root == null) return null;
        try {
            String[] parts = relativePath.split("/");
            if (parts.length == 0) return null;
            String[] dirSegs;
            String filename;
            if (parts.length == 1) {
                dirSegs = new String[]{};
                filename = parts[0];
            } else {
                dirSegs = new String[parts.length - 1];
                System.arraycopy(parts, 0, dirSegs, 0, parts.length - 1);
                filename = parts[parts.length - 1];
            }
            androidx.documentfile.provider.DocumentFile df;
            if (create) {
                df = SafManager.createChild(getContext(), dirSegs, filename, "application/octet-stream");
            } else {
                df = SafManager.getChild(getContext(), dirSegs, filename);
            }
            return (df != null) ? df.getUri().toString() : null;
        } catch (Throwable ignored) { }
        return null;
    }

    // List files under a relative SAF directory recursively. Returns full relative paths from the root.
    public static String[] listSafRecursiveFiles(String relativeDir) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        try {
            androidx.documentfile.provider.DocumentFile base = SafManager.getOrCreateDir(getContext(), relativeDir.split("/"));
            if (base == null || !base.exists()) return new String[0];
            walkDirRecursive(base, relativeDir, out);
        } catch (Throwable ignored) { }
        return out.toArray(new String[0]);
    }

    private static void walkDirRecursive(androidx.documentfile.provider.DocumentFile dir, String relPrefix, java.util.ArrayList<String> out) {
        androidx.documentfile.provider.DocumentFile[] arr = dir.listFiles();
        if (arr == null) return;
        for (androidx.documentfile.provider.DocumentFile f : arr) {
            if (f == null) continue;
            String name = f.getName();
            if (name == null || name.isEmpty()) continue;
            if (f.isDirectory()) {
                walkDirRecursive(f, relPrefix + "/" + name, out);
            } else if (f.isFile()) {
                out.add(relPrefix + "/" + name);
            }
        }
    }

    // List files directly under a relative SAF directory (non-recursive). Returns full relative paths.
    public static String[] listSafFilesFlat(String relativeDir) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        try {
            androidx.documentfile.provider.DocumentFile dir = SafManager.getOrCreateDir(getContext(), relativeDir.split("/"));
            if (dir == null || !dir.isDirectory()) return new String[0];
            androidx.documentfile.provider.DocumentFile[] arr = dir.listFiles();
            if (arr != null) {
                for (androidx.documentfile.provider.DocumentFile f : arr) {
                    if (f != null && f.isFile()) {
                        String name = f.getName();
                        if (name != null && !name.isEmpty()) out.add(relativeDir + "/" + name);
                    }
                }
            }
        } catch (Throwable ignored) { }
        return out.toArray(new String[0]);
    }

    // List filenames (files only) under a SAF subdirectory (e.g., "cheats", "patches").
    public static String[] listSafFilenames(String subdir) {
        try {
            androidx.documentfile.provider.DocumentFile dir = SafManager.getOrCreateDir(getContext(), subdir);
            if (dir == null || !dir.isDirectory()) return new String[0];
            androidx.documentfile.provider.DocumentFile[] arr = dir.listFiles();
            java.util.ArrayList<String> out = new java.util.ArrayList<>();
            if (arr != null) {
                for (androidx.documentfile.provider.DocumentFile f : arr) {
                    if (f != null && f.isFile()) {
                        String name = f.getName();
                        if (name != null && !name.isEmpty()) out.add(name);
                    }
                }
            }
            return out.toArray(new String[0]);
        } catch (Throwable ignored) { }
        return new String[0];
    }
}
