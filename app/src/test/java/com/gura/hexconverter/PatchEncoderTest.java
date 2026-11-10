package com.gura.hexconverter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.gura.hexconverter.PatchEncoder.PatchException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import org.junit.Test;

public class PatchEncoderTest {

    private static String single(boolean is64, String type, String value) throws PatchException {
        return PatchEncoder.bytesToHex(PatchEncoder.encodeSingle(is64, type, value));
    }

    private static String expectError(Runnable0 r) {
        try {
            r.run();
        } catch (PatchException e) {
            return e.getMessage();
        }
        fail("expected PatchException");
        return null;
    }

    private interface Runnable0 {
        void run() throws PatchException;
    }

    // ── Parity with the web tool ─────────────────────────────────────

    @Test
    public void matchesWebToolOnGoldenCases() throws Exception {
        InputStream in = getClass().getResourceAsStream("/web_golden_cases.tsv");
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        int checked = 0;
        String line;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("#") || line.isEmpty()) {
                continue;
            }
            String[] p = line.split("\t", -1);
            assertEquals("bad golden row: " + line, 4, p.length);
            boolean is64 = p[0].equals("ARM64");
            String type = p[1].equals("FLOAT") ? PatchEncoder.TYPE_FLOAT : p[1];
            String actual;
            try {
                actual = single(is64, type, p[2]);
            } catch (PatchException e) {
                actual = "ERR: " + e.getMessage();
            }
            assertEquals(p[0] + " " + p[1] + " '" + p[2] + "'", p[3], actual);
            checked++;
        }
        assertEquals(1234, checked);
    }

    // ── Known vectors ────────────────────────────────────────────────

    @Test
    public void arm32Integers() throws Exception {
        assertEquals("E8 03 00 E3 1E FF 2F E1", single(false, "INT", "1000"));
        assertEquals("FF 0F 0F E3 FF 0F 4F E3 1E FF 2F E1", single(false, "INT", "-1"));
        assertEquals("00 00 00 E3 1E FF 2F E1", single(false, "INT", "0"));
        assertEquals("A0 06 08 E3 01 00 40 E3 1E FF 2F E1", single(false, "INT", "100000"));
        assertEquals(single(false, "INT", "255"), single(false, "INT", "0xFF"));
    }

    @Test
    public void arm64Integers() throws Exception {
        assertEquals("00 7D 80 D2 C0 03 5F D6", single(true, "INT", "1000"));
        assertEquals("20 00 80 D2 C0 03 5F D6", single(true, "INT", "1"));
        assertEquals("E0 FF 9F D2 E0 FF BF F2 E0 FF DF F2 E0 FF FF F2 C0 03 5F D6",
                single(true, "INT", "18446744073709551615"));
    }

    @Test
    public void floats() throws Exception {
        assertEquals("00 00 00 E3 80 0F 43 E3 1E FF 2F E1", single(false, "Float", "1.0"));
        assertEquals("00 00 80 D2 00 FE E7 F2 C0 03 5F D6", single(true, "Float", "1.0"));
    }

    @Test
    public void booleans() throws Exception {
        assertEquals("01 00 00 E3 1E FF 2F E1", single(false, "Boolean", "true"));
        assertEquals("00 00 00 E3 1E FF 2F E1", single(false, "Boolean", "0"));
        assertEquals("20 00 80 D2 C0 03 5F D6", single(true, "Boolean", "TRUE"));
        assertEquals("00 00 80 D2 C0 03 5F D6", single(true, "Boolean", "false"));
        expectError(() -> single(true, "Boolean", "maybe"));
    }

    @Test
    public void rejectsInvalidInput() {
        assertEquals("Input cannot be empty.", expectError(() -> single(false, "INT", "  ")));
        assertEquals("Value exceeds 32-bit integer range (-2,147,483,648 to 4,294,967,295).",
                expectError(() -> single(false, "INT", "4294967296")));
        assertEquals("Value exceeds 64-bit integer range.",
                expectError(() -> single(true, "INT", "18446744073709551616")));
        assertEquals("Invalid float value. NaN and Infinity are not allowed.",
                expectError(() -> single(true, "Float", "1e999")));
        assertEquals("Unknown type", expectError(() -> single(true, "Nope", "1")));
    }

    // ── Range mode ───────────────────────────────────────────────────

    @Test
    public void intRange() throws Exception {
        String out = PatchEncoder.generateRange(false, PatchEncoder.TYPE_INT_RANGE, "1", "3", "1");
        assertEquals("1 = 01 00 00 E3 1E FF 2F E1\n"
                + "2 = 02 00 00 E3 1E FF 2F E1\n"
                + "3 = 03 00 00 E3 1E FF 2F E1\n", out);
    }

    @Test
    public void rangeWithEqualBoundsHasOneEntry() throws Exception {
        String out = PatchEncoder.generateRange(true, PatchEncoder.TYPE_INT_RANGE, "5", "5", "1");
        assertEquals("5 = A0 00 80 D2 C0 03 5F D6\n", out);
    }

    @Test
    public void floatRangeUsesExactDecimalSteps() throws Exception {
        String out = PatchEncoder.generateRange(false, PatchEncoder.TYPE_FLOAT_RANGE, "0.1", "0.3", "0.1");
        assertEquals(3, out.split("\n").length);
        assertTrue(out.startsWith("0.1 = "));
        assertTrue(out.contains("\n0.3 = "));
    }

    @Test
    public void rangeValidation() {
        assertEquals("Please fill all range fields!",
                expectError(() -> PatchEncoder.generateRange(false, "INT Range", "1", "", "1")));
        assertEquals("Step size must be greater than 0",
                expectError(() -> PatchEncoder.generateRange(false, "INT Range", "1", "5", "0")));
        assertEquals("Start value must not be greater than end value",
                expectError(() -> PatchEncoder.generateRange(false, "INT Range", "5", "1", "1")));
        assertEquals("Range too large (max 10000 entries)",
                expectError(() -> PatchEncoder.generateRange(false, "INT Range", "0", "10000", "1")));
        assertEquals("INT range needs whole numbers for Start and Step",
                expectError(() -> PatchEncoder.generateRange(false, "INT Range", "0.5", "5", "1")));
        assertEquals("Value exceeds 32-bit integer range (-2,147,483,648 to 4,294,967,295).",
                expectError(() -> PatchEncoder.generateRange(false, "INT Range", "4294967290", "4294967300", "1")));
        assertEquals("Unknown type",
                expectError(() -> PatchEncoder.generateRange(false, "INT", "1", "2", "1")));
    }

    @Test
    public void rangeRejectsExponentsInsteadOfHanging() {
        long t0 = System.nanoTime();
        assertEquals("Invalid number format",
                expectError(() -> PatchEncoder.generateRange(false, "INT Range", "0", "1E+999999999", "1")));
        assertEquals("Invalid number format",
                expectError(() -> PatchEncoder.generateRange(false, "Float Range", "1e-999999999", "1", "1")));
        assertTrue("must fail fast", (System.nanoTime() - t0) / 1_000_000 < 2000);
    }

    @Test
    public void maxSizedRangeIsFast() throws Exception {
        long t0 = System.nanoTime();
        String out = PatchEncoder.generateRange(true, PatchEncoder.TYPE_INT_RANGE, "1", "10000", "1");
        assertEquals(PatchEncoder.MAX_RANGE_ITEMS, out.split("\n").length);
        assertTrue("took too long", (System.nanoTime() - t0) / 1_000_000 < 3000);
    }

    // ── Disassembler ─────────────────────────────────────────────────

    @Test
    public void disassembles() throws Exception {
        assertEquals("MOVW R0, #0x3E8\nBX LR",
                PatchEncoder.disassemble(PatchEncoder.encodeSingle(false, "INT", "1000"), false));
        assertEquals("MOVZ X0, #0x0\nMOVK X0, #0x3FF0, LSL #48\nRET",
                PatchEncoder.disassemble(PatchEncoder.encodeSingle(true, "Float", "1.0"), true));
    }

    // ── Robustness: only PatchException may escape ───────────────────

    @Test
    public void fuzzNeverThrowsUnexpectedExceptions() {
        Random rnd = new Random(12345);
        String alphabet = "0123456789abcdefABCDEFxX.-+eE _\u200b\t,NaInfity";
        String[] types = {"INT", "Float", "Boolean", "INT Range", "Float Range", "garbage"};
        for (int i = 0; i < 20000; i++) {
            int len = rnd.nextInt(90);
            StringBuilder a = new StringBuilder();
            StringBuilder b = new StringBuilder();
            StringBuilder c = new StringBuilder();
            for (int k = 0; k < len; k++) {
                a.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
                b.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
                c.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
            }
            String type = types[rnd.nextInt(types.length)];
            boolean is64 = rnd.nextBoolean();
            try {
                PatchEncoder.encodeSingle(is64, type, a.toString());
            } catch (PatchException expected) {
                assertFalse(expected.getMessage() == null || expected.getMessage().isEmpty());
            }
            try {
                PatchEncoder.generateRange(is64, type, a.toString(), b.toString(), c.toString());
            } catch (PatchException expected) {
                assertFalse(expected.getMessage() == null || expected.getMessage().isEmpty());
            }
        }
    }

    @Test
    public void hugeInputIsTruncatedNotCrashing() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            sb.append('9');
        }
        // Input is cut to 64 chars (like the web tool): 64 nines overflow ARM32 ...
        expectError(() -> single(false, "INT", sb.toString()));
        // ... but are a valid finite double, and the trailing "e9" is ignored.
        assertEquals(single(true, "Float", sb.substring(0, 64)),
                single(true, "Float", sb.toString() + "e9"));
    }
}
