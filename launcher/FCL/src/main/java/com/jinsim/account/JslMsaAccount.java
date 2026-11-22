package com.jinsim.account;

import com.tungsten.fclcore.auth.Account;
import com.tungsten.fclcore.auth.AuthInfo;
import com.tungsten.fclcore.auth.AuthenticationException;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 挂在 JinSim 云端的微软账号在启动器里的实体。
 *
 * 为什么要有这个类：FCL 原生账号页读的是它自己的账号仓库（Accounts 里的 Account 对象），
 * 而微软账号是绑在 JinSim 主账号下面的。这里做一个“桥”，把云端那条记录变成 FCL 认识的
 * Account，于是它会直接出现在原生账号页、点一下就能选中启动，不需要另开一个账号页面。
 *
 * 令牌策略：绑定当时就把 Minecraft 令牌写进来；过期或为空时，logIn() 走
 * POST /accounts/{id}/session 从云端取一次（只取自己的令牌，需要有效 JinSim 会话）。
 */
public class JslMsaAccount extends Account {

    private final String name;
    private final UUID uuid;
    private final long accountId;
    private final String sessionToken;
    private String accessToken;
    private long expiresAt;

    public JslMsaAccount(String name, UUID uuid, long accountId, String accessToken, long expiresAt, String sessionToken) {
        this.name = name;
        this.uuid = uuid;
        this.accountId = accountId;
        this.accessToken = accessToken == null ? "" : accessToken;
        this.expiresAt = expiresAt;
        this.sessionToken = sessionToken == null ? "" : sessionToken;
    }

    public long getAccountId() {
        return accountId;
    }

    public String getName() {
        return name;
    }

    /** 原生列表里只显示玩家名，不再拼 “xx - xx” */
    @Override
    public String getUsername() {
        return "";
    }

    @Override
    public String getCharacter() {
        return name;
    }

    @Override
    public UUID getUUID() {
        return uuid;
    }

    @Override
    public String getIdentifier() {
        return "jsl_msa:" + uuid;
    }

    private synchronized String token() throws AuthenticationException {
        if (!accessToken.isEmpty() && System.currentTimeMillis() < expiresAt - 120_000L) {
            return accessToken;
        }
        if (sessionToken.isEmpty()) {
            throw new AuthenticationException("JinSim 会话已失效，请重新登录 JinSim 账号");
        }
        try {
            JSONObject json = JslApi.session(sessionToken, accountId);
            String token = json.optString("mc_token");
            if (token.isEmpty()) {
                throw new AuthenticationException("服务端没有返回 Minecraft 令牌，请重新绑定微软账号");
            }
            accessToken = token;
            expiresAt = System.currentTimeMillis() + 3_600_000L;
            return token;
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthenticationException(e.getMessage() == null ? "取 Minecraft 令牌失败" : e.getMessage(), e);
        }
    }

    /** 给皮肤缓存用：拿得到就用，拿不到返回空串，不抛异常 */
    public String tokenOrEmpty() {
        try {
            return token();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public AuthInfo logIn() throws AuthenticationException {
        return new AuthInfo(name, uuid, token(), AuthInfo.USER_TYPE_MSA, "{}");
    }

    @Override
    public AuthInfo playOffline() {
        // 离线启动：给一个本地随机令牌，名字和 UUID 保持正版身份
        return new AuthInfo(name, uuid, UUID.randomUUID().toString().replace("-", ""), AuthInfo.USER_TYPE_MSA, "{}");
    }

    @Override
    public Map<Object, Object> toStorage() {
        Map<Object, Object> storage = new HashMap<>();
        storage.put("uuid", uuid.toString());
        storage.put("name", name);
        storage.put("account_id", accountId);
        storage.put("access_token", accessToken);
        storage.put("expires_at", expiresAt);
        storage.put("session", sessionToken);
        return storage;
    }
}
