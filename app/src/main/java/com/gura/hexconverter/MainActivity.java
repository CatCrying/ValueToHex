package com.gura.hexconverter;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

public class MainActivity extends Activity {

    // ── Types ───────────────────────────────────────────────────────
    private static final String TYPE_INT = PatchEncoder.TYPE_INT;
    private static final String TYPE_FLOAT = PatchEncoder.TYPE_FLOAT;
    private static final String TYPE_BOOLEAN = PatchEncoder.TYPE_BOOLEAN;
    private static final String TYPE_INT_RANGE = PatchEncoder.TYPE_INT_RANGE;
    private static final String TYPE_FLOAT_RANGE = PatchEncoder.TYPE_FLOAT_RANGE;
    private static final String[] TYPES = {
            TYPE_INT, TYPE_FLOAT, TYPE_BOOLEAN, TYPE_INT_RANGE, TYPE_FLOAT_RANGE
    };

    // ── Limits ───────────────────────────────────────────────────────
    private static final int MAX_DISPLAY_LINES = 500;
    /** Binder transactions (clipboard / share intents) fail above ~1 MB. */
    private static final int MAX_SHARE_CHARS = 200000;
    private static final int MAX_COPY_CHARS = 400000;

    // ── Layout constants ─────────────────────────────────────────────
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    // ── Output (terminal) palette: always dark ───────────────────────
    private static final int TERM_BG = 0xFF0B0E14;
    private static final int TERM_TEXT = 0xFFE6EDF3;
    private static final int TERM_MUTED = 0xFF7D8590;
    private static final int TERM_ERROR = 0xFFFF7B72;
    private static final int[] WORD_COLORS = {0xFF7AA2F7, 0xFFBB9AF7};
    private static final int RET_COLOR = 0xFFE0AF68;

    // ── Theme colors (set in initColors) ─────────────────────────────
    private boolean dark;
    private int cBg, cSurface, cSurfaceAlt, cText, cMuted, cPrimary, cOnPrimary, cOutline;

    // ── State ────────────────────────────────────────────────────────
    private boolean is64 = false;
    private String type = TYPE_INT;
    private boolean restoring = false;
    private String currentCopyText = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    // ── Views ────────────────────────────────────────────────────────
    private ScrollView outer;
    private LinearLayout root, outputCard;
    private final LinearLayout[] segments = new LinearLayout[2];
    private final TextView[] segTitle = new TextView[2];
    private final TextView[] segSub = new TextView[2];
    private final TextView[] typeChips = new TextView[TYPES.length];
    private EditText inputValue, startValue, endValue, stepValue;
    private LinearLayout boolRow, rangeBox, quickRow;
    private HorizontalScrollView quickScroll;
    private Switch boolSwitch;
    private TextView boolLabel, helperText, generateBtn;
    private TextView outputText, outputMeta, copyBtn, shareBtn;
    private LinearLayout asmCard;
    private TextView asmText, asmNote;

    // ────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        initColors();
        applySystemBars();
        buildUi();

        restoring = true;
        if (savedInstanceState != null) {
            is64 = savedInstanceState.getBoolean("is64", false);
            String t = savedInstanceState.getString("type", TYPE_INT);
            type = java.util.Arrays.asList(TYPES).contains(t) ? t : TYPE_INT;
            inputValue.setText(savedInstanceState.getString("input", ""));
            startValue.setText(savedInstanceState.getString("start", ""));
            endValue.setText(savedInstanceState.getString("end", ""));
            stepValue.setText(savedInstanceState.getString("step", ""));
            boolSwitch.setChecked(savedInstanceState.getBoolean("bool", true));
        }
        restoring = false;

