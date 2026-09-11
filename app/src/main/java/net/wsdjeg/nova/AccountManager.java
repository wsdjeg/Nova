package net.wsdjeg.nova;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 账号管理类
 * 支持多账号存储、切换、聚合等功能
 * 
 * 默认账号规则：
 * 1. 无账号时添加：第一个账号自动设为默认，后续添加为非默认
 * 2. 无账号时导入：检查导入数据是否有 isDefault，有则使用，无则第一个为默认
 * 3. 有账号时导入：导入的全部为非默认（保持现有默认账号）
 * 4. 删除默认账号：自动将第一个剩余账号设为默认
 */
public class AccountManager {
    private static final String PREF_NAME = "nova_accounts";
    private static final String KEY_ACCOUNTS = "accounts";
    private static final String KEY_CURRENT_ACCOUNT_ID = "current_account_id";
    private static final String KEY_COLOR_INDEX_MIGRATED = "color_index_migrated_v1";

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

        boolean parseOk = true;
        try {
            JSONArray jsonArray = new JSONArray(accountsJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject json = jsonArray.getJSONObject(i);
                Account account = new Account();
                account.setId(json.getString("id"));
                account.setName(json.optString("name", ""));
                
                // 兼容旧数据：优先读取 host/port，如果没有则读取 url
                if (json.has("host")) {
                    account.setHost(json.getString("host"));
                    account.setPort(json.optInt("port", 8080));
                } else {
                    account.setUrl(json.optString("url", ""));
                }
                
                account.setApiKey(json.optString("apiKey", ""));
                account.setActive(json.optBoolean("isDefault", false));
                account.setCreatedAt(json.optLong("createdAt", System.currentTimeMillis()));
                account.setLastUsedAt(json.optLong("lastUsedAt", System.currentTimeMillis()));
                account.setColorIndex(json.optInt("colorIndex", -1));  // 默认使用全局设置
                accounts.add(account);
            }
        } catch (JSONException e) {
            parseOk = false;
            e.printStackTrace();
        }

        // 加载当前默认账号
        String currentId = prefs.getString(KEY_CURRENT_ACCOUNT_ID, null);
        if (currentId != null) {
            currentAccount = getAccountById(currentId);
        }

        // 如果没有默认账号但有账号列表，设置第一个为默认
        if (currentAccount == null && !accounts.isEmpty()) {
            currentAccount = accounts.get(0);
            currentAccount.setActive(true);
            saveAccounts();
        }

