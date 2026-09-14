-- Have entities: users, workspaces, devices

-- devices have properties:
--  workspace_id    (foreign key)
-- 	device_id		(primary key)
-- 	device_name

-- workspaces is an imaginary container that stores information about devices of
-- a particular users. A users may have multiple workspaces.

CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password TEXT NOT NULL,
    phase VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED'
);

CREATE TABLE workspaces (
    workspace_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    workspace_name VARCHAR(100) NOT NULL,
    main_device UUID,

    CONSTRAINT fk_workspace_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON DELETE CASCADE,

    CONSTRAINT uq_workspace_user_name
        UNIQUE (user_id, workspace_name)
);

CREATE TABLE devices (
    device_id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    device_name VARCHAR(100) NOT NULL,

    CONSTRAINT fk_device_workspace
        FOREIGN KEY (workspace_id)
        REFERENCES workspaces(workspace_id)
        ON DELETE CASCADE,

    CONSTRAINT uq_device_workspace_name
        UNIQUE (workspace_id, device_name)
);

ALTER TABLE workspaces
ADD CONSTRAINT fk_workspace_main_device
    FOREIGN KEY (main_device)
    REFERENCES devices(device_id)
    ON DELETE SET NULL;

CREATE TABLE otp (
    user_id UUID PRIMARY KEY,
    otp TEXT NOT NULL,
    sent_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_otp_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON DELETE CASCADE
);


CREATE TABLE auth_tokens (
    user_id UUID PRIMARY KEY,
    token TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_auth_token_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON DELETE CASCADE
);