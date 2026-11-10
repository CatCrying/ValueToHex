package com.gura.hexconverter;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * ARM32 / ARM64 patch encoder. Pure Java (no Android dependencies) so it can be unit tested.
 * Output is kept identical to the web tool at valuetohex-tsup.vercel.app.
 */
public final class PatchEncoder {

    /** Thrown for any invalid user input; the message is shown to the user. */
    public static final class PatchException extends Exception {
        private static final long serialVersionUID = 1L;

        public PatchException(String message) {
            super(message);
        }
    }

    public static final String TYPE_INT = "INT";
    public static final String TYPE_FLOAT = "Float";
    public static final String TYPE_BOOLEAN = "Boolean";
    public static final String TYPE_INT_RANGE = "INT Range";
    public static final String TYPE_FLOAT_RANGE = "Float Range";

    public static final int MAX_RANGE_ITEMS = 10000;
    public static final int MAX_INPUT_LENGTH = 64;

    private static final long MAX_UINT32 = 0xFFFFFFFFL;
    private static final byte[] ARM32_BX_LR = {0x1E, (byte) 0xFF, 0x2F, (byte) 0xE1};
    private static final byte[] ARM64_RET = {(byte) 0xC0, 0x03, 0x5F, (byte) 0xD6};
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private PatchEncoder() {
    }

    // ── High level API ───────────────────────────────────────────────

    /** Encodes one INT / Float / Boolean value. */
    public static byte[] encodeSingle(boolean is64, String type, String rawInput) throws PatchException {
        String raw = cleanInput(rawInput);
        switch (type) {
            case TYPE_INT:
                return encodeInt(parseIntegerInput(raw), is64);
            case TYPE_FLOAT:
                return encodeFloat(parseFloatInput(raw), is64);
            case TYPE_BOOLEAN:
                return encodeInt(parseBool(raw) ? BigInteger.ONE : BigInteger.ZERO, is64);
            default:
                throw new PatchException("Unknown type");
        }
    }

    /**
     * Generates "value = hex" lines for Start..End (inclusive) with the given Step.
     * Inputs are validated up front so a bad range fails once instead of producing thousands of
     * error lines, and plain decimals only (no exponents) keep BigDecimal math bounded.
     */
    public static String generateRange(boolean is64, String type, String startRaw, String endRaw,
                                       String stepRaw) throws PatchException {
        boolean isFloat = type.equals(TYPE_FLOAT_RANGE);
        if (!isFloat && !type.equals(TYPE_INT_RANGE)) {
            throw new PatchException("Unknown type");
        }

        String rs = cleanInput(startRaw);
        String re = cleanInput(endRaw);
        String rst = cleanInput(stepRaw);
        if (rs.isEmpty() || re.isEmpty() || rst.isEmpty()) {
            throw new PatchException("Please fill all range fields!");
        }

        BigDecimal start = parseDecimal(rs);
        BigDecimal end = parseDecimal(re);
        BigDecimal step = parseDecimal(rst);

        if (start.compareTo(end) > 0) {
            throw new PatchException("Start value must not be greater than end value");
        }
        if (step.signum() <= 0) {
            throw new PatchException("Step size must be greater than 0");
        }

        BigDecimal count = end.subtract(start).divideToIntegralValue(step).add(BigDecimal.ONE);
        if (count.compareTo(BigDecimal.valueOf(MAX_RANGE_ITEMS)) > 0) {
            throw new PatchException("Range too large (max " + MAX_RANGE_ITEMS + " entries)");
        }
        int n = count.intValue();

        if (!isFloat) {
            if (!isWhole(start) || !isWhole(step)) {
                throw new PatchException("INT range needs whole numbers for Start and Step");
            }
            // Values grow monotonically, so checking both ends covers the whole range.
            BigDecimal last = start.add(step.multiply(BigDecimal.valueOf(n - 1L)));
            encodeInt(start.toBigIntegerExact(), is64);
            encodeInt(last.toBigIntegerExact(), is64);
        }

        StringBuilder sb = new StringBuilder(n * 32);
        BigDecimal cur = start;
        for (int i = 0; i < n; i++) {
            byte[] bytes = isFloat
                    ? encodeFloat(cur.doubleValue(), is64)
                    : encodeInt(cur.toBigIntegerExact(), is64);
            sb.append(cur.stripTrailingZeros().toPlainString())
                    .append(" = ")
                    .append(bytesToHex(bytes))
                    .append('\n');
            cur = cur.add(step);
        }
        return sb.toString();
    }

    private static BigDecimal parseDecimal(String s) throws PatchException {
        if (!s.matches("[+-]?([0-9]+\\.?[0-9]*|\\.[0-9]+)")) {
            throw new PatchException("Invalid number format");
        }
        return new BigDecimal(s);
    }

    private static boolean isWhole(BigDecimal v) {
        return v.signum() == 0 || v.stripTrailingZeros().scale() <= 0;
    }

