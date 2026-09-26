package net.wsdjeg.nova;

import java.util.List;
import net.wsdjeg.nova.api.ApiConfig;
import net.wsdjeg.nova.api.BridgeApi;
import net.wsdjeg.nova.api.ChatApi;
import net.wsdjeg.nova.api.MessageApi;
import net.wsdjeg.nova.api.ProviderApi;
import net.wsdjeg.nova.api.ServerApi;
import net.wsdjeg.nova.api.SessionActionApi;
import net.wsdjeg.nova.api.SessionApi;
import net.wsdjeg.nova.api.SessionSettingsApi;
import net.wsdjeg.nova.api.UploadApi;
import net.wsdjeg.nova.api.WeChatApi;

/**
 * API 客户端（门面）
 * 实现已按功能拆分到 net.wsdjeg.nova.api 子包：
 * - ChatApi           发送消息
 * - MessageApi        消息查询/解析/删除
 * - SessionApi        会话 CRUD
 * - SessionSettingsApi 会话属性更新
 * - SessionActionApi  stop/clear/retry
 * - UploadApi         文件上传
 * - ProviderApi       providers/skills
 * - ServerApi         日志/连接测试/预览
 * - WeChatApi         微信登录
 * - BridgeApi         集成桥接
 *
 * 支持多账号：可以指定 baseUrl 和 apiKey，或使用 SettingsManager 的默认设置
 */
public class ApiClient {
    private final ApiConfig config;
    private final ChatApi chatApi;
    private final MessageApi messageApi;
    private final SessionApi sessionApi;
    private final SessionSettingsApi sessionSettingsApi;
    private final SessionActionApi sessionActionApi;
    private final UploadApi uploadApi;
    private final ProviderApi providerApi;
    private final ServerApi serverApi;
    private final WeChatApi weChatApi;
    private final BridgeApi bridgeApi;
    private String sessionId;

    public interface ApiCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public interface MessageCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface SessionsCallback {
        void onSuccess(List<Session> sessions);
        void onError(String error);
    }

    public interface MessagesCallback {
        void onSuccess(List<ChatMessage> messages);
        void onError(String error);
    }

    public interface CreateSessionCallback {
        void onSuccess(Session session);
        void onError(String error);
    }

    public interface DeleteSessionCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface StopCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface RetryCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface UpdateSessionCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface ClearCallback {
        void onSuccess();
        void onError(String error);
    }

    /**
     * 删除消息的回调接口
     * 用于 DELETE /session/:id/messages/:index API
     */
    public interface DeleteMessageCallback {
        void onSuccess();
        void onError(String error);
    }

    /**
     * 文件上传的回调接口
     * 用于 POST /session/:id/upload API
     */
    public interface UploadCallback {
        void onSuccess(String path, String fullPath, long size);
        void onError(String error);
    }

    public interface ProvidersCallback {
        void onSuccess(List<Provider> providers);
        void onError(String error);
    }

    /**
     * 获取单个会话的回调接口
     * 用于 GET /sessions/:id API
     */
    public interface SessionCallback {
        void onSuccess(Session session);
        void onError(String error);
    }

    /**
     * 获取/设置上传目录的回调接口
     * 用于 GET/PUT /session/:id/upload-dir API
     */
    public interface UploadDirCallback {
        void onSuccess(String uploadDir); // uploadDir can be null
        void onError(String error);
    }

    /**
     * 微信登录状态轮询的回调接口
     * 用于 GET /weixin/login/status API
     */
    public interface WeChatLoginCallback {
        void onSuccess(WeChatLoginResult result);
        void onError(String error);
    }

    /**
     * 微信凭证写入的回调接口
     * 用于 POST /weixin/credentials API
     */
    public interface WeChatCredentialsCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    /**
     * 获取会话已绑定集成列表的回调接口
     * 用于 GET /session/:id/bridge API
     */
    public interface BridgesCallback {
        void onSuccess(List<String> bridges);
        void onError(String error);
    }

    /**
     * 绑定集成到会话的回调接口
     * 用于 PUT /session/:id/bridge/:platform API
     */
    public interface BridgeCallback {
        void onSuccess(String platform);
        void onError(String error);
    }

