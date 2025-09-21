package org.telegram.messenger;

import android.text.TextUtils;
import android.util.Log;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AIResponseService {
    
    private static final String TAG = "AIResponseService";
    private static volatile AIResponseService[] Instance = new AIResponseService[UserConfig.MAX_ACCOUNT_COUNT];
    
    private int currentAccount;
    private ScheduledExecutorService scheduler;
    private Map<Long, MessageContext> activeContexts = new HashMap<>();
    private boolean isRunning = false;
    
    private static class MessageContext {
        long chatId;
        List<TLRPC.Message> messages = new ArrayList<>();
        long lastProcessedId = 0;
        boolean isProcessing = false;
        
        MessageContext(long chatId) {
            this.chatId = chatId;
        }
    }
    
    public static AIResponseService getInstance(int num) {
        AIResponseService localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (AIResponseService.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new AIResponseService(num);
                }
            }
        }
        return localInstance;
    }
    
    private AIResponseService(int account) {
        currentAccount = account;
        scheduler = new ScheduledThreadPoolExecutor(1);
    }
    
    public void startAutoResponse() {
        if (isRunning || TextUtils.isEmpty(SharedConfig.aiApiToken)) {
            return;
        }
        
        isRunning = true;
        
        scheduler.scheduleWithFixedDelay(() -> {
            checkForNewMessages();
        }, 0, 30, TimeUnit.SECONDS);
        
        if (BuildVars.LOGS_ENABLED) {
            Log.d(TAG, "Auto-response service started");
        }
    }

    public void stopAutoResponse() {
        isRunning = false;
        scheduler.shutdownNow();
        activeContexts.clear();

        if (BuildVars.LOGS_ENABLED) {
            Log.d(TAG, "Auto-response service stopped");
        }
    }
    
    private void checkForNewMessages() {
        if (!isRunning || !SharedConfig.aiEnabled) {
            return;
        }
        
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        
        ArrayList<TLRPC.Dialog> dialogs = messagesController.getAllDialogs();
        
        for (TLRPC.Dialog dialog : dialogs) {
            if (dialog.unread_count == 0) {
                continue;
            }
            
            long chatId = dialog.id;
            
            if (!isAIEnabledForChat(chatId)) {
                continue;
            }
            
            MessageContext context = activeContexts.get(chatId);
            if (context == null) {
                context = new MessageContext(chatId);
                activeContexts.put(chatId, context);
            }
            
            if (context.isProcessing) {
                continue;
            }
            
            loadMessagesForChat(chatId, context);
        }
    }
    
    private boolean isAIEnabledForChat(long chatId) {
        if (chatId > 0) {
            return SharedConfig.aiEnabledUsers != null &&
                   SharedConfig.aiEnabledUsers.contains(String.valueOf(chatId));
        } else {
            return SharedConfig.aiEnabledGroups != null &&
                   SharedConfig.aiEnabledGroups.contains(String.valueOf(-chatId));
        }
    }
    
    private void loadMessagesForChat(long chatId, MessageContext context) {
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        
        ArrayList<MessageObject> messages = messagesController.dialogMessage.get(chatId);
        
        if (messages == null || messages.isEmpty()) {
            return;
        }
        
        StringBuilder conversationBuilder = new StringBuilder();
        List<String> messageTexts = new ArrayList<>();
        
        for (int i = Math.max(0, messages.size() - SharedConfig.aiContextSize); i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            
            if (msg.messageOwner == null || msg.messageOwner.message == null) {
                continue;
            }
            
            if (msg.getId() <= context.lastProcessedId) {
                continue;
            }
            
            String sender = msg.isOutOwner() ? "Me" : "Other";
            String text = msg.messageOwner.message;
            
            if (!TextUtils.isEmpty(text)) {
                messageTexts.add(sender + ": " + text);
                conversationBuilder.append(sender).append(": ").append(text).append("\n");
            }
        }
        
        if (!messageTexts.isEmpty()) {
            context.isProcessing = true;
            generateAutoResponse(chatId, messageTexts, context);
        }
    }
    
    private void generateAutoResponse(long chatId, List<String> messages, MessageContext context) {
        GPTApiClient.generateAutoResponse(messages, new GPTApiClient.GPTCallback() {
            @Override
            public void onSuccess(String response) {
                if (!isRunning) {
                    return;
                }
                
                sendAutoResponse(chatId, response, context);
            }
            
            @Override
            public void onError(String error) {
                if (BuildVars.LOGS_ENABLED) {
                    Log.e(TAG, "Failed to generate response: " + error);
                }
                context.isProcessing = false;
            }
        });
    }
    
    private void sendAutoResponse(long chatId, String response, MessageContext context) {
        if (TextUtils.isEmpty(response)) {
            context.isProcessing = false;
            return;
        }
        
        int typingDelay = SharedConfig.aiTypingDelay;
        
        AndroidUtilities.runOnUIThread(() -> {
            MessagesController.getInstance(currentAccount).sendTyping(chatId, 0, 0, 0);
        });
        
        scheduler.schedule(() -> {
            AndroidUtilities.runOnUIThread(() -> {
                SendMessagesHelper.getInstance(currentAccount).sendMessage(
                    SendMessagesHelper.SendMessageParams.of(response, chatId, null, null, null, false, null, null, null, true, 0, null, false)
                );
                
                MessagesController messagesController = MessagesController.getInstance(currentAccount);
                ArrayList<MessageObject> messages = messagesController.dialogMessage.get(chatId);
                if (messages != null && !messages.isEmpty()) {
                    context.lastProcessedId = messages.get(messages.size() - 1).getId();
                }
                
                context.isProcessing = false;
                
                if (BuildVars.LOGS_ENABLED) {
                    Log.d(TAG, "Auto-response sent to chat " + chatId);
                }
            });
        }, typingDelay, TimeUnit.SECONDS);
    }
    
    public boolean isAutoResponseActive() {
        return isRunning;
    }
    
    public void toggleAutoResponseForChat(long chatId, boolean enable) {
        if (enable) {
            if (!activeContexts.containsKey(chatId)) {
                activeContexts.put(chatId, new MessageContext(chatId));
            }
        } else {
            activeContexts.remove(chatId);
        }
    }
}