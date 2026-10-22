package main

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestStore_GTFSFeedCache_RoundTrip(t *testing.T) {
	store := newTestStore(t)
	ctx := context.Background()
	_, err := store.pool.Exec(ctx, "DELETE FROM gtfs_feed_cache")
	require.NoError(t, err)

	_, err = store.LoadGTFSFeed(ctx)
	assert.ErrorIs(t, err, ErrNoCachedGTFSFeed)

	require.NoError(t, store.SaveGTFSFeed(ctx, "https://agency.example/gtfs.zip", []byte("zip-1")))
	feed, err := store.LoadGTFSFeed(ctx)
	require.NoError(t, err)
	assert.Equal(t, "https://agency.example/gtfs.zip", feed.Source)
	assert.Equal(t, []byte("zip-1"), feed.Body)
	firstFetched := feed.FetchedAt

	// The same zip again is not rewritten, so fetched_at keeps the time the
	// cached content was first downloaded.
	require.NoError(t, store.SaveGTFSFeed(ctx, "https://agency.example/gtfs.zip", []byte("zip-1")))
	feed, err = store.LoadGTFSFeed(ctx)
	require.NoError(t, err)
	assert.Equal(t, firstFetched, feed.FetchedAt)

	require.NoError(t, store.SaveGTFSFeed(ctx, "https://agency.example/gtfs.zip", []byte("zip-2")))
	feed, err = store.LoadGTFSFeed(ctx)
	require.NoError(t, err)
	assert.Equal(t, []byte("zip-2"), feed.Body)

	var rows int
	require.NoError(t, store.pool.QueryRow(ctx, "SELECT COUNT(*) FROM gtfs_feed_cache").Scan(&rows))
	assert.Equal(t, 1, rows, "only one feed is ever cached")
}
