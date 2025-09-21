package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SlideChooseView;

import java.util.ArrayList;

public class AIVoiceSettingsActivity extends BaseFragment {
    
    private RecyclerListView listView;
    private ListAdapter listAdapter;
    
    private int rowCount;
    private int headerRow;
    private int systemPromptRow;
    private int languageRow;
    private int modelHeaderRow;
    private int modelRow;
    private int cacheSummariesRow;
    private int whisperFallbackRow;
    private int maxTokensHeaderRow;
    private int maxTokensRow;
    private int infoRow;
    
    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }
    
    private void updateRows() {
        rowCount = 0;
        headerRow = rowCount++;
        systemPromptRow = rowCount++;
        languageRow = rowCount++;
        modelHeaderRow = rowCount++;
        modelRow = rowCount++;
        cacheSummariesRow = rowCount++;
        whisperFallbackRow = rowCount++;
        maxTokensHeaderRow = rowCount++;
        maxTokensRow = rowCount++;
        infoRow = rowCount++;
    }
    
    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Voice Summarization Settings");
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
        
        listView.setOnItemClickListener((view, position) -> {
            if (position == systemPromptRow) {
                showSystemPromptDialog();
            } else if (position == languageRow) {
                showLanguageDialog();
            } else if (position == cacheSummariesRow) {
                SharedConfig.aiCacheSummaries = !SharedConfig.aiCacheSummaries;
                SharedConfig.saveConfig();
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(cacheSummariesRow);
                }
            } else if (position == whisperFallbackRow) {
                SharedConfig.aiTranscribeFallback = !SharedConfig.aiTranscribeFallback;
                SharedConfig.saveConfig();
                if (listAdapter != null) {
                    listAdapter.notifyItemChanged(whisperFallbackRow);
                }
            }
        });
        
        return fragmentView;
    }
    
    private void showSystemPromptDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("System Prompt");
        
        LinearLayout linearLayout = new LinearLayout(getParentActivity());
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        
        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), false));
        editText.setMaxLines(5);
        editText.setLines(3);
        editText.setPadding(0, 0, 0, 0);
        editText.setSingleLine(false);
        editText.setGravity(Gravity.TOP | Gravity.LEFT);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setHint("e.g., 'What should I reply to this?' or 'Summarize this voice message'");
        editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editText.setText(SharedConfig.aiVoiceSystemPrompt);
        linearLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 12, 24, 0));
        
        TextView helpText = new TextView(getParentActivity());
        helpText.setText("Leave empty to use default prompt. Custom prompts will be sent with the voice transcription to GPT.");
        helpText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        helpText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        linearLayout.addView(helpText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 12));
        
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            SharedConfig.aiVoiceSystemPrompt = editText.getText().toString().trim();
            SharedConfig.saveConfig();
            org.telegram.ui.Components.SummarizeButton.clearCache();
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(systemPromptRow);
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
        AndroidUtilities.runOnUIThread(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        }, 100);
    }
    
    private void showLanguageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Output Language");
        
        final String[] languages = new String[] { "Auto-detect", "English", "Ukrainian", "Russian", "Spanish" };
        
        builder.setItems(languages, (dialog, which) -> {
            SharedConfig.aiOutputLanguage = which;
            SharedConfig.saveConfig();
            org.telegram.ui.Components.SummarizeButton.clearCache();
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(languageRow);
            }
        });
        showDialog(builder.create());
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
                    if (position == systemPromptRow) {
                        String value = TextUtils.isEmpty(SharedConfig.aiVoiceSystemPrompt) ? 
                            "Default" : SharedConfig.aiVoiceSystemPrompt;
                        if (value.length() > 30) {
                            value = value.substring(0, 30) + "...";
                        }
                        textCell.setTextAndValue("System Prompt", value, true);
                    } else if (position == languageRow) {
                        String[] languages = new String[] { "Auto-detect", "English", "Ukrainian", "Russian", "Spanish" };
                        String currentLanguage = languages[Math.min(SharedConfig.aiOutputLanguage, languages.length - 1)];
                        textCell.setTextAndValue("Output Language", currentLanguage, true);
                    }
                    break;
                }
                case 1: {
                    NotificationsCheckCell checkCell = (NotificationsCheckCell) holder.itemView;
                    if (position == cacheSummariesRow) {
                        checkCell.setTextAndValueAndCheck("Cache Summaries", "Store summaries to avoid repeated API calls", SharedConfig.aiCacheSummaries, true);
                    } else if (position == whisperFallbackRow) {
                        checkCell.setTextAndValueAndCheck("OpenAI Whisper Transcribe", "Use OpenAI Whisper for voice transcription", SharedConfig.aiTranscribeFallback, false);
                    }
                    break;
                }
                case 2: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == headerRow) {
                        headerCell.setText("Voice Summarization");
                    } else if (position == modelHeaderRow) {
                        headerCell.setText("Model");
                    } else if (position == maxTokensHeaderRow) {
                        headerCell.setText("Max Tokens");
                    }
                    break;
                }
                case 3: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == infoRow) {
                        cell.setText("Configure how AI summarizes voice messages. Custom prompts help get specific responses like suggested replies.");
                        cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 4: {
                    SlideChooseView slideView = (SlideChooseView) holder.itemView;
                    
                    if (position == maxTokensRow) {
                        int[] values = new int[] { 512, 1024, 2048, 4096 };
                        String[] options = new String[values.length];
                        int selectedOption = 2;
                        for (int i = 0; i < values.length; i++) {
                            options[i] = String.valueOf(values[i]);
                            if (SharedConfig.aiVoiceMaxTokens == values[i]) {
                                selectedOption = i;
                            }
                        }
                        slideView.setOptions(selectedOption, options);
                        slideView.setCallback(index -> {
                            SharedConfig.aiVoiceMaxTokens = values[index];
                            SharedConfig.saveConfig();
                            org.telegram.ui.Components.SummarizeButton.clearCache();
                        });
                    } else if (position == modelRow) {
                        String[] options = new String[] { "GPT-3.5", "GPT-4o", "GPT-4 Turbo" };
                        int selected = SharedConfig.aiVoiceModel;
                        if (selected < 0 || selected >= options.length) {
                            selected = Math.max(0, Math.min(1, SharedConfig.aiModel));
                        }
                        slideView.setOptions(selected, options);
                        slideView.setCallback(index -> {
                            SharedConfig.aiVoiceModel = index;
                            SharedConfig.saveConfig();
                            org.telegram.ui.Components.SummarizeButton.clearCache();
                        });
                    }
                    break;
                }
            }
        }
        
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == systemPromptRow || position == languageRow || position == cacheSummariesRow || position == whisperFallbackRow;
        }
        
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new NotificationsCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 4:
                    view = new SlideChooseView(mContext);
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
            if (position == systemPromptRow || position == languageRow) {
                return 0;
            } else if (position == cacheSummariesRow || position == whisperFallbackRow) {
                return 1;
            } else if (position == headerRow || position == maxTokensHeaderRow || position == modelHeaderRow) {
                return 2;
            } else if (position == infoRow) {
                return 3;
            } else if (position == maxTokensRow || position == modelRow) {
                return 4;
            }
            return 5;
        }
    }
    
    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, HeaderCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
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
        
        return themeDescriptions;
    }
}
