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
import java.math.BigInteger;

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
        String raw = cleanInput(inputValue.getText().toString());
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
