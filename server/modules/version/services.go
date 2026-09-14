package version

import (
	"mdcs-server/data"
	"mdcs-server/tools/version"
)

func responseBuilder(update_check_data UpdateCheckRequest) (UpdateCheckResponse, error) {
	var response UpdateCheckResponse

	app, err := criticalUpdateCheck(update_check_data)
	if err != nil {
		return UpdateCheckResponse{}, err
	}

	plugins, err := pluginCompatAndUpCheck(update_check_data)
	if err != nil {
		return UpdateCheckResponse{}, err
	}

	response.App = app
	response.Plugins = plugins
	response.Changes = data.Metadata.Changes

	return response, nil
}

/*
If client has lower app version then suggested, server APIs may be broken or may
lead to other bugs.
Application will be blocked (handled by frontend) in such case
*/
func criticalUpdateCheck(update_check_data UpdateCheckRequest) (AppData, error) {
	curr_app_ver, err := version.Parse(update_check_data.App["current_version"])
	if err != nil {
		return AppData{}, err
	}

	min_sup_ver, err := version.Parse(data.Metadata.App.MinimumSupportedVersion)
	if err != nil {
		return AppData{}, err
	}

	var app_data AppData
	app_data.CurrentVersion = update_check_data.App["current_version"]
	app_data.AvailableVersion = data.Metadata.App.LatestVersion
	app_data.CriticalUpdate = version.Lower(curr_app_ver, min_sup_ver)

	return app_data, nil
}

/*
Incompatible plugin is when current version of a plugin is not supported by the
application.
If so, that plugin may not work as expected
*/
func pluginCompatAndUpCheck(update_check_data UpdateCheckRequest) (map[string]PluginData, error) {
	plugins := make(map[string]PluginData)

	curr_app_semver, err := version.Parse(update_check_data.App["current_version"])
	if err != nil {
		return nil, err
	}

	for plugin_name, installed_ver := range update_check_data.Plugins {
		var plugin_res PluginData

		plugin_meta, exists := data.Metadata.Plugins[plugin_name]

		if !exists {
			continue
		}

		installed_semver, err := version.Parse(installed_ver)
		if err != nil {
			return nil, err
		}

		available_semver, err := version.Parse(plugin_meta.AvailableVersion)
		if err != nil {
			return nil, err
		}

		min_compat_semver, err := version.Parse(plugin_meta.CompatibleAppVersions.Min)
		if err != nil {
			return nil, err
		}

		max_compat_semver, err := version.Parse(plugin_meta.CompatibleAppVersions.Max)
		if err != nil {
			return nil, err
		}

		plugin_res.InstalledVersion = installed_ver
		plugin_res.AvailableVersion = plugin_meta.AvailableVersion
		plugin_res.UpdateRequired = version.Lower(installed_semver, available_semver)
		plugin_res.IsCompatible = version.Inrange(curr_app_semver, min_compat_semver, max_compat_semver)

		plugins[plugin_name] = plugin_res
	}

	return plugins, nil
}
