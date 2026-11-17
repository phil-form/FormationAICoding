-- Schéma SQLite de référence step-00 (PHP 7.2 / Symfony 2.8 / Doctrine 2.5), capturé le 2026-11-17 par :
--   .claude/bin/console.sh doctrine:schema:create --dump-sql
-- Ne jamais modifier : toute dérive du schéma pendant la migration est détectée par les tests de base.
CREATE TABLE reservation (id INTEGER NOT NULL, vehicle_id INTEGER NOT NULL, driver_name VARCHAR(100) NOT NULL, start_at DATETIME NOT NULL, end_at DATETIME NOT NULL, status VARCHAR(20) NOT NULL, start_km INTEGER NOT NULL, end_km INTEGER DEFAULT NULL, cost NUMERIC(10, 2) DEFAULT NULL, PRIMARY KEY(id), CONSTRAINT FK_42C84955545317D1 FOREIGN KEY (vehicle_id) REFERENCES vehicle (id) NOT DEFERRABLE INITIALLY IMMEDIATE);
CREATE INDEX IDX_42C84955545317D1 ON reservation (vehicle_id);
CREATE TABLE vehicle (id INTEGER NOT NULL, plate VARCHAR(20) NOT NULL, brand VARCHAR(50) NOT NULL, model VARCHAR(50) NOT NULL, mileage INTEGER NOT NULL, status INTEGER NOT NULL, created_at DATETIME NOT NULL, PRIMARY KEY(id));
CREATE UNIQUE INDEX UNIQ_1B80E486719ED75B ON vehicle (plate);
