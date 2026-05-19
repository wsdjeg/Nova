package net.wsdjeg.nova;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

/**
 * 会话管理器
 * 负责管理会话列表的存储、加载和更新
 * 支持多账号聚合
 */
public class SessionManager {
    private static final String PREFS_NAME = "ChatAppSessions";
    private static final String KEY_SESSIONS = "sessions";
    private static final String KEY_CURRENT_SESSION = "current_session";
    private static final String KEY_INITIALIZED_SESSIONS = "initialized_sessions";
    private static final String KEY_READ_MESSAGE_COUNTS = "read_message_counts";
    private static final String KEY_DRAFTS = "drafts";
    
    private SharedPreferences prefs;
    private AccountManager accountManager;
    
    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        accountManager = AccountManager.getInstance(context);
    }
    
    /**
     * 保存会话列表
     */
    public void saveSessions(List<Session> sessions) {
        JSONArray jsonArray = new JSONArray();
        for (Session session : sessions) {
            JSONObject json = new JSONObject();
            try {
                json.put("sessionId", session.getSessionId() != null ? session.getSessionId() : "");
                json.put("accountId", session.getAccountId() != null ? session.getAccountId() : "");
                json.put("title", session.getTitle() != null ? session.getTitle() : "");
                json.put("firstMessage", session.getFirstMessage() != null ? session.getFirstMessage() : "");
                json.put("lastMessage", session.getLastMessage() != null ? session.getLastMessage() : "");
                json.put("lastMessageTime", session.getLastMessageTime());
                json.put("messageCount", session.getMessageCount());
                json.put("unreadCount", session.getUnreadCount());
                json.put("provider", session.getProvider() != null ? session.getProvider() : "");
                json.put("model", session.getModel() != null ? session.getModel() : "");
                json.put("cwd", session.getCwd() != null ? session.getCwd() : "");
                json.put("in_progress", session.isInProgress());
                json.put("firstMessageIndex", session.getFirstMessageIndex());
                json.put("pinned", session.isPinned());
                jsonArray.put(json);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        prefs.edit().putString(KEY_SESSIONS, jsonArray.toString()).apply();
    }
    
    /**
     * 加载会话列表，按 sessionId 排序
     * 使用 optString 避免 JSON 字段缺失导致的崩溃
     */
    public List<Session> loadSessions() {
        List<Session> sessions = new ArrayList<>();
        String jsonStr = prefs.getString(KEY_SESSIONS, "");
        
        if (jsonStr.isEmpty()) {
            return sessions;
        }
        
        try {
            JSONArray jsonArray = new JSONArray(jsonStr);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject json = jsonArray.getJSONObject(i);
                
                // 使用 optString 安全获取字段，避免崩溃
                String sessionId = json.optString("sessionId", "");
                if (sessionId.isEmpty()) {
                    continue;  // 跳过无效会话
                }
                
                Session session = new Session(sessionId);
                session.setAccountId(json.optString("accountId", ""));
                session.setTitle(json.optString("title", ""));
                session.setFirstMessage(json.optString("firstMessage", ""));
                session.setLastMessage(json.optString("lastMessage", ""));
                session.setLastMessageTime(json.optLong("lastMessageTime", System.currentTimeMillis()));
                session.setMessageCount(json.optInt("messageCount", 0));
                session.setUnreadCount(json.optInt("unreadCount", 0));
                session.setProvider(json.optString("provider", ""));
                session.setModel(json.optString("model", ""));
                session.setCwd(json.optString("cwd", ""));
                session.setInProgress(json.optBoolean("in_progress", false));
                session.setFirstMessageIndex(json.optInt("firstMessageIndex", 0));
                session.setPinned(json.optBoolean("pinned", false));
                // 加载草稿
                session.setDraft(getDraft(sessionId));
                sessions.add(session);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            // JSON 解析失败，返回空列表而不是崩溃
        }
        
        Collections.sort(sessions, (s1, s2) -> 
            s1.getSessionId().compareTo(s2.getSessionId()));
        
        return sessions;
    }
    
    /**
     * 加载指定账号的会话列表
     * @param accountId 账号ID，null 或空字符串表示当前账号
     */
    public List<Session> loadSessions(String accountId) {
        if (accountId == null || accountId.isEmpty()) {
            Account currentAccount = accountManager.getCurrentAccount();
            if (currentAccount != null) {
                accountId = currentAccount.getId();
            } else {
                return new ArrayList<>();
            }
        }
        
        List<Session> allSessions = loadSessions();
        List<Session> accountSessions = new ArrayList<>();
        
        for (Session session : allSessions) {
            if (accountId.equals(session.getAccountId())) {
                accountSessions.add(session);
            }
        }
        
        return accountSessions;
    }
    
    /**
     * 加载所有账号的会话列表（聚合视图）
     * 排序规则：置顶会话优先，然后按最后消息时间降序
     */
    public List<Session> loadAllSessions() {
        List<Session> sessions = loadSessions();
        // 先按 pinned 排序（置顶优先），再按最后消息时间降序
        Collections.sort(sessions, (s1, s2) -> {
            // 置顶会话优先
            if (s1.isPinned() != s2.isPinned()) {
                return s1.isPinned() ? -1 : 1;
            }
            // 相同置顶状态下，按最后消息时间降序
            return Long.compare(s2.getLastMessageTime(), s1.getLastMessageTime());
        });
        return sessions;
    }
    
    /**
     * 添加或更新会话
     */
    public void addOrUpdateSession(Session newSession) {
        if (newSession == null || newSession.getSessionId() == null) {
            return;  // 安全检查
        }
        
        List<Session> sessions = loadSessions();
        boolean found = false;
        
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).getSessionId().equals(newSession.getSessionId())) {
                sessions.set(i, newSession);
                found = true;
                break;
            }
        }
        
        if (!found) {
            sessions.add(newSession);
        }
        
        saveSessions(sessions);
    }
    
    /**
     * 添加或更新会话（指定账号）
     */
    public void addOrUpdateSession(Session session, String accountId) {
        if (session == null) return;
        session.setAccountId(accountId != null ? accountId : "");
        addOrUpdateSession(session);
    }
    
    /**
     * 更新会话信息（来自服务器）
     */
    public void updateSession(Session updatedSession) {
        addOrUpdateSession(updatedSession);
    }
    
    /**
     * 更新会话的消息信息
     */
    public void updateMessages(String sessionId, String firstMessage, String lastMessage, int messageCount, long lastMessageTime) {
        if (sessionId == null || sessionId.isEmpty()) return;
        
        List<Session> sessions = loadSessions();
        
        for (Session session : sessions) {
            if (session.getSessionId().equals(sessionId)) {
                if (firstMessage != null && !firstMessage.isEmpty()) {
                    session.setFirstMessage(firstMessage);
                }
                session.setLastMessage(lastMessage != null ? lastMessage : "");
                session.setLastMessageTime(lastMessageTime);
                session.setMessageCount(messageCount);
                break;
            }
        }
        
        saveSessions(sessions);
    }
    
    /**
     * 更新会话的 firstMessageIndex
     */
    public void updateFirstMessageIndex(String sessionId, int firstMessageIndex) {
        if (sessionId == null || sessionId.isEmpty()) return;
        
        List<Session> sessions = loadSessions();
        
        for (Session session : sessions) {
            if (session.getSessionId().equals(sessionId)) {
                session.setFirstMessageIndex(firstMessageIndex);
                break;
            }
        }
        
        saveSessions(sessions);
    }
    
    /**
     * 增加会话的未读消息数
     */
    public void incrementUnreadCount(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return;
        
        List<Session> sessions = loadSessions();
        
        for (Session session : sessions) {
            if (session.getSessionId().equals(sessionId)) {
                session.setUnreadCount(session.getUnreadCount() + 1);
                break;
            }
        }
        
        saveSessions(sessions);
    }
    
    /**
     * 清除会话的未读消息数
     */
    public void clearUnreadCount(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return;
        
        List<Session> sessions = loadSessions();
        
        for (Session session : sessions) {
            if (session.getSessionId().equals(sessionId)) {
                session.setUnreadCount(0);
                break;
            }
        }
        
        saveSessions(sessions);
    }
    
    /**
     * 删除会话
     */
    public void deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return;
        
        List<Session> sessions = loadSessions();
        List<Session> toRemove = new ArrayList<>();
        
        for (Session session : sessions) {
            if (session.getSessionId().equals(sessionId)) {
                toRemove.add(session);
            }
        }
        
        sessions.removeAll(toRemove);
        saveSessions(sessions);
        removeInitializedSession(sessionId);
        clearDraft(sessionId);
    }
    
    /**
     * 删除指定账号的所有会话
     */
    public void deleteAccountSessions(String accountId) {
        if (accountId == null || accountId.isEmpty()) return;
        
        List<Session> sessions = loadSessions();
        List<Session> toRemove = new ArrayList<>();
        
        for (Session session : sessions) {
            if (accountId.equals(session.getAccountId())) {
                toRemove.add(session);
                removeInitializedSession(session.getSessionId());
                clearDraft(session.getSessionId());
            }
        }
        
        sessions.removeAll(toRemove);
        saveSessions(sessions);
    }
    
    /**
     * 更新会话的 in_progress 状态
     */
    public void setSessionInProgress(String sessionId, boolean inProgress) {
        if (sessionId == null || sessionId.isEmpty()) return;
        
        List<Session> sessions = loadSessions();
        
        for (Session session : sessions) {
            if (session.getSessionId().equals(sessionId)) {
                session.setInProgress(inProgress);
                break;
            }
        }
        
        saveSessions(sessions);
    }
    
    /**
     * 更新会话的置顶状态
     */
    public void setSessionPinned(String sessionId, boolean pinned) {
        if (sessionId == null || sessionId.isEmpty()) return;
        
        List<Session> sessions = loadSessions();
        
        for (Session session : sessions) {
            if (session.getSessionId().equals(sessionId)) {
                session.setPinned(pinned);
                break;
            }
        }
        
        saveSessions(sessions);
    }
    
    /**
     * 保存当前会话 ID
     */
    public void saveCurrentSession(String sessionId) {
        prefs.edit().putString(KEY_CURRENT_SESSION, sessionId != null ? sessionId : "").apply();
    }
    
    /**
     * 获取当前会话 ID
     */
    public String getCurrentSession() {
        return prefs.getString(KEY_CURRENT_SESSION, "");
    }
    
    /**
     * 检查是否存在会话
     */
    public boolean hasCurrentSession() {
        String session = getCurrentSession();
        return session != null && !session.isEmpty();
    }
    
    /**
     * 清除当前会话
     */
    public void clearCurrentSession() {
        prefs.edit().remove(KEY_CURRENT_SESSION).apply();
    }
    
    /**
     * 获取会话数量
     */
    public int getSessionCount() {
        return loadSessions().size();
    }
    
    /**
     * 根据 ID 获取会话
     */
    public Session getSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return null;
        
        List<Session> sessions = loadSessions();
        for (Session session : sessions) {
            if (session.getSessionId().equals(sessionId)) {
                return session;
            }
        }
        return null;
    }
    
    /**
     * 获取会话在列表中的索引
     */
    public int getSessionIndex(String sessionId, List<Session> sessions) {
        if (sessionId == null) return -1;
        
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).getSessionId().equals(sessionId)) {
                return i;
            }
        }
        return -1;
    }
    
    // ========== 已初始化会话状态管理 ==========
    
    /**
     * 保存已初始化的会话列表
     */
    public void saveInitializedSessions(Set<String> initializedSessions) {
        if (initializedSessions == null) return;
        
        JSONArray jsonArray = new JSONArray();
        for (String sessionId : initializedSessions) {
            if (sessionId != null) {
                jsonArray.put(sessionId);
            }
        }
        prefs.edit().putString(KEY_INITIALIZED_SESSIONS, jsonArray.toString()).apply();
    }
    
    /**
     * 加载已初始化的会话列表
     */
    public Set<String> loadInitializedSessions() {
        Set<String> initializedSessions = new HashSet<>();
        String jsonStr = prefs.getString(KEY_INITIALIZED_SESSIONS, "");
        
        if (jsonStr.isEmpty()) {
            return initializedSessions;
        }
        
        try {
            JSONArray jsonArray = new JSONArray(jsonStr);
            for (int i = 0; i < jsonArray.length(); i++) {
                String id = jsonArray.optString(i, "");
                if (!id.isEmpty()) {
                    initializedSessions.add(id);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        return initializedSessions;
    }
    
    /**
     * 添加已初始化的会话
     */
    public void addInitializedSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return;
        
        Set<String> initializedSessions = loadInitializedSessions();
        initializedSessions.add(sessionId);
        saveInitializedSessions(initializedSessions);
    }
    
    /**
     * 移除已初始化的会话
     */
    public void removeInitializedSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return;
        
        Set<String> initializedSessions = loadInitializedSessions();
        initializedSessions.remove(sessionId);
        saveInitializedSessions(initializedSessions);
    }
    
    /**
     * 检查会话是否已初始化
     */
    public boolean isSessionInitialized(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return false;
        return loadInitializedSessions().contains(sessionId);
    }
    
    /**
     * 清除所有已初始化状态
     */
    public void clearInitializedSessions() {
        prefs.edit().remove(KEY_INITIALIZED_SESSIONS).apply();
    }
    
    // ========== 已读消息数管理 ==========
    
    /**
     * 保存已读消息数映射
     */
    public void saveReadMessageCounts(Map<String, Integer> readCounts) {
        if (readCounts == null) return;
        
        JSONObject json = new JSONObject();
        for (Map.Entry<String, Integer> entry : readCounts.entrySet()) {
            try {
                if (entry.getKey() != null) {
                    json.put(entry.getKey(), entry.getValue());
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        prefs.edit().putString(KEY_READ_MESSAGE_COUNTS, json.toString()).apply();
    }
    
    /**
     * 加载已读消息数映射
     */
    public Map<String, Integer> loadReadMessageCounts() {
        Map<String, Integer> readCounts = new HashMap<>();
        String jsonStr = prefs.getString(KEY_READ_MESSAGE_COUNTS, "");
        
        if (jsonStr.isEmpty()) {
            return readCounts;
        }
        
        try {
            JSONObject json = new JSONObject(jsonStr);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key != null && !key.isEmpty()) {
                    readCounts.put(key, json.optInt(key, 0));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        return readCounts;
    }
    
    /**
     * 保存单个会话的已读消息数
     */
    public void saveReadMessageCount(String sessionId, int count) {
        if (sessionId == null || sessionId.isEmpty()) return;
        
        Map<String, Integer> readCounts = loadReadMessageCounts();
        readCounts.put(sessionId, count);
        saveReadMessageCounts(readCounts);
    }
    
    /**
     * 获取单个会话的已读消息数
     */
    public int getReadMessageCount(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return 0;
        
        Map<String, Integer> readCounts = loadReadMessageCounts();
        return readCounts.getOrDefault(sessionId, 0);
    }
    
    /**
     * 清除所有已读消息数记录
     */
    public void clearReadMessageCounts() {
        prefs.edit().remove(KEY_READ_MESSAGE_COUNTS).apply();
    }
    
    // ========== 草稿消息管理 ==========
    
    /**
     * 保存草稿消息
     * @param sessionId 会话ID
     * @param draft 草稿内容
     */
    public void saveDraft(String sessionId, String draft) {
        if (sessionId == null || sessionId.isEmpty()) return;
        
        Map<String, String> drafts = loadDrafts();
        if (draft != null && !draft.isEmpty()) {
            drafts.put(sessionId, draft);
        } else {
            drafts.remove(sessionId);
        }
        saveDrafts(drafts);
    }
    
    /**
     * 获取草稿消息
     * @param sessionId 会话ID
     * @return 草稿内容，如果没有则返回空字符串
     */
    public String getDraft(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return "";
        
        Map<String, String> drafts = loadDrafts();
        return drafts.getOrDefault(sessionId, "");
    }
    
    /**
     * 清除草稿消息
     * @param sessionId 会话ID
     */
    public void clearDraft(String sessionId) {
        saveDraft(sessionId, null);
    }
    
    /**
     * 保存草稿映射
     */
    private void saveDrafts(Map<String, String> drafts) {
        if (drafts == null) return;
        
        JSONObject json = new JSONObject();
        for (Map.Entry<String, String> entry : drafts.entrySet()) {
            try {
                if (entry.getKey() != null && entry.getValue() != null) {
                    json.put(entry.getKey(), entry.getValue());
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        prefs.edit().putString(KEY_DRAFTS, json.toString()).apply();
    }
    
    /**
     * 加载草稿映射
     */
    private Map<String, String> loadDrafts() {
        Map<String, String> drafts = new HashMap<>();
        String jsonStr = prefs.getString(KEY_DRAFTS, "");
        
        if (jsonStr.isEmpty()) {
            return drafts;
        }
        
        try {
            JSONObject json = new JSONObject(jsonStr);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key != null && !key.isEmpty()) {
                    String value = json.optString(key, "");
                    if (!value.isEmpty()) {
                        drafts.put(key, value);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        return drafts;
    }
    
    /**
     * 清除所有草稿
     */
    public void clearAllDrafts() {
        prefs.edit().remove(KEY_DRAFTS).apply();
    }
}
