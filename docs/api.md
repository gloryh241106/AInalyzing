# AInalyzing API

Chép từ Project Specification, Phụ lục A–B (bản 19:30 ngày 11/11/2026). Đổi hợp đồng thì sửa Specification và file này trong cùng một PR.

## Quy ước chung

JSON UTF-8, tên trường camelCase, tiền VND và số hũ là số nguyên, ngày `YYYY-MM-DD`, thứ trong tuần `MON`–`SUN`. Không đăng nhập; phiên là HttpSession của Spring (cookie `JSESSIONID`, HttpOnly, SameSite=Lax). Mọi lỗi trả:

```json
{"errors":[{"code":"...","message":"...","field":null,"row":null}]}
```

Một request có thể có nhiều lỗi; `field` và `row` là null khi không áp dụng.

| Method · route | Request | Response thành công | Lỗi |
|---|---|---|---|
| `GET /api/health` | (không có) | 200 `{"status":"UP","db":"UP"}` | 503 `{"status":"DOWN","db":"DOWN"}` |
| `GET /api/demo` | (không có) | 200 danh sách ca: `caseCode`, `title`, `request` (giống A.1, để React điền form) | 503 `DB_UNAVAILABLE` |
| `POST /api/analyze` | `multipart/form-data`: part `request` (application/json, A.1) và part `file` (text/csv, chỉ khi `demandSource` là `csv`) | 200 A.2 | 400 `VALIDATION_ERROR` · 413 `FILE_TOO_LARGE` · 422 `CSV_INVALID` · 422 `INSUFFICIENT_SAMPLES` · 503 `DB_UNAVAILABLE` |
| `POST /api/analysis/insight` | `{"analysisId"}` | 200 A.3 với status `ok` hoặc `fallback` | 404 `{"status":"stale_analysis"}` |
| `POST /api/analysis/chat` | `{"analysisId","question"}` (question ≤500 ký tự, server tự cắt) | 200 A.4 với status `ok`, `fallback` hoặc `unsupported` | 404 `{"status":"stale_analysis"}` |
| `POST /api/session/reset` | (không có) | 204, xóa snapshot và hội thoại của phiên | (không có) |

## A.1. Request của `POST /api/analyze`

| Trường | Kiểu | Ràng buộc |
|---|---|---|
| `sku` | chuỗi | 1–40 ký tự |
| `receiveDate` | ngày | Ngày D |
| `sellPriceVnd` | số nguyên | >0 |
| `budgetVnd` | số nguyên | ≥0 |
| `openingStock` | `{units, sellableDays}` | units ≥0; units >0 thì sellableDays 1–14 bắt buộc |
| `quoteA`, `quoteB` | `{units, goodsVnd, deliveryVnd, sellableDays}` | units >0, `quoteB.units` > `quoteA.units`; tiền ≥0; sellableDays 1–14 hoặc null (chưa xác nhận) |
| `demandSource` | chuỗi | `csv`, `manual` hoặc `demo` |
| `manualDemand` | `{slow, base, fast}` hoặc null | Bắt buộc khi manual; số hũ/ngày, 0 ≤ slow ≤ base ≤ fast |
| `demoCaseCode` | chuỗi hoặc null | Bắt buộc khi demo |

```json
{"sku":"YOGURT-100G","receiveDate":"2026-11-13","sellPriceVnd":15000,"budgetVnd":432000,
 "openingStock":{"units":0,"sellableDays":null},
 "quoteA":{"units":24,"goodsVnd":284000,"deliveryVnd":0,"sellableDays":5},
 "quoteB":{"units":48,"goodsVnd":432000,"deliveryVnd":0,"sellableDays":5},
 "demandSource":"demo","manualDemand":null,"demoCaseCode":"YOGURT_STD"}
```

## A.2. Response của `POST /api/analyze`