        selectArch(is64, false);
        selectType(type, false);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("is64", is64);
        out.putString("type", type);
        out.putString("input", inputValue.getText().toString());
        out.putString("start", startValue.getText().toString());
        out.putString("end", endValue.getText().toString());
        out.putString("step", stepValue.getText().toString());
        out.putBoolean("bool", boolSwitch.isChecked());
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ── Theme ────────────────────────────────────────────────────────
    private void initColors() {
        if (dark) {
            cBg = 0xFF0E1015;
            cSurface = 0xFF171A22;
            cSurfaceAlt = 0xFF222736;
            cText = 0xFFECEEF5;
            cMuted = 0xFF9AA1B5;
            cPrimary = 0xFF8C8CFF;
            cOnPrimary = 0xFF10121A;
            cOutline = 0xFF2C3243;
        } else {
            cBg = 0xFFF6F7FB;
            cSurface = 0xFFFFFFFF;
            cSurfaceAlt = 0xFFEEF0F7;
            cText = 0xFF14161C;
            cMuted = 0xFF5B6070;
            cPrimary = 0xFF5B5BF0;
            cOnPrimary = 0xFFFFFFFF;
            cOutline = 0xFFDADDE8;
        }
    }

    private void applySystemBars() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(cBg);
            w.setNavigationBarColor(cBg);
        }
        View decor = w.getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (!dark) {
            if (Build.VERSION.SDK_INT >= 23) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                flags |= 0x00000010; // SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
        decor.setSystemUiVisibility(flags);
    }

    // ── UI construction ──────────────────────────────────────────────
    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));
        LayoutTransition lt = new LayoutTransition();
        lt.enableTransitionType(LayoutTransition.CHANGING);
        root.setLayoutTransition(lt);

        outer = new ScrollView(this);
        outer.setFillViewport(true);
        outer.setVerticalScrollBarEnabled(false);
        outer.setBackgroundColor(cBg);
        outer.addView(root, new ViewGroup.LayoutParams(MATCH, WRAP));
        setContentView(outer);

        buildHeader();
        buildSettingsCard();
        buildInputCard();
        buildOutputCard();
        buildAsmCard();

        TextView footer = text("GPL-3.0 · based on FlutterGenerator/ValueToHex", 11, cMuted, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(MATCH, WRAP);
        flp.topMargin = dp(20);
        root.addView(footer, flp);
    }

    private void buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Value To Hex", 28, cText, true));
        TextView sub = text("ARM32 / ARM64 patch generator", 13, cMuted, false);
        titles.addView(sub);
        header.addView(titles, new LinearLayout.LayoutParams(0, WRAP, 1f));

        TextView pill = text("@GuraMods", 12, cPrimary, true);
        pill.setPadding(dp(12), dp(8), dp(12), dp(8));
        pill.setBackground(pressable(cSurfaceAlt, 20, cOutline, 1, 0x22888888));
        pill.setOnClickListener(v -> openTelegram());
        pill.setContentDescription("Open Telegram channel");
        header.addView(pill, new LinearLayout.LayoutParams(WRAP, WRAP));

        root.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private void buildSettingsCard() {
        LinearLayout card = card();
        card.addView(label("ARCHITECTURE"));

        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        seg.setBackground(shape(cSurfaceAlt, 16, 0, 0));

        String[] names = {"ARM32", "ARM64"};
        String[] subs = {"ARMv7", "AArch64"};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            LinearLayout s = new LinearLayout(this);
            s.setOrientation(LinearLayout.HORIZONTAL);
            s.setGravity(Gravity.CENTER);
            s.setPadding(dp(8), dp(12), dp(8), dp(12));
            TextView tt = text(names[i], 16, cText, true);
            TextView st = text(subs[i], 12, cMuted, false);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(WRAP, WRAP);
            slp.leftMargin = dp(8);
            s.addView(tt);
            s.addView(st, slp);
            s.setOnClickListener(v -> selectArch(idx == 1, true));
            segments[i] = s;
            segTitle[i] = tt;
            segSub[i] = st;
            seg.addView(s, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        card.addView(seg, new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView typeLabel = label("VALUE TYPE");
        typeLabel.setPadding(0, dp(16), 0, dp(10));
        card.addView(typeLabel);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        bleed(hs);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < TYPES.length; i++) {
            final String ty = TYPES[i];
            TextView c = chip(ty);
            c.setOnClickListener(v -> selectType(ty, true));
            typeChips[i] = c;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.rightMargin = dp(8);
            row.addView(c, lp);
        }
        hs.addView(row);
        card.addView(hs, bleedLp(0));
        root.addView(card);
    }

    /** Lets a horizontal chip row scroll all the way to the card edges. */
    private void bleed(HorizontalScrollView hs) {
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setClipToPadding(false);
        hs.setPadding(dp(16), 0, dp(16), 0);
        hs.setHorizontalFadingEdgeEnabled(true);
        hs.setFadingEdgeLength(dp(24));
    }

    private LinearLayout.LayoutParams bleedLp(int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.leftMargin = -dp(16);
        lp.rightMargin = -dp(16);
        lp.topMargin = dp(topMarginDp);
        return lp;
    }


    private void buildInputCard() {
        LinearLayout card = card();
        card.addView(label("VALUE"));

        // Single value
        inputValue = makeInput("e.g. 1000 or 0x3E8", false);
        inputValue.addTextChangedListener(new Watcher(() -> {
            refresh();
            ensureOutputVisible();
        }));
        card.addView(inputValue, new LinearLayout.LayoutParams(MATCH, WRAP));

        // Boolean
        boolRow = new LinearLayout(this);
        boolRow.setOrientation(LinearLayout.HORIZONTAL);
        boolRow.setGravity(Gravity.CENTER_VERTICAL);
        boolRow.setPadding(dp(14), dp(10), dp(14), dp(10));
        boolRow.setBackground(shape(cSurfaceAlt, 14, cOutline, 1));
        boolLabel = text("True (returns 1)", 16, cText, true);
        boolRow.addView(boolLabel, new LinearLayout.LayoutParams(0, WRAP, 1f));
        boolSwitch = new Switch(this);
        boolSwitch.setChecked(true);
        boolSwitch.setOnCheckedChangeListener((b, checked) -> {
            boolLabel.setText(checked ? "True (returns 1)" : "False (returns 0)");
            if (!restoring) {
                b.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                refresh();
            }
        });
        boolRow.addView(boolSwitch, new LinearLayout.LayoutParams(WRAP, WRAP));
        card.addView(boolRow, new LinearLayout.LayoutParams(MATCH, WRAP));

        // Range
        rangeBox = new LinearLayout(this);
        rangeBox.setOrientation(LinearLayout.VERTICAL);
        startValue = makeInput("Start", true);
        endValue = makeInput("End", true);
        stepValue = makeInput("Step", true);
        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, WRAP, 1f);
        a.rightMargin = dp(8);
        r1.addView(startValue, a);
        r1.addView(endValue, new LinearLayout.LayoutParams(0, WRAP, 1f));
        rangeBox.addView(r1, new LinearLayout.LayoutParams(MATCH, WRAP));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(MATCH, WRAP);
        sl.topMargin = dp(8);
        rangeBox.addView(stepValue, sl);
        card.addView(rangeBox, new LinearLayout.LayoutParams(MATCH, WRAP));

        // Helper + quick insert
        helperText = text("", 12, cMuted, false);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        hlp.topMargin = dp(10);
        card.addView(helperText, hlp);

        quickScroll = new HorizontalScrollView(this);
        bleed(quickScroll);
        quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickScroll.addView(quickRow);
        card.addView(quickScroll, bleedLp(10));

        // Generate (range only)
        generateBtn = text("GENERATE", 15, cOnPrimary, true);
        generateBtn.setGravity(Gravity.CENTER);
        generateBtn.setPadding(dp(16), dp(14), dp(16), dp(14));
        generateBtn.setBackground(pressable(cPrimary, 16, 0, 0, 0x33FFFFFF));
        generateBtn.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            onGenerate();
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(MATCH, WRAP);
        glp.topMargin = dp(14);
        card.addView(generateBtn, glp);

        root.addView(card);
    }

    private void buildOutputCard() {
        LinearLayout out = new LinearLayout(this);
        out.setOrientation(LinearLayout.VERTICAL);
        out.setBackground(shape(TERM_BG, 20, 0x22FFFFFF, 1));
        out.setPadding(dp(16), dp(14), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(12);
        out.setLayoutParams(lp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("OUTPUT HEX", 11, TERM_MUTED, true);
        if (Build.VERSION.SDK_INT >= 21) {
            title.setLetterSpacing(0.1f);
        }
        top.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));

        copyBtn = actionPill("COPY");
        copyBtn.setOnClickListener(v -> copyToClipboard());
        shareBtn = actionPill("SHARE");
        shareBtn.setOnClickListener(v -> shareResult());
        top.addView(copyBtn, new LinearLayout.LayoutParams(WRAP, WRAP));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(WRAP, WRAP);
        slp.leftMargin = dp(8);
        top.addView(shareBtn, slp);
        out.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        outputText = new TextView(this);
        outputText.setTypeface(Typeface.MONOSPACE);
        outputText.setTextSize(17f);
        outputText.setTextColor(TERM_TEXT);
        outputText.setLineSpacing(dp(4), 1f);
        outputText.setTextIsSelectable(true);
        outputText.setMinHeight(dp(56));
        outputText.setPadding(0, dp(14), 0, dp(6));
        out.addView(outputText, new LinearLayout.LayoutParams(MATCH, WRAP));

        outputMeta = text("", 12, TERM_MUTED, false);
        out.addView(outputMeta, new LinearLayout.LayoutParams(MATCH, WRAP));

        outputCard = out;
        root.addView(out);
    }

    private void buildAsmCard() {
        asmCard = card();
        asmCard.addView(label("ASSEMBLY"));
        asmText = new TextView(this);
        asmText.setTypeface(Typeface.MONOSPACE);
        asmText.setTextSize(15f);
        asmText.setTextColor(cText);
        asmText.setLineSpacing(dp(3), 1f);
        asmText.setTextIsSelectable(true);
        asmCard.addView(asmText, new LinearLayout.LayoutParams(MATCH, WRAP));
        asmNote = text("", 12, cMuted, false);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        nlp.topMargin = dp(10);
        asmCard.addView(asmNote, nlp);
        root.addView(asmCard);
    }

    // ── Selection logic ──────────────────────────────────────────────
    private void selectArch(boolean sixtyFour, boolean fromUser) {
        is64 = sixtyFour;
        for (int i = 0; i < 2; i++) {
            boolean sel = (i == 1) == is64;
            segments[i].setBackground(sel
                    ? pressable(cPrimary, 12, 0, 0, 0x33FFFFFF)
                    : pressable(Color.TRANSPARENT, 12, 0, 0, 0x22888888));
            segTitle[i].setTextColor(sel ? cOnPrimary : cText);
            segSub[i].setTextColor(sel ? ((cOnPrimary & 0x00FFFFFF) | 0xB3000000) : cMuted);
        }
        if (fromUser) {
            segments[is64 ? 1 : 0].performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        updateHelper();
        refresh();
    }

    private void selectType(String t, boolean fromUser) {
        type = t;
        for (int i = 0; i < TYPES.length; i++) {
            styleChip(typeChips[i], TYPES[i].equals(t));
        }
        if (fromUser) {
            View c = typeChips[java.util.Arrays.asList(TYPES).indexOf(t)];
            c.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }

        boolean range = isRange(t);
        boolean bool = t.equals(TYPE_BOOLEAN);
        inputValue.setVisibility(range || bool ? View.GONE : View.VISIBLE);
        boolRow.setVisibility(bool ? View.VISIBLE : View.GONE);
        rangeBox.setVisibility(range ? View.VISIBLE : View.GONE);
        generateBtn.setVisibility(range ? View.VISIBLE : View.GONE);
        quickScroll.setVisibility(range || bool ? View.GONE : View.VISIBLE);

        if (t.equals(TYPE_INT)) {
            inputValue.setHint("e.g. 1000 or 0x3E8");
        } else if (t.equals(TYPE_FLOAT)) {
            inputValue.setHint("e.g. 100.0 or 1e3");
        }
        rebuildQuickRow();
        updateHelper();
        refresh();
    }

    private boolean isRange(String t) {
        return t.equals(TYPE_INT_RANGE) || t.equals(TYPE_FLOAT_RANGE);
    }

    private void updateHelper() {
        String s;
        switch (type) {
            case TYPE_INT:
                s = is64 ? "Decimal or 0x hex · loaded into X0" : "Decimal or 0x hex · loaded into R0";
                break;
            case TYPE_FLOAT:
                s = is64 ? "64-bit double · loaded into X0" : "32-bit single · loaded into R0";
                break;
            case TYPE_BOOLEAN:
                s = "Returns 1 (true) or 0 (false)";
                break;
            default:
                s = "Start to End · max " + PatchEncoder.MAX_RANGE_ITEMS + " entries";
                break;
        }
        helperText.setText(s);
    }

    /** Keeps the result card on screen while the keyboard is open. */
    private void ensureOutputVisible() {
        if (outer == null || outputCard == null || isRange(type)) {
            return;
        }
        outer.post(() -> {
            int bottom = outputCard.getBottom() + dp(12);
            int visibleBottom = outer.getScrollY() + outer.getHeight();
            if (bottom > visibleBottom) {
                outer.smoothScrollTo(0, bottom - outer.getHeight());
            }
        });
    }

    private void rebuildQuickRow() {
        quickRow.removeAllViews();
        if (type.equals(TYPE_INT)) {
            addQuick("0x", () -> insertText("0x"));
            addQuick("-", () -> insertText("-"));
        } else if (type.equals(TYPE_FLOAT)) {
            addQuick("-", () -> insertText("-"));
            addQuick(".", () -> insertText("."));
            addQuick("e", () -> insertText("e"));
        }
        if (!type.equals(TYPE_BOOLEAN) && !isRange(type)) {
            addQuick("Paste", this::pasteFromClipboard);
            addQuick("Clear", () -> inputValue.setText(""));
        }
    }

    private void addQuick(String label, Runnable action) {
        TextView c = chip(label);
        c.setTextSize(13f);
        c.setPadding(dp(14), dp(7), dp(14), dp(7));
        styleChip(c, false);
        c.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.rightMargin = dp(8);
        quickRow.addView(c, lp);
    }

    private void insertText(String s) {
        int a = Math.max(inputValue.getSelectionStart(), 0);
        int b = Math.max(inputValue.getSelectionEnd(), 0);
        inputValue.getText().replace(Math.min(a, b), Math.max(a, b), s);
        inputValue.requestFocus();
    }

    private void pasteFromClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) {
                return;
            }
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return;
            }
            CharSequence cs = clip.getItemAt(0).coerceToText(this);
            if (cs != null) {
                inputValue.setText(cs.toString().trim());
                inputValue.setSelection(inputValue.getText().length());
            }
        } catch (RuntimeException e) {
            // Clipboard unavailable or access denied: nothing to paste.
        }
    }

    // ── Conversion + rendering ───────────────────────────────────────
    private void refresh() {
        if (restoring || outputText == null) {
            return;
        }
        if (isRange(type)) {
            showEmpty("Set Start, End and Step, then press GENERATE.");
            return;
        }
        String raw;
        if (type.equals(TYPE_BOOLEAN)) {
            raw = boolSwitch.isChecked() ? "true" : "false";
        } else {
            raw = inputValue.getText().toString();
            if (PatchEncoder.cleanInput(raw).isEmpty()) {
                showEmpty("Type a value to see the patch bytes.");
                return;
            }
        }
        try {
            byte[] code = PatchEncoder.encodeSingle(is64, type, raw);
            showSingleResult(code, PatchEncoder.bytesToHex(code));
        } catch (PatchEncoder.PatchException e) {
            showError(e.getMessage());
        } catch (RuntimeException e) {
            showError("Unexpected error: " + e.getClass().getSimpleName());
        }
    }

    private void onGenerate() {
        hideKeyboard();
        try {
            String text = PatchEncoder.generateRange(is64, type,
                    startValue.getText().toString(),
                    endValue.getText().toString(),
                    stepValue.getText().toString()).trim();
            showRangeResult(text);
        } catch (PatchEncoder.PatchException e) {
            showError(e.getMessage());
        } catch (RuntimeException | OutOfMemoryError e) {
            showError("Unexpected error: " + e.getClass().getSimpleName());
        }
    }

    private void showEmpty(String hint) {
        outputText.setTextSize(14f);
        outputText.setTextColor(TERM_MUTED);
        outputText.setText(hint);
        outputMeta.setText("");
        currentCopyText = "";
        setActionsEnabled(false);
        asmCard.setVisibility(View.GONE);
    }

    private void showError(String msg) {
        outputText.setTextSize(14f);
        outputText.setTextColor(TERM_ERROR);
        outputText.setText(msg == null ? "Error" : msg);
        outputMeta.setText("");
        currentCopyText = "";
        setActionsEnabled(false);
        asmCard.setVisibility(View.GONE);
    }

    private void showSingleResult(byte[] code, String hex) {
        SpannableString sp = new SpannableString(hex);
        int words = code.length / 4;
        for (int i = 0; i < words; i++) {
            int s = i * 12;
            int e = Math.min(s + 11, hex.length());
            int color = (i == words - 1) ? RET_COLOR : WORD_COLORS[i % 2];
            sp.setSpan(new ForegroundColorSpan(color), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        outputText.setTextSize(17f);
        outputText.setTextColor(TERM_TEXT);
        outputText.setText(sp);
        outputMeta.setText(code.length + " bytes · " + words + " instructions");
        currentCopyText = hex;
        setActionsEnabled(true);

        asmText.setText(PatchEncoder.disassemble(code, is64));
        asmNote.setText(noteFor());
        asmCard.setVisibility(View.VISIBLE);
    }

    private void showRangeResult(String text) {
        String[] lines = text.isEmpty() ? new String[0] : text.split("\n");
        String shown = text;
        if (lines.length > MAX_DISPLAY_LINES) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < MAX_DISPLAY_LINES; i++) {
                sb.append(lines[i]).append('\n');
            }
            sb.append("… +").append(lines.length - MAX_DISPLAY_LINES)
                    .append(" more lines (use COPY or SHARE for the full list)");
            shown = sb.toString();
        }
        outputText.setTextColor(TERM_TEXT);
        outputText.setTextSize(14f);
        outputText.setText(shown);
        outputMeta.setText(lines.length + " values");
        currentCopyText = text;
        setActionsEnabled(!text.isEmpty());
        asmCard.setVisibility(View.GONE);
    }

    private String noteFor() {
        switch (type) {
            case TYPE_FLOAT:
                return is64
                        ? "Float is encoded as a 64-bit double and loaded into X0."
                        : "Float is encoded as a 32-bit single and loaded into R0.";
            case TYPE_BOOLEAN:
                return boolSwitch.isChecked() ? "Returns true (1)." : "Returns false (0).";
            default:
                return is64 ? "Value is loaded into X0, then RET." : "Value is loaded into R0, then BX LR.";
        }
    }

    private void setActionsEnabled(boolean on) {
        copyBtn.setEnabled(on);
        shareBtn.setEnabled(on);
        copyBtn.setAlpha(on ? 1f : 0.35f);
        shareBtn.setAlpha(on ? 1f : 0.35f);
    }

    // ── Actions ──────────────────────────────────────────────────────
    private void copyToClipboard() {
        if (currentCopyText.isEmpty()) {
            return;
        }
        if (currentCopyText.length() > MAX_COPY_CHARS) {
            Toast.makeText(this, "Result is too large to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null) {
                throw new IllegalStateException("Clipboard unavailable");
            }
            cm.setPrimaryClip(ClipData.newPlainText("hex_result", currentCopyText));
        } catch (RuntimeException e) {
            Toast.makeText(this, "Could not copy to clipboard", Toast.LENGTH_SHORT).show();
            return;
        }
        copyBtn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        copyBtn.setText("COPIED ✓");
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> copyBtn.setText("COPY"), 1400);
    }

    private void shareResult() {
        if (currentCopyText.isEmpty()) {
            return;
        }
        if (currentCopyText.length() > MAX_SHARE_CHARS) {
            Toast.makeText(this, "Too large to share. Use COPY instead", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, currentCopyText);
            startActivity(Intent.createChooser(i, "Share patch"));
        } catch (RuntimeException e) {
            Toast.makeText(this, "Could not share the result", Toast.LENGTH_SHORT).show();
        }
    }

    private void openTelegram() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/GuraMods")));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to open the link", Toast.LENGTH_SHORT).show();
        }
    }

    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
            }
            f.clearFocus();
        }
    }

    // ── View helpers ─────────────────────────────────────────────────
    private static class Watcher implements TextWatcher {
        private final Runnable action;

        Watcher(Runnable action) {
            this.action = action;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

        @Override
        public void onTextChanged(CharSequence s, int a, int b, int c) {}

        @Override
        public void afterTextChanged(Editable e) {
            action.run();
        }
    }

    private GradientDrawable shape(int fill, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            g.setStroke(dp(strokeDp), strokeColor);
        }
        return g;
    }

    private Drawable pressable(int fill, int radiusDp, int strokeColor, int strokeDp, int rippleColor) {
        GradientDrawable base = shape(fill, radiusDp, strokeColor, strokeDp);
        if (Build.VERSION.SDK_INT >= 21) {
            return new RippleDrawable(ColorStateList.valueOf(rippleColor), base,
                    shape(0xFFFFFFFF, radiusDp, 0, 0));
        }
        return base;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(shape(cSurface, 20, cOutline, 1));
        c.setPadding(dp(16), dp(14), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s, 11, cMuted, true);
        if (Build.VERSION.SDK_INT >= 21) {
            t.setLetterSpacing(0.1f);
        }
        t.setPadding(0, 0, 0, dp(10));
        return t;
    }

    private TextView chip(String s) {
        TextView c = text(s, 14, cText, true);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(16), dp(10), dp(16), dp(10));
        styleChip(c, false);
        return c;
    }

    private void styleChip(TextView c, boolean selected) {
        int l = c.getPaddingLeft(), t = c.getPaddingTop(), r = c.getPaddingRight(), b = c.getPaddingBottom();
        if (selected) {
            c.setBackground(pressable(cPrimary, 20, 0, 0, 0x33FFFFFF));
            c.setTextColor(cOnPrimary);
        } else {
            c.setBackground(pressable(cSurfaceAlt, 20, cOutline, 1, 0x22888888));
            c.setTextColor(cText);
        }
        c.setPadding(l, t, r, b);
    }

    private TextView actionPill(String s) {
        TextView t = text(s, 12, TERM_TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(7), dp(12), dp(7));
        t.setBackground(pressable(0x1FFFFFFF, 12, 0, 0, 0x33FFFFFF));
        return t;
    }

    private EditText makeInput(String hint, boolean numeric) {
        EditText et = new EditText(this);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, shape(cSurface, 14, cPrimary, 2));
        sl.addState(new int[]{}, shape(cSurfaceAlt, 14, cOutline, 1));
        et.setBackground(sl);
        et.setPadding(dp(14), dp(14), dp(14), dp(14));
        et.setHint(hint);
        et.setHintTextColor((cMuted & 0x00FFFFFF) | 0x99000000);
        et.setTextColor(cText);
        et.setTextSize(18f);
        et.setTypeface(Typeface.MONOSPACE);
        et.setSingleLine(true);
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(PatchEncoder.MAX_INPUT_LENGTH)});
        et.setImeOptions(EditorInfo.IME_ACTION_DONE);
        if (numeric) {
            et.setInputType(InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_FLAG_SIGNED
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        } else {
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        return et;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
