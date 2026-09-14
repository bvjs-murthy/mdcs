package modules

import (
	"fmt"
	"mdcs-server/modules/auth"
	"mdcs-server/modules/version"
	"net/http"
)

func Router(mux *http.ServeMux) {
	mux.HandleFunc("/ping", func(res http.ResponseWriter, req *http.Request) {
		fmt.Fprintf(res, "Hello, there!\n")
	})

	var auth_mux *http.ServeMux = http.NewServeMux()
	auth.Routes(auth_mux)

	var ver_mux *http.ServeMux = http.NewServeMux()
	version.Routes(ver_mux)

	mux.Handle("/auth/", http.StripPrefix("/auth", auth_mux))
	mux.Handle("/version/", http.StripPrefix("/version", ver_mux))
}