| Trường | Nội dung |
|---|---|
| `analysisId` | Chuỗi ngẫu nhiên, gắn với phiên, sống 30 phút |
| `defaultScenario` | Luôn `BASE`; React đổi sang `suggestedScenario` khi thẻ AI có status `ok` |
| `demandSourceInfo` | `{source, daysUsed, inStockSamplesByWeekday}` |
| `scenarios.SLOW / BASE / FAST` | `{demand: 14 số nguyên, options: {A, B}, winner: A, B, TIE hoặc NONE, marginGapVnd}`. `marginGapVnd` là chênh khoản còn lại giữa phương án thắng và phương án kia, ≥0, null khi winner là NONE hoặc chỉ một phương án đủ điều kiện |
| `options.A / options.B` | `{eligible, blockReason (OVER_BUDGET, SHELF_LIFE_PENDING hoặc null), costVnd, unitsSold, unitsShort, unitsExpired, revenueVnd, marginVnd}` |
| `conditions` | `{SLOW, BASE, FAST}`, mỗi kịch bản `{breakEvenTotalVnd, maxWinningTotalVnd, minSellableDaysAtQuotedPrice, pending}`. `minSellableDaysAtQuotedPrice` là null khi không ngày 1–14 nào đạt hoặc B vượt ngân sách. Cả `conditions` là null khi A chưa đủ điều kiện làm mốc |
| `salesSummary` | Bản tóm tắt BR-10 khi nguồn là csv, ngược lại null |
| `facts` | Danh sách `{id, value, unit, scenario}` theo Phụ lục B |

Số kiểm cho ca chuẩn `YOGURT_STD` (B còn 5 ngày, ngân sách 432.000đ, D là thứ Sáu 13/11/2026, nhu cầu Cơ sở 6,6,6,7,7,3,6 lặp hai tuần). `costVnd` của A là 284000, của B là 432000; `revenueVnd` bằng `unitsSold × 15000`; `pending` false ở mọi dòng. `api/.../PurchaseCalculatorTest` kiểm đúng bảng này.

| Kịch bản | A: bán / thiếu / hết hạn / còn lại | B: bán / thiếu / hết hạn / còn lại | winner, marginGapVnd | Hòa / giá trần / hạn tối thiểu |
|---|---|---|---|---|
| SLOW | 24 / 44 / 0 / 76000 | 27 / 41 / 21 / −27000 | A, 103000 | 329000 / 328999 / 7 |
| BASE | 24 / 58 / 0 / 76000 | 32 / 50 / 16 / 48000 | A, 28000 | 404000 / 403999 / 6 |
| FAST | 24 / 72 / 0 / 76000 | 37 / 59 / 11 / 123000 | B, 47000 | 479000 / 432000 / 5 |

Ca `YOGURT_6D`: B bán 29 / 35 / 41 hũ, còn 3000 / 93000 / 183000 ở SLOW / BASE / FAST, nên A thắng ở SLOW và B thắng ở BASE (hơn 17000) và FAST. Ca `YOGURT_BUDGET_400K`: B bị chặn `OVER_BUDGET` ở cả ba kịch bản, giá trần BASE là 400000, hạn tối thiểu null.

## A.3. Response của `POST /api/analysis/insight`

```json
{"analysisId","status","model","promptVersion","suggestedScenario",
 "scenarioReason":{"text","factIds"},
 "insights":[{"kind","text","factIds"}],
 "recommendation":{"option","text","factIds"},
 "supplierQuestions":[{"text","factIds"}]}
```

Groq chỉ trả mã (kind, templateId, factId); mọi trường text do Renderer dựng từ danh mục template ở LLM Architecture §3 và điền số Java. Thẻ được lưu theo `analysisId`; gọi lại trả cùng nội dung, không gọi Groq lần nữa. Khi status là `fallback`, `model` là null và insights theo BR-12.

## A.4. Response của `POST /api/analysis/chat`

```json
{"analysisId","status","answer":{"intent","text","factIds"}}
```

