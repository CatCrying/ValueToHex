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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;

public class MainActivity extends Activity {

    // ── Types ───────────────────────────────────────────────────────
    private static final String TYPE_INT = "INT";
    private static final String TYPE_FLOAT = "Float";
    private static final String TYPE_BOOLEAN = "Boolean";
    private static final String TYPE_INT_RANGE = "INT Range";
    private static final String TYPE_FLOAT_RANGE = "Float Range";
    private static final String[] TYPES = {
            TYPE_INT, TYPE_FLOAT, TYPE_BOOLEAN, TYPE_INT_RANGE, TYPE_FLOAT_RANGE
    };

    // ── Limits ───────────────────────────────────────────────────────
    private static final int MAX_RANGE_ITEMS = 10000;
    private static final int MAX_DISPLAY_LINES = 500;
    private static final long MAX_UINT32 = 0xFFFFFFFFL;

    // ── Fixed ARM words ──────────────────────────────────────────────
    private static final byte[] ARM32_BX_LR = {0x1E, (byte) 0xFF, 0x2F, (byte) 0xE1};
    private static final byte[] ARM64_RET = {(byte) 0xC0, 0x03, 0x5F, (byte) 0xD6};

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
    private LinearLayout root;
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
            type = t;
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

        ScrollView outer = new ScrollView(this);
        outer.setFillViewport(true);
        outer.setVerticalScrollBarEnabled(false);
        outer.setBackgroundColor(cBg);
        outer.addView(root, new ViewGroup.LayoutParams(MATCH, WRAP));
        setContentView(outer);

        buildHeader();
        buildArchCard();
        buildTypeCard();
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

    private void buildArchCard() {
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
            s.setOrientation(LinearLayout.VERTICAL);
            s.setGravity(Gravity.CENTER);
            s.setPadding(dp(8), dp(10), dp(8), dp(10));
            TextView t = text(names[i], 16, cText, true);
            t.setGravity(Gravity.CENTER);
            TextView st = text(subs[i], 11, cMuted, false);
            st.setGravity(Gravity.CENTER);
            s.addView(t);
            s.addView(st);
            s.setOnClickListener(v -> selectArch(idx == 1, true));
            segments[i] = s;
            segTitle[i] = t;
            segSub[i] = st;
            seg.addView(s, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        card.addView(seg, new LinearLayout.LayoutParams(MATCH, WRAP));
        root.addView(card);
    }

    private void buildTypeCard() {
        LinearLayout card = card();
        card.addView(label("VALUE TYPE"));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < TYPES.length; i++) {
            final String t = TYPES[i];
            TextView c = chip(t);
            c.setOnClickListener(v -> selectType(t, true));
            typeChips[i] = c;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.rightMargin = dp(8);
            row.addView(c, lp);
        }
        hs.addView(row);
        card.addView(hs, new LinearLayout.LayoutParams(MATCH, WRAP));
        root.addView(card);
    }