    /**
     * 获取 skills 列表的回调接口
     * 用于 GET /skills API
     */
    public interface SkillsCallback {
        void onSuccess(List<Skill> skills);
        void onError(String error);
    }

    /**
     * 获取运行时日志的回调接口
     * 用于 GET /logs API
     */
    public interface LogsCallback {
        void onSuccess(List<String> logs);
        void onError(String error);
    }

    /**
     * 清空运行时日志的回调接口
     * 用于 DELETE /logs API
     */
    public interface ClearLogsCallback {
        void onSuccess();
        void onError(String error);
    }

    public ApiClient(SettingsManager settingsManager) {
        this.config = new ApiConfig(settingsManager);
        this.chatApi = new ChatApi(config);
        this.messageApi = new MessageApi(config);
        this.sessionApi = new SessionApi(config);
        this.sessionSettingsApi = new SessionSettingsApi(config);
        this.sessionActionApi = new SessionActionApi(config);
        this.uploadApi = new UploadApi(config);
        this.providerApi = new ProviderApi(config);
        this.serverApi = new ServerApi(config);
        this.weChatApi = new WeChatApi(config);
        this.bridgeApi = new BridgeApi(config);
    }

    public ApiClient(String baseUrl, String apiKey) {
        this.config = new ApiConfig(baseUrl, apiKey);
        this.chatApi = new ChatApi(config);
        this.messageApi = new MessageApi(config);
        this.sessionApi = new SessionApi(config);
        this.sessionSettingsApi = new SessionSettingsApi(config);
        this.sessionActionApi = new SessionActionApi(config);
        this.uploadApi = new UploadApi(config);
        this.providerApi = new ProviderApi(config);
        this.serverApi = new ServerApi(config);
        this.weChatApi = new WeChatApi(config);
        this.bridgeApi = new BridgeApi(config);
    }

    public void setSession(String sessionId) {
        this.sessionId = sessionId;
    }

    public boolean hasValidSettings() {
        return config.hasValidSettings();
    }

    // ===== ChatApi =====

    public void sendMessage(String sessionId, String content, ApiCallback callback) {
        chatApi.sendMessage(sessionId, content, callback);
    }

    public void sendMessage(String content, ApiCallback callback) {
        chatApi.sendMessage(content, callback);
    }

    public void sendMessage(String sessionId, String content, MessageCallback callback) {
        chatApi.sendMessage(sessionId, content, callback);
    }

    // ===== MessageApi =====

    public void getMessages(String sessionId, MessagesCallback callback) {
        messageApi.getMessages(sessionId, callback);
    }

    public void getMessagesWithOptions(String sessionId, int since, int limit, boolean last, MessagesCallback callback) {
        messageApi.getMessagesWithOptions(sessionId, since, limit, last, callback);
    }

    public void getLastMessage(String sessionId, MessagesCallback callback) {
        messageApi.getLastMessage(sessionId, callback);
    }

    public void getNewMessages(String sessionId, int sinceIndex, MessagesCallback callback) {
        messageApi.getNewMessages(sessionId, sinceIndex, callback);
    }

    public void getMessagesPaginated(String sessionId, int limit, MessagesCallback callback) {
        messageApi.getMessagesPaginated(sessionId, limit, callback);
    }

    public void deleteMessage(String sessionId, int messageIndex, DeleteMessageCallback callback) {
        messageApi.deleteMessage(sessionId, messageIndex, callback);
    }

    // ===== SessionApi =====

    public void getSessions(String accountId, SessionsCallback callback) {
        sessionApi.getSessions(accountId, callback);
    }

    public void getSession(String sessionId, String accountId, SessionCallback callback) {
        sessionApi.getSession(sessionId, accountId, callback);
    }

    public void createSession(String cwd, String provider, String model, String accountId, CreateSessionCallback callback) {
        sessionApi.createSession(cwd, provider, model, accountId, callback);
    }

    public void createSession(String cwd, String provider, String model, CreateSessionCallback callback) {
        sessionApi.createSession(cwd, provider, model, callback);
    }

    public void deleteSession(String sessionId, DeleteSessionCallback callback) {
        sessionApi.deleteSession(sessionId, callback);
    }

