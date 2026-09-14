package data

import (
	"context"
	"os"

	"github.com/jackc/pgx/v5/pgxpool"
)

var Pool *pgxpool.Pool

func Connect() (*pgxpool.Pool, error) {
	var err error

	url := os.Getenv("DB_CONNECTION_STR")
	Pool, err = pgxpool.New(context.Background(), url)

	if err != nil {
		return nil, err
	}

	if err := Pool.Ping(context.Background()); err != nil {
		Pool.Close()
		Pool = nil

		return nil, err
	}

	return Pool, nil
}
