package main

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/OneBusAway/vehicle-positions/db"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
)

// TokenRevoker records a token's jti so it is rejected for the rest of its
// lifetime. Kept separate from TokenChecker so the logout handler depends on
// the write and the auth middleware only on the read.
type TokenRevoker interface {
	RevokeToken(ctx context.Context, jti string, userID int64, expiresAt time.Time) error
}

// TokenChecker reports whether a token's jti has been revoked.
type TokenChecker interface {
	IsTokenRevoked(ctx context.Context, jti string) (bool, error)
}

// UserActiveChecker reports whether the user with the given ID still has an
// active account. requireRoles calls it after the JTI revocation check so
// access tokens issued before a deactivation are rejected immediately rather
// than remaining valid until the JWT's natural expiry.
type UserActiveChecker interface {
	IsUserActive(ctx context.Context, userID int64) (bool, error)
}

// RevokeToken adds a jti to the revocation list. It is idempotent, so logging
// out twice with the same token succeeds both times.
//
// A revocation row deliberately outlives the user it belongs to. user_id is
// ON DELETE SET NULL rather than CASCADE because nothing in the request path
// consults the users table — requireAuth and adminClaimsFromCookie decide on
// the blocklist alone — so cascading the row away on a hard user delete would
// make an already-revoked token valid again for the rest of its lifetime,
// carrying its original role claim. The row's job is to block a jti until it
// expires, and that job does not end when the account does.
//
// expires_at is recorded so revocation rows can be aged out, but nothing
// deletes them yet: this table grows one row per logout. A periodic cleanup
// job (DELETE FROM revoked_tokens WHERE expires_at < NOW()) is needed as a
// follow-up, and is the only thing that should ever remove a row.
func (s *Store) RevokeToken(ctx context.Context, jti string, userID int64, expiresAt time.Time) error {
	err := s.queries.RevokeToken(ctx, db.RevokeTokenParams{
		Jti:       jti,
		UserID:    pgtype.Int8{Int64: userID, Valid: true},
		ExpiresAt: pgtype.Timestamptz{Time: expiresAt, Valid: true},
	})
	if err != nil {
		return fmt.Errorf("revoke token: %w", err)
	}
	return nil
}

// IsTokenRevoked returns true if the jti has been revoked.
func (s *Store) IsTokenRevoked(ctx context.Context, jti string) (bool, error) {
	revoked, err := s.queries.IsTokenRevoked(ctx, jti)
	if err != nil {
		return false, fmt.Errorf("check token revocation: %w", err)
	}
	return revoked, nil
}

// IsUserActive reports whether the user with the given ID exists and has an
// active account. It is called by requireRoles on every authenticated request
// so access tokens belonging to a newly deactivated user are rejected before
// their JWT expiry rather than after it.
func (s *Store) IsUserActive(ctx context.Context, userID int64) (bool, error) {
	var active bool
	err := s.pool.QueryRow(ctx,
		`SELECT active FROM users WHERE id = $1`, userID).Scan(&active)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return false, nil // deleted user: treat as inactive
		}
		return false, fmt.Errorf("check user active %d: %w", userID, err)
	}
	return active, nil
}

var _ TokenRevoker = (*Store)(nil)
var _ TokenChecker = (*Store)(nil)
var _ UserActiveChecker = (*Store)(nil)
