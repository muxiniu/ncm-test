package com.muxiniu.ncmtest;


import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MediaStoreHelper {
    public static Uri saveToMusicLibrary(Context context, File sourceFile, String displayName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, displayName);
            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/NCM Decoder");
            values.put(MediaStore.Audio.Media.IS_PENDING, 1);
            Uri collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri itemUri = context.getContentResolver().insert(collection, values);
            if (itemUri == null) return null;
            try (OutputStream out = context.getContentResolver().openOutputStream(itemUri);
                 FileInputStream in = new FileInputStream(sourceFile)) {
                byte[] buf = new byte[65536]; int r;
                while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            } catch (Exception e) { return null; }
            values.clear(); values.put(MediaStore.Audio.Media.IS_PENDING, 0);
            context.getContentResolver().update(itemUri, values, null, null);
            return itemUri;
        } else {
            File musicDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "NCM Decoder");
            musicDir.mkdirs();
            File dest = new File(musicDir, displayName);
            int dup = 1;
            while (dest.exists()) {
                String base = displayName.replaceFirst("\\.(mp3|flac)$", "");
                String ext = displayName.substring(displayName.lastIndexOf('.'));
                dest = new File(musicDir, base + "_" + (dup++) + ext);
            }
            try (FileInputStream in = new FileInputStream(sourceFile); OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[65536]; int r;
                while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            } catch (Exception e) { return null; }
            android.media.MediaScannerConnection.scanFile(context, new String[]{dest.getAbsolutePath()}, null, null);
            return Uri.fromFile(dest);
        }
    }
}

