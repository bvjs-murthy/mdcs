package shared

import (
	"encoding/json"
	"net/http"
)

/*
Acts like global error handler for response writes. It is used the best when errors
are classified into extention classes of app error (parent write error).

Right now, it is just a util function that saves us from writing this common
multiple times.
*/
func ProcessErr(respld Response, err error, res http.ResponseWriter) {
	respld.Status = false
	respld.Body = nil
	respld.Error = err.Error()

	// Message can be sent through extention object, right now it is blank.
	respld.Message = ""

	payload, err := json.Marshal(respld)

	if err != nil {
		http.Error(res, err.Error(), http.StatusInternalServerError)
		return
	}

	res.Write(payload)
}
