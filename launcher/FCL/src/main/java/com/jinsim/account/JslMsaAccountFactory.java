package com.jinsim.account;

import com.tungsten.fclcore.auth.AccountFactory;
import com.tungsten.fclcore.auth.AuthenticationException;
import com.tungsten.fclcore.auth.CharacterSelector;
import com.tungsten.fclcore.auth.offline.OfflineAccountFactory;

import java.util.Map;
import java.util.UUID;

/**
 * JslMsaAccount 的工厂：只负责把本地存储还原成 Account，以及提供列表里的类型名。
 * 真正的“新建”永远来自 WebView 授权流程，不在这个工厂里创建。
 */
public final class JslMsaAccountFactory extends AccountFactory<JslMsaAccount> {

    public static final JslMsaAccountFactory INSTANCE = new JslMsaAccountFactory();

    private JslMsaAccountFactory() {
    }

    @Override
    public AccountLoginType getLoginType() {
        return AccountLoginType.NONE;
    }

    @Override
    public JslMsaAccount create(CharacterSelector selector, String username, String password,
                                ProgressCallback progressCallback, Object additionalData) throws AuthenticationException {
        throw new AuthenticationException("请在启动器账号页点「添加微软账号」，在内置页面完成授权");
    }

    @Override
    public JslMsaAccount fromStorage(Map<Object, Object> storage) {
        String uuidText = String.valueOf(storage.get("uuid")).replace("-", "");
        UUID uuid = uuidText.length() == 32
                ? UUID.fromString(uuidText.replaceFirst(
                        "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"))
                : OfflineAccountFactory.getUUIDFromUserName(String.valueOf(storage.get("name")));
        long accountId = parseLong(storage.get("account_id"));
        String token = storage.get("access_token") == null ? "" : String.valueOf(storage.get("access_token"));
        long expiresAt = parseLong(storage.get("expires_at"));
        String session = storage.get("session") == null ? "" : String.valueOf(storage.get("session"));
        String name = String.valueOf(storage.get("name"));
        return new JslMsaAccount(name, uuid, accountId, token, expiresAt, session);
    }

    private static long parseLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0L;
        }
    }
}
