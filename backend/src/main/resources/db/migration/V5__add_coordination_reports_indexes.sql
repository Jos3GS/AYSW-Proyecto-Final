CREATE INDEX IF NOT EXISTS idx_sr_created_desc
    ON service_requests (created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_sr_report_filters
    ON service_requests (status, category, priority, assigned_to, created_at DESC);