`intent` thuộc `WHY_OPTION`, `WHAT_IF_PRICE`, `WHAT_IF_BUDGET`, `WHAT_IF_SHELF_LIFE` hoặc `UNSUPPORTED`. Câu hỏi cần con số mới (ví dụ ngân sách khác) được trả lời bằng ngưỡng sẵn có và hướng dẫn sửa đầu vào rồi Tính lại.

## A.5. Ca demo trong PostgreSQL

Bảng `demo_cases(case_code, title, request_json, demand_slow, demand_base, demand_fast)`, ba cột demand là mảng 14 số nguyên (`api/src/main/resources/db/migration`). Ca giả lập có nhãn synthetic.

| caseCode | Khác ca chuẩn ở | demand_base | demand_slow | demand_fast |
|---|---|---|---|---|
| `YOGURT_STD` | B còn 5 ngày, ngân sách 432.000đ | 6,6,6,7,7,3,6 lặp hai tuần | 5,5,5,6,6,2,5 lặp hai tuần | 7,7,7,8,8,4,7 lặp hai tuần |
| `YOGURT_6D` | B còn 6 ngày | như trên | như trên | như trên |
| `YOGURT_BUDGET_400K` | B 6 ngày, ngân sách 400.000đ | như trên | như trên | như trên |
| `YOGURT_PENDING` | Hạn B chưa xác nhận | như trên | như trên | như trên |

`data/demo/yogurt_56d.csv` cho ra đúng ba dãy này: ở mỗi thứ, P25/P50/P75 của các ngày `in_stock` bằng mức Chậm/Cơ sở/Nhanh của thứ đó.

## Phụ lục B. Danh mục fact

Java sinh fact, Groq nhận `{id, value, unit}` và chỉ được trích id. Một lượt có CSV gửi khoảng 70 fact, không có CSV khoảng 40. Tên theo dạng `F_<đối tượng>_<chỉ số>_<kịch bản>`; S là SLOW, BASE hoặc FAST; DOW là MON–SUN.

| Fact ID | Đơn vị | Khi nào có |
|---|---|---|
| `F_DAYS_TOTAL`, `F_DAYS_IN_STOCK`, `F_DAYS_STOCKOUT`, `F_DAYS_UNKNOWN` | ngày | Nguồn csv |
| `F_DOW_DAYS`, `F_DOW_STOCKOUT_DAYS` (ví dụ `F_SAT_STOCKOUT_DAYS`) | ngày | Nguồn csv |
| `F_DOW_AVG_IN_STOCK` | hũ/ngày, 1 chữ số thập phân | Nguồn csv |
| `F_TREND_4W_PCT` | % (4 tuần gần nhất so với 4 tuần trước, ngày in_stock) | Nguồn csv và đủ 56 ngày |
| `F_ANOMALY_COUNT`, `F_ANOMALY_MAX_UNITS` | ngày, hũ | Nguồn csv và có ngày vượt P75 + 1,5×IQR cùng thứ |
| `F_DEMAND_14D_S` | hũ | Luôn có |
| `F_A_COST`, `F_B_COST`, `F_BUDGET` | VND | Luôn có |
| `F_A_SOLD_S`, `F_B_SOLD_S`, `F_A_EXPIRED_S`, `F_B_EXPIRED_S`, `F_A_SHORT_S`, `F_B_SHORT_S` | hũ | Luôn có |
| `F_A_MARGIN_S`, `F_B_MARGIN_S`, `F_MARGIN_GAP_S` | VND | Luôn có |
| `F_B_OVER_BUDGET` | VND (tổng giá B trừ ngân sách) | Khi B vượt ngân sách |
| `F_BREAKEVEN_TOTAL_S`, `F_MAX_WIN_TOTAL_S`, `F_B_MIN_DAYS_S` | VND, VND, ngày | Khi A đủ điều kiện làm mốc |
