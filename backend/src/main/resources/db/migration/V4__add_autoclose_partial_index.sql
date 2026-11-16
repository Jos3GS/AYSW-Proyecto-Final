CREATE INDEX IF NOT EXISTS idx_sr_autoclose
    ON service_requests (responded_at ASC, id ASC)
    WHERE status = 'RESUELTA';