    private void buildInputCard() {
        LinearLayout card = card();
        card.addView(label("VALUE"));

        // Single value
        inputValue = makeInput("e.g. 1000 or 0x3E8", false);
        inputValue.addTextChangedListener(new Watcher(this::refresh));
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
        quickScroll.setHorizontalScrollBarEnabled(false);
        quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickScroll.addView(quickRow);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        qlp.topMargin = dp(10);
        card.addView(quickScroll, qlp);

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
                s = is64
                        ? "Decimal or hex (0x…) · up to 18,446,744,073,709,551,615 · loaded into X0"
                        : "Decimal or hex (0x…) · up to 4,294,967,295 · loaded into R0";
                break;
            case TYPE_FLOAT:
                s = is64
                        ? "Encoded as a 64-bit double (IEEE-754) in X0"
                        : "Encoded as a 32-bit single (IEEE-754) in R0";
                break;
            case TYPE_BOOLEAN:
                s = "Returns 1 (true) or 0 (false)";
                break;
            default:
                s = "Generates a patch for every value from Start to End (max "
                        + MAX_RANGE_ITEMS + " entries)";
                break;
        }
        helperText.setText(s);
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
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            if (cs != null) {
                inputValue.setText(cs.toString().trim());
                inputValue.setSelection(inputValue.getText().length());
            }
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
            if (cleanInput(raw).isEmpty()) {
                showEmpty("Type a value to see the patch bytes.");
                return;
            }
        }
        try {
            StringBuilder sb = new StringBuilder();
            byte[] code = handleSingle(is64, type, raw, sb);
            showSingleResult(code, sb.toString());
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void onGenerate() {
        hideKeyboard();
        StringBuilder sb = new StringBuilder();
        try {
            handleRange(is64, type, sb);
            showRangeResult(sb.toString().trim());
        } catch (Exception e) {
            showError(e.getMessage());
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

        asmText.setText(disassemble(code, is64));
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
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("hex_result", currentCopyText));
        copyBtn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        copyBtn.setText("COPIED ✓");
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> copyBtn.setText("COPY"), 1400);
    }

    private void shareResult() {
        if (currentCopyText.isEmpty()) {
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, currentCopyText);
        startActivity(Intent.createChooser(i, "Share patch"));
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

    // ── Disassembler (only the instructions this app emits) ──────────
    private String disassemble(byte[] code, boolean sixtyFour) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i + 3 < code.length; i += 4) {
            int w = (code[i] & 0xFF) | ((code[i + 1] & 0xFF) << 8)
                    | ((code[i + 2] & 0xFF) << 16) | ((code[i + 3] & 0xFF) << 24);
            String s;
            if (!sixtyFour) {
                int imm = (((w >>> 16) & 0xF) << 12) | (w & 0xFFF);
                if (w == 0xE12FFF1E) {
                    s = "BX LR";
                } else if ((w & 0xFFF00000) == 0xE3000000) {
                    s = "MOVW R0, #0x" + hexUpper(imm);
                } else if ((w & 0xFFF00000) == 0xE3400000) {
                    s = "MOVT R0, #0x" + hexUpper(imm);
                } else {
                    s = ".word 0x" + hexUpper(w);
                }
            } else {
                int imm = (w >>> 5) & 0xFFFF;
                int hw = (w >>> 21) & 3;
                String shift = hw == 0 ? "" : ", LSL #" + (16 * hw);
                if (w == 0xD65F03C0) {
                    s = "RET";
                } else if ((w & 0xFF800000) == 0xD2800000) {
                    s = "MOVZ X0, #0x" + hexUpper(imm) + shift;
                } else if ((w & 0xFF800000) == 0xF2800000) {
                    s = "MOVK X0, #0x" + hexUpper(imm) + shift;
                } else {
                    s = ".word 0x" + hexUpper(w);
                }
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(s);
        }
        return out.toString();
    }

    private String hexUpper(int v) {
        return Integer.toHexString(v).toUpperCase(Locale.US);
    }

    // ── Conversion logic (kept in sync with the web tool) ────────────
    private byte[] handleSingle(boolean is64, String type, String rawInput, StringBuilder sb) throws Exception {
        String raw = cleanInput(rawInput);
        byte[] bytes;

        switch (type) {
            case TYPE_INT:
                bytes = encodeInt(parseIntegerInput(raw), is64);
                break;

            case TYPE_FLOAT:
                bytes = encodeFloat(parseFloatInput(raw), is64);
                break;

            case TYPE_BOOLEAN:
                bytes = encodeInt(parseBool(raw) ? BigInteger.ONE : BigInteger.ZERO, is64);
                break;

            default:
                throw new Exception("Unknown type");
        }

        sb.append(bytesToHex(bytes));
        return bytes;
    }

    private void handleRange(boolean is64, String type, StringBuilder sb) throws Exception {

        String rs = startValue.getText().toString().trim();
        String re = endValue.getText().toString().trim();
        String rst = stepValue.getText().toString().trim();

        if (rs.isEmpty() || re.isEmpty() || rst.isEmpty()) {
            throw new Exception("Please fill all range fields!");
        }

        BigDecimal bdStart;
        BigDecimal bdEnd;
        BigDecimal bdStep;

        try {
            bdStart = new BigDecimal(rs);
            bdEnd = new BigDecimal(re);
            bdStep = new BigDecimal(rst);
        } catch (Exception e) {
            throw new Exception("Invalid number format");
        }

        if (bdStart.compareTo(bdEnd) >= 0) {
            throw new Exception("Start value must be less than end value");
        }

        if (bdStep.compareTo(BigDecimal.ZERO) <= 0) {
            throw new Exception("Step size must be greater than 0");
        }

        BigDecimal count = bdEnd.subtract(bdStart).divideToIntegralValue(bdStep).add(BigDecimal.ONE);

        if (count.compareTo(BigDecimal.valueOf(MAX_RANGE_ITEMS)) > 0) {
            throw new Exception("Range too large (max " + MAX_RANGE_ITEMS + " entries)");
        }

        boolean isFloat = type.equals(TYPE_FLOAT_RANGE);

        for (BigDecimal cur = bdStart; cur.compareTo(bdEnd) <= 0; cur = cur.add(bdStep)) {

            try {

                byte[] bytes;

                if (isFloat) {

                    bytes = encodeFloat(cur.doubleValue(), is64);

                } else {

                    BigInteger v;

                    try {
                        v = cur.toBigIntegerExact();
                    } catch (ArithmeticException ex) {
                        throw new Exception("Not a whole number");
                    }

                    bytes = encodeInt(v, is64);
                }

                sb.append(cur.stripTrailingZeros().toPlainString())
                        .append(" = ")
                        .append(bytesToHex(bytes))
                        .append("\n");

            } catch (Exception e) {

                sb.append(cur.toPlainString()).append(" - ").append(e.getMessage()).append("\n");
            }
        }
    }

    // ── ARM32 INT: MOVW R0,#low16 [; MOVT R0,#high16 if non-zero] ; BX LR ──
    private byte[] arm32Int(int value) {
        long uval = value & 0xFFFFFFFFL;
        int low16 = (int) (uval & 0xFFFF);
        int high16 = (int) ((uval >> 16) & 0xFFFF);
        int movw = 0xE3000000 | ((low16 >> 12) << 16) | (low16 & 0xFFF);
        byte[] result = intToLE(movw);
        if (high16 != 0) {
            int movt = 0xE3400000 | ((high16 >> 12) << 16) | (high16 & 0xFFF);
            result = concat(result, intToLE(movt));
        }
        return concat(result, ARM32_BX_LR);
    }

    // ── ARM32 Float: single-precision bits via MOVW (+MOVT) R0 ; BX LR ──
    private byte[] arm32Float(float fval) {
        return arm32Int(Float.floatToRawIntBits(fval));
    }

    // ── ARM64 INT: MOVZ X0,#val [+ MOVK...] ; RET ───────────────────
    private byte[] arm64Int(long value) {
        byte[] result = new byte[0];
        boolean first = true;
        for (int i = 0; i < 4; i++) {
            int chunk = (int) ((value >> (i * 16)) & 0xFFFF);
            if (first) {
                // MOVZ X0, #chunk, LSL#(i*16)
                int movz = 0xD2800000 | (i << 21) | (chunk << 5);
                result = intToLE(movz);
                first = false;
            } else if (chunk != 0) {
                // MOVK X0, #chunk, LSL#(i*16)
                int movk = 0xF2800000 | (i << 21) | (chunk << 5);
                result = concat(result, intToLE(movk));
            }
        }
        if (first) {
            // value == 0: MOVZ X0, #0
            result = intToLE(0xD2800000);
        }
        return concat(result, ARM64_RET);
    }

    // ── ARM64 Float: double (64-bit IEEE-754) bits via MOVZ/MOVK X0 ; RET ──
    private byte[] arm64Double(double d) {
        return arm64Int(Double.doubleToRawLongBits(d));
    }

    // ── Вспомогательные методы ───────────────────────────────────────

    /** Same input cleaning as the web tool: max 64 chars, strip zero-width chars, trim. */
    private String cleanInput(String s) {
        if (s.length() > 64) {
            s = s.substring(0, 64);
        }
        return s.replaceAll("[\\u200B-\\u200D\\uFEFF]", "").trim();
    }

    /** Integer input: decimal or non-negative hex (0x..), same rules/messages as the web tool. */
    private BigInteger parseIntegerInput(String t) throws Exception {
        if (t.isEmpty()) {
            throw new Exception("Input cannot be empty.");
        }
        if (t.matches("(?i)-?0x[0-9a-f]+")) {
            if (t.startsWith("-")) {
                throw new Exception("Failed to parse integer.");
            }
            return new BigInteger(t.substring(2), 16);
        }
        if (t.matches("-?[0-9]+")) {
            return new BigInteger(t);
        }
        throw new Exception("Invalid integer format. Use decimal (e.g. 10) or hex (e.g. 0x0A).");
    }

    /** Float input parsed like JavaScript Number(): decimal/exponent, 0x/0b/0o forms. */
    private double parseFloatInput(String t) throws Exception {
        if (t.isEmpty()) {
            throw new Exception("Input cannot be empty.");
        }
        double d = Double.NaN;
        if (t.matches("0[xX][0-9a-fA-F]+")) {
            d = new BigInteger(t.substring(2), 16).doubleValue();
        } else if (t.matches("0[bB][01]+")) {
            d = new BigInteger(t.substring(2), 2).doubleValue();
        } else if (t.matches("0[oO][0-7]+")) {
            d = new BigInteger(t.substring(2), 8).doubleValue();
        } else if (t.matches("[+-]?([0-9]+\\.?[0-9]*|\\.[0-9]+)([eE][+-]?[0-9]+)?")) {
            d = Double.parseDouble(t);
        }
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new Exception("Invalid float value. NaN and Infinity are not allowed.");
        }
        return d;
    }

    private byte[] encodeInt(BigInteger v, boolean is64) throws Exception {
        if (!is64) {
            if (v.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0
                    || v.compareTo(BigInteger.valueOf(MAX_UINT32)) > 0) {
                throw new Exception("Value exceeds 32-bit integer range (-2,147,483,648 to 4,294,967,295).");
            }
            return arm32Int(v.intValue());
        }
        BigInteger min = BigInteger.ONE.shiftLeft(63).negate();
        BigInteger max = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
        if (v.compareTo(min) < 0 || v.compareTo(max) > 0) {
            throw new Exception("Value exceeds 64-bit integer range.");
        }
        return arm64Int(v.longValue());
    }

    /** ARM32: IEEE-754 single in R0. ARM64: IEEE-754 double in X0 (same as the web tool). */
    private byte[] encodeFloat(double d, boolean is64) {
        return is64 ? arm64Double(d) : arm32Float((float) d);
    }

    private boolean parseBool(String raw) throws Exception {
        if (raw.equalsIgnoreCase("true") || raw.equals("1")) {
            return true;
        }

        if (raw.equalsIgnoreCase("false") || raw.equals("0")) {
            return false;
        }

        throw new Exception("Invalid boolean value. Use 1/true or 0/false");
    }

    private byte[] intToLE(int v) {
        return new byte[]{(byte) (v), (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }

    private byte[] concat(byte[]... arrays) {
        int len = 0;
        for (byte[] a : arrays) len += a.length;
        byte[] result = new byte[len];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }
}
