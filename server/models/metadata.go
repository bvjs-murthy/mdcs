package models

import "time"

// version_metadata DTO

type Range struct {
	Min string `json:"min"`
	Max string `json:"max"`
}

type Plugin struct {
	AvailableVersion        string `json:"available_version"`
	MinimumSupportedVersion string `json:"minimum_supported_version"`
	ReleaseDate             string `json:"release_date"`
	CompatibleAppVersions   Range  `json:"compatible_app_versions"`
}

type App struct {
	LatestVersion           string `json:"latest_version"`
	MinimumSupportedVersion string `json:"minimum_supported_version"`
	ReleaseDate             string `json:"release_date"`
}

// Main DTO
type VerData struct {
	App     App               `json:"app"`
	Plugins map[string]Plugin `json:"plugins"`
	Changes []string          `json:"changes"`
	Fetched time.Time
}
