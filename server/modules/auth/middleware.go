package auth

import (
	"context"
	"encoding/json"
	"errors"
	"mdcs-server/modules/shared"
	"net/http"
	"regexp"
)

/*
Checks for strenght of password, email structure: example@email.com.

conditions for password strength:
password must contain atleast one
  - uppercase
  - lowercase
  - digit
  - sp. char

password must be atleast 6 chars long
*/
func validateCreds(next http.HandlerFunc) http.HandlerFunc {
	var tests = map[*regexp.Regexp]string{
		regexp.MustCompile(`[A-Z]+`):       "MISSING_UPPERCASE",
		regexp.MustCompile(`[a-z]+`):       "MISSING_LOWERCASE",
		regexp.MustCompile(`\d+`):          "MISSING_DIGIT",
		regexp.MustCompile(`[!@#$%^&*_]+`): "MISSING_SPECIAL",
	}

	emailtest := regexp.MustCompile(`^[a-zA-Z\d._%+-]+@(([a-z]+\.)[a-z]+)$`)

	return func(res http.ResponseWriter, req *http.Request) {
		var data CreateUsrReq
		err := json.NewDecoder(req.Body).Decode(&data)
		defer req.Body.Close()

		if err != nil {
			payload, err := json.Marshal(
				shared.Response{
					Status:  false,
					Body:    nil,
					Error:   "INVALID_DATA",
					Message: "Failed to parse data",
				})

			if err != nil {
				http.Error(res, err.Error(), http.StatusInternalServerError)
				return
			}

			res.Write(payload)
			return
		}

		if len(data.Password) < 6 {
			respld := shared.Response{
				Status:  false,
				Body:    nil,
				Error:   "SHORT_PASSWORD",
				Message: "Weak password",
			}

			payload, err := json.Marshal(respld)
			if err != nil {
				http.Error(res, err.Error(), http.StatusInternalServerError)
				return
			}

			res.Write(payload)
			return
		}

		for test, err := range tests {
			if !test.MatchString(data.Password) {
				respld := shared.Response{
					Status:  false,
					Body:    nil,
					Error:   err,
					Message: "Weak password",
				}

				payload, err := json.Marshal(respld)
				if err != nil {
					http.Error(res, err.Error(), http.StatusInternalServerError)
					return
				}

				res.Write(payload)
				return
			}
		}

		if !emailtest.MatchString(data.Email) {
			respld := shared.Response{
				Status:  false,
				Body:    nil,
				Error:   "INVALID_EMAIL",
				Message: "Invalid email id",
			}

			payload, err := json.Marshal(respld)
			if err != nil {
				http.Error(res, err.Error(), http.StatusInternalServerError)
				return
			}

			res.Write(payload)
			return
		}

		con := context.WithValue(
			req.Context(),
			SUpDataKey, data,
		)

		next(res, req.WithContext(con))
	}
}

func requireOtp(next http.HandlerFunc) http.HandlerFunc {

	return func(res http.ResponseWriter, req *http.Request) {
		var data ValidateUsrReq

		err := json.NewDecoder(req.Body).Decode(&data)

		defer req.Body.Close()

		if err != nil || data.UserId == "" || data.Email == "" || data.OTP == "" {
			payload, err := json.Marshal(
				shared.Response{
					Status:  false,
					Body:    nil,
					Error:   "INVALID_DATA",
					Message: "Failed to parse data",
				})

			if err != nil {
				http.Error(res, err.Error(), http.StatusInternalServerError)
				return
			}

			res.Write(payload)
			return
		}

		con := context.WithValue(
			req.Context(),
			VerUsrDataKey, data,
		)

		next(res, req.WithContext(con))
	}
}

func enrollDetails(
	next http.HandlerFunc,
	decode func(*http.Request) (any, error),
) http.HandlerFunc {

	return func(res http.ResponseWriter, req *http.Request) {
		defer req.Body.Close()

		data, err := decode(req)

		if err != nil {
			payload, _ := json.Marshal(
				shared.Response{
					Status:  false,
					Body:    nil,
					Error:   "INVALID_DATA",
					Message: "Failed to parse data",
				},
			)

			res.Write(payload)
			return
		}

		con := context.WithValue(
			req.Context(),
			EnrollDeviceDataKey,
			data,
		)

		next(res, req.WithContext(con))
	}
}

func firEnrollDetails(next http.HandlerFunc) http.HandlerFunc {

	return enrollDetails(next, func(req *http.Request) (any, error) {
		var data EnrollFirReq

		if err := json.NewDecoder(req.Body).Decode(&data); err != nil {
			return nil, err
		}

		if data.UserId == "" ||
			data.DeviceName == "" ||
			data.WorkspaceName == "" {
			return nil, errors.New("hi")
		}

		return data, nil
	})
}

func addEnrollDetails(next http.HandlerFunc) http.HandlerFunc {

	return enrollDetails(next, func(req *http.Request) (any, error) {
		var data EnrollAddReq

		if err := json.NewDecoder(req.Body).Decode(&data); err != nil {
			return nil, err
		}

		if data.UserId == "" ||
			data.DeviceName == "" ||
			data.WorkspaceName == "" ||
			data.PairingKey == "" {
			return nil, errors.New("hello")
		}

		return data, nil
	})
}

func loginCreds(next http.HandlerFunc) http.HandlerFunc {

	return func(res http.ResponseWriter, req *http.Request) {
		var data LoginReq

		err := json.NewDecoder(req.Body).Decode(&data)

		defer req.Body.Close()

		switch {
		case err != nil,
			data.Email == "",
			data.Password == "",
			data.DeviceId == "",
			data.WorkspaceId == "":

			payload, err := json.Marshal(
				shared.Response{
					Status:  false,
					Body:    nil,
					Error:   "MISSING_DATA",
					Message: "Required data not found",
				},
			)

			if err != nil {
				http.Error(res, err.Error(), http.StatusInternalServerError)
				return
			}

			res.Write(payload)
			return
		}

		con := context.WithValue(
			req.Context(),
			LoginDataKey, data,
		)

		next(res, req.WithContext(con))
	}
}
