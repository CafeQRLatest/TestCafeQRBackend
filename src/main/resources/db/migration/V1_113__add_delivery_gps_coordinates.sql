-- Flyway migration: V1_113__add_delivery_gps_coordinates.sql
-- Add latitude and longitude columns to orders table to store delivery location coordinates.

ALTER TABLE orders ADD COLUMN latitude numeric(10, 8);
ALTER TABLE orders ADD COLUMN longitude numeric(11, 8);
