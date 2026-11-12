# Kiểm thử backend MVP

Môi trường chạy lần đầu: máy dev Windows 11, Temurin 21.0.13+9, PostgreSQL 18.6 (bản zip, chỉ nghe 127.0.0.1), Groq `openai/gpt-oss-20b` với khóa free tier, ngày 12/11/2026. Kết quả trên VPS ghi thêm ở cuối khi chạy (việc 10/11).

## Cách chạy

```bash
cd api && ./mvnw verify                                  # 63 test, không cần DB hay Groq
python3 scripts/smoke_test.py http://localhost:8080      # cần app + DB đang chạy; có GROQ_API_KEY thì thử cả Groq
```

`smoke_test.py` chỉ dùng thư viện chuẩn Python, gửi đúng dữ liệu giả lập trong `data/demo/`, in PASS/FAIL từng ca và thoát mã 1 nếu có ca sai. Ca phụ thuộc Groq chấp nhận cả `fallback` (bảng không được phụ thuộc Groq); dòng cuối cho biết bao nhiêu câu trả lời thật sự đến từ Groq.

## Ca nghiệm thu và nơi kiểm

| Mã | Kỳ vọng (Spec) | Test tự động (`api/src/test`) | Smoke test | Kết quả 12/11 |
|---|---|---|---|---|
| AC-01 | B ≤ A hoặc hạn 15 ngày → chỉ đúng trường sai; hạn chưa xác nhận vẫn hợp lệ | `AnalyzeRequestReaderTest` | "AC-01 …" | PASS |
| AC-02 | CSV: chỉ in_stock vào mốc, trùng ngày báo dòng, thiếu mẫu chuyển nhập tay | `CsvForecastTest`, `ApiFlowTest.csvErrors` | "CSV demand equals A.5", "AC-02 …" | PASS |
| AC-03 | A 76.000đ; B 5 ngày 48.000đ, 6 ngày 93.000đ; ngân sách 400.000đ chặn B | `PurchaseCalculatorTest`, `AnalysisServiceTest` | "YOGURT_STD …", "YOGURT_6D …", "YOGURT_BUDGET_400K …" | PASS, khớp bảng A.2 |
| AC-04 | Hòa 76.000đ ở 404.000đ, chọn A vì chi ít hơn | `PurchaseCalculatorTest.tieAtBreakEvenPicksCheaperA` | "AC-04 tie …" | PASS |
| AC-05 | Chờ xác nhận cần ≥6 ngày; ngưỡng 404.000đ, giá trần 403.999đ; ngân sách 400.000đ → giá trần 400.000đ | `PurchaseCalculatorTest`, `TemplateCatalogTest.ac05ConditionsInWords` | "YOGURT_PENDING …", "AC-05 via chat …" | PASS |
| AC-06 | Sửa và tính lại; Đặt lại xóa phiên | `ApiFlowTest.resetMakesOldIdsStale` | "POST /api/session/reset …", "after reset …" | PASS |
| AC-07 | HTTPS, health 200, ca PostgreSQL, CSV qua Java, phiên mới trống | — | toàn bộ smoke test chạy từ ngoài VPS | Chưa chạy (chờ VPS) |
| AC-08a | Chat bám lượt tính: 5 ngày chọn A, 6 ngày chọn B | `GroqFlowTest.strictPathAnswersFromJavaNumbers`, `TemplateCatalogTest.ac08a…` | "AC-08a …" | PASS, Groq thật trả `ok` |
| AC-08b | Tool loop: kiểm schema và phiên, tool chỉ đọc, đúng `tool_call_id` | `GroqFlowTest.toolLoop…`, `unknownToolOrExtraArgumentsAreRefused`, `secondToolRoundIsRefused` | "AC-08b …" | PASS, Groq thật gọi `get_analysis_facts(FAST)` |
| AC-08c | Timeout, 429, JSON sai, fact không tồn tại → bảng giữ nguyên, chat dùng mẫu | `GroqFlowTest.timeout…`, `rateLimit…`, `wrongJson…`, `serverError…`; `ApiFlowTest` (Groq tắt) | — | PASS (stub) |
| AC-08d | analysisId của phiên A gọi từ phiên B → từ chối, không gửi Groq; thử lại sau reset/hết hạn | `ApiFlowTest.chatAndInsightAreScopedToTheSession`, `GroqFlowTest.resetDuringToolLoopStopsBeforeFactsLeave` | "AC-08d …" | PASS |
| AC-08e | CSV chứa chỉ thị bị báo lỗi hợp đồng; payload Groq chỉ có fact | `GroqFlowTest.payloadHoldsOnlyFactsAndTheQuestion`, `questionIsCutTo500CharactersAndTagsRemoved` | "AC-02 / AC-08e broken CSV …", "prompt injection …" | PASS |
| NFR-01 | ≤3 giây cho mỗi lượt tính | — | dòng "timings" | Local: lâu nhất 1,16 giây (gồm cả chat) |
| NFR-02 | 1 MiB / 366 dòng → 413; CSV không lưu | `ApiFlowTest.csvErrors` | "NFR-02 …" | PASS |
| NFR-05 | DB tắt: health 503, demo 503 `DB_UNAVAILABLE`, CSV vẫn tính | `ApiFlowTest.validationAndDemoErrors` (503) | tắt PostgreSQL thủ công | PASS (thử tay 12/11) |

## Ghi chú khi thử Groq thật (12/11)

- Hạn mức khóa: 8.000 token/phút, 1.000 request/ngày (header `x-ratelimit-*`). Đo được: chat ~1.000 token, tool loop ~1.800, insight ~1.850. Chạy liên tục nhiều lượt thử trong một phút sẽ gặp 429 và app tự trả mẫu; đây là hành vi đúng của AC-08c.
- Độ trễ Groq: chat 0,3–1,8 giây, insight 1,1–3,2 giây.
- Hai lỗi tìm thấy và đã sửa: (1) tool loop bị Groq trả 400 `tool_use_failed` khi request đầu có luật trả lời, xem D-12; (2) lỗi "B ≤ A" bị che khi hạn của B sai, xem AC-01.

## Kết quả trên VPS

Điền khi chạy việc 10/11: URL, commit, thời điểm, người chạy, người rà, kết quả `smoke_test.py`.
