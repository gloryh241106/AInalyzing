-- Synthetic demo cases (Specification Phụ lục A.5). D = Friday 2026-11-13, demand repeats by weekday.
INSERT INTO demo_cases (case_code, title, request_json, demand_slow, demand_base, demand_fast) VALUES
('YOGURT_STD', 'Sữa chua: B còn 5 ngày, ngân sách 432.000đ (synthetic)',
 '{"sku":"YOGURT-100G","receiveDate":"2026-11-13","sellPriceVnd":15000,"budgetVnd":432000,"openingStock":{"units":0,"sellableDays":null},"quoteA":{"units":24,"goodsVnd":284000,"deliveryVnd":0,"sellableDays":5},"quoteB":{"units":48,"goodsVnd":432000,"deliveryVnd":0,"sellableDays":5},"demandSource":"demo","manualDemand":null,"demoCaseCode":"YOGURT_STD"}',
 '{5,5,5,6,6,2,5,5,5,5,6,6,2,5}', '{6,6,6,7,7,3,6,6,6,6,7,7,3,6}', '{7,7,7,8,8,4,7,7,7,7,8,8,4,7}'),
('YOGURT_6D', 'Sữa chua: B còn 6 ngày (synthetic)',
 '{"sku":"YOGURT-100G","receiveDate":"2026-11-13","sellPriceVnd":15000,"budgetVnd":432000,"openingStock":{"units":0,"sellableDays":null},"quoteA":{"units":24,"goodsVnd":284000,"deliveryVnd":0,"sellableDays":5},"quoteB":{"units":48,"goodsVnd":432000,"deliveryVnd":0,"sellableDays":6},"demandSource":"demo","manualDemand":null,"demoCaseCode":"YOGURT_6D"}',
 '{5,5,5,6,6,2,5,5,5,5,6,6,2,5}', '{6,6,6,7,7,3,6,6,6,6,7,7,3,6}', '{7,7,7,8,8,4,7,7,7,7,8,8,4,7}'),
('YOGURT_BUDGET_400K', 'Sữa chua: B 6 ngày, ngân sách 400.000đ (synthetic)',
 '{"sku":"YOGURT-100G","receiveDate":"2026-11-13","sellPriceVnd":15000,"budgetVnd":400000,"openingStock":{"units":0,"sellableDays":null},"quoteA":{"units":24,"goodsVnd":284000,"deliveryVnd":0,"sellableDays":5},"quoteB":{"units":48,"goodsVnd":432000,"deliveryVnd":0,"sellableDays":6},"demandSource":"demo","manualDemand":null,"demoCaseCode":"YOGURT_BUDGET_400K"}',
 '{5,5,5,6,6,2,5,5,5,5,6,6,2,5}', '{6,6,6,7,7,3,6,6,6,6,7,7,3,6}', '{7,7,7,8,8,4,7,7,7,7,8,8,4,7}'),
('YOGURT_PENDING', 'Sữa chua: hạn B chưa xác nhận (synthetic)',
 '{"sku":"YOGURT-100G","receiveDate":"2026-11-13","sellPriceVnd":15000,"budgetVnd":432000,"openingStock":{"units":0,"sellableDays":null},"quoteA":{"units":24,"goodsVnd":284000,"deliveryVnd":0,"sellableDays":5},"quoteB":{"units":48,"goodsVnd":432000,"deliveryVnd":0,"sellableDays":null},"demandSource":"demo","manualDemand":null,"demoCaseCode":"YOGURT_PENDING"}',
 '{5,5,5,6,6,2,5,5,5,5,6,6,2,5}', '{6,6,6,7,7,3,6,6,6,6,7,7,3,6}', '{7,7,7,8,8,4,7,7,7,7,8,8,4,7}');
