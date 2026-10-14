-- route_id and start_date were accepted by the ingest endpoint and published in
-- the feed's TripDescriptor, but never stored, so the tracker lost them when it
-- was reseeded from location_points at startup. Defaults match driver_id's: a
-- report that named neither already means "no route context", which is exactly
-- what every pre-existing row holds.
ALTER TABLE location_points ADD COLUMN route_id TEXT NOT NULL DEFAULT '';
ALTER TABLE location_points ADD COLUMN start_date TEXT NOT NULL DEFAULT '';
