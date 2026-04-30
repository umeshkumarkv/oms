-- Fix seed user password hashes — previous hash in V2 did not match Demo@1234.
-- New hash generated with BCrypt strength 10 and verified against Demo@1234.
UPDATE auth_schema.users
SET password_hash = '$2b$10$RZqbdGxbVlkbWNIDIHd9x.GIQB5frOvhzhWHcksVh.MxA/h64f7X6';
