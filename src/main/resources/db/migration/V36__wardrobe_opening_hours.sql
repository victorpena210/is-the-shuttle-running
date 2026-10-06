ALTER TABLE wardrobe_hours_rules
    ADD COLUMN opening_time TIME NULL,
    ADD COLUMN day_of_week INT NULL,
    ADD CONSTRAINT chk_wardrobe_weekday CHECK (day_of_week IS NULL OR day_of_week BETWEEN 1 AND 7);
-- Existing closing-only rules remain valid until a manager saves their opening hours.
