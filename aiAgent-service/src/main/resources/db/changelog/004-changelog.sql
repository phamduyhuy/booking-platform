-- liquibase formatted sql

-- changeset PhamDuyHuy:1762244613243-2
ALTER TABLE chat_message
    ADD json_content TEXT;

-- changeset PhamDuyHuy:1762244613243-1
ALTER TABLE chat_message
    ALTER COLUMN content DROP NOT NULL;