    // ===== SessionSettingsApi =====

    public void setSessionCwd(String sessionId, String cwd, UpdateSessionCallback callback) {
        sessionSettingsApi.setSessionCwd(sessionId, cwd, callback);
    }

    public void setSessionTitle(String sessionId, String title, UpdateSessionCallback callback) {
        sessionSettingsApi.setSessionTitle(sessionId, title, callback);
    }

    public void setSessionPinned(String sessionId, boolean pinned, UpdateSessionCallback callback) {
        sessionSettingsApi.setSessionPinned(sessionId, pinned, callback);
    }

    public void updateSession(String sessionId, String provider, String model, UpdateSessionCallback callback) {
        sessionSettingsApi.updateSession(sessionId, provider, model, callback);
    }

    // ===== SessionActionApi =====

    public void stopSession(String sessionId, StopCallback callback) {
        sessionActionApi.stopSession(sessionId, callback);
    }

    public void clearSession(String sessionId, ClearCallback callback) {
        sessionActionApi.clearSession(sessionId, callback);
    }

    public void retrySession(String sessionId, RetryCallback callback) {
        sessionActionApi.retrySession(sessionId, callback);
    }

    // ===== UploadApi =====

    public void uploadFile(String sessionId, byte[] fileData, String relativePath,
                           String contentType, UploadCallback callback) {
        uploadApi.uploadFile(sessionId, fileData, relativePath, contentType, callback);
    }

    public void getUploadDir(String sessionId, UploadDirCallback callback) {
        uploadApi.getUploadDir(sessionId, callback);
    }

    public void setUploadDir(String sessionId, String uploadDir, UpdateSessionCallback callback) {
        uploadApi.setUploadDir(sessionId, uploadDir, callback);
    }

    // ===== ProviderApi =====

    public void getProviders(ProvidersCallback callback) {
        providerApi.getProviders(callback);
    }

    public void getSkills(SkillsCallback callback) {
        providerApi.getSkills(callback);
    }

    /**
     * 静态方法：直接指定服务器地址查询 providers
     */
    public static void getProviders(String serverUrl, String apiKey, ProvidersCallback callback) {
        ProviderApi.getProviders(serverUrl, apiKey, callback);
    }

    // ===== ServerApi =====

    public void getLogs(String level, String name, int tail, LogsCallback callback) {
        serverApi.getLogs(level, name, tail, callback);
    }

    public void clearLogs(ClearLogsCallback callback) {
        serverApi.clearLogs(callback);
    }

    /**
     * 静态方法：测试服务器连接
     */
    public static void testConnection(String serverUrl, String apiKey, ApiCallback callback) {
        ServerApi.testConnection(serverUrl, apiKey, callback);
    }

    public void testConnection(ApiCallback callback) {
        serverApi.testConnection(callback);
    }

    public void getSessionPreview(String sessionId, ApiCallback callback) {
        serverApi.getSessionPreview(sessionId, callback);
    }

    // ===== WeChatApi =====

    public void getWeChatLoginStatus(WeChatLoginCallback callback) {
        weChatApi.getWeChatLoginStatus(callback);
    }

    public void writeWeChatCredentials(String id, String key, String baseUrl,
                                        String userId, WeChatCredentialsCallback callback) {
        weChatApi.writeWeChatCredentials(id, key, baseUrl, userId, callback);
    }

    public void deleteWeChatCredentials(WeChatCredentialsCallback callback) {
        weChatApi.deleteWeChatCredentials(callback);
    }

    // ===== BridgeApi =====

    public void getBridges(String sessionId, BridgesCallback callback) {
        bridgeApi.getBridges(sessionId, callback);
    }

    public void bridgeIntegration(String sessionId, String platform, BridgeCallback callback) {
        bridgeApi.bridgeIntegration(sessionId, platform, callback);
    }

    public void unbridgeIntegration(String sessionId, String platform, UpdateSessionCallback callback) {
        bridgeApi.unbridgeIntegration(sessionId, platform, callback);
    }

    public void unbridgeAll(String sessionId, UpdateSessionCallback callback) {
        bridgeApi.unbridgeAll(sessionId, callback);
    }
}

