package net.wsdjeg.nova;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 账号管理类
 * 支持多账号存储、切换、聚合等功能
 */
public class AccountManager {
    private static final String PREF_NAME = "nova_accounts";
    private static final String KEY_ACCOUNTS = "accounts";
    private static final String KEY_CURRENT_ACCOUNT_ID = "current_account_id";

    private static AccountManager instance;
    private final SharedPreferences prefs;
    private List<Account> accounts;
    private Account currentAccount;

    private AccountManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadAccounts();
    }

    public static synchronized AccountManager getInstance(Context context) {
        if (instance == null) {
            instance = new AccountManager(context);
        }
        return instance;
    }

    /**
     * 从 SharedPreferences 加载账号列表
     */
    private void loadAccounts() {
        accounts = new ArrayList<>();
        String accountsJson = prefs.getString(KEY_ACCOUNTS, "[]");

        try {
            JSONArray jsonArray = new JSONArray(accountsJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject json = jsonArray.getJSONObject(i);
                Account account = new Account();
                account.setId(json.getString("id"));
                account.setName(json.optString("name", ""));
                account.setUrl(json.getString("url"));
                account.setApiKey(json.optString("apiKey", ""));
                account.setActive(json.optBoolean("isActive", false));
                account.setCreatedAt(json.optLong("createdAt", System.currentTimeMillis()));
                account.setLastUsedAt(json.optLong("lastUsedAt", System.currentTimeMillis()));
                accounts.add(account);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // 加载当前激活账号
        String currentId = prefs.getString(KEY_CURRENT_ACCOUNT_ID, null);
        if (currentId != null) {
            currentAccount = getAccountById(currentId);
        }

        // 如果没有激活账号但有账号列表，激活第一个
        if (currentAccount == null && !accounts.isEmpty()) {
            currentAccount = accounts.get(0);
            currentAccount.setActive(true);
            saveAccounts();
        }
    }

    /**
     * 保存账号列表到 SharedPreferences
     */
    private void saveAccounts() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (Account account : accounts) {
                JSONObject json = new JSONObject();
                json.put("id", account.getId());
                json.put("name", account.getName());
                json.put("url", account.getUrl());
                json.put("apiKey", account.getApiKey() != null ? account.getApiKey() : "");
                json.put("isActive", account.isActive());
                json.put("createdAt", account.getCreatedAt());
                json.put("lastUsedAt", account.getLastUsedAt());
                jsonArray.put(json);
            }
            prefs.edit()
                    .putString(KEY_ACCOUNTS, jsonArray.toString())
                    .putString(KEY_CURRENT_ACCOUNT_ID, currentAccount != null ? currentAccount.getId() : "")
                    .apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取所有账号
     */
    public List<Account> getAccounts() {
        return new ArrayList<>(accounts);
    }

    /**
     * 获取当前激活的账号
     */
    public Account getCurrentAccount() {
        return currentAccount;
    }

    /**
     * 获取当前激活的账号（兼容性别名）
     */
    public Account getActiveAccount() {
        return getCurrentAccount();
    }

    /**
     * 根据 ID 获取账号
     */
    public Account getAccountById(String id) {
        for (Account account : accounts) {
            if (account.getId().equals(id)) {
                return account;
            }
        }
        return null;
    }

    /**
     * 根据 ID 获取账号（兼容性别名）
     */
    public Account getAccount(String id) {
        return getAccountById(id);
    }

    /**
     * 添加账号
     */
    public void addAccount(Account account) {
        // 如果是第一个账号，自动激活
        if (accounts.isEmpty()) {
            account.setActive(true);
            currentAccount = account;
        }
        accounts.add(account);
        saveAccounts();
    }

    /**
     * 更新账号
     */
    public void updateAccount(Account account) {
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).getId().equals(account.getId())) {
                accounts.set(i, account);
                if (account.isActive()) {
                    currentAccount = account;
                }
                break;
            }
        }
        saveAccounts();
    }

    /**
     * 删除账号
     */
    public void deleteAccount(String accountId) {
        Account toDelete = null;
        for (Account account : accounts) {
            if (account.getId().equals(accountId)) {
                toDelete = account;
                break;
            }
        }

        if (toDelete != null) {
            accounts.remove(toDelete);

            // 如果删除的是当前账号，激活第一个
            if (toDelete == currentAccount && !accounts.isEmpty()) {
                switchToAccount(accounts.get(0).getId());
            } else if (accounts.isEmpty()) {
                currentAccount = null;
            }
            saveAccounts();
        }
    }

    /**
     * 切换到指定账号
     */
    public void switchToAccount(String accountId) {
        // 取消所有账号的激活状态
        for (Account account : accounts) {
            account.setActive(false);
        }

        // 激活指定账号
        Account account = getAccountById(accountId);
        if (account != null) {
            account.setActive(true);
            account.updateLastUsed();
            currentAccount = account;
        }
        saveAccounts();
    }

    /**
     * 检查是否有账号
     */
    public boolean hasAccounts() {
        return !accounts.isEmpty();
    }

    /**
     * 获取账号数量
     */
    public int getAccountCount() {
        return accounts.size();
    }

    /**
     * 检查 URL 是否已存在
     */
    public boolean isUrlExists(String url) {
        for (Account account : accounts) {
            if (account.getUrl().equals(url)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查名称是否已存在
     */
    public boolean isNameExists(String name) {
        for (Account account : accounts) {
            if (account.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建默认账号（首次使用）
     */
    public void createDefaultAccount(String name, String url) {
        if (accounts.isEmpty()) {
            Account account = new Account(name, url);
            account.setActive(true);
            accounts.add(account);
            currentAccount = account;
            saveAccounts();
        }
    }

    /**
     * 获取所有账号（别名方法）
     */
    public List<Account> getAllAccounts() {
        return getAccounts();
    }

    /**
     * 移除账号（别名方法）
     */
    public void removeAccount(String accountId) {
        deleteAccount(accountId);
    }

    /**
     * 切换账号（别名方法）
     */
    public void switchAccount(String accountId) {
        switchToAccount(accountId);
    }
}
