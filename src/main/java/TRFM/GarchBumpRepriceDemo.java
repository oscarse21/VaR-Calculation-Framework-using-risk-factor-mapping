package TRFM;

import TRFM.AssetModels.OptionType;
import TRFM.VolatilityModels.GarchVolatilityModel;
import TRFM.VolatilityResults.GarchResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Independent, reproducible educational example using SYNTHETIC market data only.
 * GARCH forecasts underlying log-return volatility, not option implied volatility.
 * Bump-and-reprice estimates portfolio delta/gamma. Full repricing is the benchmark.
 * The demo generates its own synthetic input and hypothetical portfolio.
 * Publication rights of pre-existing repository components must be verified independently.
 */
public final class GarchBumpRepriceDemo 
{
    private static final int HISTORY_DAYS = 750;
    private static final int SCENARIOS = 5_000;
    private static final long HISTORY_SEED = 20261120L;
    private static final long SCENARIO_SEED = 42L;

    private static final double SPOT = 100.0;
    private static final double IMPLIED_VOL = 0.24;
    private static final double RATE = 0.03;
    private static final double MATURITY_YEARS = 0.5;
    private static final double SPOT_BUMP = 0.5;

    private GarchBumpRepriceDemo() { }

    public static void main(String[] args) 
    {
        Locale.setDefault(Locale.US);
        // Synthetic daily log returns expressed in PERCENT for the existing GARCH estimator.
        double[] returnsPct = syntheticGarchHistory(HISTORY_DAYS, HISTORY_SEED);
        List<LocalDate> dates = new ArrayList<>();
        double[][] observations = new double[HISTORY_DAYS][1];
        for (int t = 0; t < HISTORY_DAYS; t++) 
        {
            dates.add(LocalDate.of(2022, 1, 3).plusDays(t)); // synthetic observation index
            observations[t][0] = returnsPct[t];
        }
        List<String> factors = List.of("SYNTHETIC_EQUITY_RETURN_PCT");
        RiskFactorMatrix history = new RiskFactorMatrix(dates, factors, observations);

        GarchVolatilityModel garch = new GarchVolatilityModel(GarchDistribution.NORMAL);
        GarchResult fitted = (GarchResult) garch.estimate(history);
        double[] params = fitted.getGarchParams()[0];
        double nextSigmaPct = garch.forecastNextSigma(history, fitted)[0];
        
        if (!(Double.isFinite(nextSigmaPct) && nextSigmaPct > 0)) 
        {
            throw new IllegalStateException("GARCH forecast is invalid: " + nextSigmaPct);
        }

        // Filter returns by estimated conditional volatility and resample residuals.
        double[][] standardizedResiduals = new double[HISTORY_DAYS][1];
        
        for (int t = 0; t < HISTORY_DAYS; t++) 
        {
            double conditionalSigmaPct = fitted.getSigma()[t][0];
            
            if (!(Double.isFinite(conditionalSigmaPct) && conditionalSigmaPct > 0)) 
            {
                throw new IllegalStateException("Invalid conditional sigma at t=" + t);
            }
            standardizedResiduals[t][0] = returnsPct[t] / conditionalSigmaPct;
        }
        ResidualMatrix residuals = new ResidualMatrix(dates, factors, standardizedResiduals);
        FhsScenarioGenerator sampler = new FhsScenarioGenerator(residuals, SCENARIO_SEED);
        List<FhsScenario> scenarios = sampler.drawScenarios(new double[]{nextSigmaPct}, SCENARIOS);

        double baseValue = portfolioValue(SPOT);
        double valueUp = portfolioValue(SPOT + SPOT_BUMP);
        double valueDown = portfolioValue(SPOT - SPOT_BUMP);
        double delta = (valueUp - valueDown) / (2.0 * SPOT_BUMP);
        double gamma = (valueUp - 2.0 * baseValue + valueDown) / (SPOT_BUMP * SPOT_BUMP);

        double[] fullPnl = new double[SCENARIOS];
        double[] taylorPnl = new double[SCENARIOS];
        double absoluteErrorSum = 0.0;
        for (int i = 0; i < SCENARIOS; i++) 
        {
            // GARCH shocks are in percent; exp() maps synthetic log-returns to spot.
            double shockPct = scenarios.get(i).getSimulatedShock()[0];
            double shockedSpot = SPOT * Math.exp(shockPct / 100.0);
            double spotChange = shockedSpot - SPOT;
            fullPnl[i] = portfolioValue(shockedSpot) - baseValue;
            taylorPnl[i] = delta * spotChange + 0.5 * gamma * spotChange * spotChange;
            absoluteErrorSum += Math.abs(fullPnl[i] - taylorPnl[i]);
        }

        double fullVar95 = valueAtRisk(fullPnl, 0.95);
        double approxVar95 = valueAtRisk(taylorPnl, 0.95);
        double fullVar99 = valueAtRisk(fullPnl, 0.99);
        double approxVar99 = valueAtRisk(taylorPnl, 0.99);
        double mae = absoluteErrorSum / SCENARIOS;
        
        for (double result : new double[]{baseValue, delta, gamma, fullVar95, approxVar95, fullVar99, approxVar99, mae}) 
        {
            if (!Double.isFinite(result)) throw new IllegalStateException("Non-finite demo output");
        }

        System.out.println("=== GARCH + BUMP-AND-REPRICE | SYNTHETIC EDUCATIONAL DEMO ===");
        System.out.printf("History: %d generated observations | Scenarios: %d | Seeds: %d / %d%n", HISTORY_DAYS, SCENARIOS, HISTORY_SEED, SCENARIO_SEED);
        System.out.printf("GARCH(1,1) estimates: omega=%.5f, alpha=%.4f, beta=%.4f (returns in percent)%n", params[0], params[1], params[2]);
        System.out.printf("Next-day underlying volatility forecast: %.4f%% (daily)%n", nextSigmaPct);
        System.out.printf("Portfolio value: %.4f | spot delta: %.4f | spot gamma: %.5f%n", baseValue, delta, gamma);
        System.out.printf("95%% VaR: full=%.4f | delta-gamma=%.4f%n", fullVar95, approxVar95);
        System.out.printf("99%% VaR: full=%.4f | delta-gamma=%.4f%n", fullVar99, approxVar99);
        System.out.printf("Mean absolute P&L approximation error: %.6f%n", mae);
        System.out.println("Scope: synthetic inputs; European Black-Scholes options; fixed implied vol/rate/maturity;");
        System.out.println("spot shocks only; no volatility surface shocks, time decay, costs or production validation.");
    }

