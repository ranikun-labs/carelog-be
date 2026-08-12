-- Product Customer API의 메모만 추가한다.
-- Customer는 계속 users.role=CUSTOMER 행이며, 기존 데이터는 backfill하지 않고 NULL을 유지한다.
ALTER TABLE users ADD COLUMN customer_memo text;
