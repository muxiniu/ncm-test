package com.ncm.decoder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class DecryptService extends Service {
    private static final String CHANNEL_ID = "ncm_decrypt_channel";
    private int success = 0, fail = 0;
    private StringBuilder failDetails = new StringBuilder();
    private Set<String> processedHashes = new HashSet<>();

    @Override public void onCreate() { super.onCreate(); createNotificationChannel(); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        ArrayList<Uri> uris = intent.getParcelableArrayListExtra("uris");
        if (uris == null || uris.isEmpty()) { stopSelf(); return START_NOT_STICKY; }

        startForeground(1, buildNotification("准备解密...", 0, uris.size()));

        new Thread(() -> {
            int total = uris.size();
            boolean writeCover = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("write_cover", true);
            boolean dedup = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dedup", true);

            for (int i = 0; i < total; i++) {
                Uri uri = uris.get(i);
                String fileName = getFileName(uri);
                updateNotification("解密中: " + fileName, i + 1, total);
                sendProgress(i + 1, total, fileName);

                try {
                    java.io.InputStream in = getContentResolver().openInputStream(uri);
                    if (in == null) throw new Exception("无法打开文件");
                    File tmpFile = new File(getExternalFilesDir("tmp"), "_dec_" + System.currentTimeMillis());
                    tmpFile.getParentFile().mkdirs();
                    FileOutputStream fos = new FileOutputStream(tmpFile);
                    NcmDecoder.MetaInfo meta = NcmDecoder.decode(in, fos);
                    in.close(); fos.close();

                    if (dedup) {
                        String hash = md5First8MB(tmpFile);
                        if (processedHashes.contains(hash)) { tmpFile.delete(); continue; }
                        processedHashes.add(hash);
                    }

                    if (meta != null && writeCover && tmpFile.getName().toLowerCase().endsWith(".mp3")) {
                        writeId3Streaming(tmpFile, meta);
                    }

                    String outName = meta != null ? meta.outName : fileName.replace(".ncm", ".mp3");
                    MediaStoreHelper.saveToMusicLibrary(this, tmpFile, outName);
                    tmpFile.delete();
                    success++;
                } catch (Exception e) {
                    fail++; failDetails.append(fileName).append(": ").append(e.getMessage()).append("\n");
                }
            }
            stopForeground(true); sendResult(); stopSelf();
        }).start();
        return START_NOT_STICKY;
    }

    private void writeId3Streaming(File mp3File, NcmDecoder.MetaInfo meta) {
        try {
            File tmpOut = new File(mp3File.getParent(), "_id3_" + System.currentTimeMillis());
            FileOutputStream fos = new FileOutputStream(tmpOut);
            fos.write("ID3".getBytes()); fos.write(new byte[]{3, 0}); fos.write((byte) 0);
            java.io.ByteArrayOutputStream tagOut = new java.io.ByteArrayOutputStream();
            if (meta.musicName != null && !meta.musicName.isEmpty()) tagOut.write(buildTextFrame("TIT2", meta.musicName));
            if (meta.artist != null && !meta.artist.isEmpty()) tagOut.write(buildTextFrame("TPE1", meta.artist));
            if (meta.album != null && !meta.album.isEmpty()) tagOut.write(buildTextFrame("TALB", meta.album));
            if (meta.coverData != null && meta.coverData.length > 0) tagOut.write(buildApicFrame(meta.coverData));
            byte[] tagBytes = tagOut.toByteArray();
            fos.write(syncSafeInt(tagBytes.length)); fos.write(tagBytes);
            FileInputStream fis = new FileInputStream(mp3File);
            byte[] buf = new byte[65536]; int r;
            while ((r = fis.read(buf)) != -1) fos.write(buf, 0, r);
            fis.close(); fos.close(); mp3File.delete(); tmpOut.renameTo(mp3File);
        } catch (Exception e) {}
    }

    private byte[] buildTextFrame(String frameId, String text) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(frameId.getBytes());
        byte[] textBytes = text.getBytes("UTF-8");
        out.write
