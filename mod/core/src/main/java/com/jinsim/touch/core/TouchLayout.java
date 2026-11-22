package com.jinsim.touch.core;

/**
 * 布局模块：只算矩形，不负责画。坐标一律是 GUI 缩放后的坐标（HUD 回调里直接可用）。
 * 实际点击由启动器触摸层接管，这里的位置同时给启动器做命中区对齐用（见 README 的坐标约定）。
 */
public final class TouchLayout {

    public static final class Rect {
        public int left, top, right, bottom;
        public void set(int l, int t, int r, int b) { left = l; top = t; right = r; bottom = b; }
        public int width() { return right - left; }
        public int height() { return bottom - top; }
        public int cx() { return (left + right) / 2; }
        public int cy() { return (top + bottom) / 2; }
        public boolean contains(int x, int y) { return x >= left && x < right && y >= top && y < bottom; }
        @Override public String toString() { return "(" + left + "," + top + "," + right + "," + bottom + ")"; }
    }

    public final Rect up = new Rect();
    public final Rect down = new Rect();
    public final Rect left = new Rect();
    public final Rect right = new Rect();
    public final Rect exit = new Rect();
    public final Rect jump = new Rect();
    public final Rect sneak = new Rect();
    public final Rect inventory = new Rect();

    /** 单键边长（GUI 像素） */
    public int keySize;
    /** 屏幕宽度，供启动器换算归一化坐标 */
    public int screenWidth;
    public int screenHeight;

    private static final int MARGIN = 10;
    private static final int GAP = 4;

    /**
     * @param guiWidth  缩放后的宽度（Window#getScaledWidth）
     * @param guiHeight 缩放后的高度
     * @param scale     配置里的 keyScale
     */
    public void update(int guiWidth, int guiHeight, float scale) {
        screenWidth = guiWidth;
        screenHeight = guiHeight;

        int base = Math.min(guiWidth, guiHeight) / 12;
        if (base < 26) base = 26;
        if (base > 60) base = 60;
        keySize = Math.max(18, (int) (base * scale));

        // 左下十字键：以 (cx, cy) 为十字中心
        int pad = MARGIN + keySize / 2;
        int cx = pad + keySize;
        int cy = guiHeight - pad - keySize;

        up.set(cx - keySize / 2, cy - keySize / 2 - keySize - GAP,
                cx + keySize / 2, cy - keySize / 2 - GAP);
        down.set(cx - keySize / 2, cy + keySize / 2 + GAP,
                cx + keySize / 2, cy + keySize / 2 + GAP + keySize);
        left.set(cx - keySize / 2 - keySize - GAP, cy - keySize / 2,
                cx - keySize / 2 - GAP, cy + keySize / 2);
        right.set(cx + keySize / 2 + GAP, cy - keySize / 2,
                cx + keySize / 2 + GAP + keySize, cy + keySize / 2);

        // 右上 EXIT：单键大小的一半高度，方便单手点
        int ex = guiWidth - MARGIN - keySize * 2;
        exit.set(ex, MARGIN, guiWidth - MARGIN, MARGIN + Math.max(14, keySize / 2));

        // 右下动作键竖排：跳跃 / 潜行 / 背包
        int ax = guiWidth - MARGIN - keySize;
        int ay = guiHeight - MARGIN - keySize;
        inventory.set(ax, ay, ax + keySize, ay + keySize);
        sneak.set(ax, ay - keySize - GAP - keySize, ax + keySize, ay - keySize - GAP);
        jump.set(ax, ay - (keySize + GAP) * 2 - keySize, ax + keySize, ay - (keySize + GAP) * 2);
    }

    /** 把某个矩形换算成 0~1 归一化坐标（启动器触摸层对齐用） */
    public static float[] normalized(Rect r, int guiWidth, int guiHeight) {
        if (guiWidth <= 0 || guiHeight <= 0) return new float[]{0f, 0f, 0f, 0f};
        return new float[]{
                r.left / (float) guiWidth,
                r.top / (float) guiHeight,
                r.right / (float) guiWidth,
                r.bottom / (float) guiHeight
        };
    }
}
