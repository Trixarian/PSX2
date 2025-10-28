package com.izzy2lost.psx2;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import android.os.Build;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.app.Dialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.core.view.GravityCompat;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.io.File;

public class GamesCoverDialogFragment extends DialogFragment {
    private boolean didInitialNudge = false;
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_FRAME, R.style.AppTheme);
    }
    private CoversAdapter adapter;
    private String[] titles;
    private String[] uris;
    private String[] coverUrls;
    private String[] localPaths;
    private String[] origTitles;
    private String[] origUris;
    private String[] origCoverUrls;
    private String[] origLocalPaths;
    private RecyclerView rv;
    private LinearLayoutManager llm;
    private PagerSnapHelper snapHelper;
    private int lastRvW = -1, lastRvH = -1;
    private boolean pendingResnap = false;
    private int lastItemWidthPx = 0;

    private static final int SORT_ALPHA = 0;
    private static final int SORT_RECENT = 1;
    private int sortMode = SORT_ALPHA;
    private String query = null;
    // Simpler grouping/sort helpers (revert)


    public interface OnGameSelectedListener {
        void onGameSelected(String gameUri);
    }

    private static final String ARG_TITLES = "titles";
    private static final String ARG_URIS = "uris";

    public static GamesCoverDialogFragment newInstance(String[] titles, String[] uris) {
        GamesCoverDialogFragment f = new GamesCoverDialogFragment();
        Bundle b = new Bundle();
        b.putStringArray(ARG_TITLES, titles);
        b.putStringArray(ARG_URIS, uris);
        f.setArguments(b);
        return f;
    }

    private OnGameSelectedListener listener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnGameSelectedListener) {
            listener = (OnGameSelectedListener) context;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Keep dialog immersive like the rest of the app
        forceDialogImmersive();
        if (rv != null) applyCoverflowTransforms(rv);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Dialog is being destroyed - resume the game
        android.util.Log.d("GamesCoverDialog", "onDestroy called - resuming game");
        try {
            if (getActivity() != null && !getActivity().isFinishing() && getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).onDialogClosed();
            }
        } catch (Throwable e) {
            android.util.Log.e("GamesCoverDialog", "Error in onDestroy: " + e.getMessage());
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // The layout file is cached when dialog is created, so we need to recreate the dialog
        // to get the correct layout for the new orientation
        dismiss();
        
        // Recreate the dialog with the correct layout for new orientation
        if (getParentFragmentManager() != null) {
            GamesCoverDialogFragment newDialog = GamesCoverDialogFragment.newInstance(origTitles, origUris);
            newDialog.show(getParentFragmentManager(), getTag());
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        // Notify MainActivity that this dialog is opening
        try {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).onDialogOpened();
            }
        } catch (Throwable ignored) {}
        
        // Return a styled dialog; content is provided by onCreateView
        Dialog d = new Dialog(requireContext(), R.style.PSX2_FullScreenDialog);
        // Ensure immersive as soon as window exists
        try { applyImmersiveToWindow(d.getWindow()); } catch (Throwable ignored) {}
        
        return d;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.dialog_covers_grid, container, false);
        // Post to re-assert immersive after layout
        try { root.post(this::forceDialogImmersive); } catch (Throwable ignored) {}

        rv = root.findViewById(R.id.recycler_covers);
        rv.setHasFixedSize(true);
        llm = new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false);
        rv.setLayoutManager(llm);
        rv.setClipToPadding(false);
        int sidePad = (int) (48 * getResources().getDisplayMetrics().density);
        int vertPad = (int) (24 * getResources().getDisplayMetrics().density);
        rv.setPadding(sidePad, vertPad, sidePad, vertPad);
        // Snap to center item
        snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(rv);
        // Scale/alpha transform based on distance from center
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                applyCoverflowTransforms(recyclerView);
            }
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (pendingResnap) {
                        resnapToCenter(recyclerView);
                        pendingResnap = false;
                    }
                    applyCoverflowTransforms(recyclerView);
                }
            }
        });

        titles = getArguments() != null ? getArguments().getStringArray(ARG_TITLES) : new String[0];
        uris = getArguments() != null ? getArguments().getStringArray(ARG_URIS) : new String[0];
        coverUrls = new String[uris.length];
        localPaths = new String[uris.length];
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        boolean isFirstBoot = !prefs.getBoolean("has_resolved_titles_once", false);
        
        for (int i = 0; i < uris.length; i++) {
            String saved = prefs.getString("serial:" + uris[i], null);
            String serial = saved;
            // On first boot, skip native serial extraction to avoid crashes
            // The background thread will handle it later with proper delays
            if (serial == null || serial.isEmpty()) {
                if (!isFirstBoot) {
                    try {
                        String nativeSerial = NativeApp.getGameSerialSafe(uris[i]);
                        if (nativeSerial != null && !nativeSerial.isEmpty()) {
                            serial = normalizeSerial(nativeSerial);
                            prefs.edit().putString("serial:" + uris[i], serial).apply();
                        }
                    } catch (Throwable e) {
                        android.util.Log.w("GamesCoverDialog", "Error getting serial for " + uris[i] + ": " + e.getMessage());
                    }
                }
            }
            if (serial == null || serial.isEmpty()) {
                serial = buildSerialFromUri(uris[i]);
            }
            coverUrls[i] = buildCoverUrlFromSerial(serial);
            // Prefer SAF content URI if data root is set, else absolute file path
            android.net.Uri dataRoot = SafManager.getDataRootUri(requireContext());
            if (dataRoot != null) {
                androidx.documentfile.provider.DocumentFile f = SafManager.getChild(requireContext(), new String[]{"covers"}, serial + ".png");
                if (f != null && f.exists()) {
                    localPaths[i] = f.getUri().toString();
                } else {
                    // Pre-create to get a stable Uri
                    androidx.documentfile.provider.DocumentFile nf = SafManager.createChild(requireContext(), new String[]{"covers"}, serial + ".png", "image/png");
                    localPaths[i] = (nf != null) ? nf.getUri().toString() : new java.io.File(getCoversDir(), serial + ".png").getAbsolutePath();
                }
            } else {
                localPaths[i] = new java.io.File(getCoversDir(), serial + ".png").getAbsolutePath();
            }
        }

        // cache originals for sorting/filtering
        origTitles = Arrays.copyOf(titles, titles.length);
        origUris = Arrays.copyOf(uris, uris.length);
        origCoverUrls = Arrays.copyOf(coverUrls, coverUrls.length);
        origLocalPaths = Arrays.copyOf(localPaths, localPaths.length);
        // restore sort pref if any
        sortMode = prefs.getInt("covers_sort_mode", SORT_ALPHA);

        adapter = new CoversAdapter(requireContext(), titles, coverUrls, localPaths, R.layout.item_coverflow,
                position -> {
                    if (listener != null && position >= 0 && position < uris.length) {
                        listener.onGameSelected(uris[position]);
                        dismissAllowingStateLoss();
                    }
                },
                position -> {
                    if (position >= 0 && position < uris.length) {
                        showGameSettings(titles[position], uris[position]);
                    }
                });
        rv.setAdapter(adapter);
        // Build letters list if row present (RecyclerView variant)
        
        // Hook sort/search buttons if present
        View btnSortV = root.findViewById(R.id.btn_sort);
        if (btnSortV instanceof com.google.android.material.button.MaterialButton) {
            com.google.android.material.button.MaterialButton btnSort = (com.google.android.material.button.MaterialButton) btnSortV;
            updateSortButtonUi(btnSort);
            btnSort.setOnClickListener(v -> {
                sortMode = (sortMode == SORT_ALPHA) ? SORT_RECENT : SORT_ALPHA;
                requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        .edit().putInt("covers_sort_mode", sortMode).apply();
                applyFilterAndSort();
                updateSortButtonUi(btnSort);
            });
        }
        View btnSearch = root.findViewById(R.id.btn_search);
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> showSearchDialog());
        }
        // apply initial sort/filter if needed
        if (sortMode != SORT_ALPHA || (query != null && !query.isEmpty())) {
            applyFilterAndSort();
        }
        // One-time tiny nudge to force snap/transform on some devices
        rv.post(() -> {
            if (!isAdded() || didInitialNudge) return;
            // Anchor to a large middle position for "infinite" scroll
            int n = titles != null ? titles.length : 0;
            if (n > 0) {
                int center = (1 << 29); // ~536 million
                int startPos = center - (center % n);
                llm.scrollToPosition(startPos);
            }
            rv.scrollBy(1, 0);
            rv.scrollBy(-1, 0);
            applyCoverflowTransforms(rv);
            didInitialNudge = true;
        });
        // Reduce resize flicker and keep a few views ready
        rv.setItemAnimator(null);
        rv.setItemViewCacheSize(12);

        // Ensure initial measurement + transforms run after first layout
        rv.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (!isAdded()) return;
                applyCoverflowTransforms(rv);
                rv.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });

        // Resolve proper game titles using local YAML index if available (GameIndex/Redump).
        // Falls back to native URI API, then filename if needed.
        // Capture context early to avoid requireContext() crashes if fragment detaches
        final Context ctx = requireContext().getApplicationContext();
        
        new Thread(() -> {
            try {
                // On first boot, add a delay to let native library fully initialize
                if (isFirstBoot) {
                    Thread.sleep(2000); // 2 second delay on first boot
                }
                
                // Check if fragment is still attached before proceeding
                if (!isAdded()) return;
                
                boolean changed = false;
                for (int i = 0; i < uris.length; i++) {
                    // Check if fragment is still attached on each iteration
                    if (!isAdded()) break;
                    
                    try {
                        String t = TitleResolver.resolveTitleForUri(ctx, uris[i], titles[i]);
                        if (t != null && !t.isEmpty() && i < titles.length && !t.equals(titles[i])) {
                            titles[i] = t;
                            if (origTitles != null && i < origTitles.length) origTitles[i] = t;
                            changed = true;
                        }
                    } catch (Throwable e) {
                        android.util.Log.w("GamesCoverDialog", "Error resolving title for " + uris[i] + ": " + e.getMessage());
                    }
                }
                
                // Mark that we've resolved titles at least once
                if (isFirstBoot && isAdded()) {
                    try {
                        ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("has_resolved_titles_once", true).apply();
                    } catch (Throwable ignored) {}
                }
                
                // Only update UI if fragment is still attached
                if (changed && isAdded()) {
                    try {
                        requireActivity().runOnUiThread(() -> {
                            try {
                                if (!isAdded()) return;
                                if (sortMode != SORT_ALPHA || (query != null && !query.isEmpty())) {
                                    applyFilterAndSort();
                                } else {
                                    adapter.notifyDataSetChanged();
                                }
                            } catch (Throwable e) {
                                android.util.Log.w("GamesCoverDialog", "Error updating UI after title resolution: " + e.getMessage());
                            }
                        });
                    } catch (Throwable e) {
                        android.util.Log.w("GamesCoverDialog", "Error posting to UI thread: " + e.getMessage());
                    }
                }
            } catch (Throwable e) {
                android.util.Log.e("GamesCoverDialog", "Error in title resolution thread: " + e.getMessage());
            }
        }).start();

        // Dynamically size items based on RecyclerView size and orientation
        rv.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            final int rvW = right - left;
            final int rvH = bottom - top;
            if (rvW <= 0 || rvH <= 0) return;
            if (rvW == lastRvW && rvH == lastRvH) return; // no real size change
            lastRvW = rvW;
            lastRvH = rvH;
            rv.post(() -> {
                if (!isAdded()) return;
                boolean landscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
                
                // Recalculate padding based on current orientation - this was the issue!
                float density = getResources().getDisplayMetrics().density;
                int currentSidePad = (int) (48 * density);
                int currentVertPad = (int) ((landscape ? 12 : 24) * density);
                
                int titleDp = 36;
                int extraDp = 16;
                int reservedH = (int) ((titleDp + extraDp) * density) + (currentVertPad * 2);
                int availableH = Math.max(0, rvH - reservedH);
                float ratio = 567f / 878f;
                int widthFromHeight = (int) (availableH * ratio);
                int widthFromWidth = (int) (rvW * (landscape ? 0.35f : 0.55f));
                int itemWidth = Math.max(160, Math.min(widthFromHeight, widthFromWidth));
                lastItemWidthPx = itemWidth;
                adapter.setItemWidthPx(itemWidth);
                
                // Center vertically in portrait by adjusting top/bottom padding
                if (!landscape) {
                    int imageH = (int) (itemWidth / (567f / 878f));
                    int titlePx = (int) ((titleDp + extraDp) * density);
                    int contentH = imageH + titlePx;
                    int desiredPad = Math.max(currentVertPad, Math.max(0, (rvH - contentH) / 2));
                    rv.setPadding(currentSidePad, desiredPad, currentSidePad, desiredPad);
                } else {
                    rv.setPadding(currentSidePad, currentVertPad, currentSidePad, currentVertPad);
                }
                // If user is scrolling, defer resnap until idle to avoid fighting gesture
                if (rv.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
                    resnapToCenter(rv);
                    applyCoverflowTransforms(rv);
                } else {
                    pendingResnap = true;
                }
            });
        });

        View btnHome = root.findViewById(R.id.btn_home);
        if (btnHome != null) btnHome.setOnClickListener(v -> {
            try {
                // Refresh drawer settings before opening
                refreshDialogDrawerSettings();
                androidx.drawerlayout.widget.DrawerLayout drawer = root.findViewById(R.id.dlg_drawer_layout);
                if (drawer != null) {
                    // Defer the open to avoid layout/reentrancy races similar to activity path
                    drawer.post(() -> {
                        try {
                            drawer.openDrawer(GravityCompat.START);
                        } catch (Throwable t) {
                            android.util.Log.e("GamesCoverDialog", "Error opening dialog drawer (posted): " + t.getMessage());
                        }
                    });
                }
            } catch (Throwable t) {
                android.util.Log.e("GamesCoverDialog", "Error scheduling dialog drawer open: " + t.getMessage());
            }
        });

        // Setup drawer listener for pause/resume tracking
        try {
            androidx.drawerlayout.widget.DrawerLayout dialogDrawer = root.findViewById(R.id.dlg_drawer_layout);
            if (dialogDrawer != null) {
                dialogDrawer.addDrawerListener(new androidx.drawerlayout.widget.DrawerLayout.DrawerListener() {
                    @Override
                    public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {}

                    @Override
                    public void onDrawerOpened(@NonNull View drawerView) {
                        // Notify MainActivity that drawer opened (will pause game)
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).onDrawerOpened();
                        }
                    }

                    @Override
                    public void onDrawerClosed(@NonNull View drawerView) {
                        // Notify MainActivity that drawer closed (will resume if no other dialogs open)
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).onDrawerClosed();
                        }
                    }

                    @Override
                    public void onDrawerStateChanged(int newState) {}
                });
            }
        } catch (Throwable ignored) {}

        // Wire in-dialog navigation header actions to mirror main drawer
        try {
            com.google.android.material.navigation.NavigationView nav = root.findViewById(R.id.dialog_nav_view);
            if (nav != null) {
                View header = (nav.getHeaderCount() > 0) ? nav.getHeaderView(0) : nav.inflateHeaderView(R.layout.drawer_header_settings);
                if (header != null) {
                    View btnPower = header.findViewById(R.id.drawer_btn_power);
                    if (btnPower != null) {
                        btnPower.setOnClickListener(v -> {
                            new MaterialAlertDialogBuilder(requireContext(),
                                    com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                                    .setTitle("Power Off")
                                    .setMessage("Quit the app?")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Quit", (d,w) -> { try { NativeApp.shutdown(); } catch (Throwable ignored) {} if (getActivity()!=null){ getActivity().finishAffinity(); getActivity().finishAndRemoveTask(); } System.exit(0); })
                                    .show();
                        });
                    }
                    View btnReboot = header.findViewById(R.id.drawer_btn_reboot);
                    if (btnReboot != null) {
                        btnReboot.setOnClickListener(v -> {
                            new MaterialAlertDialogBuilder(requireContext(),
                                    com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                                    .setTitle("Reboot")
                                    .setMessage("Restart the current game?")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Reboot", (d,w) -> { if (getActivity() instanceof MainActivity) { ((MainActivity) getActivity()).rebootEmu(); } })
                                    .show();
                        });
                    }
                    MaterialButtonToggleGroup tg = header.findViewById(R.id.drawer_tg_renderer);
                    View tbAt = header.findViewById(R.id.drawer_tb_at);
                    View tbVk = header.findViewById(R.id.drawer_tb_vk);
                    View tbGl = header.findViewById(R.id.drawer_tb_gl);
                    View tbSw = header.findViewById(R.id.drawer_tb_sw);
                    if (tg != null) {
                        int current = -1;
                        try { current = NativeApp.getCurrentRenderer(); } catch (Throwable ignored) {}
                        if (current == 14 && tbVk != null) tg.check(tbVk.getId());
                        else if (current == 12 && tbGl != null) tg.check(tbGl.getId());
                        else if (current == 13 && tbSw != null) tg.check(tbSw.getId());
                        else if (tbAt != null) tg.check(tbAt.getId());
                        tg.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                            if (!isChecked) return;
                            int r = -1;
                            if (checkedId == (tbVk != null ? tbVk.getId() : -2)) r = 14;
                            else if (checkedId == (tbGl != null ? tbGl.getId() : -2)) r = 12;
                            else if (checkedId == (tbSw != null ? tbSw.getId() : -2)) r = 13;
                            else r = -1;
                            try {
                                requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().putInt("renderer", r).apply();
                                NativeApp.renderGpu(r);
                            } catch (Throwable ignored) {}
                        });
                    }
                    View btnGames = header.findViewById(R.id.drawer_btn_games);
                    if (btnGames != null) {
                        btnGames.setOnClickListener(v -> {
                            try {
                                // Close drawer first
                                androidx.drawerlayout.widget.DrawerLayout drawer = root.findViewById(R.id.dlg_drawer_layout);
                                if (drawer != null) drawer.closeDrawer(androidx.core.view.GravityCompat.START);
                                // Since we're already in the games dialog, just close this drawer
                                // The user is already viewing games, so no need to open another dialog
                            } catch (Throwable ignored) {}
                        });
                    }
                    View btnGameState = header.findViewById(R.id.drawer_btn_game_state);
                    if (btnGameState != null) {
                        btnGameState.setOnClickListener(v -> {
                            try { new SavesDialogFragment().show(getParentFragmentManager(), "saves_dialog"); } catch (Throwable ignored) {}
                        });
                    }
                    View btnMemcardManager = header.findViewById(R.id.drawer_btn_memcard_manager);
                    if (btnMemcardManager != null) {
                        btnMemcardManager.setOnClickListener(v -> {
                            try { new MemoryCardManagerDialogFragment().show(getParentFragmentManager(), "memcard_manager_dialog"); } catch (Throwable ignored) {}
                        });
                    }
                    View btnAbout = header.findViewById(R.id.drawer_btn_about);
                    if (btnAbout != null) {
                        btnAbout.setOnClickListener(v -> {
                            try {
                                showAboutDialog();
                            } catch (Throwable ignored) {}
                        });
                    }

                    // Setup drawer settings controls to mirror quick actions
                    setupDialogDrawerSettings(header);
                }
            }
        } catch (Throwable ignored) {}
        View btnDownload = root.findViewById(R.id.btn_download);
        if (btnDownload != null) btnDownload.setOnClickListener(v -> startDownloadCovers());

        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Re-assert immersive when dialog shows
        forceDialogImmersive();
    }

    private void forceDialogImmersive() {
        try {
            Dialog dlg = getDialog();
            if (dlg != null) applyImmersiveToWindow(dlg.getWindow());
        } catch (Throwable ignored) {}
    }

    private void applyImmersiveToWindow(@Nullable Window w) {
        if (w == null) return;
        // Match activity behavior: full-screen and transient bars by swipe
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        final int legacyFlags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = w.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
        View decor = w.getDecorView();
        if (decor != null) {
            decor.setSystemUiVisibility(legacyFlags);
            decor.setOnSystemUiVisibilityChangeListener(vis -> {
                if ((vis & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    decor.setSystemUiVisibility(legacyFlags);
                }
            });
        }
    }

    // Public API to open the in-dialog navigation drawer from other components
    public void openDialogDrawer() {
        try {
            View root = getView();
            if (root == null) return;
            // Refresh drawer settings before opening
            refreshDialogDrawerSettings();
            androidx.drawerlayout.widget.DrawerLayout drawer = root.findViewById(R.id.dlg_drawer_layout);
            if (drawer != null) {
                drawer.post(() -> {
                    try {
                        drawer.openDrawer(androidx.core.view.GravityCompat.START);
                    } catch (Throwable t) {
                        android.util.Log.e("GamesCoverDialog", "Error opening dialog drawer (posted): " + t.getMessage());
                    }
                });
            }
        } catch (Throwable t) {
            android.util.Log.e("GamesCoverDialog", "Error scheduling dialog drawer open: " + t.getMessage());
        }
    }

        private void setupDialogDrawerSettings(View header) {
        if (header == null) return;
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);

        // Aspect Ratio spinner
        android.widget.Spinner spAspect = header.findViewById(R.id.drawer_sp_aspect_ratio);
        if (spAspect != null) {
            if (spAspect.getAdapter() == null) {
                android.widget.ArrayAdapter<CharSequence> aspectAdapter = android.widget.ArrayAdapter.createFromResource(requireContext(),
                        R.array.aspect_ratio_entries, android.R.layout.simple_spinner_item);
                aspectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spAspect.setAdapter(aspectAdapter);
                spAspect.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                        prefs.edit().putInt("aspect_ratio", position).apply();
                        try { NativeApp.setAspectRatio(position); } catch (Throwable ignored) {}
                    }
                    @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                });
            }
            android.widget.ArrayAdapter<?> adapter = (android.widget.ArrayAdapter<?>) spAspect.getAdapter();
            if (adapter != null) {
                int savedAspect = prefs.getInt("aspect_ratio", 1);
                if (savedAspect < 0 || savedAspect >= adapter.getCount()) savedAspect = 1;
                spAspect.setSelection(savedAspect);
            }
        }

        // Blending Accuracy spinner
        android.widget.Spinner spBlending = header.findViewById(R.id.drawer_sp_blending_accuracy);
        if (spBlending != null) {
            if (spBlending.getAdapter() == null) {
                android.widget.ArrayAdapter<CharSequence> blendAdapter = android.widget.ArrayAdapter.createFromResource(requireContext(),
                        R.array.blending_accuracy_entries, android.R.layout.simple_spinner_item);
                blendAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spBlending.setAdapter(blendAdapter);
                spBlending.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                        prefs.edit().putInt("blending_accuracy", position).apply();
                        try { NativeApp.setBlendingAccuracy(position); } catch (Throwable ignored) {}
                    }
                    @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                });
            }
            android.widget.ArrayAdapter<?> adapter = (android.widget.ArrayAdapter<?>) spBlending.getAdapter();
            if (adapter != null) {
                int savedBlend = prefs.getInt("blending_accuracy", 1);
                if (savedBlend < 0 || savedBlend >= adapter.getCount()) savedBlend = 1;
                spBlending.setSelection(savedBlend);
            }
        }

        // Resolution Scale spinner
        android.widget.Spinner spScale = header.findViewById(R.id.drawer_sp_scale);
        if (spScale != null) {
            if (spScale.getAdapter() == null) {
                android.widget.ArrayAdapter<CharSequence> scaleAdapter = android.widget.ArrayAdapter.createFromResource(requireContext(),
                        R.array.scale_entries, android.R.layout.simple_spinner_item);
                scaleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spScale.setAdapter(scaleAdapter);
                spScale.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                        float scale = Math.max(1, Math.min(8, position + 1));
                        prefs.edit().putFloat("upscale_multiplier", scale).apply();
                        try { NativeApp.renderUpscalemultiplier(scale); } catch (Throwable ignored) {}
                    }
                    @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                });
            }
            android.widget.ArrayAdapter<?> adapter = (android.widget.ArrayAdapter<?>) spScale.getAdapter();
            if (adapter != null) {
                float savedScale = prefs.getFloat("upscale_multiplier", 1.0f);
                int scaleIndex = Math.max(0, Math.min(adapter.getCount() - 1, Math.round(savedScale) - 1));
                spScale.setSelection(scaleIndex);
            }
        }

        // Setup switch listeners only once
        setupDialogDrawerSwitchListeners(header, prefs);
    }private void setupDialogDrawerSwitchListeners(View header, android.content.SharedPreferences prefs) {
        // Widescreen Patches switch
        com.google.android.material.materialswitch.MaterialSwitch swWide = header.findViewById(R.id.drawer_sw_widescreen);
        if (swWide != null && swWide.getTag() == null) {
            swWide.setTag("setup"); // Mark as setup to avoid duplicate listeners
            swWide.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("widescreen_patches", isChecked).apply();
                try { NativeApp.setWidescreenPatches(isChecked); } catch (Throwable ignored) {}
            });
        }

        // No Interlacing switch
        com.google.android.material.materialswitch.MaterialSwitch swNoInt = header.findViewById(R.id.drawer_sw_no_interlacing);
        if (swNoInt != null && swNoInt.getTag() == null) {
            swNoInt.setTag("setup");
            swNoInt.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("no_interlacing_patches", isChecked).apply();
                try { NativeApp.setNoInterlacingPatches(isChecked); } catch (Throwable ignored) {}
            });
        }

        // Load Textures switch
        com.google.android.material.materialswitch.MaterialSwitch swLoadTex = header.findViewById(R.id.drawer_sw_load_textures);
        if (swLoadTex != null && swLoadTex.getTag() == null) {
            swLoadTex.setTag("setup");
            swLoadTex.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("load_textures", isChecked).apply();
                try { NativeApp.setLoadTextures(isChecked); } catch (Throwable ignored) {}
            });
        }

        // Async Textures switch
        com.google.android.material.materialswitch.MaterialSwitch swAsyncTex = header.findViewById(R.id.drawer_sw_async_textures);
        if (swAsyncTex != null && swAsyncTex.getTag() == null) {
            swAsyncTex.setTag("setup");
            swAsyncTex.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("async_texture_loading", isChecked).apply();
                try { NativeApp.setAsyncTextureLoading(isChecked); } catch (Throwable ignored) {}
            });
        }

        // Precache Textures switch
        com.google.android.material.materialswitch.MaterialSwitch swPrecache = header.findViewById(R.id.drawer_sw_precache_textures);
        if (swPrecache != null && swPrecache.getTag() == null) {
            swPrecache.setTag("setup");
            swPrecache.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("precache_textures", isChecked).apply();
                try { NativeApp.setPrecacheTextureReplacements(isChecked); } catch (Throwable ignored) {}
            });
        }
    }

    // --- Helper methods reintroduced after letters-row removal ---
    private void applyCoverflowTransforms(@NonNull RecyclerView recyclerView) {
        int width = recyclerView.getWidth();
        if (width <= 0) return;
        int centerX = width / 2;
        final float maxScale = 1.0f;
        final float minScale = 0.85f;
        final float maxAlpha = 1.0f;
        final float minAlpha = 0.6f;
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            int childCenter = (child.getLeft() + child.getRight()) / 2;
            float dist = Math.abs(childCenter - centerX);
            float norm = Math.min(1f, dist / (width * 0.5f));
            float scale = maxScale - (maxScale - minScale) * norm;
            float alpha = maxAlpha - (maxAlpha - minAlpha) * norm;
            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha(alpha);
        }
    }

    private void resnapToCenter(@NonNull RecyclerView recyclerView) {
        try {
            if (snapHelper == null) return;
            RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
            View snap = snapHelper.findSnapView(lm);
            if (snap == null) return;
            int[] dist = snapHelper.calculateDistanceToFinalSnap(lm, snap);
            if (dist != null && (dist[0] != 0 || dist[1] != 0)) recyclerView.scrollBy(dist[0], dist[1]);
        } catch (Throwable ignored) {}
    }

    private static String normalizeSerial(String s) {
        if (s == null) return "";
        String t = s.trim().toUpperCase(java.util.Locale.ROOT);
        // Keep letters, digits and dash
        return t.replaceAll("[^A-Z0-9-]", "");
    }

    private String buildSerialFromUri(String uriStr) {
        try {
            Uri u = Uri.parse(uriStr);
            String last = u.getLastPathSegment();
            if (last == null) last = uriStr;
            int dot = last.lastIndexOf('.')
;            if (dot > 0) last = last.substring(0, dot);
            return normalizeSerial(last);
        } catch (Throwable ignored) {
            return String.format(java.util.Locale.ROOT, "%08X", Math.abs(uriStr != null ? uriStr.hashCode() : 0));
        }
    }

    private String extractSerialFromUri(String gameUri) {
        try {
            java.io.InputStream in = requireContext().getContentResolver().openInputStream(Uri.parse(gameUri));
            if (in == null) return null;
            final int MAX_BYTES = 8 * 1024 * 1024; // scan first 8MB
            final byte[] buf = new byte[64 * 1024];
            int read;
            int total = 0;
            StringBuilder sb = new StringBuilder();
            while ((read = in.read(buf)) != -1 && total < MAX_BYTES) {
                total += read;
                sb.append(new String(buf, 0, read));
                String found = findSerialInString(sb);
                if (found != null) { in.close(); return found; }
                if (sb.length() > 512 * 1024) sb.delete(0, sb.length() - 128 * 1024);
            }
            in.close();
        } catch (Exception ignored) { }
        return null;
    }

    private static String findSerialInString(CharSequence cs) {
        // Match common forms: SLUS_203.12, SLPM_650.51, SCES_123.45 etc.
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([A-Z]{4,5})[_-]([0-9]{3})\\.([0-9]{2})")
                .matcher(cs);
        if (m.find()) {
            String prefix = m.group(1);
            String part1 = m.group(2);
            String part2 = m.group(3);
            return prefix + "-" + part1 + part2; // SLUS-20312
        }
        return null;
    }

    private String buildCoverUrlFromSerial(String serial) {
        // Use repo-backed PS2 cover set (3D covers)
        return "https://raw.githubusercontent.com/izzy2lost/ps2-covers/main/covers/3d/" + serial + ".png";
    }

    private File getCoversDir() {
        File base = requireContext().getExternalFilesDir("covers");
        if (base == null) base = new File(requireContext().getFilesDir(), "covers");
        if (!base.exists()) base.mkdirs();
        return base;
    }

    private void showGameSettings(String gameTitle, String gameUri) {
        try {
            // Prefer native extraction so CHDs work
            String gameSerial = null;
            try { gameSerial = NativeApp.getGameSerialSafe(gameUri); } catch (Throwable ignored) {}
            if (gameSerial == null || gameSerial.isEmpty()) {
                gameSerial = extractSerialFromUri(gameUri);
            }
            if (gameSerial == null || gameSerial.isEmpty()) {
                gameSerial = buildSerialFromUri(gameUri);
            }
            gameSerial = normalizeSerial(gameSerial);

            // CRC (native if available)
            String gameCrc = null;
            try { gameCrc = NativeApp.getGameCrc(gameUri); } catch (Throwable ignored) {}
            if (gameCrc == null || gameCrc.isEmpty()) {
                gameCrc = String.format(java.util.Locale.ROOT, "%08X", Math.abs(gameUri != null ? gameUri.hashCode() : 0));
            }

            GameSettingsDialogFragment dialog = GameSettingsDialogFragment.newInstance(
                    gameTitle, gameUri, gameSerial, gameCrc);
            dialog.show(getParentFragmentManager(), "game_settings");
        } catch (Throwable ignored) {}
    }

    private void updateSortButtonUi(com.google.android.material.button.MaterialButton btn) {
        if (btn == null) return;
        btn.setIconResource(R.drawable.sort_24px);
        boolean alpha = (sortMode == SORT_ALPHA);
        btn.setText(alpha ? "A–Z" : "RECENT");
        btn.setContentDescription(alpha ? "Sort A–Z" : "Sort Recent");
    }

    private void applyFilterAndSort() {
        int n = origTitles != null ? origTitles.length : 0;
        java.util.ArrayList<Integer> idxs = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (query == null || query.isEmpty()) {
                idxs.add(i);
            } else {
                String t = origTitles[i] != null ? origTitles[i] : "";
                if (t.toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT))) idxs.add(i);
            }
        }
        SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        if (sortMode == SORT_RECENT) {
            idxs.sort((a, b) -> {
                long ta = prefs.getLong("last_played:" + origUris[a], 0L);
                long tb = prefs.getLong("last_played:" + origUris[b], 0L);
                if (ta == tb) return (origTitles[a] != null ? origTitles[a] : "").compareToIgnoreCase(origTitles[b] != null ? origTitles[b] : "");
                return Long.compare(tb, ta);
            });
        } else {
            java.text.Normalizer.Form form = java.text.Normalizer.Form.NFD;
            java.util.function.Function<Integer, String> keyFn = (Integer i) -> {
                String t = origTitles[i];
                if (t == null) return "";
                String s = t.trim().toLowerCase(java.util.Locale.ROOT);
                // Drop leading articles commonly used in titles
                if (s.startsWith("the ")) s = s.substring(4);
                else if (s.startsWith("an ")) s = s.substring(3);
                else if (s.startsWith("a ")) s = s.substring(2);
                // Remove diacritics
                s = java.text.Normalizer.normalize(s, form).replaceAll("\\p{M}+", "");
                // Strip non-alphanumeric at start
                s = s.replaceFirst("^[^a-z0-9]+", "");
                return s;
            };
            idxs.sort((a,b) -> keyFn.apply(a).compareTo(keyFn.apply(b)));
        }
        titles = new String[idxs.size()];
        uris = new String[idxs.size()];
        coverUrls = new String[idxs.size()];
        localPaths = new String[idxs.size()];
        for (int k = 0; k < idxs.size(); k++) {
            int i = idxs.get(k);
            titles[k] = origTitles[i];
            uris[k] = origUris[i];
            coverUrls[k] = origCoverUrls[i];
            localPaths[k] = origLocalPaths[i];
        }
        adapter = new CoversAdapter(requireContext(), titles, coverUrls, localPaths, R.layout.item_coverflow,
                position -> {
                    if (listener != null && position >= 0 && position < uris.length) {
                        listener.onGameSelected(uris[position]);
                        dismissAllowingStateLoss();
                    }
                },
                position -> {
                    if (position >= 0 && position < uris.length) {
                        showGameSettings(titles[position], uris[position]);
                    }
                });
        rv.setAdapter(adapter);
        if (lastItemWidthPx > 0) adapter.setItemWidthPx(lastItemWidthPx);
        int n2 = titles.length;
        if (n2 > 0) {
            int center = (1 << 29);
            int startPos = center - (center % n2);
            llm.scrollToPosition(startPos);
        }
        rv.post(() -> { resnapToCenter(rv); applyCoverflowTransforms(rv); });
    }

    private void showSearchDialog() {
        final EditText input = new EditText(requireContext());
        input.setHint("Search games");
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new MaterialAlertDialogBuilder(requireContext(),
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Search")
                .setView(input)
                .setNegativeButton("Clear", (d, w) -> {
                    query = null;
                    applyFilterAndSort();
                })
                .setPositiveButton("Apply", (d, w) -> {
                    query = input.getText() != null ? input.getText().toString().trim() : null;
                    if (query != null && query.isEmpty()) query = null;
                    applyFilterAndSort();
                })
                .show();
    }

    private void startDownloadCovers() {
        Toast.makeText(requireContext(), "Downloading covers in background", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            for (int i = 0; i < uris.length; i++) {
                try {
                    String better = null;
                    try { better = NativeApp.getGameSerialSafe(uris[i]); } catch (Throwable ignored) {}
                    if (better == null) better = extractSerialFromUri(uris[i]);
                    if (better != null && !better.equalsIgnoreCase(serialFromUrl(coverUrls[i]))) {
                        String serial = normalizeSerial(better);
                        coverUrls[i] = buildCoverUrlFromSerial(serial);
                        android.net.Uri dataRoot = SafManager.getDataRootUri(requireContext());
                        if (dataRoot != null) {
                            androidx.documentfile.provider.DocumentFile nf = SafManager.createChild(requireContext(), new String[]{"covers"}, serial + ".png", "image/png");
                            localPaths[i] = (nf != null) ? nf.getUri().toString() : new java.io.File(getCoversDir(), serial + ".png").getAbsolutePath();
                        } else {
                            localPaths[i] = new java.io.File(getCoversDir(), serial + ".png").getAbsolutePath();
                        }
                        editor.putString("serial:" + uris[i], serial);
                    }
                } catch (Exception ignored) { }
            }
            editor.apply();

            int total = coverUrls.length;
            int ok = 0;
            java.io.File dir = getCoversDir();
            if (!dir.exists()) dir.mkdirs();
            for (int i = 0; i < total; i++) {
                String url = coverUrls[i];
                String outPath = localPaths[i];
                if (isFileValid(outPath)) { ok++; continue; }
                try {
                    if (downloadToTarget(url, outPath)) ok++;
                } catch (Exception ignored) { }
            }
            final int downloaded = ok;
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), "Covers ready: " + downloaded + "/" + total, Toast.LENGTH_SHORT).show();
                if (adapter != null) adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private static String serialFromUrl(String url) {
        if (url == null) return null;
        int slash = url.lastIndexOf('/');
        int dot = url.lastIndexOf('.');
        if (slash >= 0 && dot > slash) return url.substring(slash + 1, dot);
        return null;
    }

    private boolean isFileValid(String path) {
        if (path == null) return false;
        if (path.startsWith("content://")) {
            try {
                androidx.documentfile.provider.DocumentFile f = androidx.documentfile.provider.DocumentFile.fromSingleUri(requireContext(), android.net.Uri.parse(path));
                return f != null && f.length() > 0;
            } catch (Throwable ignored) { return false; }
        }
        java.io.File f = new java.io.File(path);
        return f.exists() && f.length() > 0;
    }

    private boolean downloadToTarget(String urlStr, String outPath) throws Exception {
        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);
        conn.connect();
        int code = conn.getResponseCode();
        if (code != 200) { conn.disconnect(); return false; }
        java.io.InputStream in = conn.getInputStream();
        if (outPath.startsWith("content://")) {
            android.net.Uri uri = android.net.Uri.parse(outPath);
            try (java.io.OutputStream os = requireContext().getContentResolver().openOutputStream(uri, "w")) {
                if (os == null) { conn.disconnect(); return false; }
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
                os.flush();
            }
            in.close();
        } else {
            java.io.File outFile = new java.io.File(outPath);
            java.io.File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
            fos.flush();
            fos.close();
            in.close();
        }
        conn.disconnect();
        return true;
    }

    private void refreshDialogDrawerSettings() {
        try {
            View root = getView();
            if (root == null) return;
            
            com.google.android.material.navigation.NavigationView nav = root.findViewById(R.id.dialog_nav_view);
            if (nav != null && nav.getHeaderCount() > 0) {
                View header = nav.getHeaderView(0);
                if (header != null) {
                    android.content.SharedPreferences prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
                    
                    // Refresh spinner values to reflect current settings
                    android.widget.Spinner spAspect = header.findViewById(R.id.drawer_sp_aspect_ratio);
                    if (spAspect != null && spAspect.getAdapter() != null) {
                        int savedAspect = prefs.getInt("aspect_ratio", 1);
                        android.widget.ArrayAdapter<?> aspectAdapter = (android.widget.ArrayAdapter<?>) spAspect.getAdapter();
                        if (savedAspect >= 0 && savedAspect < aspectAdapter.getCount()) {
                            spAspect.setSelection(savedAspect);
                        }
                    }

                    android.widget.Spinner spScale = header.findViewById(R.id.drawer_sp_scale);
                    if (spScale != null && spScale.getAdapter() != null) {
                        float savedScale = prefs.getFloat("upscale_multiplier", 1.0f);
                        android.widget.ArrayAdapter<?> scaleAdapter = (android.widget.ArrayAdapter<?>) spScale.getAdapter();
                        int scaleIndex = Math.max(0, Math.min(scaleAdapter.getCount() - 1, Math.round(savedScale) - 1));
                        spScale.setSelection(scaleIndex);
                    }

                    android.widget.Spinner spBlending = header.findViewById(R.id.drawer_sp_blending_accuracy);
                    if (spBlending != null && spBlending.getAdapter() != null) {
                        int savedBlend = prefs.getInt("blending_accuracy", 1);
                        android.widget.ArrayAdapter<?> blendAdapter = (android.widget.ArrayAdapter<?>) spBlending.getAdapter();
                        if (savedBlend >= 0 && savedBlend < blendAdapter.getCount()) {
                            spBlending.setSelection(savedBlend);
                        }
                    }
                    
                    // Refresh switch states
                    com.google.android.material.materialswitch.MaterialSwitch swWide = header.findViewById(R.id.drawer_sw_widescreen);
                    if (swWide != null) {
                        swWide.setChecked(prefs.getBoolean("widescreen_patches", true));
                    }

                    com.google.android.material.materialswitch.MaterialSwitch swNoInt = header.findViewById(R.id.drawer_sw_no_interlacing);
                    if (swNoInt != null) {
                        swNoInt.setChecked(prefs.getBoolean("no_interlacing_patches", true));
                    }

                    com.google.android.material.materialswitch.MaterialSwitch swLoadTex = header.findViewById(R.id.drawer_sw_load_textures);
                    if (swLoadTex != null) {
                        swLoadTex.setChecked(prefs.getBoolean("load_textures", false));
                    }

                    com.google.android.material.materialswitch.MaterialSwitch swAsyncTex = header.findViewById(R.id.drawer_sw_async_textures);
                    if (swAsyncTex != null) {
                        swAsyncTex.setChecked(prefs.getBoolean("async_texture_loading", true));
                    }

                    com.google.android.material.materialswitch.MaterialSwitch swPrecache = header.findViewById(R.id.drawer_sw_precache_textures);
                    if (swPrecache != null) {
                        swPrecache.setChecked(prefs.getBoolean("precache_textures", false));
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private void showAboutDialog() {
        String aboutMessage = "PSX2 - PlayStation 2 Emulator for Android\n\n" +
                "This is an Android port of PCSX2, the renowned PlayStation 2 emulator.\n\n" +
                "Based on:\n" +
                "• PCSX2: https://github.com/PCSX2/pcsx2\n" +
                "• PCSX2_ARM64: https://github.com/pontos2024/PCSX2_ARM64\n\n" +
                "Free Version: Follow the build instructions in the repository to compile from source.\n" +
                "Paid Version: Get convenient automatic updates through the Play Store.\n\n" +
                "Important:\n" +
                "• No games or BIOS files are included\n" +
                "• You must own original PlayStation 2 games and console\n" +
                "• This emulator is for educational and preservation purposes\n\n" +
                "Licensed under GNU General Public License v3.0\n" +
                "Source code: https://github.com/izzy2lost/PSX2\n" +
                "View full license: https://github.com/izzy2lost/PSX2/blob/master/LICENSE";

        new MaterialAlertDialogBuilder(requireContext(),
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("About PSX2")
                .setMessage(aboutMessage)
                .setPositiveButton("OK", null)
                .setNeutralButton("View License", (dialog, which) -> {
                    // Open LICENSE file or GitHub link
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                    intent.setData(android.net.Uri.parse("https://github.com/izzy2lost/PSX2/blob/master/LICENSE"));
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Could not open license link", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

}


