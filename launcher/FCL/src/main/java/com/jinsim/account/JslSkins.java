package com.jinsim.account;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.tungsten.fclauncher.utils.FCLPath;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

/**
 * 微软账号的皮肤缓存。
 *
 * 为什么要自己缓存：JslMsaAccount 是自定义账号类型，FCL 原来那套“在线拉皮肤”的路子不认它，
 * 所以列表里永远是默认皮。这里做三件事：
 *  1. 用账号自己的 Minecraft 令牌请求 profile，拿到 skins[0].url；
 *  2. 把皮肤 PNG 落盘到 files/jsl_skins/<uuid>.png，断网也能显示；
 *  3. 启动器只读本地缓存，速度不依赖网络。
 */
public final class JslSkins {

    private static final String TAG = "JslSkins";
    private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final String UA = "MinecraftLauncher/2.4.22";

    private JslSkins() {
    }

    public static File cacheFile(UUID uuid) {
        File dir = new File(FCLPath.FILES_DIR, "jsl_skins");
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "无法创建皮肤缓存目录: " + dir);
        }
        return new File(dir, uuid.toString().replace("-", "") + ".png");
    }

    /** 本地缓存，没有就返回 null（调用方回落到默认皮） */
    public static Bitmap cached(UUID uuid) {
        File file = cacheFile(uuid);
        if (!file.exists() || file.length() <= 0) return null;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        return bitmap != null && bitmap.getWidth() >= 64 ? bitmap : null;
    }

    /** 后台补一次皮肤，已有缓存直接跳过 */
    public static void ensureAsync(final JslMsaAccount account) {
        if (account == null) return;
        final UUID uuid = account.getUUID();
        if (cacheFile(uuid).exists()) return;
        Thread worker = new Thread(() -> {
            try {
                String token = account.tokenOrEmpty();
                if (token.isEmpty()) return;
                String skinUrl = fetchSkinUrl(token);
                if (skinUrl == null || skinUrl.isEmpty()) return;
                downloadSkin(skinUrl, cacheFile(uuid));
            } catch (Exception e) {
                Log.w(TAG, "补皮肤失败: " + e.getMessage());
            }
        }, "jsl-skin-" + uuid);
        worker.setDaemon(true);
        worker.start();
    }

    private static String fetchSkinUrl(String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(PROFILE_URL).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(15_000);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", UA);
        try (InputStream in = conn.getInputStream()) {
            String body = new String(readAll(in), "UTF-8");
            JSONObject json = new JSONObject(body);
            JSONArray skins = json.optJSONArray("skins");
            if (skins == null || skins.length() == 0) return null;
            JSONObject skin = skins.optJSONObject(0);
            return skin == null ? null : skin.optString("url", "");
        } finally {
            conn.disconnect();
        }
    }

    private static void downloadSkin(String url, File dest) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20_000);
        conn.setReadTimeout(20_000);
        conn.setRequestProperty("User-Agent", UA);
        try (InputStream in = conn.getInputStream()) {
            byte[] data = readAll(in);
            if (data.length < 100) return;
            File tmp = new File(dest.getAbsolutePath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(data);
            }
            if (!tmp.renameTo(dest)) {
                // 重命名失败也要保证最终有文件
                try (FileOutputStream out = new FileOutputStream(dest)) {
                    out.write(data);
                }
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) > 0) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