    /** Generates synthetic daily percentage log returns with a stationary GARCH(1,1) process. */
    private static double[] syntheticGarchHistory(int count, long seed) 
    {
        Random rng = new Random(seed);
        // Percent-squared units, chosen for a stable, illustrative demonstration.
        double omega = 0.04;
        double alpha = 0.08;
        double beta = 0.88;
        double conditionalVariance = omega / (1.0 - alpha - beta);
        double[] history = new double[count];
        for (int t = 0; t < count; t++) 
        {
            history[t] = Math.sqrt(conditionalVariance) * rng.nextGaussian();
            conditionalVariance = omega + alpha * history[t] * history[t] + beta * conditionalVariance;
        }
        return history;
    }

    /** Toy book: five calls, short three puts, and 10 shares. */
    private static double portfolioValue(double spot) 
    {
        double call = BlackScholesPricer.priceEuropean(OptionType.CALL, spot, 100.0, MATURITY_YEARS, IMPLIED_VOL, RATE, 0.0);
        double put = BlackScholesPricer.priceEuropean(OptionType.PUT, spot, 95.0, MATURITY_YEARS, IMPLIED_VOL, RATE, 0.0);
        return 5.0 * call - 3.0 * put + 10.0 * spot;
    }

    /** Historical P&L loss quantile */
    private static double valueAtRisk(double[] pnl, double confidence) 
    {
        double[] sorted = pnl.clone();
        Arrays.sort(sorted);
        int index = Math.max(0, (int) Math.ceil((1.0 - confidence) * sorted.length) - 1);
        return Math.max(0.0, -sorted[index]);
    }
}
