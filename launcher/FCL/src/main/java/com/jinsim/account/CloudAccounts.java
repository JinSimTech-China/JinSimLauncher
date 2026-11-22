package com.jinsim.account;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.jinsim.account.ui.MsaLoginActivity;
import com.jinsim.account.ui.PersonalCenterActivity;
import com.tungsten.fcl.setting.Accounts;
import com.tungsten.fclcore.auth.Account;
import com.tungsten.fclcore.auth.authlibinjector.AuthlibInjectorAccount;
import com.tungsten.fclcore.auth.offline.OfflineAccount;

import java.util.ArrayList;
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
        // 主线程取一份快照，避免工作线程直接读 ObservableList
        final List<Account> local = new ArrayList<>(Accounts.getAccounts());
        Thread worker = new Thread(() -> {
            try {
                pushMissingSync(session, local);
            } catch (Exception ignored) {
                // 登记失败不影响本地使用，下次进页面再试
            }
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
        for (BoundAccount bound : cloud) {
            UUID uuid = parseUuid(bound.getUuid());
            if (uuid == null) continue;
            String name = bound.getPlayerName().isEmpty() ? bound.getDisplayName() : bound.getPlayerName();
            if (name == null || name.isEmpty()) continue;

            switch (bound.getType()) {
                case OFFLINE: {
                    // 本地已经有这个 uuid（可能带自定义皮肤），不重建，避免皮肤丢失
                    if (findLocal(uuid) != null) continue;
                    Account offline = Accounts.FACTORY_OFFLINE.create(name, uuid);
                    Accounts.replaceAccount(uuid, offline);
                    break;
                }
                case AUTHLIB: {
                    // 第三方皮肤站账号要靠本地密码会话换 yggdrasil 令牌，
                    // 跨设备只能同步“存在这个账号”，启动仍需在本机重新登录一次
                    if (findLocal(uuid) != null) continue;
                    break;
                }
                case MICROSOFT: {
                    long accountId = parseLong(bound.getId());
                    if (accountId <= 0L) continue;
                    Account existing = findLocal(uuid);
                    if (existing instanceof JslMsaAccount
                            && ((JslMsaAccount) existing).getAccountId() == accountId) {
                        continue;
                    }
                    JslMsaAccount account = new JslMsaAccount(name, uuid, accountId, "", 0L, session);
                    Accounts.replaceAccount(uuid, account);
                    JslSkins.ensureAsync(account);
                    break;
                }
                default:
                    break;
            }
        }
    }

    private static Account findLocal(UUID uuid) {
        for (Account account : Accounts.getAccounts()) {
            if (account.getUUID().equals(uuid)) return account;
        }
        return null;
    }

    /**
     * 统一账号 ID：本地有的离线 / 第三方账号，云端没有登记的补一条，
     * 这样三种账号在云端都占同一张表的一行、同一个 id，资料以服务端为准。
     */
    private static void pushMissingSync(String session, List<Account> local) throws Exception {
        List<BoundAccount> cloud = JslApi.INSTANCE.pullAccounts(session);
        for (Account account : local) {
            String uuidHex = account.getUUID().toString().replace("-", "");
            if (containsUuid(cloud, uuidHex)) continue;
            if (account instanceof OfflineAccount) {
                JslApi.pushOffline(session, account.getUsername(), uuidHex);
            } else if (account instanceof AuthlibInjectorAccount) {
                String url = ((AuthlibInjectorAccount) account).getServer().getUrl();
                JslApi.pushAuthlib(session, account.getUsername(), uuidHex, url);
            }
        }
    }

    private static boolean containsUuid(List<BoundAccount> cloud, String uuidHex) {
        for (BoundAccount bound : cloud) {
            if (parseUuid(bound.getUuid()) != null
                    && parseUuid(bound.getUuid()).toString().replace("-", "").equalsIgnoreCase(uuidHex)) {
                return true;
            }
        }
        return false;
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
