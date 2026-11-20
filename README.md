# Value-at-Risk via Risk-Factor Mapping
### GARCH, filtered historical simulation, and bump-and-reprice — reproducible Java demo

This public research **demonstration** explores a practical question in computational risk modelling: **How closely can a fast, second-order approximation track repeated full portfolio revaluation?**

It combines Gaussian GARCH(1,1) forecasting, filtered historical simulation (FHS), Black–Scholes option pricing and finite-difference bump-and-reprice sensitivities. It uses **only generated, synthetic returns and a hypothetical portfolio**.

> **Scope:** This is a deliberately limited educational demonstration — **not** the complete 2026 master's thesis, a validated production VaR engine, or the code deployed at Scila AB.

## Quick start

**Requirements:** Java 17+ and Apache Maven 3.9+.

```bash
mvn clean test
mvn exec:java
```

The example requires no credentials, Bloomberg subscription, external data files or runtime API access. Maven downloads its public dependencies on the first build.

The program prints estimated GARCH parameters, a next-day underlying-volatility forecast, finite-difference delta and gamma, empirical one-day 95% and 99% VaR under **full repricing versus delta–gamma approximation**, and mean absolute scenario P&L error. Random seeds are fixed.

## How it works

1. **Synthetic data:** Generate 750 daily log-returns in **percentage units** from a stationary Gaussian GARCH(1,1) process.
2. **Estimate volatility:** Fit GARCH(1,1) with a numerical optimizer and forecast next-day **underlying return volatility**.
3. **Generate scenarios:** Standardize historical residuals by conditional volatility, resample them and apply the volatility forecast to create 5,000 one-day FHS shocks.
4. **Price instruments:** Revalue a hypothetical portfolio of European calls, puts and shares using Black–Scholes.
5. **Approximate P&L:** Estimate spot delta and gamma using symmetric finite-difference **bump-and-reprice**, then approximate scenario P&L by
   `ΔV ≈ Δ × ΔS + 0.5 × Γ × (ΔS)^2`.
6. **Compare:** Calculate empirical 95%/99% one-day VaR and mean absolute P&L error for the approximation against full repricing.

The example focuses on the **computational trade-off between approximation and full evaluation**. It does not train an ML surrogate; the local Taylor approximation is a classical mathematical surrogate.

## Project layout

| Path | Purpose |
|---|---|
| `src/main/java/TRFM/GarchBumpRepriceDemo.java` | Deterministic end-to-end simulation and comparison |
| `src/main/java/TRFM/VolatilityModels/GarchVolatilityModel.java` | Conditional volatility estimation and one-day forecasting |
| `src/main/java/TRFM/VolatilityEstimators/Garch11_Estimator.java` | GARCH parameter fitting |
| `src/main/java/TRFM/Optimize/Optimizer_NelderMead.java` | Numerical minimizer |
| `src/main/java/TRFM/ScenarioGeneratorModel.java` | Standardized-residual resampling |
| `src/main/java/TRFM/BlackScholesPricer.java` | European option pricing |
| `src/test/java/TRFM/GarchDemoTest.java` | Sanity, reproducibility and pricing tests |
| `.github/workflows/java-ci.yml` | Automatically builds, tests and runs the demo |

## Limitations and interpretation

- The forecasted **GARCH underlying-return volatility is not option implied volatility**. Option implied volatility is held at 24%.
- Only **one synthetic equity spot** is shocked; no volatility-surface, FX or interest-rate shocks are included.
- Time to maturity, rate and implied volatility are fixed across all repricings; no theta or vega P&L is captured.
- Black–Scholes prices **European options**. No early exercise, transaction costs, bid/ask spreads, corporate actions or market-data quality issues are modelled.
- This is **not** an out-of-sample backtest. It provides no empirical performance guarantees for real portfolios or production systems.
- Simplified FHS resamples single-factor standardized residuals. Parameter stability and model risk require separate validation.

## Research background and data provenance

The master's thesis **_Efficient Value at Risk – Risk Factor Mapping_** (Lund University, 2026) was jointly authored by **Oscar Stenander Eklund** and **Philip Johansson Casserstål**, in collaboration with Scila AB. The full research addressed a wider set of instruments, market risk factors, scenario generation approaches and historical backtesting. Follow-on implementation work is being developed for Scila's risk platform.

**This repository does not contain the full thesis implementation, Scila's internal/production systems, or raw Bloomberg market data.** Historical SPX risk-factor and option data used for academic work were accessed via Bloomberg terminals at Lund University; they are **not redistributed here**. Everything used by the runnable demo is generated synthetically.

This code is a selected, self-contained demonstration of methods and programming skills. Rights to any code developed in collaboration with third parties should be checked independently before reuse.

## License

See [LICENSE](LICENSE). The license applies only to the contents for which the rights holders have permission to grant it; it does not cover Bloomberg data, third-party proprietary material or non-public Scila code.
