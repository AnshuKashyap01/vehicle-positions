package main

import (
	"bytes"
	"context"
	"crypto/sha256"
	"errors"
	"fmt"
	"time"

	"github.com/OneBusAway/vehicle-positions/db"
	"github.com/jackc/pgx/v5"
)

// ErrNoCachedGTFSFeed is returned when no GTFS feed has been cached yet.
var ErrNoCachedGTFSFeed = errors.New("no cached GTFS feed")

// CachedGTFSFeed is the last GTFS static zip that downloaded and indexed cleanly.
type CachedGTFSFeed struct {
	Source    string
	Body      []byte
	FetchedAt time.Time
}

// LoadGTFSFeed returns the cached feed, or ErrNoCachedGTFSFeed.
func (s *Store) LoadGTFSFeed(ctx context.Context) (*CachedGTFSFeed, error) {
	row, err := s.queries.GetGTFSFeedCache(ctx)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrNoCachedGTFSFeed
	}
	if err != nil {
		return nil, fmt.Errorf("load cached GTFS feed: %w", err)
	}
	return &CachedGTFSFeed{Source: row.Source, Body: row.Body, FetchedAt: row.FetchedAt.Time}, nil
}

// SaveGTFSFeed replaces the cached feed. A feed identical to the one already
// cached is not written again: a refresh downloads the same zip most days, and
// rewriting tens of megabytes each time would only churn the table.
func (s *Store) SaveGTFSFeed(ctx context.Context, source string, body []byte) error {
	sum := sha256.Sum256(body)
	current, err := s.queries.GetGTFSFeedCacheDigest(ctx)
	if err == nil && current.Source == source && bytes.Equal(current.Sha256, sum[:]) {
		return nil
	}
	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return fmt.Errorf("read cached GTFS feed digest: %w", err)
	}
	if err := s.queries.UpsertGTFSFeedCache(ctx, db.UpsertGTFSFeedCacheParams{
		Source: source,
		Body:   body,
		Sha256: sum[:],
	}); err != nil {
		return fmt.Errorf("save cached GTFS feed: %w", err)
	}
	return nil
}
