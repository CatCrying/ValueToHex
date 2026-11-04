package com.gura.hexconverter;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;

import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.math.BigDecimal;

public class MainActivity extends Activity {

    // ── UI ──────────────────────────────────────────────────────────
    private RadioGroup archGroup;
    private int arm32Id, arm64Id;
    private Spinner typeSpinner;
    private LinearLayout singleInputLayout;
    private LinearLayout rangeInputLayout;
    private EditText inputValue;
    private EditText startValue;
    private EditText endValue;
    private EditText stepValue;
    private TextView resultText;
    private Button copyButton;

    // ── Типы (должны совпадать с arrays.xml) ────────────────────────
    private static final String TYPE_INT = "INT";
    private static final String TYPE_FLOAT = "Float";
    private static final String TYPE_BOOLEAN = "Boolean";
    private static final String TYPE_INT_RANGE = "INT Range";
    private static final String TYPE_FLOAT_RANGE = "Float Range";

    // ── Limits ───────────────────────────────────────────────────────
    private static final int MAX_RANGE_ITEMS = 10000;
    private static final long MAX_UINT32 = 0xFFFFFFFFL;

    // ── ARM фиксированные слова ──────────────────────────────────────
    // ARM32 BX LR
    private static final byte[] ARM32_BX_LR = {0x1E, (byte) 0xFF, 0x2F, (byte) 0xE1};
    // ARM64 RET
    private static final byte[] ARM64_RET = {(byte) 0xC0, 0x03, 0x5F, (byte) 0xD6};

    // ────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(Color.WHITE);

        ScrollView outer = new ScrollView(this);
        outer.setFillViewport(true);
        outer.setBackgroundColor(Color.WHITE);
        outer.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(outer);

        // Заголовок Telegram
        TextView credit = new TextView(this);
        credit.setText("Telegram: @GuraMods");
        credit.setTextSize(14f);
        credit.setTextColor(Color.BLACK);
        credit.setBackground(makeRoundedBg(Color.parseColor("#E0E0E0")));
        credit.setTypeface(null, Typeface.BOLD);
        credit.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(credit);
        addSpace(root, 8);

        // Группа архитектуры
        LinearLayout archBox = new LinearLayout(this);
        archBox.setOrientation(LinearLayout.VERTICAL);
        archBox.setBackground(makeRoundedBg(Color.parseColor("#E0E0E0")));
        archBox.setPadding(dp(8), dp(4), dp(8), dp(4));

        archGroup = new RadioGroup(this);
        archGroup.setOrientation(LinearLayout.VERTICAL);

        RadioButton rb32 = new RadioButton(this);
        rb32.setText("ARM32");
        arm32Id = View.generateViewId();
        rb32.setId(arm32Id);

        RadioButton rb64 = new RadioButton(this);
        rb64.setText("ARM64");
        arm64Id = View.generateViewId();
        rb64.setId(arm64Id);

        archGroup.addView(rb32);
        archGroup.addView(rb64);
        archGroup.check(arm32Id);
        archBox.addView(archGroup);
        root.addView(archBox);
        addSpace(root, 8);

        // Спиннер типа
        LinearLayout spinnerBox = new LinearLayout(this);
        spinnerBox.setBackground(makeRoundedBg(Color.parseColor("#E0E0E0")));
        spinnerBox.setPadding(dp(8), dp(4), dp(8), dp(4));

