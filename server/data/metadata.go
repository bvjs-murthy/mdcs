package data

import (
	"encoding/json"
	"fmt"
	"io"
	"mdcs-server/models"
	"net/http"
	"os"
	"time"
)

var Metadata *models.VerData

// Server startup must fail if metadata server is unreachable
func GetMetadata() error {
	url := os.Getenv("VERSION_DATA_URL")

	if url == "" {
		return fmt.Errorf("error: VERSION_DATA_URL not found")
	}

	client := http.Client{
		Timeout: 10 * time.Second,
	}

	res, err := client.Get(url)

	if err != nil {
		return err
	}

	defer res.Body.Close()

	if res.StatusCode != http.StatusOK {
		return fmt.Errorf("error: Bad status code: %d", res.StatusCode)
	}

	data, err := io.ReadAll(res.Body)

	if err != nil {
		return err
	}

	err = json.Unmarshal(data, &Metadata)

	if err != nil {
		return err
	}

	// Used for later cases when may want to load metadata at regular intervals
	Metadata.Fetched = time.Now()

	return nil
}
