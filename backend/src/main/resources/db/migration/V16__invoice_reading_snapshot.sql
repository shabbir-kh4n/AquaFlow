-- Snapshot of the meter reading values (not just the derived consumption delta) behind an
-- invoice's billed period, so the invoice PDF can show "previous reading -> current reading"
-- the way a physical utility bill does. Nullable: historic invoices finalized before this column
-- existed have no snapshot to backfill, and a household with genuinely no readings that month
-- (a zero-usage invoice) also has nothing to show here.
ALTER TABLE invoices
    ADD COLUMN previous_reading_value NUMERIC(12, 3),
    ADD COLUMN current_reading_value NUMERIC(12, 3);
