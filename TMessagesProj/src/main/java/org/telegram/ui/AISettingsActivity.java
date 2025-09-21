package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.GPTApiClient;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class AISettingsActivity extends BaseFragment {
    
    private RecyclerListView listView;
    private ListAdapter listAdapter;
    
    private int rowCount;
    private int headerRow;
    private int apiKeyRow;
    private int apiKeySectionRow;
    private int featuresHeaderRow;
    private int voiceSummarizeRow;
    private int autoReplyRow;
    private int featuresSectionRow;
    private int testConnectionRow;
    private int testSectionRow;
    
    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }
    
    private void updateRows() {
        rowCount = 0;
        headerRow = rowCount++;
        apiKeyRow = rowCount++;
        apiKeySectionRow = rowCount++;
        featuresHeaderRow = rowCount++;
        voiceSummarizeRow = rowCount++;
        autoReplyRow = rowCount++;
        featuresSectionRow = rowCount++;
        testConnectionRow = rowCount++;
        testSectionRow = rowCount++;
    }
    
    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("AISettings", R.string.AISettings));
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
            if (position == apiKeyRow) {
                showApiKeyDialog();
            } else if (position == voiceSummarizeRow) {
                if (view instanceof NotificationsCheckCell) {
                    NotificationsCheckCell checkCell = (NotificationsCheckCell) view;
                    if (LocaleController.isRTL && x <= AndroidUtilities.dp(76) ||
                        !LocaleController.isRTL && x >= view.getMeasuredWidth() - AndroidUtilities.dp(76)) {
                        SharedConfig.aiVoiceSummarize = !SharedConfig.aiVoiceSummarize;
                        SharedConfig.saveConfig();
                        checkCell.setChecked(SharedConfig.aiVoiceSummarize);
                    } else {
                        if (TextUtils.isEmpty(SharedConfig.aiApiToken)) {
                            BulletinFactory.of(this).createErrorBulletin("Please set API key first").show();
                            return;
                        }
                        presentFragment(new AIVoiceSettingsActivity());
                    }
                }
            } else if (position == autoReplyRow) {
                if (view instanceof NotificationsCheckCell) {
                    if (LocaleController.isRTL && x <= AndroidUtilities.dp(76) ||
                        !LocaleController.isRTL && x >= view.getMeasuredWidth() - AndroidUtilities.dp(76)) {
                        BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_info, "Auto Reply coming soon").show();
                    } else {
                        BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_info, "Auto Reply settings coming soon").show();
                    }
                }
            } else if (position == testConnectionRow) {
                testAIConnection();
            }
        });
        
        return fragmentView;
    }
    
    private void showApiKeyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("OpenAI API Key");
        
        LinearLayout linearLayout = new LinearLayout(getParentActivity());
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        
        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), false));
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setHint("sk-...");
        editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editText.setPadding(0, 0, 0, 0);
        editText.setText(SharedConfig.aiApiToken);
        linearLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 24, 6, 24, 0));
        
        TextView helpText = new TextView(getParentActivity());
        helpText.setText("Get your API key from platform.openai.com");
        helpText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        helpText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        linearLayout.addView(helpText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 6));
        
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString("Save", R.string.Save), (dialog, which) -> {
            String apiKey = editText.getText().toString().trim();
            SharedConfig.aiApiToken = apiKey;
            SharedConfig.saveConfig();
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(apiKeyRow);
            }
            if (!TextUtils.isEmpty(apiKey)) {
                BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_check_s, "API Key saved").show();
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.setNeutralButton("Clear", (dialog, which) -> {
            SharedConfig.aiApiToken = "";
            SharedConfig.saveConfig();
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(apiKeyRow);
            }
            BulletinFactory.of(this).createSimpleBulletin(R.drawable.msg_delete, "API Key cleared").show();
        });
        showDialog(builder.create());
        AndroidUtilities.runOnUIThread(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        }, 100);
    }
    
    private void testAIConnection() {
        if (TextUtils.isEmpty(SharedConfig.aiApiToken)) {
            BulletinFactory.of(this).createErrorBulletin("Please set API key first").show();
            return;
        }
        
        AlertDialog progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.setMessage("Testing connection...");
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();
        
        long startTime = System.currentTimeMillis();
        
        GPTApiClient.testConnection(SharedConfig.aiApiToken, new GPTApiClient.GPTCallback() {
            @Override
            public void onSuccess(String response) {
                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    long responseTime = System.currentTimeMillis() - startTime;
                    showTestResultDialog(true, response, responseTime, null);
                });
            }
            
            @Override
            public void onError(String error) {
                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    showTestResultDialog(false, null, 0, error);
                });
            }
        });
    }
    
    private void showTestResultDialog(boolean success, String response, long responseTime, String error) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(success ? "Connection Successful" : "Connection Failed");
        
        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(10), AndroidUtilities.dp(24), AndroidUtilities.dp(10));
        
        if (success) {
            String model = "gpt-3.5-turbo";
            String message = "Connection successful!";
            long respTime = responseTime;
            
            if (response != null) {
                try {
                    org.json.JSONObject json = new org.json.JSONObject(response);
                    model = json.optString("model", "gpt-3.5-turbo");
                    message = json.optString("message", "Connection successful!");
                    respTime = json.optLong("responseTime", responseTime);
                } catch (Exception e) {
                }
            }
            
            addResultRow(layout, "Model", model);
            addResultRow(layout, "Response Time", respTime + "ms");
            addResultRow(layout, "Test Response", message);
        } else {
            TextView errorText = new TextView(getParentActivity());
            errorText.setText(error != null ? error : "Unknown error occurred");
            errorText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            errorText.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
            layout.addView(errorText);
            
            TextView helpText = new TextView(getParentActivity());
            helpText.setText("\nPlease check:\n• API key is valid\n• Internet connection is active\n• OpenAI services are available");
            helpText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            helpText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            helpText.setPadding(0, AndroidUtilities.dp(10), 0, 0);
            layout.addView(helpText);
        }
        
        builder.setView(layout);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        showDialog(builder.create());
    }
    
    private void addResultRow(LinearLayout layout, String label, String value) {
        LinearLayout row = new LinearLayout(getParentActivity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        
        TextView labelView = new TextView(getParentActivity());
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        labelView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(labelView);
        
        TextView valueView = new TextView(getParentActivity());
        valueView.setText(value);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        valueView.setGravity(Gravity.RIGHT);
        row.addView(valueView);
        
        layout.addView(row);
    }
    
    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        
        private Context mContext;
        
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
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == apiKeyRow) {
                        String value;
                        if (TextUtils.isEmpty(SharedConfig.aiApiToken)) {
                            value = "Not set";
                        } else {
                            String key = SharedConfig.aiApiToken;
                            if (key.length() > 4) {
                                value = "••••" + key.substring(key.length() - 4);
                            } else {
                                value = "••••";
                            }
                        }
                        textCell.setTextAndValue("API Key", value, true);
                    } else if (position == testConnectionRow) {
                        textCell.setText("Test AI Connection", false);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    }
                    break;
                }
                case 1: {
                    TextInfoPrivacyCell textCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == apiKeySectionRow) {
                        textCell.setText("Your OpenAI API key is stored locally and never shared");
                    } else if (position == featuresSectionRow) {
                        textCell.setText("Enable AI features for your chats");
                    } else if (position == testSectionRow) {
                        textCell.setText("Check if your API key is working correctly");
                    }
                    break;
                }
                case 2: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == headerRow) {
                        headerCell.setText("Configuration");
                    } else if (position == featuresHeaderRow) {
                        headerCell.setText("Features");
                    }
                    break;
                }
                case 3: {
                    NotificationsCheckCell checkCell = (NotificationsCheckCell) holder.itemView;
                    if (position == voiceSummarizeRow) {
                        checkCell.setTextAndValueAndCheck(
                            "Voice Summarization",
                            "Summarize voice messages with AI",
                            SharedConfig.aiVoiceSummarize, 0, true, true);
                    } else if (position == autoReplyRow) {
                        checkCell.setTextAndValueAndCheck(
                            "Auto Reply",
                            "Coming soon",
                            false, 0, false, true);
                        checkCell.setEnabled(false);
                    }
                    break;
                }
            }
        }
        
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == apiKeyRow || position == voiceSummarizeRow || 
                   position == testConnectionRow;
        }
        
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 2:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new NotificationsCheckCell(mContext);
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
            if (position == apiKeyRow || position == testConnectionRow) {
                return 0;
            } else if (position == apiKeySectionRow || position == featuresSectionRow || 
                       position == testSectionRow) {
                return 1;
            } else if (position == headerRow || position == featuresHeaderRow) {
                return 2;
            } else if (position == voiceSummarizeRow || position == autoReplyRow) {
                return 3;
            }
            return 4;
        }
    }
    
    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, HeaderCell.class, NotificationsCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));
        
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
        
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));
        
        return themeDescriptions;
    }
}