package version

// Incoming data DTO
type UpdateCheckRequest struct {
	App     map[string]string `json:"app"`
	Plugins map[string]string `json:"plugins"`
}

// Response DTO
type AppData struct {
	CurrentVersion   string `json:"current_version"`
	AvailableVersion string `json:"available_version"`
	CriticalUpdate   bool   `json:"critical_update"`
}

type PluginData struct {
	InstalledVersion string `json:"installed_version"`
	AvailableVersion string `json:"available_version"`
	IsCompatible     bool   `json:"is_compatible"`
	UpdateRequired   bool   `json:"update_required"`
}

type UpdateCheckResponse struct {
	App     AppData               `json:"app"`
	Plugins map[string]PluginData `json:"plugins"`
	Changes []string              `json:"changes"`
}
