package com.tungsten.fcl.ui.main;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;

import com.mio.skin.AnimationDialog;
import com.mio.skin.SkinRenderer;
import com.mio.skin.SkinTextureLoader;
import com.mio.skin.SkinViewer;

import static com.mio.skin.SkinAnimationsKt.restoreSkinSettings;
import static com.mio.skin.SkinAnimationsKt.saveSkinSettings;
import com.jinsim.account.JslNotice;
import com.tungsten.fcl.R;
import com.tungsten.fcl.setting.Accounts;
import com.tungsten.fclcore.auth.Account;
import com.tungsten.fclcore.fakefx.beans.InvalidationListener;
import com.tungsten.fclcore.task.Schedulers;
import com.tungsten.fclcore.task.Task;
import com.tungsten.fclcore.util.Logging;
import com.tungsten.fclcore.util.io.HttpRequest;
import com.tungsten.fcllibrary.component.dialog.FCLAlertDialog;
import com.tungsten.fcllibrary.component.theme.ThemeEngine;
import com.tungsten.fcllibrary.component.ui.FCLCommonUI;
import com.tungsten.fcllibrary.component.view.FCLButton;
import com.tungsten.fcllibrary.component.view.FCLTextView;
import com.tungsten.fcllibrary.util.LocaleUtils;

import java.util.ArrayList;

import java.util.logging.Level;

public class MainUI extends FCLCommonUI implements View.OnClickListener {

    /** 公告来自 JinSim 账号后端（管理后台发布），不再读取 FCL-Team 的远程公告文件 */
    public static final String ANNOUNCEMENT_URL = JslNotice.URL;
    /** 记录用户点过“不再显示”的公告 id，与 FCL 自带公告分开存，避免互相顶掉 */
    private static final String PREF_IGNORED_NOTICE = "ignore_announcement_jsl";


    private LinearLayoutCompat announcementContainer;
    private LinearLayoutCompat announcementLayout;
    private FCLTextView title;
    private FCLTextView announcementView;
    private FCLTextView date;
    private FCLButton hide;
    private Announcement announcement = null;

    /** 皮肤模型预览视图（public 供引导遮罩以它为高亮目标） */
    public SkinViewer skinViewer;
    private SkinRenderer renderer;
    private SkinTextureLoader skinLoader;

    /** 选中账户变化时重载皮肤（attach 时注册、detach 时注销） */
    private final InvalidationListener accountListener = o ->
            skinLoader.load(Accounts.getSelectedAccount(), false);

