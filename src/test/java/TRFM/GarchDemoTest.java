package TRFM;

import TRFM.AssetModels.OptionType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class GarchDemoTest {
    @Test
    void blackScholesPutCallParity() {
        double spot = 100.0, strike = 105.0, maturity = 0.75, volatility = 0.25, rate = 0.03, dividend = 0.01;
        double call = BlackScholesPricer.priceEuropean(OptionType.CALL, spot, strike, maturity, volatility, rate, dividend);
        double put = BlackScholesPricer.priceEuropean(OptionType.PUT, spot, strike, maturity, volatility, rate, dividend);
        double parity = spot * Math.exp(-dividend * maturity) - strike * Math.exp(-rate * maturity);
        assertEquals(parity, call - put, 1e-9);
        assertTrue(call > 0 && put > 0);
    }

    @Test
    void optionIntrinsicValueAtExpiry() {
        assertEquals(10.0, BlackScholesPricer.priceEuropean(OptionType.CALL, 110, 100, 0, 0.2, 0.03, 0.0), 1e-10);
        assertEquals(10.0, BlackScholesPricer.priceEuropean(OptionType.PUT, 90, 100, 0, 0.2, 0.03, 0.0), 1e-10);
    }

    @Test
    void syntheticEndToEndDemoIsDeterministic() {
        String first = runDemo();
        String second = runDemo();
        assertEquals(first, second, "Fixed seeds should produce identical console results");
        assertTrue(first.contains("SYNTHETIC EDUCATIONAL DEMO"));
        assertTrue(first.contains("GARCH(1,1) estimates"));
        assertTrue(first.contains("95% VaR: full="));
        assertTrue(first.contains("99% VaR: full="));
        assertTrue(first.contains("Mean absolute P&L approximation error"));
        assertFalse(first.contains("NaN"));
        assertFalse(first.contains("Infinity"));
    }

    private String runDemo() {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            GarchBumpRepriceDemo.main(new String[0]);
            return buffer.toString(StandardCharsets.UTF_8);
        } finally {
            System.setOut(original);
        }
    }
}
