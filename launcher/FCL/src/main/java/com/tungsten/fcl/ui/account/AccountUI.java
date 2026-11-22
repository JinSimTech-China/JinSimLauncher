package com.tungsten.fcl.ui.account;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tungsten.fcl.R;
import com.tungsten.fcl.setting.Accounts;
import com.tungsten.fcl.setting.ConfigHolder;
import com.tungsten.fclcore.auth.authlibinjector.AuthlibInjectorServer;
import com.tungsten.fclcore.task.Task;
import com.tungsten.fcllibrary.component.ui.FCLCommonUI;
import com.jinsim.account.AccountRepository;
import com.jinsim.account.CloudAccounts;
import com.jinsim.account.ui.PersonalCenterActivity;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class AccountUI extends FCLCommonUI implements View.OnClickListener {

    private static final int MANAGE_JINSIM_ID = View.generateViewId();


    private LinearLayoutCompat addOfflineAccount;
    private LinearLayoutCompat addMicrosoftAccount;
    private LinearLayoutCompat addLoginServer;

    private RecyclerView recyclerView;

    private int dp(int value) {
        return (int) (value * getContext().getResources().getDisplayMetrics().density);
    }

    private AccountListAdapter accountListAdapter;

    public AccountUI(Context context, int id) {
        super(context, id);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        addOfflineAccount = findViewById(R.id.offline);
        addMicrosoftAccount = findViewById(R.id.microsoft);
        addLoginServer = findViewById(R.id.add_login_server);
        addOfflineAccount.setOnClickListener(this);
        addMicrosoftAccount.setOnClickListener(this);
        addLoginServer.setOnClickListener(this);

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        ListView serverListView = findViewById(R.id.server_list);
        serverListView.setAdapter(new ServerListAdapter(getContext()));

        applyJinSimState();
        // 先用本地缓存渲染（断网也能看到并启动），再从 JinSim 云端补齐
        loadAccounts();
        CloudAccounts.refresh(getContext(), this::loadAccounts);
    }

    /** 未登录 JinSim 时不出微软入口，只留一个登录入口；登录后才显示「添加微软账号 / 添加第三方登录」 */
    private void applyJinSimState() {
        boolean loggedIn = AccountRepository.Companion.get(getContext()).sessionToken() != null
                && !AccountRepository.Companion.get(getContext()).sessionToken().isEmpty();
        TextView microsoftText = (TextView) addMicrosoftAccount.getChildAt(1);
        microsoftText.setText(loggedIn ? "添加微软账号" : "登录 JinSim 账号");
        TextView thirdText = (TextView) addLoginServer.getChildAt(1);
        thirdText.setText("添加第三方登录");
        // 主色描边高亮：加粗 + 淡光晕（软件层渲染才吃 shadowLayer）
        // 登录后在原列表上方加一行「管理 JinSim 账号」（只加一次）
        View parent = (View) addLoginServer.getParent();
        if (loggedIn && parent instanceof android.view.ViewGroup
                && ((android.view.ViewGroup) parent).findViewById(MANAGE_JINSIM_ID) == null) {
            TextView manage = new TextView(getContext());
            manage.setId(MANAGE_JINSIM_ID);
            manage.setText("管理 JinSim 账号");
            manage.setTextSize(15f);
            manage.setPadding(dp(20), dp(12), dp(20), dp(12));
            manage.setGravity(android.view.Gravity.CENTER_VERTICAL);
            manage.setTypeface(null, android.graphics.Typeface.BOLD);
            manage.setOnClickListener(v -> PersonalCenterActivity.launch(getContext()));
            ((android.view.ViewGroup) parent).addView(manage);
        }
        for (TextView tv : new TextView[]{microsoftText, thirdText}) {
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
            tv.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            tv.setShadowLayer(6f, 0f, 0f, 0xFF99DAD6);
        }
        addLoginServer.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
    }

    /** 第三方登录（皮肤站/authlib-injector）：先选服务商，再输邮箱密码 */
    private void addThirdPartyAccount() {
        if (AccountRepository.Companion.get(getContext()).sessionToken() == null
                || AccountRepository.Companion.get(getContext()).sessionToken().isEmpty()) {
            PersonalCenterActivity.launch(getContext());
            return;
        }
        String[] providers = {"LittleSkin（littleskin.cn）", "自定义皮肤站地址"};
        new AlertDialog.Builder(getContext())
                .setTitle("添加第三方登录")
                .setItems(providers, (dialog, which) -> {
                    if (which == 0) {
                        openThirdParty("https://littleskin.cn/api/yggdrasil");
                    } else {
                        askServerUrl();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void askServerUrl() {
        EditText input = new EditText(getContext());
        input.setHint("https://example.com/api/yggdrasil");
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(getContext())
                .setTitle("皮肤站地址")
                .setMessage("填 authlib-injector 的 API 地址")
                .setView(input)
                .setPositiveButton("下一步", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (url.isEmpty()) return;
                    openThirdParty(url);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openThirdParty(String url) {
        AuthlibInjectorServer server = null;
        for (AuthlibInjectorServer existing : ConfigHolder.config().getAuthlibInjectorServers()) {
            if (url.equals(existing.getUrl())) {
                server = existing;
                break;
            }
        }
        if (server == null) {
            server = new AuthlibInjectorServer(url);
            ConfigHolder.config().getAuthlibInjectorServers().add(server);
        }
        CreateAccountDialog dialog = new CreateAccountDialog(getContext(), server);
        // 第三方账号登录成功后直接登记到云端，换设备能从账号页看到
        dialog.setOnDismissListener(d -> CloudAccounts.refresh(getContext(), this::loadAccounts));
        dialog.show();
    }

    public void loadAccounts() {
        ArrayList<AccountListItem> accountList = Accounts.getAccounts().stream()
                .map(account -> new AccountListItem(getContext(), account))
                .collect(Collectors.toCollection(ArrayList::new));
        if (accountListAdapter == null) {
            accountListAdapter = new AccountListAdapter(getContext(), accountList);
            recyclerView.setAdapter(accountListAdapter);
        } else {
            accountListAdapter.refresh(accountList);
        }
    }

    @Override
    public void onClick(View view) {
        if (view == addOfflineAccount) {
            CreateAccountDialog dialog = new CreateAccountDialog(getContext(), Accounts.FACTORY_OFFLINE);
            // 关掉弹窗后立刻把新建的离线账号登记到云端（已登录时），列表再按云端数据重排
            dialog.setOnDismissListener(d -> CloudAccounts.refresh(getContext(), this::loadAccounts));
            dialog.show();
        }
        if (view == addMicrosoftAccount) {
            // 未登录 JinSim 不展示微软登录入口，先登录主账号
            String session = AccountRepository.Companion.get(getContext()).sessionToken();
            if (session == null || session.isEmpty()) {
                PersonalCenterActivity.launch(getContext());
            } else {
                // 账号列表就是这一页：微软账号绑定后直接出现在下面
                // 只有“授权”这一步用内置 WebView，其余全部原生
                CloudAccounts.openBind(getContext());
            }
        }
        if (view == addLoginServer) {
            addThirdPartyAccount();
        }
    }

}
