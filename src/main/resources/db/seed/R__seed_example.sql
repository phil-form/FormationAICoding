-- Données de seed (dev uniquement, profil "seed" via ./start.sh --seed).
-- Migration répétable : réappliquée quand son contenu change, doit donc être idempotente
-- (INSERT ... ON CONFLICT DO NOTHING, ou TRUNCATE préalable des tables concernées).
SELECT 1;
