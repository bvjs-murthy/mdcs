package bootstrap

// Manages startup process of the application

import (
	"fmt"
	"mdcs-server/data"
)

/*
Bootstrap includes the phases:
- Load .env file globally
- Fetch the latest verion metadata and cache it
- Check persistence files existence and validate their format and schema
*/

func Run() bool {
	fmt.Println("process: Starting server bootstrap")
	err := env()

	if err != nil {
		fmt.Println("fatal: .env file can't be located or loaded")
		return false
	}

	err = data.GetMetadata()

	if err != nil {
		fmt.Println("fatal: Could not load version metadata")
		return false
	}

	return true
}