        typeSpinner = new Spinner(this);

        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        typeSpinner.setLayoutParams(spinnerParams);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.value_types, android.R.layout.simple_spinner_item);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        typeSpinner.setAdapter(adapter);

        spinnerBox.addView(typeSpinner);
        root.addView(spinnerBox);
        addSpace(root, 8);

        // Одиночный ввод
        singleInputLayout = new LinearLayout(this);
        singleInputLayout.setOrientation(LinearLayout.VERTICAL);
        singleInputLayout.setBackground(makeRoundedBg(Color.parseColor("#E0E0E0")));
        singleInputLayout.setPadding(dp(8), dp(4), dp(8), dp(4));
        inputValue = makeEditText("Enter value");
        singleInputLayout.addView(inputValue);
        root.addView(singleInputLayout);

        // Диапазон
        rangeInputLayout = new LinearLayout(this);
        rangeInputLayout.setOrientation(LinearLayout.VERTICAL);
        rangeInputLayout.setVisibility(View.GONE);
        rangeInputLayout.setBackground(makeRoundedBg(Color.parseColor("#E0E0E0")));
        rangeInputLayout.setPadding(dp(8), dp(4), dp(8), dp(4));
        startValue = makeEditText("Start value");
        endValue = makeEditText("End value");
        stepValue = makeEditText("Step size");
        rangeInputLayout.addView(startValue);
        rangeInputLayout.addView(endValue);
        rangeInputLayout.addView(stepValue);
        root.addView(rangeInputLayout);
        addSpace(root, 8);

        // Кнопка Convert
        LinearLayout btnBox = new LinearLayout(this);
        btnBox.setBackground(makeRoundedBg(Color.parseColor("#E0E0E0")));
        btnBox.setPadding(0, 0, 0, 0);
        Button convertBtn = new Button(this);
        convertBtn.setText("CONVERT");
        convertBtn.setBackground(null);
        convertBtn.setTextColor(Color.BLACK);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        convertBtn.setLayoutParams(btnLp);
        convertBtn.setOnClickListener(v -> convert());
        btnBox.addView(convertBtn);
        root.addView(btnBox);
        addSpace(root, 8);

        // Результат
        LinearLayout resultBox = new LinearLayout(this);
        resultBox.setBackground(makeRoundedBg(Color.parseColor("#E0E0E0")));
        resultBox.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams resultBoxLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        resultBox.setLayoutParams(resultBoxLp);
        resultBox.setMinimumHeight(dp(160));

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(
                new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        resultText = new TextView(this);
        resultText.setTextSize(14f);
        resultText.setTextColor(Color.BLACK);
        scroll.addView(resultText);
        resultBox.addView(scroll);
        root.addView(resultBox);
        addSpace(root, 8);

        // Кнопка Copy
        LinearLayout copyBox = new LinearLayout(this);
        copyBox.setBackground(makeRoundedBg(Color.parseColor("#E0E0E0")));
        copyButton = new Button(this);
        copyButton.setText("COPY");
        copyButton.setBackground(null);
        copyButton.setTextColor(Color.BLACK);
        copyButton.setLayoutParams(btnLp);
        copyButton.setOnClickListener(v -> copyToClipboard());
        copyBox.addView(copyButton);
        root.addView(copyBox);

        // Слушатель спиннера
        typeSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        String t = p.getItemAtPosition(pos).toString();
                        boolean range = t.equals(TYPE_INT_RANGE) || t.equals(TYPE_FLOAT_RANGE);
                        singleInputLayout.setVisibility(range ? View.GONE : View.VISIBLE);
                        rangeInputLayout.setVisibility(range ? View.VISIBLE : View.GONE);
                        // Boolean needs text keyboard (true/false), others need numeric
                        if (t.equals(TYPE_BOOLEAN)) {
                            inputValue.setInputType(InputType.TYPE_CLASS_TEXT);
                        } else {
                            inputValue.setInputType(
                                    InputType.TYPE_CLASS_NUMBER
                                            | InputType.TYPE_NUMBER_FLAG_SIGNED
                                            | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> p) {}
                });
    }

    // ── Конвертация ──────────────────────────────────────────────────
    private void convert() {
        boolean is64 = archGroup.getCheckedRadioButtonId() == arm64Id;
        String type = typeSpinner.getSelectedItem().toString();
        StringBuilder sb = new StringBuilder();

        try {
            boolean isRange = type.equals(TYPE_INT_RANGE) || type.equals(TYPE_FLOAT_RANGE);

            if (isRange) {
                handleRange(is64, type, sb);
            } else {
                handleSingle(is64, type, sb);
            }

            resultText.setText(sb.toString().trim());
            copyButton.setVisibility(View.VISIBLE);

        } catch (Exception e) {
            resultText.setText("Error: " + e.getMessage());
            copyButton.setVisibility(View.GONE);
        }
    }

    private void handleSingle(boolean is64, String type, StringBuilder sb) throws Exception {
        String raw = inputValue.getText().toString().trim();

        if (raw.isEmpty()) {
            if (type.equals(TYPE_INT)) {
                throw new Exception("Invalid integer value");
            } else if (type.equals(TYPE_FLOAT)) {
                throw new Exception("Invalid float value");
            } else if (type.equals(TYPE_BOOLEAN)) {
                throw new Exception("Invalid boolean value. Use 1/true or 0/false");
            } else {
                throw new Exception("Empty value");
            }
        }

        byte[] bytes;

        try {
            switch (type) {
                case TYPE_INT:
                    long ival = Long.parseLong(raw);

                    if (!is64 && (ival < Integer.MIN_VALUE || ival > MAX_UINT32)) {
                        throw new Exception("Invalid integer value");
                    }

                    bytes = is64 ? arm64Int(ival) : arm32Int((int) ival);
                    break;

                case TYPE_FLOAT:
                    float fval = (float) Double.parseDouble(raw);

                    if (Float.isNaN(fval) || Float.isInfinite(fval)) {
                        throw new Exception("Invalid float value");
                    }

                    bytes = is64 ? arm64Float(fval) : arm32Float(fval);
                    break;

                case TYPE_BOOLEAN:
                    boolean bval = parseBool(raw);
                    bytes = is64 ? arm64Int(bval ? 1L : 0L) : arm32Int(bval ? 1 : 0);
                    break;

                default:
                    throw new Exception("Unknown type");
            }

        } catch (NumberFormatException e) {

            if (type.equals(TYPE_INT)) {
                throw new Exception("Invalid integer value");
            }

            if (type.equals(TYPE_FLOAT)) {
                throw new Exception("Invalid float value");
            }

            throw new Exception("Invalid value");
        }

        sb.append(bytesToHex(bytes));
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

                    float f = (float) cur.doubleValue();

                    if (Float.isNaN(f) || Float.isInfinite(f)) {
                        throw new Exception("Invalid float value");
                    }

                    bytes = is64 ? arm64Float(f) : arm32Float(f);

                } else {

                    long v;

                    try {
                        v = cur.longValueExact();
                    } catch (ArithmeticException ex) {
                        throw new Exception("Not a whole number");
                    }

                    if (!is64 && (v < Integer.MIN_VALUE || v > MAX_UINT32)) {
                        throw new Exception("Invalid integer value");
                    }

                    bytes = is64 ? arm64Int(v) : arm32Int((int) v);
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

    // ── ARM32 INT: MOV R0,#imm ; BX LR (или MOVW+MOVT если imm не кодируется) ──
    private byte[] arm32Int(int value) {
        // Пробуем закодировать как ARM immediate (8-битный imm + rotation)
        int[] enc = encodeArm32Imm(value & 0xFFFFFFFFL);
        if (enc != null) {
            int rot = enc[0], imm8 = enc[1];
            int instr = 0xE3A00000 | (rot << 8) | imm8;
            return concat(intToLE(instr), ARM32_BX_LR);
        } else {
            // MOVW + MOVT + BX LR
            long uval = value & 0xFFFFFFFFL;
            int low16 = (int) (uval & 0xFFFF);
            int high16 = (int) ((uval >> 16) & 0xFFFF);
            int movw = 0xE3000000 | ((low16 >> 12) << 16) | (low16 & 0xFFF);
            int movt = 0xE3400000 | ((high16 >> 12) << 16) | (high16 & 0xFFF);
            return concat(intToLE(movw), intToLE(movt), ARM32_BX_LR);
        }
    }

    // ── ARM32 Float: MOVW R0,#low; MOVT R0,#high; BX LR ────────────
    private byte[] arm32Float(float fval) {
        int bits = Float.floatToRawIntBits(fval);
        int low16 = bits & 0xFFFF;
        int high16 = (bits >> 16) & 0xFFFF;
        int movw = 0xE3000000 | ((low16 >> 12) << 16) | (low16 & 0xFFF);
        int movt = 0xE3400000 | ((high16 >> 12) << 16) | (high16 & 0xFFF);
        return concat(intToLE(movw), intToLE(movt), ARM32_BX_LR);
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

    // ── ARM64 Float: MOVZ/MOVK W0 ; FMOV S0,W0 ; RET (float 32-bit -> S0) ──
    private byte[] arm64Float(float fval) {
        int bits = Float.floatToRawIntBits(fval);
        int lo = bits & 0xFFFF;
        int hi = (bits >>> 16) & 0xFFFF;
        byte[] r = intToLE(0x52800000 | (lo << 5));              // MOVZ W0, #lo
        if (hi != 0) {
            r = concat(r, intToLE(0x72A00000 | (hi << 5)));      // MOVK W0, #hi, LSL#16
        }
        return concat(r, intToLE(0x1E270000), ARM64_RET);        // FMOV S0, W0 ; RET
    }

    // ── Вспомогательные методы ───────────────────────────────────────

    /** Кодирование ARM32 immediate: возвращает {rotation, imm8} или null */
    private int[] encodeArm32Imm(long value) {
        value = value & 0xFFFFFFFFL;
        for (int ror = 0; ror < 32; ror += 2) {
            long rotated = ((value << ror) | (value >>> (32 - ror))) & 0xFFFFFFFFL;
            if (rotated <= 0xFF) {
                return new int[]{ror / 2, (int) rotated};
            }
        }
        return null;
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

    private void copyToClipboard() {
        String text = resultText.getText().toString();
        if (text.isEmpty()) {
            Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("hex_result", text));
        Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
    }

    private GradientDrawable makeRoundedBg(int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp(8));
        gd.setColor(color);
        return gd;
    }

    private EditText makeEditText(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextColor(Color.BLACK);
        et.setHintTextColor(Color.GRAY);
        // Полная клавиатура с минусом и точкой (signed decimal)
        et.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_SIGNED
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        et.setLayoutParams(lp);
        return et;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void addSpace(LinearLayout parent, int dpValue) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(dpValue)));
    }
}
