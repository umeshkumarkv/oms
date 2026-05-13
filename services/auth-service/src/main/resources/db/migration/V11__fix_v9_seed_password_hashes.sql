-- Fix V9 seed user password hashes — SCRUM-79
-- V9 used an incorrect bcrypt hash that does not match Demo@1234.
-- Correct hash: $2b$10$RZqbdGxbVlkbWNIDIHd9x.GIQB5frOvhzhWHcksVh.MxA/h64f7X6
-- Verified against Demo@1234 with BCrypt strength 10 (same as V5).
UPDATE auth_schema.users
SET password_hash = '$2b$10$RZqbdGxbVlkbWNIDIHd9x.GIQB5frOvhzhWHcksVh.MxA/h64f7X6'
WHERE username IN ('trader01', 'trader02', 'admin01', 'viewer01')
  AND password_hash = '$2a$10$TKh8H1.PfQx37YgCzwiKb.KjNyWgaHb9cbcoQgdIXFlpb7pIgSkb2';
