CREATE TABLE IF NOT EXISTS request_code_sequences (
    year INT PRIMARY KEY,
    last_number INT NOT NULL DEFAULT 0 CHECK (last_number >= 0)
);

CREATE TABLE IF NOT EXISTS service_requests (
    id UUID PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    requester_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    category VARCHAR(40) NOT NULL CHECK (
        category IN ('TRAMITE', 'ORIENTACION', 'ATENCION_ADMINISTRATIVA', 'REQUERIMIENTO_SERVICIO')
    ),
    subject VARCHAR(100) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (
        status IN ('ABIERTA', 'EN_REVISION', 'ESCALADA', 'RESUELTA', 'CERRADA', 'CANCELADA')
    ),
    previous_request_id UUID NULL REFERENCES service_requests(id) ON DELETE RESTRICT,
    previous_request_code VARCHAR(20) NULL,
    assigned_to UUID NULL REFERENCES users(id) ON DELETE RESTRICT,
    priority VARCHAR(10) NULL CHECK (priority IS NULL OR priority IN ('ALTA', 'MEDIA', 'BAJA')),
    first_assigned_at TIMESTAMPTZ NULL,
    assigned_at TIMESTAMPTZ NULL,
    due_at TIMESTAMPTZ NULL,
    response_text VARCHAR(2000) NULL,
    responded_at TIMESTAMPTZ NULL,
    responded_within_sla BOOLEAN NULL,
    closed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sr_requester_created
    ON service_requests (requester_id, created_at DESC, id ASC);

CREATE INDEX IF NOT EXISTS idx_sr_requester_status_created
    ON service_requests (requester_id, status, created_at DESC, id ASC);

CREATE INDEX IF NOT EXISTS idx_sr_status_created
    ON service_requests (status, created_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_sr_assigned_due
    ON service_requests (assigned_to, status, due_at);

CREATE TABLE IF NOT EXISTS request_history (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES service_requests(id) ON DELETE RESTRICT,
    actor_id UUID NULL REFERENCES users(id) ON DELETE RESTRICT,
    actor_role VARCHAR(30) NOT NULL CHECK (
        actor_role IN ('SOLICITANTE', 'PERSONAL_ATENCION', 'COORDINADOR', 'SISTEMA')
    ),
    action VARCHAR(30) NOT NULL CHECK (
        action IN (
            'CREACION', 'CANCELACION', 'ASIGNACION', 'INTERVENCION',
            'ESCALAMIENTO', 'REASIGNACION', 'RESPUESTA', 'CIERRE_AUTOMATICO'
        )
    ),
    previous_state VARCHAR(20) NULL,
    new_state VARCHAR(20) NOT NULL CHECK (
        new_state IN ('ABIERTA', 'EN_REVISION', 'ESCALADA', 'RESUELTA', 'CERRADA', 'CANCELADA')
    ),
    comment VARCHAR(2000) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rh_request_created
    ON request_history (request_id, created_at ASC, id ASC);
