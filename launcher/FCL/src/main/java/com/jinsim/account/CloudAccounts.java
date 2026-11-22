package com.jinsim.account;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.jinsim.account.ui.MsaLoginActivity;
import com.jinsim.account.ui.PersonalCenterActivity;
import com.tungsten.fcl.setting.Accounts;

import java.util.List;
import java.util.UUID;

/**
 * 云端账号 → FCL 原生账号页 的桥。
 *
 * 单写多读：写（绑定/删除）都落在 JinSim 云端；读（列表渲染、启动选号）仍读 FCL 本地仓库。
 * 这里负责把云端有的微软账号补进本地仓库（按 UUID 去重），于是一进账号页就能看到，
 * 而且断网也能用上一次同步下来的数据启动。
 */
public final class CloudAccounts {

    private CloudAccounts() {
    }

    private static AccountRepository repo(Context context) {
        return AccountRepository.Companion.get(context.getApplicationContext());
    }

    /** 进账号页时静默同步；完成后在 Android 主线程回调（accounts 是 ObservableList，必须在主线程改） */
    public static void refresh(Context context, Runnable onDone) {
        final Context app = context.getApplicationContext();
        final String session = repo(app).sessionToken();
        if (session == null || session.isEmpty()) {
            if (onDone != null) onDone.run();
            return;
        }
        Thread worker = new Thread(() -> {
            List<BoundAccount> cloud = null;
            try {
                cloud = JslApi.INSTANCE.pullAccounts(session);
            } catch (Exception ignored) {
                // 云端不可达就保持本地数据，不影响离线使用
            }
            final List<BoundAccount> list = cloud;
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    if (list != null) merge(list, session);
                } finally {
                    if (onDone != null) onDone.run();
                }
            });
        }, "jsl-cloud-sync");
        worker.start();
    }

    private static void merge(List<BoundAccount> cloud, String session) {
        outer:
        for (BoundAccount bound : cloud) {
            if (bound.getType() != AccountType.MICROSOFT) continue;
            UUID uuid = parseUuid(bound.getUuid());
            if (uuid == null) continue;
            String name = bound.getPlayerName().isEmpty() ? bound.getDisplayName() : bound.getPlayerName();
            if (name == null || name.isEmpty()) continue;
            long accountId = parseLong(bound.getId());
            if (accountId <= 0L) continue;

            // 已存在且还是同一个云端 id：保留已有令牌，避免重复请求
            for (com.tungsten.fclcore.auth.Account existing : Accounts.getAccounts()) {
                if (existing instanceof JslMsaAccount
                        && ((JslMsaAccount) existing).getAccountId() == accountId
                        && existing.getUUID().equals(uuid)) {
                    continue outer;
                }
            }
            JslMsaAccount account = new JslMsaAccount(name, uuid, accountId, "", 0L, session);
            Accounts.replaceAccount(uuid, account);
            JslSkins.ensureAsync(account);
        }
    }

    /** 绑定成功后立刻写进原生账号页，回到页面就能看到（不等下一次云端同步） */
    public static void addBound(Context context, long accountId, String name, String uuidText,
                                String mcToken, String session) {
        UUID uuid = parseUuid(uuidText);
        if (uuid == null || name == null || name.isEmpty()) return;
        JslMsaAccount account =
                new JslMsaAccount(name, uuid, accountId, mcToken, System.currentTimeMillis() + 3_600_000L, session);
        Accounts.replaceAccount(uuid, account);
        JslSkins.ensureAsync(account);
    }

    /**
     * 「添加微软账号」入口：已登录 JinSim 就直接开 WebView 授权；
     * 没登录先去登 JinSim 主账号，登完自动续上授权。
     */
    public static void openBind(Context context) {
        String session = repo(context).sessionToken();
        if (session != null && !session.isEmpty()) {
            context.startActivity(new Intent(context, MsaLoginActivity.class));
        } else {
            PersonalCenterActivity.launchForBind(context);
        }
    }

    private static UUID parseUuid(String text) {
        if (text == null) return null;
        String hex = text.replace("-", "").trim();
        if (hex.length() != 32) return null;
        try {
            return UUID.fromString(hex.replaceFirst(
                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseLong(String text) {
        try {
            return Long.parseLong(String.valueOf(text).trim());
        } catch (Exception e) {
            return 0L;
        }
    }
}
