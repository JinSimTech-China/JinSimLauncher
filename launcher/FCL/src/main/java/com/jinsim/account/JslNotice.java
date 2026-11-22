package com.jinsim.account;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * JinSim 后端公告（GET /api/v1/notices）的响应模型。
 * 与 FCL 自带的 announcement_v2.txt 相比，这里的公告由我们在 jinsim.jcmj.ren 管理后台上发布，
 * 不再依赖 FCL-Team 的远程文件。
 */
public final class JslNotice {

    /** 公告接口地址，与 {@link JslApi#BASE_URL} 同一个 Worker */
    public static final String URL = "https://jst-api.jcmj.ren/api/v1/notices";

    public boolean ok;
    public List<Item> notices;

    public static final class Item {
        public long id;
        public String title;
        public String body;
        /** info / important / maintenance */
        public String level;
        public int pinned;
        @SerializedName("created_at")
        public String createdAt;

        public boolean isSignificant() {
            return pinned == 1 || "important".equals(level) || "maintenance".equals(level);
        }

        /** 2026-11-21 20:07:31 → 2026.11.21 */
        public String displayDate() {
            if (createdAt == null || createdAt.isEmpty()) return "";
            String d = createdAt.length() >= 10 ? createdAt.substring(0, 10) : createdAt;
            return d.replace('-', '.');
        }
    }
}