    // ── Formatting ───────────────────────────────────────────────────

    /** Space separated upper-case hex bytes, e.g. "E8 03 00 E3". */
    public static String bytesToHex(byte[] bytes) {
        if (bytes.length == 0) {
            return "";
        }
        char[] out = new char[bytes.length * 3 - 1];
        int j = 0;
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[j++] = HEX[v >>> 4];
            out[j++] = HEX[v & 0xF];
            if (i < bytes.length - 1) {
                out[j++] = ' ';
            }
        }
        return new String(out);
    }

    /** Disassembles only the instructions this encoder emits. */
    public static String disassemble(byte[] code, boolean is64) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i + 3 < code.length; i += 4) {
            int w = (code[i] & 0xFF) | ((code[i + 1] & 0xFF) << 8)
                    | ((code[i + 2] & 0xFF) << 16) | ((code[i + 3] & 0xFF) << 24);
            String s;
            if (!is64) {
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

    private static String hexUpper(int v) {
        return Integer.toHexString(v).toUpperCase(java.util.Locale.US);
    }

    // ── Input parsing and instruction encoders ───────────────────────

    /** Same input cleaning as the web tool: max 64 chars, strip zero-width chars, trim. */
    public static String cleanInput(String s) {
        if (s.length() > 64) {
            s = s.substring(0, 64);
        }
        return s.replaceAll("[\\u200B-\\u200D\\uFEFF]", "").trim();
    }

    /** Integer input: decimal or non-negative hex (0x..), same rules/messages as the web tool. */
    public static BigInteger parseIntegerInput(String t) throws PatchException {
        if (t.isEmpty()) {
            throw new PatchException("Input cannot be empty.");
        }
        if (t.matches("(?i)-?0x[0-9a-f]+")) {
            if (t.startsWith("-")) {
                throw new PatchException("Failed to parse integer.");
            }
            return new BigInteger(t.substring(2), 16);
        }
        if (t.matches("-?[0-9]+")) {
            return new BigInteger(t);
        }
        throw new PatchException("Invalid integer format. Use decimal (e.g. 10) or hex (e.g. 0x0A).");
    }

    /** Float input parsed like JavaScript Number(): decimal/exponent, 0x/0b/0o forms. */
    public static double parseFloatInput(String t) throws PatchException {
        if (t.isEmpty()) {
            throw new PatchException("Input cannot be empty.");
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
            throw new PatchException("Invalid float value. NaN and Infinity are not allowed.");
        }
        return d;
    }

    /** Boolean input: true/false/1/0 (case-insensitive). */
    public static boolean parseBool(String raw) throws PatchException {
        if (raw.equalsIgnoreCase("true") || raw.equals("1")) {
            return true;
        }

        if (raw.equalsIgnoreCase("false") || raw.equals("0")) {
            return false;
        }

        throw new PatchException("Invalid boolean value. Use 1/true or 0/false");
    }

    /** Encodes an integer. ARM32: -2^31..2^32-1, ARM64: -2^63..2^64-1. */
    public static byte[] encodeInt(BigInteger v, boolean is64) throws PatchException {
        if (!is64) {
            if (v.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0
                    || v.compareTo(BigInteger.valueOf(MAX_UINT32)) > 0) {
                throw new PatchException("Value exceeds 32-bit integer range (-2,147,483,648 to 4,294,967,295).");
            }
            return arm32Int(v.intValue());
        }
        BigInteger min = BigInteger.ONE.shiftLeft(63).negate();
        BigInteger max = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
        if (v.compareTo(min) < 0 || v.compareTo(max) > 0) {
            throw new PatchException("Value exceeds 64-bit integer range.");
        }
        return arm64Int(v.longValue());
    }

    /** ARM32: IEEE-754 single in R0. ARM64: IEEE-754 double in X0 (same as the web tool). */
    public static byte[] encodeFloat(double d, boolean is64) {
        return is64 ? arm64Double(d) : arm32Float((float) d);
    }

    /** ARM32: MOVW R0,#low16 [; MOVT R0,#high16 if non-zero] ; BX LR */
    public static byte[] arm32Int(int value) {
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

    /** ARM32 float: single-precision bits via MOVW (+MOVT) R0 ; BX LR */
    public static byte[] arm32Float(float fval) {
        return arm32Int(Float.floatToRawIntBits(fval));
    }

    /** ARM64: MOVZ X0 + MOVK for each non-zero 16-bit chunk ; RET */
    public static byte[] arm64Int(long value) {
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

    /** ARM64 float: double (64-bit IEEE-754) bits via MOVZ/MOVK X0 ; RET */
    public static byte[] arm64Double(double d) {
        return arm64Int(Double.doubleToRawLongBits(d));
    }

    private static byte[] intToLE(int v) {
        return new byte[]{(byte) (v), (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }

    private static byte[] concat(byte[]... arrays) {
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
}
