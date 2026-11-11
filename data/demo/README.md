# Demo data (synthetic)

Both files are made-up sales histories for one SKU, `YOGURT-100G`, for the 56 days before the receive date D = Friday 2026-11-13. They contain no real store data.

| File | Use | Shape |
|---|---|---|
| `yogurt_56d.csv` | AC-08, demo | Saturday sold out in 5 of 8 weeks; per weekday the P25/P50/P75 of `in_stock` days equal the slow/base/fast series in Specification Phụ lục A.5; recent 4 weeks sell more than the 4 before. |
| `yogurt_trend_56d.csv` | AC-10 | No sell-outs; the last 4 weeks sell about 24% more than the 4 before. |

`api/src/test/java/vn/ainalyzing/api/csv/CsvForecastTest.java` reads these files and checks the series, so a change here that breaks Phụ lục A.5 fails the build.
