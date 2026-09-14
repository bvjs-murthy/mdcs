package version

import (
	"encoding/json"
	"io"
	"net/http"
)

// Still need to improve the error handling and write responses accordingly

/*
Version check is intentionally kept without authentication so that users can check for
updates or compatibilities
*/
func versionCheck(res http.ResponseWriter, req *http.Request) {
	data, err := io.ReadAll(req.Body)

	if err != nil {
		return
	}

	var update_check_data UpdateCheckRequest
	err = json.Unmarshal(data, &update_check_data)

	if err != nil {
		return
	}

	response, err := responseBuilder(update_check_data)
	if err != nil {
		return
	}

	json_data, err := json.Marshal(response)
	if err != nil {
		return
	}

	res.Header().Set("Content-Type", "application/json")
	res.Write(json_data)
}