    public MainUI(Context context, int id) {
        super(context, id);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        announcementContainer = findViewById(R.id.announcement_container);
        announcementLayout = findViewById(R.id.announcement_layout);
        title = findViewById(R.id.title);
        announcementView = findViewById(R.id.announcement);
        date = findViewById(R.id.date);
        hide = findViewById(R.id.hide);
        ThemeEngine.getInstance().registerEvent(announcementLayout, () -> announcementLayout.getBackground().setTint(ThemeEngine.getInstance().getTheme().getColor()));
        hide.setOnClickListener(this);

        skinViewer = findViewById(R.id.skin_viewer);
        renderer = new SkinRenderer(getContext());
        skinViewer.setRenderer(renderer, 5f);
        skinLoader = new SkinTextureLoader(renderer);
        skinLoader.load(Accounts.getSelectedAccount(), false);
        // 恢复上次选择的动画与 3D 皮肤层开关
        restoreSkinSettings(getContext(), renderer);
        // 双指点击模型弹出皮肤模型设置窗口
        skinViewer.setOnTwoFingerTap(() -> {
            Context context = getContext();
            if (context instanceof Activity && !((Activity) context).isDestroyed() && !((Activity) context).isFinishing()) {
                new AnimationDialog(context, renderer.getAnimation(), renderer.getOverlayEnabled(), animation -> {
                    renderer.playAnimation(animation);
                    saveSkinSettings(context, renderer);
                }, enabled -> {
                    renderer.setOverlayEnabled(enabled);
                    saveSkinSettings(context, renderer);
                }).show();
            }
        });
        checkAnnouncement();

        // 皮肤渲染随页面挂载/回收恢复与暂停（替代原 onStart/onStop 生命周期）
        getContentView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                if (skinViewer != null) {
                    if (!ThemeEngine.getInstance().getTheme().isCloseSkinModel()) {
                        skinViewer.setVisibility(View.VISIBLE);
                        skinViewer.onResume();
                        // 纹理由渲染线程 onSurfaceCreated 从 renderer.texture 自行重建，
                        // 此处不重喂纹理；同账户已在 onCreate 加载过，load 会自动跳过
                    } else {
                        skinViewer.onPause();
                        skinViewer.setVisibility(View.GONE);
                    }
                }
                if (skinLoader != null) {
                    // detach 期间选中的账户可能已切换（监听已注销），重新对齐一次
                    Accounts.selectedAccountProperty().addListener(accountListener);
                    skinLoader.load(Accounts.getSelectedAccount(), false);
                }
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                Accounts.selectedAccountProperty().removeListener(accountListener);
                if (skinLoader != null) {
                    skinLoader.release();
                }
                if (skinViewer != null) {
                    skinViewer.onPause();
                    skinViewer.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        if (skinViewer != null) {
            skinViewer.onPause();
            skinViewer.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (skinViewer != null && isShowing() && !ThemeEngine.getInstance().getTheme().isCloseSkinModel()) {
            skinViewer.setVisibility(View.VISIBLE);
            skinViewer.onResume();
        }
    }


    private void checkAnnouncement() {
        try {
            Task.supplyAsync(() -> HttpRequest.HttpGetRequest.GET(ANNOUNCEMENT_URL).getJson(JslNotice.class))
                    .thenAcceptAsync(Schedulers.androidUIThread(), notice -> {
                        if (notice == null || notice.notices == null || notice.notices.isEmpty())
                            return;
                        JslNotice.Item item = notice.notices.get(0);

                        // 包成 FCL 原有的 Announcement，复用它的展示逻辑（含“重要公告”二次确认）
                        ArrayList<Announcement.Content> titles = new ArrayList<>();
                        titles.add(new Announcement.Content("zh", item.title == null ? "" : item.title));
                        ArrayList<Announcement.Content> contents = new ArrayList<>();
                        contents.add(new Announcement.Content("zh", item.body == null ? "" : item.body));
                        Announcement a = new Announcement(
                                (int) item.id,
                                item.isSignificant(),
                                false, -1, -1,
                                new ArrayList<>(),
                                titles,
                                item.displayDate(),
                                contents
                        );
                        this.announcement = a;

                        int ignored = getContext().getSharedPreferences("launcher", Context.MODE_PRIVATE)
                                .getInt(PREF_IGNORED_NOTICE, 0);
                        if (item.id <= ignored)
                            return;

                        announcementContainer.setVisibility(View.VISIBLE);
                        title.setText(getContext().getString(R.string.announcement, a.getDisplayTitle(getContext())));
                        announcementView.setText(a.getDisplayContent(getContext()));
                        date.setText(getContext().getString(R.string.update_date, a.getDate()));
                    }).start();
        } catch (Exception e) {
            Logging.LOG.log(Level.WARNING, "Failed to get announcement!", e);
        }
    }

    private void hideAnnouncement() {
        announcementContainer.setVisibility(View.GONE);
        if (announcement != null) {
            @SuppressLint("CommitPrefEdits")
            SharedPreferences.Editor editor = getContext()
                    .getSharedPreferences("launcher", Context.MODE_PRIVATE).edit();
            editor.putInt(PREF_IGNORED_NOTICE, announcement.getId());
            editor.apply();
        }
    }

    public void refreshSkin(Account account) {
        Schedulers.androidUIThread().execute(() -> {
            if (skinLoader != null) {
                skinLoader.load(account, true);
            }
        });
    }

    @Override
    public void onClick(View view) {
        if (view == hide) {
            if (announcement != null && announcement.isSignificant()) {
                FCLAlertDialog.Builder builder = new FCLAlertDialog.Builder(getContext());
                builder.setAlertLevel(FCLAlertDialog.AlertLevel.ALERT);
                builder.setCancelable(false);
                builder.setMessage(getContext().getString(R.string.announcement_significant));
                builder.setPositiveButton(this::hideAnnouncement);
                builder.setNegativeButton(null);
                builder.create().show();
            } else {
                hideAnnouncement();
            }
        }
    }
}
