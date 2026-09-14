package bootstrap

import "github.com/joho/godotenv"

func env() error {
	err := godotenv.Load()

	if err != nil {
		return err
	}

	return nil
}
