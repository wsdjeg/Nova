package net.wsdjeg.nova;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 会话列表界面
 * 显示所有账号的会话（聚合视图）
 * 点击进入聊天界面
 * 支持会话置顶功能（左滑取消置顶，右滑置顶）
 * 支持按标题搜索会话
 */
public class SessionListActivity extends AppCompatActivity implements SessionAdapter.OnSessionClickListener {
    
    private static final int REFRESH_INTERVAL_MS = 5000; // 5秒刷新一次
    private static final int REQUEST_ACCOUNT_MANAGE = 1001;
    
    private RecyclerView rvSessions;
    private FloatingActionButton fabNewSession;
    private SessionAdapter adapter;
    private List<Session> sessions;       // 当前显示的会话（可能经过搜索过滤）
    private List<Session> allSessions;    // 全量会话列表（未过滤）
    private String searchQuery = "";      // 当前搜索关键词
    private SessionManager sessionManager;
    private SettingsManager settingsManager;
    private AccountManager accountManager;
    
    // 自动刷新相关
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private boolean isFirstRefreshDone = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_list);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        sessionManager = new SessionManager(this);
        settingsManager = new SettingsManager(this);
        accountManager = AccountManager.getInstance(this);
        
        initViews();
        setupRecyclerView();
        setupAutoRefresh();
        
        // 先加载本地会话列表
        loadSessions();
        
        // 启动时刷新所有账号的会话列表
        refreshAllAccountsSessions();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadSessions();
        
        // 刷新所有账号的会话
        if (accountManager.hasAccounts()) {
            refreshAllAccountsSessions();
            startAutoRefresh();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        stopAutoRefresh();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoRefresh();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.session_list_menu, menu);
        
        // 配置搜索框
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setQueryHint(getString(R.string.search_session_hint));
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    return false;
                }
                
                @Override
                public boolean onQueryTextChange(String newText) {
                    searchQuery = newText != null ? newText.trim() : "";
                    applyFilter();
                    return true;
                }
            });
            
            // 收起搜索框时清空搜索词
            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    return true;
                }
                
                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    searchQuery = "";
                    applyFilter();
                    return true;
                }
            });
        }
        
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_accounts) {
            startActivityForResult(
                new Intent(this, AccountManagerActivity.class),
                REQUEST_ACCOUNT_MANAGE
            );
            return true;
        } else if (id == R.id.action_view_log) {
            startActivity(new Intent(this, LogViewerActivity.class));
            return true;
        } else if (id == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void initViews() {
        rvSessions = findViewById(R.id.rv_sessions);
        fabNewSession = findViewById(R.id.fab_new_session);
        
        fabNewSession.setOnClickListener(v -> createNewSession());
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_ACCOUNT_MANAGE) {
            // 从账号管理返回后，更新显示和刷新会话
            loadSessions();
            refreshAllAccountsSessions();
        }
    }
    
    private void setupRecyclerView() {
        sessions = new ArrayList<>();
        allSessions = new ArrayList<>();
        adapter = new SessionAdapter(sessions, this);
        adapter.setAccountManager(accountManager);
        adapter.setSettingsManager(settingsManager);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvSessions.setLayoutManager(layoutManager);
        rvSessions.setAdapter(adapter);
        
        // 添加分割线，使每个会话项之间的间距相等
        androidx.recyclerview.widget.DividerItemDecoration divider = 
            new androidx.recyclerview.widget.DividerItemDecoration(
                this, 
                androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
            );
        rvSessions.addItemDecoration(divider);
        
        // 禁用 change 动画，使滑动操作后 item 复位更流畅
        RecyclerView.ItemAnimator animator = rvSessions.getItemAnimator();
        if (animator instanceof androidx.recyclerview.widget.DefaultItemAnimator) {
            ((androidx.recyclerview.widget.DefaultItemAnimator) animator)
                .setSupportsChangeAnimations(false);
        }
        
        // 滑动置顶/取消置顶
        setupSwipeToPin();
    }
    
    /**
     * 设置滑动置顶/取消置顶
     * 右滑 -> 置顶（未置顶时可用）
     * 左滑 -> 取消置顶（已置顶时可用）
     */
    private void setupSwipeToPin() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                        @NonNull RecyclerView.ViewHolder viewHolder) {
                int position = viewHolder.getAdapterPosition();
                if (position < 0 || position >= sessions.size()) {
                    return 0;
                }
                Session session = sessions.get(position);
                int swipeFlags;
                if (session.isPinned()) {
                    // 已置顶：只允许左滑取消置顶
                    swipeFlags = ItemTouchHelper.LEFT;
                } else {
                    // 未置顶：只允许右滑置顶
                    swipeFlags = ItemTouchHelper.RIGHT;
                }
                return makeMovementFlags(0, swipeFlags);
            }
            
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }
            
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position < 0 || position >= sessions.size()) {
                    return;
                }
                Session session = sessions.get(position);
                // 立即复位 view 位置，避免残留背景色
                viewHolder.itemView.setTranslationX(0);
                // 通知 adapter 重新绑定（change 动画已禁用，无闪烁）
                adapter.notifyItemChanged(position);
                // 执行置顶/取消置顶
                toggleSessionPin(session);
            }
            
            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 0.35f;
            }
            
            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState,
                                    boolean isCurrentlyActive) {
                View itemView = viewHolder.itemView;
                
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    Paint paint = new Paint();
                    paint.setAntiAlias(true);
                    
                    if (dX > 0) {
                        // 右滑 -> 置顶：蓝色背景
                        paint.setColor(Color.parseColor("#2196F3"));
                        c.drawRect(itemView.getLeft(), itemView.getTop(),
                                itemView.getLeft() + dX, itemView.getBottom(), paint);
                        
                        // 绘制 "置顶" 文字
                        paint.setColor(Color.WHITE);
                        paint.setTextSize(40f);
                        paint.setTextAlign(Paint.Align.LEFT);
                        Paint.FontMetrics fm = paint.getFontMetrics();
                        float textY = itemView.getTop()
                                + (itemView.getHeight() - (fm.descent - fm.ascent)) / 2
                                - fm.ascent;
                        float textX = Math.min(itemView.getLeft() + 48f,
                                itemView.getLeft() + dX / 3);
                        c.drawText(getString(R.string.pin), textX, textY, paint);
                        
                    } else if (dX < 0) {
                        // 左滑 -> 取消置顶：橙色背景
                        paint.setColor(Color.parseColor("#FF9800"));
                        c.drawRect(itemView.getRight() + dX, itemView.getTop(),
                                itemView.getRight(), itemView.getBottom(), paint);
                        
                        // 绘制 "取消置顶" 文字
                        paint.setColor(Color.WHITE);
                        paint.setTextSize(40f);
                        paint.setTextAlign(Paint.Align.RIGHT);
                        Paint.FontMetrics fm = paint.getFontMetrics();
                        float textY = itemView.getTop()
                                + (itemView.getHeight() - (fm.descent - fm.ascent)) / 2
                                - fm.ascent;
                        float textX = Math.max(itemView.getRight() - 48f,
                                itemView.getRight() + dX / 3);
                        c.drawText(getString(R.string.unpin), textX, textY, paint);
                    }
                }
                
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY,
                        actionState, isCurrentlyActive);
            }
        };
        
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvSessions);
    }
    
    /**
     * 设置自动刷新
     */
    private void setupAutoRefresh() {
        refreshHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (accountManager.hasAccounts()) {
                    refreshAllAccountsSessions();
                }
                refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
            }
        };
    }
    
    private void startAutoRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.post(refreshRunnable);
    }
    
    private void stopAutoRefresh() {
        if (refreshHandler != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }
    
    /**
     * 从本地加载所有账号的会话列表（聚合视图）
     * 加载全量数据到 allSessions，再通过 applyFilter() 过滤显示
     */
    private void loadSessions() {
        allSessions.clear();
        
        if (!accountManager.hasAccounts()) {
            sessions.clear();
            adapter.notifyDataSetChanged();
            updateSubtitle();
            Toast.makeText(this, getString(R.string.please_add_account), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Load sessions from all accounts
        List<Session> loadedSessions = sessionManager.loadAllSessions();
        
        // 确保 accountId 正确设置
        for (Session session : loadedSessions) {
            if (session.getAccountId() == null || session.getAccountId().isEmpty()) {
                // 尝试从当前账号获取
                Account currentAccount = accountManager.getCurrentAccount();
                if (currentAccount != null) {
                    session.setAccountId(currentAccount.getId());
                }
            }
        }
        
        allSessions.addAll(loadedSessions);
        applyFilter();
        
        if (allSessions.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_sessions_hint), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 根据搜索关键词过滤会话列表
     * 搜索匹配标题（不区分大小写）
     * 排序规则：置顶会话优先，然后按最后消息时间降序
     */
    private void applyFilter() {
        sessions.clear();
        
        if (searchQuery.isEmpty()) {
            sessions.addAll(allSessions);
        } else {
            String queryLower = searchQuery.toLowerCase(Locale.getDefault());
            for (Session session : allSessions) {
                String title = session.getTitle();
                if (title != null && title.toLowerCase(Locale.getDefault()).contains(queryLower)) {
                    sessions.add(session);
                }
            }
        }
        
        // 排序规则：
        // 1. 置顶会话优先显示（pinned = true 的排在前面）
        // 2. 置顶会话之间按最后消息时间降序排序
        // 3. 未置顶会话之间按最后消息时间降序排序
        Collections.sort(sessions, new Comparator<Session>() {
            @Override
            public int compare(Session s1, Session s2) {
                // 先比较置顶状态
                if (s1.isPinned() && !s2.isPinned()) {
                    return -1; // s1 置顶，排在前面
                } else if (!s1.isPinned() && s2.isPinned()) {
                    return 1;  // s2 置顶，排在前面
                }
                // 同为置顶或同为非置顶，按最后消息时间降序排序
                return Long.compare(s2.getLastMessageTime(), s1.getLastMessageTime());
            }
        });
        
        adapter.notifyDataSetChanged();
        updateSubtitle();
    }
    
    /**
     * 更新 Toolbar 副标题，显示会话数量
     * 搜索时显示 "找到 M/N 个会话"，正常显示 "共 N 个会话"
     */
    private void updateSubtitle() {
        int total = allSessions.size();
        int shown = sessions.size();
        
        String subtitle;
        if (searchQuery.isEmpty()) {
            subtitle = getString(R.string.total_sessions, total);
        } else {
            subtitle = getString(R.string.found_sessions, shown, total);
        }
        getSupportActionBar().setSubtitle(subtitle);
    }
    
    /**
     * 刷新所有账号的会话列表
     */
    private void refreshAllAccountsSessions() {
        List<Account> accounts = accountManager.getAccounts();
        if (accounts.isEmpty()) {
            return;
        }
        
        for (Account account : accounts) {
            refreshSessionsFromServer(account);
        }
    }
    
    /**
     * 从服务器刷新指定账号的会话列表
     * 同步 pinned 状态
     */
    private void refreshSessionsFromServer(Account account) {
        if (account == null) {
            return;
        }
        
        String baseUrl = account.getUrl();
        String apiKey = account.getApiKey();
        
        if (baseUrl == null || baseUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            return;
        }
        
        ApiClient accountApiClient = new ApiClient(baseUrl, apiKey);
        String accountId = account.getId();
        
        accountApiClient.getSessions(accountId, new ApiClient.SessionsCallback() {
            @Override
            public void onSuccess(List<Session> serverSessions) {
                runOnUiThread(() -> {
                    Map<String, Session> serverSessionMap = new HashMap<>();
                    for (Session session : serverSessions) {
                        serverSessionMap.put(session.getSessionId(), session);
                    }
                    
                    // 同步：添加或更新本地会话
                    for (Session serverSession : serverSessions) {
                        serverSession.setAccountId(accountId);
                        
                        Session localSession = sessionManager.getSession(serverSession.getSessionId());
                        if (localSession == null) {
                            // 新会话，直接添加（包括 pinned 状态）
                            sessionManager.addOrUpdateSession(serverSession, accountId);
                        } else {
                            // 已存在的会话，更新服务器返回的所有字段（包括 pinned）
                            localSession.setTitle(serverSession.getTitle());
                            localSession.setLastMessage(serverSession.getLastMessage());
                            localSession.setLastMessageTime(serverSession.getLastMessageTime());
                            localSession.setMessageCount(serverSession.getMessageCount());
                            localSession.setProvider(serverSession.getProvider());
                            localSession.setModel(serverSession.getModel());
                            localSession.setCwd(serverSession.getCwd());
                            localSession.setInProgress(serverSession.isInProgress());
                            // 同步 pinned 状态
                            localSession.setPinned(serverSession.isPinned());
                            sessionManager.addOrUpdateSession(localSession, accountId);
                        }
                    }
                    
                    // 同步：删除服务器没有的本地会话（只删除当前账号的）
                    List<Session> localSessions = sessionManager.loadSessions(accountId);
                    for (Session localSession : localSessions) {
                        if (!serverSessionMap.containsKey(localSession.getSessionId())) {
                            sessionManager.deleteSession(localSession.getSessionId());
                        }
                    }
                    
                    loadSessions();
                    
                    if (!isFirstRefreshDone) {
                        isFirstRefreshDone = true;
                        for (Session serverSession : serverSessions) {
                            sessionManager.addInitializedSession(serverSession.getSessionId());
                        }
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    // 静默失败，不影响其他账号的刷新
                });
            }
        });
    }
    
    /**
     * 创建新会话
     * 使用默认账号创建
     */
    private void createNewSession() {
        Account defaultAccount = accountManager.getDefaultAccount();
        if (defaultAccount == null) {
            Toast.makeText(this, getString(R.string.please_add_account), Toast.LENGTH_SHORT).show();
            startActivityForResult(
                new Intent(this, AccountManagerActivity.class),
                REQUEST_ACCOUNT_MANAGE
            );
            return;
        }
        
        String baseUrl = defaultAccount.getUrl();
        String apiKey = defaultAccount.getApiKey();
        
        if (baseUrl == null || baseUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            Toast.makeText(this, getString(R.string.account_config_incomplete), Toast.LENGTH_SHORT).show();
            return;
        }
        
        ApiClient accountApiClient = new ApiClient(baseUrl, apiKey);
        
        // 从设置获取默认的 provider 和 model
        String defaultProvider = settingsManager.getDefaultProvider();
        String defaultModel = settingsManager.getDefaultModel();
        
        Toast.makeText(this, getString(R.string.creating_session), Toast.LENGTH_SHORT).show();
        
        accountApiClient.createSession(null, defaultProvider, defaultModel, defaultAccount.getId(), new ApiClient.CreateSessionCallback() {
            @Override
            public void onSuccess(Session session) {
                runOnUiThread(() -> {
                    String accountId = defaultAccount.getId();
                    
                    // API 返回完整会话信息，设置所有字段
                    session.setAccountId(accountId);
                    session.setLastMessageTime(System.currentTimeMillis());
                    
                    sessionManager.addOrUpdateSession(session, accountId);
                    sessionManager.saveCurrentSession(session.getSessionId());
                    sessionManager.addInitializedSession(session.getSessionId());
                    
                    loadSessions();
                    
                    // 传递完整的会话信息给 ChatActivity
                    openChatActivity(session);
                    
                    Toast.makeText(SessionListActivity.this, 
                        getString(R.string.session_created, session.getSessionId()), 
                        Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(SessionListActivity.this, 
                        getString(R.string.create_session_failed, error), 
                        Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * 打开聊天界面
     * 传递完整的会话信息，包括 provider, model, cwd, in_progress
     */
    private void openChatActivity(Session session) {
        String sessionId = session.getSessionId();
        int currentMessageCount = session.getMessageCount();
        sessionManager.saveReadMessageCount(sessionId, currentMessageCount);
        
        sessionManager.clearUnreadCount(sessionId);
        loadSessions();
        
        sessionManager.saveCurrentSession(sessionId);
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("session_id", sessionId);
        intent.putExtra("session_title", session.getTitle());
        // 传递 API 返回的完整会话信息
        intent.putExtra("provider", session.getProvider());
        intent.putExtra("model", session.getModel());
        intent.putExtra("cwd", session.getCwd());
        intent.putExtra("in_progress", session.isInProgress());
        startActivity(intent);
    }
    
    @Override
    public void onSessionClick(Session session) {
        openChatActivity(session);
    }
    
    @Override
    public void onSessionLongClick(Session session) {
        // 显示菜单：删除、置顶（或取消置顶）
        String pinActionText = session.isPinned() ? getString(R.string.unpin) : getString(R.string.pin);
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.session_actions))
            .setItems(new String[]{pinActionText, getString(R.string.delete)}, (dialog, which) -> {
                if (which == 0) {
                    // 置顶/取消置顶
                    toggleSessionPin(session);
                } else if (which == 1) {
                    // 删除 - 需要确认
                    confirmDeleteSession(session);
                }
            })
            .show();
    }
    
    /**
     * 确认删除会话
     */
    private void confirmDeleteSession(Session session) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_session_title))
            .setMessage(getString(R.string.delete_session_confirm, session.getTitle()))
            .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                deleteSession(session);
            })
            .setNegativeButton(getString(R.string.cancel), null)
            .show();
    }
    
    /**
     * 切换会话置顶状态
     */
    private void toggleSessionPin(Session session) {
        String accountId = session.getAccountId();
        Account account = null;
        
        if (accountId != null && !accountId.isEmpty()) {
            account = accountManager.getAccount(accountId);
        }
        
        // 如果找不到账号，尝试使用默认账号
        if (account == null) {
            account = accountManager.getDefaultAccount();
        }
        
        if (account == null) {
            // 没有账号，只更新本地状态
            session.setPinned(!session.isPinned());
            sessionManager.addOrUpdateSession(session);
            loadSessions();
            Toast.makeText(this, 
                session.isPinned() ? getString(R.string.pinned_local) : getString(R.string.unpinned_local), 
                Toast.LENGTH_SHORT).show();
            return;
        }
        
        String baseUrl = account.getUrl();
        String apiKey = account.getApiKey();
        
        if (baseUrl == null || baseUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            // 账号配置不完整，只更新本地状态
            session.setPinned(!session.isPinned());
            sessionManager.addOrUpdateSession(session);
            loadSessions();
            Toast.makeText(this, 
                session.isPinned() ? getString(R.string.pinned_local) : getString(R.string.unpinned_local), 
                Toast.LENGTH_SHORT).show();
            return;
        }
        
        ApiClient accountApiClient = new ApiClient(baseUrl, apiKey);
        boolean newPinnedState = !session.isPinned();
        
        Toast.makeText(this, 
            newPinnedState ? getString(R.string.pinning) : getString(R.string.unpinning), 
            Toast.LENGTH_SHORT).show();
        
        accountApiClient.setSessionPinned(session.getSessionId(), newPinnedState, new ApiClient.UpdateSessionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    // 更新本地状态
                    session.setPinned(newPinnedState);
                    sessionManager.addOrUpdateSession(session);
                    loadSessions();
                    Toast.makeText(SessionListActivity.this, 
                        newPinnedState ? getString(R.string.pinned) : getString(R.string.unpinned), 
                        Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(SessionListActivity.this, 
                        (newPinnedState ? getString(R.string.pin_failed, error) : getString(R.string.unpin_failed, error)), 
                        Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void deleteSession(Session session) {
        String accountId = session.getAccountId();
        Account account = null;
        
        if (accountId != null && !accountId.isEmpty()) {
            account = accountManager.getAccount(accountId);
        }
        
        // 如果找不到账号，尝试使用默认账号
        if (account == null) {
            account = accountManager.getDefaultAccount();
        }
        
        if (account == null) {
            sessionManager.deleteSession(session.getSessionId());
            loadSessions();
            Toast.makeText(this, getString(R.string.deleted_local_session), Toast.LENGTH_SHORT).show();
            return;
        }
        
        String baseUrl = account.getUrl();
        String apiKey = account.getApiKey();
        
        if (baseUrl == null || baseUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            sessionManager.deleteSession(session.getSessionId());
            loadSessions();
            Toast.makeText(this, getString(R.string.deleted_local_session), Toast.LENGTH_SHORT).show();
            return;
        }
        
        ApiClient accountApiClient = new ApiClient(baseUrl, apiKey);
        
        Toast.makeText(this, getString(R.string.deleting_session), Toast.LENGTH_SHORT).show();
        
        accountApiClient.deleteSession(session.getSessionId(), new ApiClient.DeleteSessionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    sessionManager.deleteSession(session.getSessionId());
                    loadSessions();
                    Toast.makeText(SessionListActivity.this, 
                        getString(R.string.session_deleted_toast), Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (error.contains("404") || error.contains("Not Found")) {
                        sessionManager.deleteSession(session.getSessionId());
                        loadSessions();
                        Toast.makeText(SessionListActivity.this, 
                            getString(R.string.deleted_local_session_not_on_server), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(SessionListActivity.this, 
                            getString(R.string.delete_session_failed, error), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}

