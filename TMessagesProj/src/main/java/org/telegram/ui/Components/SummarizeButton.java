package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.StateSet;
import android.view.MotionEvent;

import androidx.core.graphics.ColorUtils;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.GPTApiClient;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import android.app.Activity;

import java.util.HashMap;
import java.util.Map;

import android.view.Gravity;
import android.view.ViewParent;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ImageView;
public class SummarizeButton {

    private final static int[] pressedState = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};
    
    private static final Map<Integer, String> summaryCache = new HashMap<>();

    private int backgroundColor, color, iconColor, rippleColor;
    private float backgroundBack;
    private Paint backgroundPaint, strokePaint;

    private boolean loading;
    private final AnimatedFloat loadingFloat;

    private int iconDrawableAlpha;
    private Drawable iconDrawable;

    private Drawable selectorDrawable;
    private ChatMessageCell parent;
    private SeekBarWaveform seekBar;

    private long start;
    private Rect bounds, pressBounds;

    private boolean isOpen, shouldBeOpen;
    private String voiceTranscription;
    private String summary;
    private boolean summaryOpen = false;

    public SummarizeButton(ChatMessageCell parent, SeekBarWaveform seekBar) {
        start = SystemClock.elapsedRealtime();
        this.parent = parent;
        this.seekBar = seekBar;
        this.bounds = new Rect(0, 0, dp(30), dp(30));
        this.pressBounds = new Rect(this.bounds);
        this.pressBounds.inset(dp(8), dp(8));

        iconDrawable = parent.getContext().getResources().getDrawable(R.drawable.msg_bot).mutate();
        iconDrawable.setBounds(0, 0, dp(20), dp(20));

        loadingFloat = new AnimatedFloat(parent, 250, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    }

    private boolean pressed;
    public boolean onTouch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            boolean consumed = pressed;
            if (pressed && action == MotionEvent.ACTION_UP && pressBounds.contains((int) x, (int) y)) {
                onTap();
            }
            pressed = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && selectorDrawable instanceof RippleDrawable) {
                selectorDrawable.setState(StateSet.NOTHING);
                parent.invalidate();
            }
            return consumed;
        } else if (action == MotionEvent.ACTION_DOWN) {
            if (pressBounds.contains((int) x, (int) y)) {
                pressed = true;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && selectorDrawable instanceof RippleDrawable) {
                    selectorDrawable.setHotspot(x, y);
                    selectorDrawable.setState(pressedState);
                    parent.invalidate();
                }
                return true;
            }
        }
        return false;
    }

    public void onTap() {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("SummarizeButton: onTap called");
        }
        
        if (parent == null) {
            return;
        }
        
        if (TextUtils.isEmpty(SharedConfig.aiApiToken)) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("SummarizeButton: No API token");
            }
            if (parent.getContext() instanceof Activity) {
                AlertDialog.Builder builder = new AlertDialog.Builder(parent.getContext());
                builder.setTitle(LocaleController.getString("AIError", R.string.AIError));
                builder.setMessage(LocaleController.getString("AINoApiToken", R.string.AINoApiToken));
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                builder.show();
            }
            return;
        }

        if (!SharedConfig.aiVoiceSummarize) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("SummarizeButton: Voice summarization disabled");
            }
            if (parent.getContext() instanceof Activity) {
                AlertDialog.Builder builder = new AlertDialog.Builder(parent.getContext());
                builder.setTitle("Voice Summarization Disabled");
                builder.setMessage("Enable voice summarization in AI Settings");
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                builder.show();
            }
            return;
        }

        MessageObject messageObject = parent.getMessageObject();
        if (messageObject == null || messageObject.messageOwner == null) {
            return;
        }

        if (SharedConfig.aiCacheSummaries && messageObject.getId() != 0) {
            String cachedSummary = summaryCache.get(messageObject.getId());
            if (cachedSummary != null) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("SummarizeButton: Using cached summary for message " + messageObject.getId());
                }
                // Show cached summary
                showSummaryDialog(cachedSummary);
                return;
            }
        }

        voiceTranscription = messageObject.messageOwner.voiceTranscription;
        
        if (TextUtils.isEmpty(voiceTranscription)) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("SummarizeButton: No transcription available");
            }
            if (parent.getContext() instanceof Activity) {
                AlertDialog.Builder builder = new AlertDialog.Builder(parent.getContext());
                builder.setTitle("No Transcription");
                builder.setMessage("Please transcribe the voice message first before summarizing");
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                builder.show();
            }
            return;
        }
        
        generateSummary();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && selectorDrawable instanceof RippleDrawable) {
            selectorDrawable.setState(StateSet.NOTHING);
            parent.invalidate();
        }
        pressed = false;
    }

    private void generateSummary() {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("SummarizeButton: generateSummary called");
        }
        if (TextUtils.isEmpty(voiceTranscription)) {
            if (parent != null && parent.getContext() instanceof Activity) {
                AlertDialog.Builder builder = new AlertDialog.Builder(parent.getContext());
                builder.setTitle("No Transcription");
                builder.setMessage("Please transcribe the voice message first before summarizing");
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                builder.show();
            }
            return;
        }
        setLoading(true, true);
        
        String systemPrompt = SharedConfig.aiVoiceSystemPrompt;
        if (TextUtils.isEmpty(systemPrompt)) {
            systemPrompt = "Summarize this voice message concisely";
        }
        
        int maxTokens = SharedConfig.aiVoiceMaxTokens;
        if (maxTokens <= 0) {
            maxTokens = 2048;
        }
        
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("SummarizeButton: Using systemPrompt: " + systemPrompt);
            FileLog.d("SummarizeButton: Using maxTokens: " + maxTokens);
            FileLog.d("SummarizeButton: Transcription: " + voiceTranscription);
        }
        
        GPTApiClient.summarizeVoiceMessageWithSettings(voiceTranscription, systemPrompt, maxTokens, new GPTApiClient.GPTCallback() {
            @Override
            public void onSuccess(String response) {
                summary = response;
                setLoading(false, true);
                
                if (SharedConfig.aiCacheSummaries && parent != null) {
                    MessageObject messageObject = parent.getMessageObject();
                    if (messageObject != null && messageObject.getId() != 0) {
                        summaryCache.put(messageObject.getId(), response);
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("SummarizeButton: Cached summary for message " + messageObject.getId());
                        }
                    }
                }
                
                showSummaryDialog(response);
            }
            
            @Override
            public void onError(String error) {
                setLoading(false, true);
                
                if (parent != null && parent.getContext() instanceof Activity) {
                    AndroidUtilities.runOnUIThread(() -> {
                        AlertDialog.Builder builder = new AlertDialog.Builder(parent.getContext());
                        builder.setTitle(LocaleController.getString("AIError", R.string.AIError));
                        builder.setMessage("Failed to generate summary: " + error);
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                        builder.show();
                    });
                }
            }
        });
    }
    
    private void showSummaryDialog(String summaryText) {
        if (parent == null || parent.getContext() == null || !(parent.getContext() instanceof Activity)) {
            return;
        }
        
        AndroidUtilities.runOnUIThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(parent.getContext());
            builder.setTitle("Voice Message Summary");
            builder.setMessage(summaryText);
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
            
            AlertDialog dialog = builder.create();
            dialog.show();
            
            ViewGroup buttonsLayout = dialog.getButtonsLayout();
            if (buttonsLayout != null) {
                final int itemSize = dp(36);
                final int itemPadding = dp(8);

                LinearLayout iconButtonsLayout = new LinearLayout(parent.getContext());
                iconButtonsLayout.setOrientation(LinearLayout.HORIZONTAL);
                iconButtonsLayout.setGravity(Gravity.CENTER_VERTICAL);

                ImageView copyButton = new ImageView(parent.getContext());
                copyButton.setImageResource(R.drawable.msg_copy);
                copyButton.setScaleType(ImageView.ScaleType.CENTER);
                copyButton.setColorFilter(Theme.getColor(Theme.key_dialogTextBlue2));
                copyButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 1));
                copyButton.setPadding(itemPadding, itemPadding, itemPadding, itemPadding);
                LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(itemSize, itemSize);
                copyParams.setMargins(dp(8), 0, dp(4), 0);
                copyButton.setLayoutParams(copyParams);
                copyButton.setOnClickListener(v -> {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) parent.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Voice Summary", summaryText);
                    clipboard.setPrimaryClip(clip);
                    android.widget.Toast.makeText(parent.getContext(), "Summary copied to clipboard", android.widget.Toast.LENGTH_SHORT).show();
                });
                iconButtonsLayout.addView(copyButton);

                if (SharedConfig.aiCacheSummaries) {
                    ImageView regenerateButton = new ImageView(parent.getContext());
                    regenerateButton.setImageResource(R.drawable.msg_retry);
                    regenerateButton.setScaleType(ImageView.ScaleType.CENTER);
                    regenerateButton.setColorFilter(Theme.getColor(Theme.key_dialogTextBlue2));
                    regenerateButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 1));
                    regenerateButton.setPadding(itemPadding, itemPadding, itemPadding, itemPadding);
                    LinearLayout.LayoutParams regenParams = new LinearLayout.LayoutParams(itemSize, itemSize);
                    regenParams.setMargins(dp(4), 0, 0, 0);
                    regenerateButton.setLayoutParams(regenParams);
                    regenerateButton.setOnClickListener(v -> {
                        dialog.dismiss();
                        MessageObject messageObject = parent.getMessageObject();
                        if (messageObject != null && messageObject.getId() != 0) {
                            summaryCache.remove(messageObject.getId());
                        }
                        if (messageObject != null && messageObject.messageOwner != null) {
                            voiceTranscription = messageObject.messageOwner.voiceTranscription;
                        }
                        generateSummary();
                    });
                    iconButtonsLayout.addView(regenerateButton);
                }

                if (buttonsLayout instanceof FrameLayout) {
                    iconButtonsLayout.setTag(android.app.Dialog.BUTTON_NEUTRAL);
                    buttonsLayout.addView(iconButtonsLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.LEFT));
                } else if (buttonsLayout instanceof LinearLayout) {
                    (buttonsLayout).addView(iconButtonsLayout, 0, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, Gravity.LEFT, 0, 0, 0, 0));
                } else {
                    ViewParent parentView = dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null ? dialog.getButton(AlertDialog.BUTTON_POSITIVE).getParent() : null;
                    if (parentView instanceof ViewGroup) {
                        ((ViewGroup) parentView).addView(iconButtonsLayout, 0);
                    }
                }
            }
        });
    }
    
    private int dp(float value) {
        return AndroidUtilities.dp(value);
    }
    
    public String getSummary() {
        return summaryOpen ? summary : null;
    }

    public void setLoading(boolean loading, boolean animated) {
        if (!animated) {
            this.loading = loading;
            loadingFloat.set(loading ? 1f : 0f, true);
        } else {
            this.loading = loading;
        }
        if (parent != null) {
            parent.invalidate();
        }
    }

    public void setColor(int color, int grayColor, boolean isOut, float bgBack) {
        boolean newColor = this.color != color;
        this.iconColor = this.color = color;
        this.backgroundColor = ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * 0.156f));
        this.backgroundBack = bgBack;
        this.rippleColor = Theme.blendOver(this.backgroundColor, ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * (Theme.isCurrentThemeDark() ? .3f : .2f))));
        if (backgroundPaint == null) {
            backgroundPaint = new Paint();
        }
        backgroundPaint.setColor(this.backgroundColor);
        backgroundPaint.setAlpha((int) (backgroundPaint.getAlpha() * (1f - bgBack)));
        if (newColor || selectorDrawable == null) {
            selectorDrawable = Theme.createSimpleSelectorRoundRectDrawable(dp(8), 0, this.rippleColor);
            selectorDrawable.setCallback(parent);
        }
        if (newColor && iconDrawable != null) {
            iconDrawable.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
            iconDrawable.setAlpha(iconDrawableAlpha = (int) (Color.alpha(color)));
        }
        if (strokePaint == null) {
            strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
        }
        strokePaint.setColor(color);
    }

    private final FastOutSlowInInterpolator interpolator = new FastOutSlowInInterpolator();
    private Path boundsPath;
    private Path loadingPath;
    private int radius, diameter;

    private float a, b;
    public void setBounds(int x, int y, int w, int h, int r) {
        if (w != this.bounds.width() || h != this.bounds.height()) {
            a = (float) (Math.atan((w/2f-r) / (h/2f)) * 180f / Math.PI);
            b = (float) (Math.atan((w/2f) / (h/2f-r)) * 180f / Math.PI);
        }
        this.bounds.set(x, y, x + w, y + h);
        this.radius = Math.min(Math.min(w, h) / 2, r);
        this.diameter = this.radius * 2;
    }
    
    public int width() {
        return this.bounds.width();
    }

    public int height() {
        return this.bounds.height();
    }

    public void draw(Canvas canvas, float alpha) {
        this.pressBounds.set(this.bounds.left - dp(8), this.bounds.top - dp(8), this.bounds.right + dp(8), this.bounds.bottom + dp(8));
        if (boundsPath == null) {
            boundsPath = new Path();
        } else {
            boundsPath.rewind();
        }
        AndroidUtilities.rectTmp.set(this.bounds);
        boundsPath.addRoundRect(AndroidUtilities.rectTmp, this.radius, this.radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(boundsPath);
        if (backgroundPaint != null) {
            int wasAlpha = backgroundPaint.getAlpha();
            backgroundPaint.setAlpha((int) (wasAlpha * alpha));
            canvas.drawRect(this.bounds, backgroundPaint);
            backgroundPaint.setAlpha(wasAlpha);
        }
        if (selectorDrawable != null) {
            selectorDrawable.setBounds(bounds);
            selectorDrawable.draw(canvas);
        }
        canvas.restore();

        float loadingT = loadingFloat.set(loading ? 1f : 0f);
        if (loadingT > 0f) {
            if (strokePaint != null) {
                int wasAlpha = strokePaint.getAlpha();
                strokePaint.setAlpha((int) (wasAlpha * alpha * loadingT));
                strokePaint.setStrokeWidth(dp(1.5f));
                
                float sweepAngle = (SystemClock.elapsedRealtime() - start) % 2000 / 2000f * 360f;
                canvas.drawArc(
                    bounds.left + dp(5), bounds.top + dp(5),
                    bounds.right - dp(5), bounds.bottom - dp(5),
                    sweepAngle, 270, false, strokePaint
                );
                strokePaint.setAlpha(wasAlpha);
            }
        }
        
        if (iconDrawable != null && loadingT < 1f) {
            canvas.save();
            canvas.translate(
                bounds.left + (bounds.width() - iconDrawable.getBounds().width()) / 2f,
                bounds.top + (bounds.height() - iconDrawable.getBounds().height()) / 2f
            );
            iconDrawable.setAlpha((int) (iconDrawableAlpha * alpha * (1f - loadingT)));
            iconDrawable.draw(canvas);
            iconDrawable.setAlpha(iconDrawableAlpha);
            canvas.restore();
        }
    }

    public void reset() {
        summary = null;
        voiceTranscription = null;
        setLoading(false, false);
    }
    
    public static void clearCache() {
        summaryCache.clear();
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("SummarizeButton: Cache cleared");
        }
    }
}