package net.wsdjeg.nova;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 会话管理器
 * 负责管理会话列表的存储、加载和更新
 */
public class SessionManager {
    private static final String PREFS_NAME = "ChatAppSessions";
    private static final String KEY_SESSIONS = "sessions";
    private static final String KEY_CURRENT_SESSION = "current_session";
    private static final String KEY_INITIALIZED_SESSIONS = "initialized_sessions";
    
    private SharedPreferences prefs;
    
    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * 保存会话列表
     */
    public void saveSessions(List<Session> sessions) {
        JSONArray jsonArray = new JSONArray();
        for (Session session : sessions) {
            JSONObject json = new JSONObject();
            try {
                json.put("sessionId", session.getSessionId());
                json.put("firstMessage", session.getFirstMessage());
                json.put("lastMessage", session.getLastMessage());
                json.put("lastMessageTime", session.getLastMessageTime());
                json.put("messageCount", session.getMessageCount());
                json.put("unreadCount", session.getUnreadCount());
                // 新增字段：来自 API /sessions
                json.put("provider", session.getProvider());
                json.put("model", session.getModel());
                json.put("cwd", session.getCwd());
                jsonArray.put(json);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        prefs.edit().putString(KEY_SESSIONS, jsonArray.toString()).apply();
    }
    
    /**
     * 加载会话列表，按 sessionId 排序
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
                Session session = new Session(
                    json.getString("sessionId"),
                    json.optString("firstMessage", ""),
                    json.optString("lastMessage", ""),
                    json.optLong("lastMessageTime", System.currentTimeMillis()),
                    json.optInt("messageCount", 0)
                );
                session.setUnreadCount(json.optInt("unreadCount", 0));
                // 加载新增字段
                session.setProvider(json.optString("provider", ""));
                session.setModel(json.optString("model", ""));
                session.setCwd(json.optString("cwd", ""));
                sessions.add(session);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        // 按 sessionId 排序（字符串字典序）
        Collections.sort(sessions, (s1, s2) -> 
            s1.getSessionId().compareTo(s2.getSessionId()));
        
        return sessions;
    }
    
    /**
     * 添加或更新会话
     */
    public void addOrUpdateSession(Session newSession) {
        List<Session> sessions = loadSessions();
        boolean found = false;
        
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).getSessionId().equals(newSession.getSessionId())) {
                // 保留未读数（如果新session没有设置）
                if (newSession.getUnreadCount() == 0 && sessions.get(i).getUnreadCount() > 0) {
                    newSession.setUnreadCount(sessions.get(i).getUnreadCount());
                }
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
     * 更新会话信息（来自服务器）
     */
    public void updateSession(Session updatedSession) {
        addOrUpdateSession(updatedSession);
    }
    
    /**
     * 更新会话的消息信息
     * @param lastMessageTime 最后一条消息的时间戳（毫秒）
     */
    public void updateMessages(String sessionId, String firstMessage, String lastMessage, int messageCount, long lastMessageTime) {
        List<Session> sessions = loadSessions();
        
        for (Session session : sessions) {
            if (session.getSessionId().equals(sessionId)) {
                if (firstMessage != null && !firstMessage.isEmpty()) {
                    session.setFirstMessage(firstMessage);
                }
                session.setLastMessage(lastMessage);
                session.setLastMessageTime(lastMessageTime);
                session.setMessageCount(messageCount);
                break;
            }
        }
        
        saveSessions(sessions);
    }
    
    /**
     * 增加会话的未读消息数
     */
    public void incrementUnreadCount(String sessionId) {
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
        List<Session> sessions = loadSessions();
        List<Session> toRemove = new ArrayList<>();
        
        for (Session session : sessions) {
            if (session.getSessionId().equals(sessionId)) {
                toRemove.add(session);
            }
        }
        
        sessions.removeAll(toRemove);
        saveSessions(sessions);
        
        // 同时从已初始化列表中移除
        removeInitializedSession(sessionId);
    }
    
    /**
     * 保存当前会话 ID
     */
    public void saveCurrentSession(String sessionId) {
        prefs.edit().putString(KEY_CURRENT_SESSION, sessionId).apply();
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
        JSONArray jsonArray = new JSONArray();
        for (String sessionId : initializedSessions) {
            jsonArray.put(sessionId);
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
                initializedSessions.add(jsonArray.getString(i));
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
        Set<String> initializedSessions = loadInitializedSessions();
        initializedSessions.add(sessionId);
        saveInitializedSessions(initializedSessions);
    }
    
    /**
     * 移除已初始化的会话
     */
    public void removeInitializedSession(String sessionId) {
        Set<String> initializedSessions = loadInitializedSessions();
        initializedSessions.remove(sessionId);
        saveInitializedSessions(initializedSessions);
    }
    
    /**
     * 检查会话是否已初始化
     */
    public boolean isSessionInitialized(String sessionId) {
        return loadInitializedSessions().contains(sessionId);
    }
    
    /**
     * 清除所有已初始化状态（用于强制重新同步）
     */
    public void clearInitializedSessions() {
        prefs.edit().remove(KEY_INITIALIZED_SESSIONS).apply();
    }
}
