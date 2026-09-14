package auth

import "net/http"

func Routes(mux *http.ServeMux) {
	mux.HandleFunc("/user/signup", validateCreds(register))
	mux.HandleFunc("/user/verify-otp", requireOtp(verifyUsr))
	mux.HandleFunc("/user/login", loginCreds(login))
	mux.HandleFunc("/device/first-enroll", firEnrollDetails(firEnroll))
	mux.HandleFunc("/device/additional-enroll", addEnrollDetails(addEnroll))
}
