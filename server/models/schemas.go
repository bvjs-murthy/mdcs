package models

import "time"

// Have entities: user, workspace, device

/*
user have properties:
		user_id			(primary key)
		username
		email
		password
		verified

workspace is an imaginary container that stores the information about devices of
a particular user.
		workspace_id	(primary key)
		workspace_name
		main_device

A user may have multiple workspaces, but later in the application, may assume
single workspace for simplicity in v1.

device have properties:
		device_id		(primary key)
		device_name
*/

type OTP struct {
	OTP  string    `json:"otp"`
	Sent time.Time `json:"sent_at"`
}

type AuthToken struct {
	Token   string    `json:"token"`
	Created time.Time `json:"created_at"`
}

type UserAttrs struct {
	Username   string    `json:"username"`
	Email      string    `json:"email"`
	Password   string    `json:"password"`
	Otp        OTP       `json:"verification"`
	RefreshTok AuthToken `json:"refresh_token"`
	Phase      string    `json:"status"`
}

// string(user_id) -> user attributes
type Users map[string]UserAttrs

type WorkspaceAttrs struct {
	WId        string `json:"wid"`
	WName      string `json:"wname"`
	MainDevice string `json:"main_device"`
}

// string(user_id) -> array of workspaces
type Workspaces map[string][]WorkspaceAttrs

type DeviceAttrs struct {
	DId   string `json:"did"`
	DName string `json:"dname"`
}

// string(workspace_id) -> array of devices
type Devices map[string][]DeviceAttrs