        // 一次性迁移：旧版账号颜色索引（1-8）归一化为当前调色板索引（0-4）
        // 放在默认账号逻辑之后，避免迁移时的回写丢失 current_account_id
        if (parseOk) {
            migrateLegacyColorIndex();
        }
    }

    /**
     * 一次性迁移旧版账号颜色索引
     *
     * 旧版账号编辑页将自定义颜色存为 1-8（对应旧 8 色调色板），而渲染端按
     * ACCOUNT_TAG_COLORS 下标取色（索引错位，且 8 会被视为未设置颜色）。
     * 新版统一存储 -1（跟随全局）或 0..length-1（length 为当前调色板颜色数），
     * 此处将旧数据 1..length 平移为 0..length-1，使颜色与用户当初选中的
     * 选项一致；指向已移除颜色的旧值（超出当前调色板范围）重置为 -1。
     */
    private void migrateLegacyColorIndex() {
        if (prefs.getBoolean(KEY_COLOR_INDEX_MIGRATED, false)) {
            return;
        }
        boolean changed = false;
        for (Account account : accounts) {
            int legacy = account.getColorIndex();
            if (legacy >= 1 && legacy <= SettingsManager.ACCOUNT_TAG_COLORS.length) {
                account.setColorIndex(legacy - 1);
                changed = true;
            } else if (legacy > SettingsManager.ACCOUNT_TAG_COLORS.length) {
                // 异常值（不可能来自旧版取色器），重置为跟随全局
                account.setColorIndex(SettingsManager.AUTO_COLOR_INDEX);
                changed = true;
            }
        }
        if (changed) {
            saveAccounts();
        }
        prefs.edit().putBoolean(KEY_COLOR_INDEX_MIGRATED, true).apply();
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
                json.put("host", account.getHost());
                json.put("port", account.getPort());
                json.put("url", account.getUrl());  // 兼容旧版本
                json.put("apiKey", account.getApiKey() != null ? account.getApiKey() : "");
                json.put("isDefault", account.isActive());
                json.put("createdAt", account.getCreatedAt());
                json.put("lastUsedAt", account.getLastUsedAt());
                json.put("colorIndex", account.getColorIndex());
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
     * 获取当前默认账号
     */
    public Account getCurrentAccount() {
        return currentAccount;
    }

    /**
     * 获取当前默认账号（兼容性别名）
     */
    public Account getDefaultAccount() {
        return getCurrentAccount();
    }

    /**
     * 获取当前激活的账号（兼容旧名称）
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
     * 规则：第一个账号自动设为默认，后续添加为非默认
     */
    public void addAccount(Account account) {
        // 如果是第一个账号，自动设为默认
        if (accounts.isEmpty()) {
            account.setActive(true);
            currentAccount = account;
        } else {
            // 已有账号时，新添加的账号为非默认
            account.setActive(false);
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
     * 规则：如果删除的是默认账号，自动将第一个剩余账号设为默认
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

            // 如果删除的是当前默认账号，设置第一个为默认
            if (toDelete == currentAccount && !accounts.isEmpty()) {
                setDefaultAccount(accounts.get(0).getId());
            } else if (accounts.isEmpty()) {
                currentAccount = null;
            }
            saveAccounts();
        }
    }

    /**
     * 设置默认账号
     */
    public void setDefaultAccount(String accountId) {
        // 取消所有账号的默认状态
        for (Account account : accounts) {
            account.setActive(false);
        }

        // 设置指定账号为默认
        Account account = getAccountById(accountId);
        if (account != null) {
            account.setActive(true);
            account.updateLastUsed();
            currentAccount = account;
        }
        saveAccounts();
    }

    /**
     * 切换到指定账号（兼容旧方法名）
     */
    public void switchToAccount(String accountId) {
        setDefaultAccount(accountId);
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
     * 检查 Host 是否已存在
     */
    public boolean isHostExists(String host) {
        for (Account account : accounts) {
            if (account.getHost().equals(host)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查 URL 是否已存在（兼容旧方法）
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
        setDefaultAccount(accountId);
    }
    
    /**
     * 获取账号的颜色
     * 优先级：账号自己的颜色 > 全局设置
     * @param account 账号
     * @param settingsManager 设置管理器
     * @return 颜色字符串
     */
    public static String getAccountColor(Account account, SettingsManager settingsManager) {
        // 如果账号设置了自定义颜色，优先使用
        if (account.hasCustomColor()) {
            return SettingsManager.ACCOUNT_TAG_COLORS[account.getColorIndex()];
        }
        
        // 检查全局设置是否为自动模式
        if (settingsManager.isAutoColorMode()) {
            // 自动分配颜色
            return SettingsManager.getAutoAssignedColor(account.getId());
        }
        
        // 使用全局默认颜色
        return settingsManager.getAccountTagColor();
    }

    /**
     * 归一化导入数据中的颜色索引
     *
     * @param colorIndex 导入文件中的颜色索引
     * @param legacy     true 表示 version < 2 的旧导出格式（自定义颜色存为 1-8）
     * @return -1（跟随全局）或 0-4
     */
    private static int normalizeImportedColorIndex(int colorIndex, boolean legacy) {
        if (legacy && colorIndex >= 1 && colorIndex <= SettingsManager.ACCOUNT_TAG_COLORS.length) {
            return colorIndex - 1;
        }
        if (colorIndex >= 0 && colorIndex < SettingsManager.ACCOUNT_TAG_COLORS.length) {
            return colorIndex;
        }
        return SettingsManager.AUTO_COLOR_INDEX;
    }

    /**
     * 导出所有账号为 JSON 字符串
     * @return JSON 字符串
     */
    public String toJson() {
        try {
            JSONObject root = new JSONObject();
            // version 2：colorIndex 语义统一为 -1（跟随全局）或 0-4（固定颜色），
            // version 1 的旧导出文件中自定义颜色为 1-8，导入时会做归一化
            root.put("version", 2);
            root.put("exportTime", System.currentTimeMillis());
            root.put("appName", "Nova");
            
            JSONArray accountsArray = new JSONArray();
            for (Account account : accounts) {
                JSONObject json = new JSONObject();
                json.put("id", account.getId());
                json.put("name", account.getName());
                json.put("host", account.getHost());
                json.put("port", account.getPort());
                json.put("apiKey", account.getApiKey() != null ? account.getApiKey() : "");
                json.put("isDefault", account.isActive());
                json.put("createdAt", account.getCreatedAt());
                json.put("lastUsedAt", account.getLastUsedAt());
                json.put("colorIndex", account.getColorIndex());
                accountsArray.put(json);
            }
            root.put("accounts", accountsArray);
            
            return root.toString(2);  // 格式化输出
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 从 JSON 字符串导入账号（追加模式）
     * 规则：
     * - 无账号时导入：检查导入数据是否有 isDefault，有则使用，无则第一个为默认
     * - 有账号时导入：导入的全部为非默认（保持现有默认账号）
     * 
     * @param jsonString JSON 字符串
     * @return 导入的账号数量
     * @throws JSONException 解析错误时抛出
     */
    public int importFromJson(String jsonString) throws JSONException {
        JSONObject root = new JSONObject(jsonString);
        
        // 检查版本（version < 2 的导出文件中 colorIndex 为旧语义 1-8）
        int version = root.optInt("version", 1);
        boolean legacyColorIndex = version < 2;
        
        JSONArray accountsArray = root.getJSONArray("accounts");
        Account firstImportedAccount = null;
        Account markedDefaultAccount = null;  // 导入数据中标记为默认的账号
        int importedCount = 0;
        
        // 是否已有账号（决定默认账号逻辑）
        boolean hadExistingAccounts = !accounts.isEmpty();
        
        for (int i = 0; i < accountsArray.length(); i++) {
            JSONObject json = accountsArray.getJSONObject(i);
            
            // 检查是否已存在相同 host 的账号
            String host = json.optString("host", "");
            if (!host.isEmpty() && isHostExists(host)) {
                // 跳过已存在的账号
                continue;
            }
            
            Account account = new Account();
            // 生成新的 ID，避免 ID 冲突
            account.setId(UUID.randomUUID().toString());
            account.setName(json.optString("name", ""));
            account.setHost(host);
            account.setPort(json.optInt("port", 8080));
            account.setApiKey(json.optString("apiKey", ""));
            account.setCreatedAt(json.optLong("createdAt", System.currentTimeMillis()));
            account.setLastUsedAt(json.optLong("lastUsedAt", System.currentTimeMillis()));
            account.setColorIndex(
                    normalizeImportedColorIndex(json.optInt("colorIndex", -1), legacyColorIndex));
            
            // 默认账号逻辑：
            // - 有账号时：导入的全部为非默认
            // - 无账号时：检查导入数据的 isDefault 标记
            if (hadExistingAccounts) {
                account.setActive(false);
            } else {
                // 无账号时，检查导入数据是否有 isDefault 标记
                boolean isMarkedDefault = json.optBoolean("isDefault", false);
                if (isMarkedDefault && markedDefaultAccount == null) {
                    // 第一个标记为默认的账号设为默认
                    account.setActive(true);
                    markedDefaultAccount = account;
                } else {
                    account.setActive(false);
                }
            }
            
            accounts.add(account);
            
            // 记录第一个成功导入的账号（用于无默认账号时的后备）
            if (firstImportedAccount == null) {
                firstImportedAccount = account;
            }
            
            importedCount++;
        }
        
        // 如果导入成功且当前没有默认账号，设置默认账号
        if (importedCount > 0 && currentAccount == null) {
            if (markedDefaultAccount != null) {
                // 使用导入数据中标记为默认的账号
                currentAccount = markedDefaultAccount;
            } else if (firstImportedAccount != null) {
                // 后备：使用第一个导入的账号
                firstImportedAccount.setActive(true);
                currentAccount = firstImportedAccount;
            }
        }
        
        if (importedCount > 0) {
            saveAccounts();
        }
        
        return importedCount;
    }

    /**
     * 清空所有账号并从 JSON 导入（覆盖模式）
     * 规则：检查导入数据是否有 isDefault，有则使用，无则第一个为默认
     * 
     * @param jsonString JSON 字符串
     * @return 导入的账号数量
     * @throws JSONException 解析错误时抛出
     */
    public int importFromJsonOverride(String jsonString) throws JSONException {
        JSONObject root = new JSONObject(jsonString);
        
        // 检查版本（version < 2 的导出文件中 colorIndex 为旧语义 1-8）
        int version = root.optInt("version", 1);
        boolean legacyColorIndex = version < 2;
        
        JSONArray accountsArray = root.getJSONArray("accounts");
        Account markedDefaultAccount = null;  // 导入数据中标记为默认的账号
        Account firstImportedAccount = null;
        int importedCount = 0;
        
        // 清空现有账号
        accounts.clear();
        currentAccount = null;
        
        for (int i = 0; i < accountsArray.length(); i++) {
            JSONObject json = accountsArray.getJSONObject(i);
            
            Account account = new Account();
            account.setId(UUID.randomUUID().toString());  // 生成新 ID
            account.setName(json.optString("name", ""));
            account.setHost(json.optString("host", ""));
            account.setPort(json.optInt("port", 8080));
            account.setApiKey(json.optString("apiKey", ""));
            account.setCreatedAt(json.optLong("createdAt", System.currentTimeMillis()));
            account.setLastUsedAt(json.optLong("lastUsedAt", System.currentTimeMillis()));
            account.setColorIndex(
                    normalizeImportedColorIndex(json.optInt("colorIndex", -1), legacyColorIndex));
            
            // 检查导入数据是否有 isDefault 标记
            boolean isMarkedDefault = json.optBoolean("isDefault", false);
            if (isMarkedDefault && markedDefaultAccount == null) {
                // 第一个标记为默认的账号设为默认
                account.setActive(true);
                markedDefaultAccount = account;
            } else {
                account.setActive(false);
            }
            
            // 记录第一个导入的账号（后备）
            if (firstImportedAccount == null) {
                firstImportedAccount = account;
            }
            
            accounts.add(account);
            importedCount++;
        }
        
        // 设置默认账号
        if (importedCount > 0) {
            if (markedDefaultAccount != null) {
                currentAccount = markedDefaultAccount;
            } else if (firstImportedAccount != null) {
                // 没有标记为默认的账号，使用第一个
                firstImportedAccount.setActive(true);
                currentAccount = firstImportedAccount;
            }
        }
        
        saveAccounts();
        
        return importedCount;
    }
}

