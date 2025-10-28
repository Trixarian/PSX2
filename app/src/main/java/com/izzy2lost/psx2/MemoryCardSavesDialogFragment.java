package com.izzy2lost.psx2;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MemoryCardSavesDialogFragment extends DialogFragment {
    
    private static final String ARG_MEMCARD_NAME = "memcard_name";
    
    public static MemoryCardSavesDialogFragment newInstance(String memcardName) {
        MemoryCardSavesDialogFragment fragment = new MemoryCardSavesDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MEMCARD_NAME, memcardName);
        fragment.setArguments(args);
        return fragment;
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_memcard_saves, null);
        
        String memcardName = getArguments() != null ? getArguments().getString(ARG_MEMCARD_NAME) : "";
        
        TextView tvMemcardName = view.findViewById(R.id.tv_memcard_name);
        TextView tvMemcardInfo = view.findViewById(R.id.tv_memcard_info);
        ListView lvSaves = view.findViewById(R.id.lv_saves);
        TextView tvNoSaves = view.findViewById(R.id.tv_no_saves);
        
        tvMemcardName.setText(memcardName);
        
        // Get memory card file
        File memcardDir = new File(requireContext().getExternalFilesDir(null), "memcards");
        File memcardFile = new File(memcardDir, memcardName);
        
        if (memcardFile.exists()) {
            // Show file info
            long fileSize = memcardFile.length();
            long lastModified = memcardFile.lastModified();
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
            String dateStr = sdf.format(new Date(lastModified));
            
            String info = String.format(Locale.getDefault(), 
                "Size: %.2f MB\nLast Modified: %s", 
                fileSize / (1024.0 * 1024.0), 
                dateStr);
            tvMemcardInfo.setText(info);
            
            // Use native PCSX2 code to parse saves
            List<String> saves = parseSaveFilesNative(memcardFile);
            
            if (saves.isEmpty()) {
                lvSaves.setVisibility(View.GONE);
                tvNoSaves.setVisibility(View.VISIBLE);
            } else {
                lvSaves.setVisibility(View.VISIBLE);
                tvNoSaves.setVisibility(View.GONE);
                
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    requireContext(),
                    android.R.layout.simple_list_item_1,
                    saves
                );
                lvSaves.setAdapter(adapter);
            }
        } else {
            tvMemcardInfo.setText("Memory card file not found");
            lvSaves.setVisibility(View.GONE);
            tvNoSaves.setVisibility(View.VISIBLE);
        }
        
        return new MaterialAlertDialogBuilder(requireContext())
            .setCustomTitle(UiUtils.centeredDialogTitle(requireContext(), "MEMORY CARD SAVES"))
            .setView(view)
            .setPositiveButton("Close", null)
            .create();
    }
    
    private List<String> parseSaveFilesNative(File memcardFile) {
        List<String> saves = new ArrayList<>();
        
        try {
            // Call native PCSX2 code to parse the memory card
            String[] nativeSaves = NativeApp.getMemoryCardSaves(memcardFile.getAbsolutePath());
            
            if (nativeSaves != null) {
                for (String saveInfo : nativeSaves) {
                    // Format is "filename|size|isDirectory"
                    String[] parts = saveInfo.split("\\|");
                    if (parts.length >= 3) {
                        String filename = parts[0];
                        int size = Integer.parseInt(parts[1]);
                        boolean isDir = parts[2].equals("1");
                        
                        String displayInfo;
                        if (isDir) {
                            displayInfo = String.format(Locale.getDefault(),
                                "%s (%d files)",
                                filename,
                                size
                            );
                        } else {
                            displayInfo = String.format(Locale.getDefault(),
                                "%s (%d KB)",
                                filename,
                                size / 1024
                            );
                        }
                        saves.add(displayInfo);
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MemcardSaves", "Error parsing memory card with native code: " + e.getMessage(), e);
        }
        
        return saves;
    }
    
    private List<String> parseSaveFilesOld(File memcardFile) {
        List<String> saves = new ArrayList<>();
        
        try (RandomAccessFile raf = new RandomAccessFile(memcardFile, "r")) {
            android.util.Log.d("MemcardSaves", "Parsing memory card: " + memcardFile.getName() + " (" + memcardFile.length() + " bytes)");
            
            // Verify this is a PS2 memory card by checking magic
            byte[] magic = new byte[28];
            raf.seek(0);
            raf.read(magic);
            String magicStr = new String(magic, 0, 28).trim();
            android.util.Log.d("MemcardSaves", "Magic: " + magicStr);
            
            // Standard PS2 memory card: directory starts at page 13 (0x1A00)
            // Each page is 512 bytes
            long[] possibleDirStarts = {
                0x1A00,  // Standard location (page 13)
                0x2000,  // Alternative location
                0x4000   // Another possible location
            };
            
            for (long dirStart : possibleDirStarts) {
                if (dirStart >= raf.length()) continue;
                
                android.util.Log.d("MemcardSaves", "Trying directory at offset: 0x" + Long.toHexString(dirStart));
                
                // Scan for directory entries
                // Each entry is 512 bytes
                int foundCount = 0;
                for (int i = 0; i < 100; i++) {
                    long entryPos = dirStart + (i * 512);
                    if (entryPos + 512 > raf.length()) break;
                    
                    raf.seek(entryPos);
                    
                    // Read entry mode (first 4 bytes, little-endian)
                    int mode = readInt32LE(raf);
                    
                    // Skip if empty
                    if (mode == 0 || mode == 0xFFFFFFFF) continue;
                    
                    // Read filename first
                    raf.seek(entryPos + 0x40);
                    byte[] nameBytes = new byte[32];
                    raf.read(nameBytes);
                    String filename = extractString(nameBytes);
                    
                    // Skip "." and ".." entries
                    if (filename.equals(".") || filename.equals("..")) continue;
                    
                    // Skip if no filename
                    if (filename.isEmpty()) continue;
                    
                    // Read length/size field
                    raf.seek(entryPos + 0x04);
                    int lengthOrSize = readInt32LE(raf);
                    
                    // Log the entry for debugging
                    android.util.Log.d("MemcardSaves", String.format("Entry: mode=0x%08X, name=%s, length=%d", mode, filename, lengthOrSize));
                    
                    // Check flags
                    boolean isUsed = (mode & 0x8000) != 0;
                    boolean isDir = (mode & 0x0020) != 0;
                    boolean isFile = (mode & 0x0010) != 0;
                    
                    // Accept if it's used and has a reasonable size/count
                    if (isUsed && lengthOrSize > 0 && lengthOrSize < 10 * 1024 * 1024) {
                        String saveInfo;
                        if (isDir) {
                            // Directory - show number of files
                            saveInfo = String.format(Locale.getDefault(),
                                "%s (%d files)",
                                filename,
                                lengthOrSize
                            );
                            android.util.Log.d("MemcardSaves", "Found save directory: " + filename + " (" + lengthOrSize + " files)");
                        } else {
                            // File - show size
                            saveInfo = String.format(Locale.getDefault(),
                                "%s (%d KB)",
                                filename,
                                lengthOrSize / 1024
                            );
                            android.util.Log.d("MemcardSaves", "Found file: " + filename + " (" + lengthOrSize + " bytes)");
                        }
                        saves.add(saveInfo);
                        foundCount++;
                    }
                }
                
                // If we found saves at this location, stop searching
                if (foundCount > 0) {
                    android.util.Log.d("MemcardSaves", "Found " + foundCount + " saves at offset 0x" + Long.toHexString(dirStart));
                    break;
                }
            }
            
            if (saves.isEmpty()) {
                android.util.Log.w("MemcardSaves", "No saves found in memory card");
            }
            
        } catch (Exception e) {
            android.util.Log.e("MemcardSaves", "Error parsing memory card: " + e.getMessage(), e);
        }
        
        return saves;
    }
    
    private int readInt32LE(RandomAccessFile raf) throws Exception {
        byte[] bytes = new byte[4];
        raf.read(bytes);
        return (bytes[0] & 0xFF) | 
               ((bytes[1] & 0xFF) << 8) | 
               ((bytes[2] & 0xFF) << 16) | 
               ((bytes[3] & 0xFF) << 24);
    }
    
    private String extractString(byte[] bytes) {
        // Find null terminator
        int length = 0;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == 0) {
                length = i;
                break;
            }
        }
        if (length == 0) length = bytes.length;
        
        // Try to decode as ASCII/Latin-1
        try {
            String str = new String(bytes, 0, length, "ISO-8859-1").trim();
            // Filter out non-printable characters
            StringBuilder sb = new StringBuilder();
            for (char c : str.toCharArray()) {
                if (c >= 32 && c < 127) {
                    sb.append(c);
                } else if (c >= 160) {
                    // Extended ASCII characters
                    sb.append(c);
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            // Fallback to simple ASCII extraction
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < length; i++) {
                byte b = bytes[i];
                if (b >= 32 && b < 127) {
                    sb.append((char) b);
                }
            }
            return sb.toString().trim();
        }
    }
}
