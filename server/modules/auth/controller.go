package auth

import (
	"encoding/json"
	"mdcs-server/modules/shared"
	"mdcs-server/tools/auth"
	"net/http"
)

// Write user entry and return user id
func register(res http.ResponseWriter, req *http.Request) {
	data := req.Context().Value(SUpDataKey).(CreateUsrReq)
	var respld shared.Response

	user_id, err := createUsr(data)

	if err != nil {
		shared.ProcessErr(respld, err, res)
		return
	}

	respld.Status = true

	respld.Body = map[string]string{}
	respld.Body["user_id"] = user_id
	respld.Body["username"] = data.Username
	respld.Body["email"] = data.Email

	respld.Error = ""
	respld.Message = "Account created successfully"

	payload, err := json.Marshal(respld)

	if err != nil {
		http.Error(res, err.Error(), http.StatusInternalServerError)
		return
	}

	res.Write(payload)
}

func verifyUsr(res http.ResponseWriter, req *http.Request) {
	data := req.Context().Value(VerUsrDataKey).(ValidateUsrReq)

	var respld shared.Response

	err := verifyOtp(data)

	if err != nil {
		shared.ProcessErr(respld, err, res)
		return
	}

	auth_tok, refresh_tok, err := getAccessTokens(data.UserId)

	if err != nil {
		// This is not error, it will be automatically rectified in next user login
	}

	respld.Body = map[string]string{}
	respld.Body["auth_tok"] = auth_tok
	respld.Body["refresh_tok"] = refresh_tok

	respld.Status = true
	respld.Error = ""
	respld.Message = "Validation successful"

	payload, err := json.Marshal(respld)

	if err != nil {
		http.Error(res, err.Error(), http.StatusInternalServerError)
		return
	}

	res.Write(payload)
}

func firEnroll(res http.ResponseWriter, req *http.Request) {
	data := req.Context().Value(EnrollDeviceDataKey).(EnrollFirReq)

	var respld shared.Response

	wid, did, err := firstEnroll(data)

	if err != nil {
		shared.ProcessErr(respld, err, res)
		return
	}

	respld.Status = true

	respld.Body = map[string]string{}
	respld.Body["device_id"] = did
	respld.Body["device_name"] = data.DeviceName
	respld.Body["workspace_id"] = wid
	respld.Body["workspace_name"] = data.WorkspaceName

	respld.Error = ""
	respld.Message = "Device enrolled successfully"

	payload, err := json.Marshal(respld)

	if err != nil {
		http.Error(res, err.Error(), http.StatusInternalServerError)
		return
	}

	res.Write(payload)
}

func addEnroll(res http.ResponseWriter, req *http.Request) {
	//
}

func login(res http.ResponseWriter, req *http.Request) {
	data := req.Context().Value(LoginDataKey).(LoginReq)

	var respld shared.Response

	err, user_id, username, email, phase := loginVer(data)

	if err != nil {
		shared.ProcessErr(respld, err, res)
		return
	}

	if phase != "ONBOARDED" {
		respld.Status = true

		respld.Body = map[string]string{
			"user_id":  user_id,
			"username": username,
			"phase":    phase,
		}

		respld.Error = ""
		respld.Message = "Login defered"

		if phase == "UNVERIFIED" {
			auth.SendOtp(user_id, email)
		}

		if phase == "VERIFIED" {
			// Nothing to do, client will request for device enrollment.
		}

		payload, err := json.Marshal(respld)

		if err != nil {
			http.Error(res, err.Error(), http.StatusInternalServerError)
			return
		}

		res.Write(payload)

		return
	}

	auth_tok, refresh_tok, err := getAccessTokens(user_id)

	if err != nil {
		// This is not error, it will be automatically rectified in next user login
		// Also, user should be prompted for login next time instead of continuing
	}

	respld.Status = true

	respld.Body = map[string]string{
		"user_id":     user_id,
		"username":    username,
		"auth_tok":    auth_tok,
		"refresh_tok": refresh_tok,
		"phase":       "ONBOARDED",
	}

	respld.Error = ""
	respld.Message = "Validation successful"

	payload, err := json.Marshal(respld)

	if err != nil {
		http.Error(res, err.Error(), http.StatusInternalServerError)
		return
	}

	res.Write(payload)
}
