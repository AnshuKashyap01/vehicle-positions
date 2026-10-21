package main

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewGTFSRuntime_LoadsIndex(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	rt, err := newGTFSRuntime(ctx, "rider/testdata/fixture.zip", time.Hour, nil)
	require.NoError(t, err)
	defer rt.Stop()
	assert.Equal(t, 3, rt.Index().Stats().Trips)
	assert.Equal(t, 2, rt.Index().Stats().Routes)
	assert.Same(t, rt.refresher.Current(), rt.Index())
}

func TestNewGTFSRuntime_FailsWhenTheFeedIsMissing(t *testing.T) {
	_, err := newGTFSRuntime(context.Background(), "does/not/exist.zip", time.Hour, nil)
	assert.Error(t, err)
}

func TestNewGTFSRuntime_StopReturns(t *testing.T) {
	rt, err := newGTFSRuntime(context.Background(), "rider/testdata/fixture.zip", time.Hour, nil)
	require.NoError(t, err)
	done := make(chan struct{})
	go func() { rt.Stop(); close(done) }()
	select {
	case <-done:
	case <-time.After(5 * time.Second):
		t.Fatal("Stop did not return")
	}
}

// fakeGTFSCache is an in-memory gtfsFeedCache.
type fakeGTFSCache struct {
	feed  *CachedGTFSFeed
	saves int
}

func (c *fakeGTFSCache) LoadGTFSFeed(context.Context) (*CachedGTFSFeed, error) {
	if c.feed == nil {
		return nil, ErrNoCachedGTFSFeed
	}
	return c.feed, nil
}

func (c *fakeGTFSCache) SaveGTFSFeed(_ context.Context, source string, body []byte) error {
	c.saves++
	c.feed = &CachedGTFSFeed{Source: source, Body: body, FetchedAt: time.Now()}
	return nil
}

func TestNewGTFSRuntime_CachesTheFeedItLoads(t *testing.T) {
	cache := &fakeGTFSCache{}
	rt, err := newGTFSRuntime(context.Background(), "rider/testdata/fixture.zip", time.Hour, cache)
	require.NoError(t, err)
	defer rt.Stop()

	want, err := os.ReadFile("rider/testdata/fixture.zip")
	require.NoError(t, err)
	require.NotNil(t, cache.feed)
	assert.Equal(t, "rider/testdata/fixture.zip", cache.feed.Source)
	assert.Equal(t, want, cache.feed.Body)
}

// TestNewGTFSRuntime_StartsOnCacheWhenTheFeedIsDown is the outage the cache
// exists for: the GTFS host fails at startup, and the server starts on the
// last copy it loaded rather than exiting.
func TestNewGTFSRuntime_StartsOnCacheWhenTheFeedIsDown(t *testing.T) {
	down := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
	}))
	defer down.Close()
	source := down.URL + "/gtfs.zip"
	body, err := os.ReadFile("rider/testdata/fixture.zip")
	require.NoError(t, err)
	cachedAt := time.Date(2026, 10, 1, 12, 0, 0, 0, time.UTC)
	cache := &fakeGTFSCache{feed: &CachedGTFSFeed{Source: source, Body: body, FetchedAt: cachedAt}}

	rt, err := newGTFSRuntime(context.Background(), source, time.Hour, cache)
	require.NoError(t, err)
	defer rt.Stop()
	assert.Equal(t, 3, rt.Index().Stats().Trips)
	assert.Equal(t, cachedAt, rt.Index().Stats().LoadedAt, "the index says how old the schedule is")
	assert.Zero(t, cache.saves, "a failed download must not overwrite the cache")
}

func TestNewGTFSRuntime_IgnoresCacheOfAnotherSource(t *testing.T) {
	body, err := os.ReadFile("rider/testdata/fixture.zip")
	require.NoError(t, err)
	cache := &fakeGTFSCache{feed: &CachedGTFSFeed{Source: "https://old.example/gtfs.zip", Body: body}}

	_, err = newGTFSRuntime(context.Background(), "does/not/exist.zip", time.Hour, cache)
	require.Error(t, err)
	assert.Contains(t, err.Error(), "the cached copy is of https://old.example/gtfs.zip")
}

func TestNewGTFSRuntime_FailsWithNoCachedCopy(t *testing.T) {
	_, err := newGTFSRuntime(context.Background(), "does/not/exist.zip", time.Hour, &fakeGTFSCache{})
	require.Error(t, err)
	assert.ErrorIs(t, err, os.ErrNotExist)
}
