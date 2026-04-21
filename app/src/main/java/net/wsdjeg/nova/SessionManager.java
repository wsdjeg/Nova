package net.wsdjeg.nova;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 会话管理器
 * 负责管理会话列表的存储、加载和更新
 */
public class SessionManager {
    private static final String PREFS_NAME = "ChatAppSessions";
    private static final String KEY_SESSIONS = "sessions";
    private static final String KEY_CURRENT_SESSION = "current_session";
    
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
                jsonArray.put(json);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        prefs.edit().putString(KEY_SESSIONS, jsonArray.toString()).apply();
    }
    
    /**
     * 加载会话列表
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
                    json.optString("firstMessage", ""),  // 兼容旧版本
                    json.optString("lastMessage", ""),
                    json.optLong("lastMessageTime", System.currentTimeMillis()),
                    json.optInt("messageCount", 0)
                );
                sessions.add(session);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        // 按时间倒序排列（最新的在前面）
        Collections.sort(sessions, (s1, s2) -> 
            Long.compare(s2.getLastMessageTime(), s1.getLastMessageTime()));
        
        return sessions;
    }
    
    /**
     * 添加或更新会话
     * 如果会话已存在，更新其信息；否则添加新会话
     */
    public void addOrUpdateSession(Session newSession) {
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
     * 更新会话的第一条和最后消息
     */
    public void updateMessages(String sessionId, String firstMessage, String lastMessage, int messageCount) {
        List<Session> sessions = loadSessions();
        
        for (Session session : sessions) {
            if (session.getSessionId().equals(sessionId)) {
                if (firstMessage != null && !firstMessage.isEmpty()) {
                    session.setFirstMessage(firstMessage);
                }
                session.setLastMessage(lastMessage);
                session.setLastMessageTime(System.currentTimeMillis());
                session.setMessageCount(messageCount);
                break;
            }
        }
        
        saveSessions(sessions);
    }
    
    /**
     * 更新会话的最后消息（兼容旧版本）
     */
    public void updateLastMessage(String sessionId, String message, int messageCount) {
        updateMessages(sessionId, null, message, messageCount);
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
}
