package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class AdvancedSettingsActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private int rowCount;
    private int headerRow;
    private int allowScreenshotsRow;
    private int allowSaveMediaRow;
    private int allowCopyRow;
    private int keepDisappearingRow;
    private int localPremiumRow;
    private int roundVideoDurationRow;
    private int roundVideoCameraRow;
    private int outgoingMessagesBlockDurationRow;
    

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    private void updateRows() {
        rowCount = 0;
        headerRow = rowCount++;
        allowScreenshotsRow = rowCount++;
        allowSaveMediaRow = rowCount++;
        allowCopyRow = rowCount++;
        keepDisappearingRow = rowCount++;
        localPremiumRow = rowCount++;
        roundVideoDurationRow = rowCount++;
        roundVideoCameraRow = rowCount++;
        outgoingMessagesBlockDurationRow = rowCount++;
    }

    private String getRoundVideoCameraValue() {
        int mode = SharedConfig.roundVideoCameraBehavior;
        if (mode == SharedConfig.ROUND_VIDEO_CAMERA_BACK) {
            return LocaleController.getString(R.string.DeveloperRoundVideoCameraBack);
        } else if (mode == SharedConfig.ROUND_VIDEO_CAMERA_ASK) {
            return LocaleController.getString(R.string.DeveloperRoundVideoCameraAsk);
        }
        return LocaleController.getString(R.string.DeveloperRoundVideoCameraFront);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(org.telegram.messenger.LocaleController.getString("DeveloperOptions", org.telegram.messenger.R.string.DeveloperOptions));
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
            if (position == allowScreenshotsRow) {
                SharedConfig.allowScreenshotsEverywhere = !SharedConfig.allowScreenshotsEverywhere;
                SharedConfig.saveConfig();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.allowScreenshotsEverywhere);
                } else if (listAdapter != null) {
                    listAdapter.notifyItemChanged(allowScreenshotsRow);
                }
                if (getParentActivity() != null) {
                    getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
            } else if (position == allowSaveMediaRow) {
                SharedConfig.allowSaveToGalleryEverywhere = !SharedConfig.allowSaveToGalleryEverywhere;
                SharedConfig.saveConfig();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.allowSaveToGalleryEverywhere);
                } else if (listAdapter != null) {
                    listAdapter.notifyItemChanged(allowSaveMediaRow);
                }
            } else if (position == allowCopyRow) {
                SharedConfig.allowCopyEverywhere = !SharedConfig.allowCopyEverywhere;
                SharedConfig.saveConfig();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.allowCopyEverywhere);
                } else if (listAdapter != null) {
                    listAdapter.notifyItemChanged(allowCopyRow);
                }
            } else if (position == keepDisappearingRow) {
                SharedConfig.keepDisappearingMedia = !SharedConfig.keepDisappearingMedia;
                SharedConfig.saveConfig();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.keepDisappearingMedia);
                } else if (listAdapter != null) {
                    listAdapter.notifyItemChanged(keepDisappearingRow);
                }
            } else if (position == localPremiumRow) {
                SharedConfig.localPremium = !SharedConfig.localPremium;
                SharedConfig.saveConfig();
                SharedConfig.applyLocalPremium();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.localPremium);
                } else if (listAdapter != null) {
                    listAdapter.notifyItemChanged(localPremiumRow);
                }
            } else if (position == roundVideoDurationRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString(R.string.DeveloperRoundVideoDuration));

                FrameLayout dialogContainer = new FrameLayout(getParentActivity());
                final EditText editText = new EditText(getParentActivity());
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setText(String.valueOf(SharedConfig.roundVideoMaxDuration));
                editText.setSelection(editText.getText().length());
                dialogContainer.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START, 24, 12, 24, 12));

                builder.setView(dialogContainer);
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
                    int value;
                    try {
                        value = Integer.parseInt(editText.getText().toString());
                    } catch (Exception e) {
                        value = 60;
                    }
                    if (value <= 0) {
                        value = 1;
                    }
                    if (value != SharedConfig.roundVideoMaxDuration) {
                        SharedConfig.roundVideoMaxDuration = value;
                        SharedConfig.saveConfig();
                        if (listAdapter != null) {
                            listAdapter.notifyItemChanged(roundVideoDurationRow);
                        }
                    }
                });
                showDialog(builder.create());
            } else if (position == roundVideoCameraRow) {
                if (getParentActivity() == null) {
                    return;
                }
                CharSequence[] options = new CharSequence[] {
                        LocaleController.getString(R.string.DeveloperRoundVideoCameraFront),
                        LocaleController.getString(R.string.DeveloperRoundVideoCameraBack),
                        LocaleController.getString(R.string.DeveloperRoundVideoCameraAsk)
                };
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString(R.string.DeveloperRoundVideoCamera));
                builder.setItems(options, (dialog, which) -> {
                    if (which != SharedConfig.roundVideoCameraBehavior) {
                        SharedConfig.roundVideoCameraBehavior = which;
                        SharedConfig.saveConfig();
                        if (listAdapter != null) {
                            listAdapter.notifyItemChanged(roundVideoCameraRow);
                        }
                    }
                });
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                showDialog(builder.create());
            } else if (position == outgoingMessagesBlockDurationRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString(R.string.DeveloperOutgoingBlockDuration));

                FrameLayout dialogContainer = new FrameLayout(getParentActivity());
                final EditText editText = new EditText(getParentActivity());
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setText(String.valueOf(SharedConfig.outgoingMessagesBlockDuration));
                editText.setSelection(editText.getText().length());
                dialogContainer.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START, 24, 12, 24, 12));

                builder.setView(dialogContainer);
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
                    int value;
                    try {
                        value = Integer.parseInt(editText.getText().toString());
                    } catch (Exception e) {
                        value = 60;
                    }
                    if (value <= 0) {
                        value = 1;
                    }
                    if (value != SharedConfig.outgoingMessagesBlockDuration) {
                        SharedConfig.outgoingMessagesBlockDuration = value;
                        SharedConfig.saveConfig();
                        if (listAdapter != null) {
                            listAdapter.notifyItemChanged(outgoingMessagesBlockDurationRow);
                        }
                    }
                });
                showDialog(builder.create());
            }
        });

        return fragmentView;
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
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    headerCell.setText("General");
                    break;
                }
                case 1: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    if (position == allowScreenshotsRow) {
                        checkCell.setTextAndCheck(LocaleController.getString(R.string.DeveloperAllowScreenshots), SharedConfig.allowScreenshotsEverywhere, true);
                    } else if (position == allowSaveMediaRow) {
                        checkCell.setTextAndCheck(LocaleController.getString(R.string.DeveloperAllowSaveMedia), SharedConfig.allowSaveToGalleryEverywhere, true);
                    } else if (position == allowCopyRow) {
                        checkCell.setTextAndCheck(LocaleController.getString(R.string.DeveloperAllowCopy), SharedConfig.allowCopyEverywhere, true);
                    } else if (position == keepDisappearingRow) {
                        checkCell.setTextAndCheck(LocaleController.getString(R.string.DeveloperKeepDisappearingMedia), SharedConfig.keepDisappearingMedia, true);
                    } else if (position == localPremiumRow) {
                        checkCell.setTextAndValueAndCheck(LocaleController.getString(R.string.DeveloperLocalPremium), LocaleController.getString(R.string.DeveloperLocalPremiumInfo), SharedConfig.localPremium, false, true);
                    }
                    break;
                }
                case 2: {
                    TextSettingsCell settingsCell = (TextSettingsCell) holder.itemView;
                    if (position == roundVideoDurationRow) {
                        settingsCell.setTextAndValue(LocaleController.getString(R.string.DeveloperRoundVideoDuration), LocaleController.formatPluralString("Seconds", SharedConfig.roundVideoMaxDuration), true);
                    } else if (position == roundVideoCameraRow) {
                        settingsCell.setTextAndValue(LocaleController.getString(R.string.DeveloperRoundVideoCamera), getRoundVideoCameraValue(), false);
                    } else if (position == outgoingMessagesBlockDurationRow) {
                        settingsCell.setTextAndValue(LocaleController.getString(R.string.DeveloperOutgoingBlockDuration), LocaleController.formatPluralString("Minutes", SharedConfig.outgoingMessagesBlockDuration), false);
                    }
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == allowScreenshotsRow || position == allowSaveMediaRow || position == allowCopyRow || position == keepDisappearingRow || position == localPremiumRow || position == roundVideoDurationRow || position == roundVideoCameraRow || position == outgoingMessagesBlockDurationRow;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
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
            if (position == headerRow) {
                return 0;
            } else if (position == allowScreenshotsRow || position == allowSaveMediaRow || position == allowCopyRow || position == keepDisappearingRow || position == localPremiumRow) {
                return 1;
            } else if (position == roundVideoDurationRow || position == roundVideoCameraRow || position == outgoingMessagesBlockDurationRow) {
                return 2;
            }
            return 3;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, TextCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        return themeDescriptions;
    }
}
