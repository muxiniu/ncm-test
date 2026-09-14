package com.muxiniu.ncmtest;

import java.io.*;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONObject;

public class NcmDecoder {

    public static class MetaInfo {
        public String musicName = "Unknown";
        public String artist = "Unknown";
        public String album = "Unknown";
        public String outName = "decrypted.mp3";
        public byte[] coverData;
        public String format = "mp3";
    }

    public static MetaInfo decode(InputStream ncmIn, OutputStream out) throws Exception {
        DataInputStream dis = new DataInputStream(ncmIn);

        byte[] magic = new byte[8];
        dis.readFully(magic);
        if (!new String(magic).equals("CTENFDAM")) throw new Exception("不是有效的 NCM 文件");

        dis.skipBytes(2);
        dis.skipBytes(4);

        int keyLen = dis.readInt();
        byte[] encKey = new byte[keyLen];
        dis.readFully(encKey);
        for (int i = 0; i < encKey.length; i++) encKey[i] ^= 0x64;

        byte[] aesKey = padTo16("neteasecloudmusic".getBytes("UTF-8"));
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"));
        byte[] decKey = cipher.doFinal(encKey);

        dis.skipBytes(4);

        MetaInfo meta = new MetaInfo();
        int metaLen = dis.readInt();
        if (metaLen > 0 && metaLen < 0x100000) {
            byte[] metaEnc = new byte[metaLen];
            dis.readFully(metaEnc);
            for (int i = 0; i < metaEnc.length; i++) metaEnc[i] ^= 0x63;
            String metaJson = new String(metaEnc, "UTF-8").trim();
            try {
                JSONObject jo = new JSONObject(metaJson);
                if (jo.has("musicName")) meta.musicName = jo.getString("musicName");
                if (jo.has("artist")) meta.artist = jo.getString("artist");
                if (jo.has("album")) meta.album = jo.getString("album");
                if (jo.has("format")) meta.format = jo.getString("format");
                if (!"Unknown".equals(meta.musicName)) {
                    meta.outName = sanitize(meta.artist) + " - " + sanitize(meta.musicName) + "." + meta.format;
                }
            } catch (Exception e) {}
        }

        int coverLen = dis.readInt();
        if (coverLen > 0 && coverLen < 0x10000000) {
            meta.coverData = new byte[coverLen];
            dis.readFully(meta.coverData);
        }

        dis.skipBytes(4);

        byte[] rc4Key = new byte[decKey.length - 17];
        System.arraycopy(decKey, 17, rc4Key, 0, rc4Key.length);
        RC4 rc4 = new RC4(rc4Key);

        byte[] buffer = new byte[65536];
        int read;
        while ((read = dis.read(buffer)) != -1) {
            byte[] chunk = new byte[read];
            System.arraycopy(buffer, 0, chunk, 0, read);
            rc4.crypt(chunk);
            out.write(chunk);
        }
        out.close();
        dis.close();
        return meta;
    }

    private static byte[] padTo16(byte[] input) {
        int pad = 16 - (input.length % 16);
        if (pad == 16) return input;
        byte[] out = new byte[input.length + pad];
        System.arraycopy(input, 0, out, 0, input.length);
        return out;
    }

    private static String sanitize(String n) { return n.replaceAll("[\\\\/:*?\"<>|]", "_"); }

    static class RC4 {
        private final byte[] S = new byte[256];
        private int i = 0, j = 0;
        RC4(byte[] key) {
            for (int i = 0; i < 256; i++) S[i] = (byte) i;
            int j = 0;
            for (int i = 0; i < 256; i++) { j = (j + S[i] + key[i % key.length]) & 0xFF; byte tmp = S[i]; S[i] = S[j]; S[j] = tmp; }
        }
        void crypt(byte[] data) {
            for (int k = 0; k < data.length; k++) {
                i = (i + 1) & 0xFF; j = (j + S[i]) & 0xFF; byte tmp = S[i]; S[i] = S[j]; S[j] = tmp;
                data[k] ^= S[(S[i] + S[j]) & 0xFF];
            }
        }
    }
}

