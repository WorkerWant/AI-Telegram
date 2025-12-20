package org.telegram.messenger;

import android.content.SharedPreferences;

import org.telegram.messenger.support.LongSparseLongArray;

public final class OutgoingMessagesBlocker {
    private static final String PREF_KEY = "outgoing_messages_blocked";
    private static final Object[] locks = new Object[UserConfig.MAX_ACCOUNT_COUNT];
    private static final LongSparseLongArray[] blockedUntil = new LongSparseLongArray[UserConfig.MAX_ACCOUNT_COUNT];

    static {
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
    }

    private static LongSparseLongArray getMap(int account) {
        LongSparseLongArray map = blockedUntil[account];
        if (map != null) {
            return map;
        }
        synchronized (locks[account]) {
            map = blockedUntil[account];
            if (map == null) {
                map = load(account);
                blockedUntil[account] = map;
            }
        }
        return map;
    }

    private static LongSparseLongArray load(int account) {
        LongSparseLongArray map = new LongSparseLongArray();
        SharedPreferences preferences = MessagesController.getInstance(account).getMainSettings();
        String raw = preferences.getString(PREF_KEY, "");
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        long now = System.currentTimeMillis();
        String[] entries = raw.split(",");
        for (String entry : entries) {
            int split = entry.indexOf(':');
            if (split <= 0) {
                continue;
            }
            try {
                long dialogId = Long.parseLong(entry.substring(0, split));
                long until = Long.parseLong(entry.substring(split + 1));
                if (until > now) {
                    map.put(dialogId, until);
                }
            } catch (Exception ignore) {
            }
        }
        return map;
    }

    public static long getBlockedUntil(int account, long dialogId) {
        LongSparseLongArray map = getMap(account);
        long until = map.get(dialogId);
        if (until != 0 && until <= System.currentTimeMillis()) {
            map.delete(dialogId);
            return 0;
        }
        return until;
    }

    public static long blockForMinutes(int account, long dialogId, int minutes) {
        if (minutes <= 0) {
            minutes = 1;
        }
        long until = System.currentTimeMillis() + minutes * 60_000L;
        LongSparseLongArray map = getMap(account);
        map.put(dialogId, until);
        save(account, map);
        return until;
    }

    private static void save(int account, LongSparseLongArray map) {
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        for (int i = 0, size = map.size(); i < size; i++) {
            long until = map.valueAt(i);
            if (until <= now) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(map.keyAt(i)).append(':').append(until);
        }
        MessagesController.getInstance(account).getMainSettings().edit().putString(PREF_KEY, sb.toString()).apply();
    }
}
