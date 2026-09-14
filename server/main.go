package main

// Main Server for MDCS

import (
	"fmt"
	"log"
	"mdcs-server/core/bootstrap"
	"mdcs-server/data"
	"mdcs-server/modules"
	"net/http"
)

func main() {
	if !bootstrap.Run() {
		return
	}

	db, err := data.Connect()

	if err != nil {
		log.Fatal("Failed to connect to database:", err)
	}

	defer db.Close()

	log.Println("Connected to database successfully.")

	mux := http.NewServeMux()
	modules.Router(mux)

	app := http.StripPrefix("/mdcs", mux)

	// Port should be moved to .env and is imported at run-time
	fmt.Println("process: Server listening at :1800")
	http.ListenAndServe("0.0.0.0:1800", app)
}
