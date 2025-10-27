package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.SerializedData;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class ObjectDataActivity extends BaseFragment {
    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private long dialogId;
    private long topicId;
    private boolean isMessage;
    private TLRPC.Message message;

    private int rowCount;
    private int headerRow;
    private int typeRow;
    private int idRow;
    private int dialogIdRow;
    private int usernameRow;
    private int titleRow;
    private int nameRow;
    private int phoneRow;
    private int flagsHeaderRow;
    private int flagsSectionRow;
    private int detailsHeaderRow;
    private int detailsStartRow;
    private int detailsEndRow;
    private int jsonRow;

    private final ArrayList<Map.Entry<String, String>> details = new ArrayList<>();

    public ObjectDataActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        dialogId = getArguments().getLong("dialog_id");
        topicId = getArguments().getLong("topic_id", 0);
        String messageData = getArguments().getString("message_data", null);
        if (!TextUtils.isEmpty(messageData)) {
            try {
                byte[] bytes = android.util.Base64.decode(messageData, android.util.Base64.DEFAULT);
                SerializedData sd = new SerializedData(bytes);
                int constructor = sd.readInt32(false);
                message = TLRPC.Message.TLdeserialize(sd, constructor, false);
                isMessage = message != null;
                sd.cleanup();
            } catch (Throwable ignore) {}
        }
        buildData();
        updateRows();
        return super.onFragmentCreate();
    }

    private void updateRows() {
        rowCount = 0;
        headerRow = rowCount++;
        if (!isMessage) {
            typeRow = rowCount++;
            idRow = rowCount++;
            dialogIdRow = rowCount++;
            if (hasUsername()) {
                usernameRow = rowCount++;
            } else {
                usernameRow = -1;
            }
            if (hasTitle()) {
                titleRow = rowCount++;
            } else {
                titleRow = -1;
            }
            if (hasName()) {
                nameRow = rowCount++;
            } else {
                nameRow = -1;
            }
            if (hasPhone()) {
                phoneRow = rowCount++;
            } else {
                phoneRow = -1;
            }
            flagsHeaderRow = rowCount++;
        } else {
            typeRow = idRow = dialogIdRow = usernameRow = titleRow = nameRow = phoneRow = -1;
            flagsHeaderRow = -1;
        }
        detailsHeaderRow = rowCount++;
        detailsStartRow = rowCount;
        rowCount += details.size();
        detailsEndRow = rowCount;
        jsonRow = rowCount++;
        flagsSectionRow = rowCount++;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.ObjectData));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter = new ListAdapter(context));

        listView.setOnItemClickListener((view, position, x, y) -> {
            if (position == typeRow) {
                showDetails("Type", getTypeValue());
            } else if (position == idRow) {
                showDetails("ID", String.valueOf(getPrimaryId()));
            } else if (position == dialogIdRow) {
                showDetails("Dialog ID", String.valueOf(dialogId));
            } else if (position == usernameRow && usernameRow >= 0) {
                showDetails("Username", getUsername());
            } else if (position == titleRow && titleRow >= 0) {
                showDetails("Title", getTitle());
            } else if (position == nameRow && nameRow >= 0) {
                showDetails("Name", getName());
            } else if (position == phoneRow && phoneRow >= 0) {
                showDetails("Phone", getPhone());
            } else if (position >= detailsStartRow && position < detailsEndRow) {
                Map.Entry<String, String> e = details.get(position - detailsStartRow);
                showDetails(e.getKey(), e.getValue());
            } else if (position == jsonRow) {
                showDetails("JSON", buildJson());
            }
        });

        listView.setOnItemLongClickListener((view, position, x, y) -> {
            if (position == typeRow) {
                copy(getTypeValue());
            } else if (position == idRow) {
                copy(String.valueOf(getPrimaryId()));
            } else if (position == dialogIdRow) {
                copy(String.valueOf(dialogId));
            } else if (position == usernameRow && usernameRow >= 0) {
                copy(getUsername());
            } else if (position == titleRow && titleRow >= 0) {
                copy(getTitle());
            } else if (position == nameRow && nameRow >= 0) {
                copy(getName());
            } else if (position == phoneRow && phoneRow >= 0) {
                copy(getPhone());
            } else if (position >= detailsStartRow && position < detailsEndRow) {
                Map.Entry<String, String> e = details.get(position - detailsStartRow);
                copy(e.getValue());
            } else if (position == jsonRow) {
                copy(buildJson());
            }
            return true;
        });

        return fragmentView;
    }

    private void copy(String text) {
        if (TextUtils.isEmpty(text)) return;
        AndroidUtilities.addToClipboard(text);
        BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
    }

    private String shortValue(String value) {
        if (TextUtils.isEmpty(value)) return "";
        final int max = 60;
        if (value.length() <= max) return value;
        return value.substring(0, max - 1) + "…";
    }

    private void showDetails(String title, String value) {
        if (TextUtils.isEmpty(value)) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        b.setTitle(title);
        b.setMessage(value);
        b.setPositiveButton(LocaleController.getString(R.string.Copy), (d, w) -> copy(value));
        b.setNegativeButton(LocaleController.getString(R.string.Close), null);
        showDialog(b.create());
    }

    private void buildData() {
        details.clear();
        if (isMessage && message != null) {
            buildMessageDetails();
        } else if (DialogObject.isEncryptedDialog(dialogId)) {
            int encId = DialogObject.getEncryptedChatId(dialogId);
            TLRPC.EncryptedChat ec = MessagesController.getInstance(currentAccount).getEncryptedChat(encId);
            if (ec != null) {
                putDetail("EncryptedChat.id", String.valueOf(ec.id));
                putDetail("EncryptedChat.layer", String.valueOf(ec.layer));
                putDetail("EncryptedChat.ttl", String.valueOf(ec.ttl));
                putDetail("EncryptedChat.key_fingerprint", String.valueOf(ec.key_fingerprint));
                putDetail("EncryptedChat.participant_id", String.valueOf(ec.participant_id));
            }
        } else if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            if (user != null) {
                putDetail("User.premium", String.valueOf(user.premium));
                putDetail("User.verified", String.valueOf(user.verified));
                if (user.bot) putDetail("User.bot", "true");
                if (user.restricted) putDetail("User.restricted", "true");
                if (user.scam) putDetail("User.scam", "true");
                if (user.fake) putDetail("User.fake", "true");
                if (user.access_hash != 0) putDetail("User.access_hash", String.valueOf(user.access_hash));
            }
        } else if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            if (chat != null) {
                putDetail("Chat.megagroup", String.valueOf(chat.megagroup));
                putDetail("Chat.broadcast", String.valueOf(ChatObject.isChannelAndNotMegaGroup(chat)));
                if (chat.participants_count > 0) putDetail("Chat.participants", String.valueOf(chat.participants_count));
                if (!TextUtils.isEmpty(chat.username)) putDetail("Chat.username", chat.username);
                TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(chat.id);
                if (chatFull != null && chatFull.linked_chat_id != 0) putDetail("Chat.linked_chat_id", String.valueOf(chatFull.linked_chat_id));
            }
        }
    }

    private void buildMessageDetails() {
        putDetail("Kind", "Message");
        putDetail("Message.id", String.valueOf(message.id));
        putDetail("Message.date", String.valueOf(message.date));
        putDetail("Dialog ID", String.valueOf(dialogId));
        if (message.from_id != null) {
            long fromPeer = DialogObject.getPeerDialogId(message.from_id);
            putDetail("From", fromPeer + " (" + DialogObject.getName(MessagesController.getInstance(currentAccount).getUserOrChat(fromPeer)) + ")");
        }
        if (message.peer_id != null) {
            putDetail("Peer", String.valueOf(DialogObject.getPeerDialogId(message.peer_id)));
        }
        if (message.via_bot_id != 0) putDetail("via_bot_id", String.valueOf(message.via_bot_id));
        if (message.via_bot_name != null) putDetail("via_bot_name", message.via_bot_name);
        if (message.reply_to != null) {
            if (message.reply_to.reply_to_msg_id != 0) putDetail("reply_to_msg_id", String.valueOf(message.reply_to.reply_to_msg_id));
            if (message.reply_to.reply_to_top_id != 0) putDetail("reply_to_top_id", String.valueOf(message.reply_to.reply_to_top_id));
        }
        if (message.fwd_from != null) {
            if (message.fwd_from.from_id != null) putDetail("fwd_from.from", String.valueOf(DialogObject.getPeerDialogId(message.fwd_from.from_id)));
            if (message.fwd_from.channel_post != 0) putDetail("fwd_from.channel_post", String.valueOf(message.fwd_from.channel_post));
            if (message.fwd_from.saved_from_peer != null) putDetail("fwd_from.saved_from_peer", String.valueOf(DialogObject.getPeerDialogId(message.fwd_from.saved_from_peer)));
            if (message.fwd_from.saved_from_msg_id != 0) putDetail("fwd_from.saved_from_msg_id", String.valueOf(message.fwd_from.saved_from_msg_id));
        }
        if (message.views != 0) putDetail("views", String.valueOf(message.views));
        if (message.forwards != 0) putDetail("forwards", String.valueOf(message.forwards));
        if (message.edit_date != 0) putDetail("edit_date", String.valueOf(message.edit_date));
        if (message.ttl_period != 0) putDetail("ttl_period", String.valueOf(message.ttl_period));
        if (message.ttl != 0) putDetail("ttl", String.valueOf(message.ttl));
        if (message.grouped_id != 0) putDetail("grouped_id", String.valueOf(message.grouped_id));
        putDetail("flags.out", String.valueOf(message.out));
        putDetail("flags.unread", String.valueOf(message.unread));
        putDetail("flags.mentioned", String.valueOf(message.mentioned));
        putDetail("flags.silent", String.valueOf(message.silent));
        putDetail("flags.post", String.valueOf(message.post));
        putDetail("flags.pinned", String.valueOf(message.pinned));
        putDetail("flags.noforwards", String.valueOf(message.noforwards));
        if (!TextUtils.isEmpty(message.message)) {
            putDetail("text", message.message);
        }
        if (message.entities != null && !message.entities.isEmpty()) {
            putDetail("entities.count", String.valueOf(message.entities.size()));
        }
        if (message.media != null) {
            putDetail("media.type", message.media.getClass().getSimpleName());
            if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
                TLRPC.Photo p = ((TLRPC.TL_messageMediaPhoto) message.media).photo;
                if (p != null) {
                    putDetail("photo.id", String.valueOf(p.id));
                    if (p.sizes != null) {
                        putDetail("photo.sizes", String.valueOf(p.sizes.size()));
                        for (int i = 0; i < p.sizes.size(); i++) {
                            TLRPC.PhotoSize s = p.sizes.get(i);
                            String prefix = "photo.size[" + i + "]";
                            putDetail(prefix + ".type", s.type);
                            putDetail(prefix + ".w", String.valueOf(s.w));
                            putDetail(prefix + ".h", String.valueOf(s.h));
                            putDetail(prefix + ".size", String.valueOf(s.size));
                        }
                    }
                }
            } else if (message.media instanceof TLRPC.TL_messageMediaDocument) {
                TLRPC.Document d = ((TLRPC.TL_messageMediaDocument) message.media).document;
                if (d != null) {
                    putDetail("document.id", String.valueOf(d.id));
                    putDetail("document.size", String.valueOf(d.size));
                    if (d.mime_type != null) putDetail("document.mime", d.mime_type);
                    if (d.attributes != null) {
                        for (int i = 0; i < d.attributes.size(); i++) {
                            TLRPC.DocumentAttribute a = d.attributes.get(i);
                            String key = "document.attr." + a.getClass().getSimpleName();
                            if (a instanceof TLRPC.TL_documentAttributeFilename) {
                                putDetail(key + ".name", ((TLRPC.TL_documentAttributeFilename) a).file_name);
                            } else if (a instanceof TLRPC.TL_documentAttributeVideo) {
                                TLRPC.TL_documentAttributeVideo v = (TLRPC.TL_documentAttributeVideo) a;
                                putDetail(key + ".w", String.valueOf(v.w));
                                putDetail(key + ".h", String.valueOf(v.h));
                                putDetail(key + ".duration", String.valueOf(v.duration));
                                putDetail(key + ".round_message", String.valueOf(v.round_message));
                            } else if (a instanceof TLRPC.TL_documentAttributeAudio) {
                                TLRPC.TL_documentAttributeAudio au = (TLRPC.TL_documentAttributeAudio) a;
                                putDetail(key + ".duration", String.valueOf(au.duration));
                                if (au.title != null) putDetail(key + ".title", au.title);
                                if (au.performer != null) putDetail(key + ".performer", au.performer);
                                putDetail(key + ".voice", String.valueOf(au.voice));
                            } else if (a instanceof TLRPC.TL_documentAttributeSticker) {
                                TLRPC.TL_documentAttributeSticker st = (TLRPC.TL_documentAttributeSticker) a;
                                if (st.alt != null) putDetail(key + ".alt", st.alt);
                                if (st.stickerset != null) {
                                    if (st.stickerset instanceof TLRPC.TL_inputStickerSetShortName) {
                                        putDetail(key + ".set", ((TLRPC.TL_inputStickerSetShortName) st.stickerset).short_name);
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (message.media instanceof TLRPC.TL_messageMediaGeo) {
                TLRPC.GeoPoint g = ((TLRPC.TL_messageMediaGeo) message.media).geo;
                if (g != null) {
                    putDetail("geo.lat", String.valueOf(g.lat));
                    putDetail("geo.long", String.valueOf(g._long));
                }
            } else if (message.media instanceof TLRPC.TL_messageMediaGeoLive) {
                TLRPC.GeoPoint g = ((TLRPC.TL_messageMediaGeoLive) message.media).geo;
                if (g != null) {
                    putDetail("geolive.lat", String.valueOf(g.lat));
                    putDetail("geolive.long", String.valueOf(g._long));
                }
                TLRPC.TL_messageMediaGeoLive gl = (TLRPC.TL_messageMediaGeoLive) message.media;
                putDetail("geolive.heading", String.valueOf(gl.heading));
                putDetail("geolive.period", String.valueOf(gl.period));
                putDetail("geolive.radius", String.valueOf(gl.proximity_notification_radius));
            } else if (message.media instanceof TLRPC.TL_messageMediaContact) {
                TLRPC.TL_messageMediaContact c = (TLRPC.TL_messageMediaContact) message.media;
                putDetail("contact.first_name", c.first_name);
                putDetail("contact.last_name", c.last_name);
                putDetail("contact.phone_number", c.phone_number);
                putDetail("contact.user_id", String.valueOf(c.user_id));
            } else if (message.media instanceof TLRPC.TL_messageMediaPoll) {
                TLRPC.TL_messageMediaPoll pm = (TLRPC.TL_messageMediaPoll) message.media;
                if (pm.poll != null) {
                    if (pm.poll.question != null) putDetail("poll.question", pm.poll.question.text);
                    putDetail("poll.quiz", String.valueOf(pm.poll.quiz));
                    putDetail("poll.public_voters", String.valueOf(pm.poll.public_voters));
                    putDetail("poll.multiple_choice", String.valueOf(pm.poll.multiple_choice));
                    putDetail("poll.answers", String.valueOf(pm.poll.answers != null ? pm.poll.answers.size() : 0));
                }
            } else if (message.media instanceof TLRPC.TL_messageMediaVenue) {
                TLRPC.TL_messageMediaVenue v = (TLRPC.TL_messageMediaVenue) message.media;
                putDetail("venue.title", v.title);
                putDetail("venue.address", v.address);
                putDetail("venue.provider", v.provider);
                putDetail("venue.id", v.venue_id);
                putDetail("venue.type", v.venue_type);
            } else if (message.media instanceof TLRPC.TL_messageMediaInvoice) {
                TLRPC.TL_messageMediaInvoice inv = (TLRPC.TL_messageMediaInvoice) message.media;
                putDetail("invoice.title", inv.title);
                putDetail("invoice.currency", inv.currency);
                putDetail("invoice.total_amount", String.valueOf(inv.total_amount));
            } else if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
                TLRPC.WebPage w = ((TLRPC.TL_messageMediaWebPage) message.media).webpage;
                if (w != null) {
                    putDetail("webpage.url", w.url);
                    putDetail("webpage.site_name", w.site_name);
                    putDetail("webpage.title", w.title);
                    if (!TextUtils.isEmpty(w.description)) putDetail("webpage.description", w.description);
                }
            } else if (message.media instanceof TLRPC.TL_messageMediaStory) {
                TLRPC.TL_messageMediaStory st = (TLRPC.TL_messageMediaStory) message.media;
                if (st.peer != null) putDetail("story.peer", String.valueOf(DialogObject.getPeerDialogId(st.peer)));
                putDetail("story.id", String.valueOf(st.id));
            }
        }
    }

    private void putDetail(String k, String v) {
        if (TextUtils.isEmpty(v)) return;
        details.add(new LinkedHashMap.SimpleEntry<>(k, v));
    }

    private boolean hasUsername() {
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(dialogId);
            return u != null && !TextUtils.isEmpty(u.username);
        } else if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat c = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            return c != null && !TextUtils.isEmpty(c.username);
        }
        return false;
    }

    private boolean hasTitle() {
        if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat c = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            return c != null && !TextUtils.isEmpty(c.title);
        }
        return false;
    }

    private boolean hasName() {
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(dialogId);
            return u != null && (!TextUtils.isEmpty(u.first_name) || !TextUtils.isEmpty(u.last_name));
        }
        return false;
    }

    private boolean hasPhone() {
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(dialogId);
            return u != null && !TextUtils.isEmpty(u.phone);
        }
        return false;
    }

    private String getUsername() {
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(dialogId);
            return u != null ? u.username : null;
        } else if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat c = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            return c != null ? c.username : null;
        }
        return null;
    }

    private String getTitle() {
        if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat c = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            return c != null ? c.title : null;
        }
        return null;
    }

    private String getName() {
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(dialogId);
            if (u == null) return null;
            String fn = u.first_name == null ? "" : u.first_name;
            String ln = u.last_name == null ? "" : u.last_name;
            String full = (fn + " " + ln).trim();
            return full.length() == 0 ? null : full;
        }
        return null;
    }

    private String getPhone() {
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(dialogId);
            return u != null ? u.phone : null;
        }
        return null;
    }

    private String getTypeValue() {
        if (DialogObject.isEncryptedDialog(dialogId)) return "Secret Chat";
        if (DialogObject.isUserDialog(dialogId)) return "Private";
        if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat c = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            if (c != null) {
                if (ChatObject.isChannelAndNotMegaGroup(c)) return "Channel";
                if (c.megagroup) return "Supergroup";
                return "Group";
            }
            return "Chat";
        }
        return "Dialog";
    }

    private long getPrimaryId() {
        if (DialogObject.isEncryptedDialog(dialogId)) {
            return DialogObject.getEncryptedChatId(dialogId);
        } else if (DialogObject.isUserDialog(dialogId)) {
            return dialogId;
        } else if (DialogObject.isChatDialog(dialogId)) {
            return -dialogId;
        }
        return dialogId;
    }

    private String buildJson() {
        try {
            JSONObject json = new JSONObject();
            if (isMessage && message != null) {
                json.put("dialog_id", dialogId);
                if (topicId != 0) json.put("topic_id", topicId);
                json.put("id", message.id);
                json.put("date", message.date);
                if (message.from_id != null) json.put("from_id", DialogObject.getPeerDialogId(message.from_id));
                if (message.peer_id != null) json.put("peer_id", DialogObject.getPeerDialogId(message.peer_id));
                json.put("out", message.out);
                json.put("unread", message.unread);
                if (!TextUtils.isEmpty(message.message)) json.put("text", message.message);
                if (message.entities != null) json.put("entities_count", message.entities.size());
                if (message.media != null) json.put("media_type", message.media.getClass().getSimpleName());
                for (Map.Entry<String, String> e : details) {
                    json.put(e.getKey(), e.getValue());
                }
            } else {
                json.put("dialog_id", dialogId);
                if (topicId != 0) json.put("topic_id", topicId);
                json.put("type", getTypeValue());
                json.put("primary_id", getPrimaryId());
                if (hasUsername()) json.put("username", getUsername());
                if (hasTitle()) json.put("title", getTitle());
                if (hasName()) json.put("name", getName());
                if (hasPhone()) json.put("phone", getPhone());
                for (Map.Entry<String, String> e : details) {
                    json.put(e.getKey(), e.getValue());
                }
            }
            return json.toString(2);
        } catch (Exception ignore) {
            return "";
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context mContext;
        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    cell.setText(LocaleController.getString(R.string.ObjectData));
                    break;
                }
                case 1: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == typeRow) {
                        cell.setTextAndValue("Type", shortValue(getTypeValue()), true);
                    } else if (position == idRow) {
                        cell.setTextAndValue("ID", shortValue(String.valueOf(getPrimaryId())), true);
                    } else if (position == dialogIdRow) {
                        cell.setTextAndValue("Dialog ID", shortValue(String.valueOf(dialogId)), usernameRow >= 0 || titleRow >= 0 || nameRow >= 0 || phoneRow >= 0);
                    } else if (position == usernameRow) {
                        cell.setTextAndValue("Username", shortValue(getUsername()), titleRow >= 0 || nameRow >= 0 || phoneRow >= 0);
                    } else if (position == titleRow) {
                        cell.setTextAndValue("Title", shortValue(getTitle()), nameRow >= 0 || phoneRow >= 0);
                    } else if (position == nameRow) {
                        cell.setTextAndValue("Name", shortValue(getName()), phoneRow >= 0);
                    } else if (position == phoneRow) {
                        cell.setTextAndValue("Phone", shortValue(getPhone()), true);
                    } else if (position == detailsHeaderRow) {
                        cell.setTextAndValue("Details", "", details.size() > 0);
                    } else if (position >= detailsStartRow && position < detailsEndRow) {
                        Map.Entry<String, String> e = details.get(position - detailsStartRow);
                        boolean needDivider = position + 1 < detailsEndRow;
                        cell.setTextAndValue(e.getKey(), shortValue(e.getValue()), needDivider);
                    } else if (position == jsonRow) {
                        cell.setTextAndValue(LocaleController.getString(R.string.CopyObject), "", false);
                    }
                    break;
                }
                default: {
                    break;
                }
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                default:
                    view = new ShadowSectionCell(mContext);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) return 0;
            return 1;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position != headerRow && position != flagsHeaderRow && position != flagsSectionRow;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, TextSettingsCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        return themeDescriptions;
    }
}
