/**
 *
 *  Hubitat Package Manager
 *
 *  Copyright 2020 Dominick Meglio
 *
 *	If you find this useful, donations are always appreciated 
 *	https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=7LBRPJRLJSDDN&source=url
 *
 */
 
definition(
    name: "Hubitat Package Manager",
    namespace: "dcm.hpm",
    author: "Dominick Meglio",
    description: "Provides a utility to maintain the apps and drivers on your Hubitat making both installation and updates easier",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	documentationLink: "https://github.com/dcmeglio/hubitat-rebooter/blob/master/README.md")

preferences {
    page(name: "prefMain")
	page(name: "prefOptions")
    page(name: "prefPkgInstall")
	page(name: "prefPkgModify")
    page(name: "prefPkgUpdate")
	page(name: "prefPkgUninstall")
    page(name: "prefInstallChoices")
	page(name: "prefInstallVerify")
	page(name: "prefInstall")
	page(name: "prefPkgModifyChoices")
	page(name: "prefVerifyPackageChanges")
	page(name: "prefMakePackageChanges")
	page(name: "prefPkgUninstallConfirm")
	page(name: "prefPkgUninstallComplete")
	page(name: "prefPkgVerifyUpdates")
	page(name: "prefPkgUpdatesComplete")
}

def installed() {
    initialize()
}

def updated() {
	unschedule()
    initialize()
}

def initialize() {

}

def uninstalled() {
	logDebug "uninstalling app"
	unschedule()
}

def prefMain() {
	clearStateSettings()
    return dynamicPage(name: "prefMain", title: "Hubitat Connection Configuration", nextPage: "prefOptions", install: false, uninstall: false) {
		section {
			paragraph "In order to automatically install apps and drivers you must specify your Hubitat admin username and password if Hub Security is enabled."
			input("hpmSecurity", "bool", title: "Is Hub Security enabled?", submitOnChange: true)
			if (hpmSecurity)
			{
				input("hpmUsername", "string", title: "Hub Security username", required: true)
				input("hpmPassword", "password", title: "Hub Security password", required: true)
			}
		}
	}
}
def prefOptions() {
	return dynamicPage(name: "prefMain", title: "Package Options", install: true, uninstall: true) {
		section {
			paragraph "What would you like to do?"
			href(name: "prefPkgInstall", title: "Install", required: false, page: "prefPkgInstall", description: "Install a new package")
			href(name: "prefPkgModify", title: "Modify", required: false, page: "prefPkgModify", description: "Modify an already installed package")
			href(name: "prefPkgUninstall", title: "Uninstall", required: false, page: "prefPkgUninstall", description: "Uninstall a package")
            href(name: "prefPkgUpdate", title: "Update", required: false, page: "prefPkgUpdate", description: "Check for updates")
		}
	}
}

// Install a package pathway
def prefPkgInstall() {
	return dynamicPage(name: "prefPkgInstall", title: "Install a Package", nextPage: "prefInstallChoices", install: false, uninstall: false) {
		section {
			input "pkgInstall", "text", title: "Enter the URL of a package you wish to install"
		}
	}
}

def prefInstallChoices() {
	if (state.manifests == null)
		state.manifests = [:]
    def manifest = getManifestFile(pkgInstall)
	
	if (manifest == null) {
		return dynamicPage(name: "prefInstallChoices", title: "Invalid package file", install: true, uninstall: true) {
			section {
				paragraph "${pkgInstall} does not appear to be a valid Hubitat Package."
			}
		}
	}
	
	state.manifests[pkgInstall] = manifest
	
	def apps = getOptionalAppsFromManifest(manifest)
	def drivers = getOptionalDriversFromManifest(manifest)
	
	if (!verifyHEVersion(manifest.minimumHEVersion)) {
		return dynamicPage(name: "prefInstallChoices", title: "Unsupported Hubitat Firmware", install: true, uninstall: true) {
			section {
				paragraph "Your Hubitat Elevation firmware is not supported. You are running ${location.hub.firmwareVersionString} and this package requires  at least ${manifest.minimumHEVersion}. Please upgrade your firmware to continue installing."
			}
		}
	} 
	else { 
		def title = "Choose the components to install"
		if (apps.size() == 0 && drivers.size() == 0)
			title = "Ready to install"
		return dynamicPage(name: "prefInstallChoices", title: title, nextPage: "prefInstallVerify", install: false, uninstall: false) {
			section {
				if (apps.size() > 0 || drivers.size() > 0)
					paragraph "You are about to install <b>${manifest.packageName}</b>. This package includes some optional components. Please choose which ones you would like to include below. Click Next when you are ready."
				else
					paragraph "You are about to install <b>${manifest.packageName}</b>. Click next when you are ready."
				if (apps.size() > 0)
					input "appsToInstall", "enum", title: "Select the apps to install", options: apps, hideWhenEmpty: true, multiple: true
				if (drivers.size() > 0)
					input "driversToInstall", "enum", title: "Select the drivers to install", options: drivers, hideWhenEmpty: true, multiple: true
			}
		}
	}
}

def prefInstallVerify() {
    return dynamicPage(name: "prefInstallVerify", title: "Ready to install", nextPage: "prefInstall", install: false, uninstall: false) {
		section {
			paragraph "Click the next button to install your selections. This may take some time..."
		}
	}
}

def prefInstall() {
	login()
	def manifest = state.manifests[pkgInstall]
	
	// Download all files first to reduce the chances of a network error
	def appFiles = [:]
	def driverFiles = [:]
	
	def requiredApps = getRequiredAppsFromManifest(manifest)
	def requiredDrivers = getRequiredDriversFromManifest(manifest)
	
	for (requiredApp in requiredApps) {
		def fileContents = downloadFile(requiredApp.value.location)
		appFiles[requiredApp.value.location] = fileContents
	}
	for (appToInstall in appsToInstall) {
		def matchedApp = manifest.apps.find { it.id == appToInstall}
		if (matchedApp != null) {
			def fileContents = downloadFile(matchedApp.location)
			appFiles[matchedApp.location] = fileContents
		}
	}
	for (requiredDriver in requiredDrivers) {
		def fileContents = downloadFile(requiredDriver.value.location)
		driverFiles[requiredDriver.value.location] = fileContents
	}
	for (driverToInstall in driversToInstall) {
		def matchedDriver = manifest.drivers.find { it.id == driverToInstall}
		if (matchedDriver != null) {
			def fileContents = downloadFile(matchedDriver.location)
			driverFiles[matchedDriver.location] = fileContents
		}
	}

	for (requiredApp in requiredApps) {
		requiredApp.value.heID = installApp(appFiles[requiredApp.value.location])
		if (requiredApp.value.oauth)
			enableOAuth(requiredApp.value.heID)
	}
	
	for (appToInstall in appsToInstall) {
		def matchedApp = manifest.apps.find { it.id == appToInstall}
		if (matchedApp != null) {
			matchedApp.heID = installApp(appFiles[matchedApp.location] = fileContents)
			if (matchedApp.oauth)
				enableOAuth(matchedApp.heID)
		}
	}
	
	for (requiredDriver in requiredDrivers) {
		requiredDriver.value.heID = installDriver(driverFiles[requiredDriver.value.location])
	}
	
	for (driverToInstall in driversToInstall) {
		def matchedDriver = manifest.drivers.find { it.id == driverToInstall}
		if (matchedDriver != null) {
			matchedDriver.heID = installDriver(driverFiles[matchedDriver.location])
		}
	}
	
    return dynamicPage(name: "prefInstall", title: "Ready to install", install: true, uninstall: true) {
		section {
			paragraph "Installation successful, click done."
		}
	}
}

// Modify a package pathway
def prefPkgModify() {
	def pkgsToList = getInstalledPackages()
	return dynamicPage(name: "prefPkgModify", title: "Choose the package to modify", nextPage: "prefPkgModifyChoices", install: false, uninstall: false) {
		section {
			input "pkgModify", "enum", options: pkgsToList, required: true
		}
	}
}

def prefPkgModifyChoices() {
	def manifest = getInstalledManifest(pkgModify)
	
	def optionalApps = getOptionalAppsFromManifest(manifest)
	def optionalDrivers = getOptionalDriversFromManifest(manifest)
	if (optionalApps?.size() > 0 || optionalDrivers?.size() > 0) {
		def installedOptionalApps = []
		def installedOptionalDrivers = []
		for (optApp in optionalApps) {
			if (isAppInstalled(manifest, optApp.key)) {
				installedOptionalApps << optApp.key
			}
		}
		
		for (optDriver in optionalDrivers) {
			if (isDriverInstalled(manifest, optDriver.key)) {
				installedOptionalDrivers << optDriver.key
			}
		}
		
		return dynamicPage(name: "prefPkgModifyChoices", title: "What would you like to modify?", nextPage: "prefVerifyPackageChanges", install: false, uninstall: false) {
			section {
				if (optionalApps.size() > 0)
					input "appsToModify", "enum", title: "Select the apps to install/remove", options: optionalApps, hideWhenEmpty: true, multiple: true, defaultValue: installedOptionalApps
				if (optionalDrivers.size() > 0)
					input "driversToModify", "enum", title: "Select the drivers to install/remove", options: optionalDrivers, hideWhenEmpty: true, multiple: true, defaultValue: installedOptionalDrivers
			}
		}
	}
	else {
		return dynamicPage(name: "prefPkgModifyChoices", title: "Nothing to modify", install: true, uninstall: true) {
			section {
				paragraph "This package does not have any optional components that you can modify."
			}
		}
	}
}

def prefVerifyPackageChanges() {
	def appsToUninstallStr = "<ul>"
	def appsToInstallStr = "<ul>"
	def driversToUninstallStr = "<ul>"
	def driversToInstallStr = "<ul>"
	state.appsToUninstall = []
	state.appsToInstall = []
	state.driversToUninstall = []
	state.driversToInstall = []
	def hasChanges = false
	
	def manifest = getInstalledManifest(pkgModify)
	for (optApp in appsToModify) {
		if (!isAppInstalled(manifest,optApp)) {
			appsToInstallStr += "<li>${getAppNameById(manifest,optApp)}</li>"
			state.appsToInstall << optApp
			hasChanges = true
		}
	}
	appsToInstallStr += "</ul>"
	for (optDriver in driversToModify) {
		if (!isDriverInstalled(manifest,optDriver)) {
			driversToInstallStr += "<li>${getDriverNameById(manifest,optDriver)}</li>"
			state.driversToInstall << optDriver
			hasChanges = true
		}
	}
	driversToInstallStr += "</ul>"
	
	def installedApps = getInstalledOptionalApps(manifest)
	def installedDrivers = getInstalledOptionalDrivers(manifest)
	for (installedApp in installedApps) {
		if (!appsToModify?.contains(installedApp)) {
			appsToUninstallStr += "<li>${getAppNameById(manifest,installedApp)}</li>"
			state.appsToUninstall << installedApp
			hasChanges = true
		}
	}
	appsToUninstallStr += "</ul>"
	
	for (installedDriver in installedDrivers) {
		if (!driversToModify?.contains(installedDriver)) {
			driversToUninstallStr += "<li>${getDriverNameById(manifest,installedDriver)}</li>"
			state.driversToUninstall << installedDriver
			hasChanges = true
		}
	}
	driversToUninstallStr += "</ul>"

	if (hasChanges) {
		return dynamicPage(name: "prefVerifyPackageChanges", title: "The following changes will be made. Click next when you are ready. This may take some time.", nextPage: "prefMakePackageChanges", install: false, uninstall: false) {
			section {
				if (appsToUninstallStr != "<ul></ul>")
					paragraph "The following apps will be removed: ${appsToUninstallStr}"
				if (appsToInstallStr != "<ul></ul>")
					paragraph "The following apps will be installed: ${appsToInstallStr}"
				if (driversToUninstallStr != "<ul></ul>")
					paragraph "The following drivers will be removed: ${driversToUninstallStr}"
				if (driversToInstallStr != "<ul></ul>")
					paragraph "The following drivers will be installed: ${driversToInstallStr}"
			}
		}
	}
	else {
		return dynamicPage(name: "prefVerifyPackageChanges", title: "Nothing to modify", install: true, uninstall: true) {
			section {
				paragraph "You did not make any changes."
			}
		}
	}
}

def prefMakePackageChanges() {
	// Download all files first to reduce the chances of a network error
	def appFiles = [:]
	def driverFiles = [:]
	def manifest = getInstalledManifest(pkgModify)
	
	for (appToInstall in state.appsToInstall) {
		def app = getAppById(manifest, appToInstall)
		def fileContents = downloadFile(app.location)
		appFiles[app.location] = fileContents
	}
	for (driverToInstall in state.driversToInstall) {
		def driver = getDriverById(manifest, driverToInstall)
		def fileContents = downloadFile(driver.location)
		driverFiles[driver.location] = fileContents
	}
	
	
	for (appToInstall in state.appsToInstall) {
		def app = getAppById(manifest, appToInstall)
		app.heID = installApp(appFiles[app.location])
		if (app.oauth)
				enableOAuth(app.heID)
	}
	for (appToUninstall in state.appsToUninstall) {
		def app = getAppById(manifest, appToUninstall)
		uninstallApp(app.heID)
		app.heID = null
	}
	
	for (driverToInstall in state.driversToInstall) {
		def driver = getDriverById(manifest, driverToInstall)
		driver.heID = installDriver(driverFiles[driver.location])
		
	}
	for (driverToUninstall in state.driversToUninstall) {
		def driver = getDriverById(manifest, driverToUninstall)
		uninstallDriver(driver.heID)
		driver.heID = null
	}
	
	return dynamicPage(name: "prefMakePackageChanges", title: "Installation successful", install: true, uninstall: true) {
		section {
			paragraph "Installation successful, click done."
		}
	}
}

// Uninstall a package pathway
def prefPkgUninstall() {
	def pkgsToList = getInstalledPackages()

	return dynamicPage(name: "prefPkgUninstall", title: "Choose the package to uninstall", nextPage: "prefPkgUninstallConfirm", install: false, uninstall: false) {
		section {
			input "pkgUninstall", "enum", options: pkgsToList, required: true
		}
	}
}

def prefPkgUninstallConfirm() {
	return dynamicPage(name: "prefPkgUninstallConfirm", title: "Choose the package to uninstall", nextPage: "prefPkgUninstallComplete", install: false, uninstall: false) {
		section {
			paragraph "The following apps and drivers will be removed:"
			
			def str = "<ul>"
			def pkg = state.manifests[pkgUninstall]
			for (app in pkg.apps) {
				if (app.heID != null)
					str += "<li>" + app.name + "</li>"
			}
			
			for (driver in pkg.drivers) {
				if (driver.heID != null)
					str += "<li>" + driver.name + "</li>"
			}
			str += "</ul>"
			paragraph str
		}
	}
}

def prefPkgUninstallComplete() {
	def pkg = state.manifests[pkgUninstall]
			
	for (app in pkg.apps) {
		if (app.heID != null)
			uninstallApp(app.heID)
	}
	
	for (driver in pkg.drivers) {
		if (driver.heID != null)
			uninstallDriver(driver.heID)
	}
	state.manifests.remove(pkgUninstall)
	
	return dynamicPage(name: "prefPkgUninstallComplete", title: "Uninstall complete", install: true, uninstall: true) {
		section {
			paragraph "Package successfully removed."
		}
	}
}

// Update packages pathway
def prefPkgUpdate() {
	def needsUpdate = [:]

	for (pkg in state.manifests) {
		def manifest = getManifestFile(pkg.key)
		
		if (newVersionAvailable(manifest.version, state.manifests[pkg.key].version)) {
			needsUpdate << ["${pkg.key}": "${state.manifests[pkg.key].packageName} (installed: ${state.manifests[pkg.key].version} current: ${manifest.version})"]
		}
	}
	if (needsUpdate.size() > 0) {
		return dynamicPage(name: "prefPkgUpdate", title: "Updates Available", nextPage: "prefPkgVerifyUpdates", install: false, uninstall: false) {
			section {
				paragraph "Updates are available."
				input "pkgsToUpdate", "enum", title: "Which packages do you want to update?", multiple: true, required: true, options:needsUpdate
			}
		}
	}
	else {
		return dynamicPage(name: "prefPkgUpdate", title: "Updates Available", install: true, uninstall: true) {
			section {
				paragraph "All packages are up to date."
			}
		}
	}
}

def prefPkgVerifyUpdates() {
	def updatesToInstall = "<ul>"
	
	for (pkg in pkgsToUpdate) {
		updatesToInstall += "<li>${state.manifests[pkg].packageName}</li>"
	}
	updatesToInstall += "</ul>"
	return dynamicPage(name: "prefPkgVerifyUpdates", title: "Install Updates?", nextPage: "prefPkgUpdatesComplete", install: false, uninstall: false) {
		section {
			paragraph "The following updates will be installed: ${updatesToInstall}. Click next to continue. This may take some time."
		}
	}
}

def prefPkgUpdatesComplete() {
	// Download all files first to reduce the chances of a network error
	def downloadedManifests = [:]
	def appFiles = [:]
	def driverFiles = [:]
	for (pkg in pkgsToUpdate) {
		def manifest = getManifestFile(pkg)
		def installedManifest = state.manifests[pkg]
		
		downloadedManifests[pkg] = manifest
		if (manifest) {
			for (app in manifest.apps) {
				if (isAppInstalled(installedManifest,app.id)) {
					def fileContents = downloadFile(app.location)
					appFiles[app.location] = fileContents					
				}
				else if (app.required) {
					def fileContents = downloadFile(app.location)
					appFiles[app.location] = fileContents
				}
			}
			for (driver in manifest.drivers) {
				if (isDriverInstalled(installedManifest,driver.id)) {
					def fileContents = downloadFile(driver.location)
					driverFiles[driver.location] = fileContents
				}
				else if (driver.required) {
					def fileContents = downloadFile(driver.location)
					driverFiles[driver.location] = fileContents
				}
			}
		}
	}
	
	for (pkg in pkgsToUpdate) {
		def manifest = downloadedManifests[pkg]
		def installedManifest = state.manifests[pkg]
		
		if (manifest) {
			for (app in manifest.apps) {
				if (isAppInstalled(installedManifest,app.id)) {
					app.heID = getAppById(installedManifest, app.id).heID
					upgradeApp(app.heID, appFiles[app.location])
					if (app.oauth)
						enableOAuth(app.heID)					
				}
				else if (app.required) {
					app.heID = installApp(appFiles[app.location])
					if (app.oauth)
						enableOAuth(app.heID)
				}
			}
			
			for (driver in manifest.drivers) {
				if (isDriverInstalled(installedManifest,driver.id)) {
					driver.heID = getDriverById(installedManifest, driver.id).heID
					upgradeDriver(driver.heID, driverFiles[driver.location])	
				}
				else if (driver.required) {
					driver.heID = installDriver(driverFiles[driver.location])
				}
			}
			state.manifests[pkg] = manifest
		}
		else {
		}
	}
	return dynamicPage(name: "prefPkgUpdatesComplete", title: "Updates complete", install: true, uninstall: true) {
		section {
			paragraph "Packages successfully updated."
		}
	}
}

def clearStateSettings() {
	app.removeSetting("pkgInstall")
	app.removeSetting("appsToInstall")
	app.removeSetting("driversToInstall")
	app.removeSetting("pkgModify")
	app.removeSetting("appsToModify")
	app.removeSetting("driversToModify")
	app.removeSetting("pkgUninstall")
	app.removeSetting("pkgsToUpdate")
}

def getInstalledPackages() {
	def pkgsToList = [:]
	for (pkg in state.manifests) 
		pkgsToList[pkg.key] = pkg.value.packageName
	return pkgsToList
}


def isAppInstalled(manifest, id) {
	for (app in manifest.apps) {
		if (app.id == id) {
			if (app.heID != null)
				return true
			else
				return false
		}
	}
	return false
}

def isDriverInstalled(manifest, id) {
	for (driver in manifest.drivers) {
		if (driver.id == id) {
			if (driver.heID != null)
				return true
			else
				return false
		}
	}
	return false
}

def getAppNameById(manifest, id) {
	for (app in manifest.apps) {
		if (app.id == id) {
			return app.name
		}
	}
	return null
}

def getDriverNameById(manifest, id) {
	for (driver in manifest.drivers) {
		if (driver.id == id) {
			return driver.name
		}
	}
	return null
}

def getAppById(manifest, id) {
	for (app in manifest.apps) {
		if (app.id == id) {
			return app
		}
	}
	return null
}

def getDriverById(manifest, id) {
	for (driver in manifest.drivers) {
		if (driver.id == id) {
			return driver
		}
	}
	return null
}

def getInstalledOptionalApps(manifest) {
	def result = []
	for (app in manifest.apps) {
		if (app.heID != null && app.required == false) {
			result << app.id
		}
	}
	return result
}

def getInstalledOptionalDrivers(manifest) {
	def result = []
	for (driver in manifest.drivers) {
		if (driver.heID != null && driver.required == false) {
			result << driver.id
		}
	}
	return result
}

def downloadFile(file) {
	def params = [
        uri: file,
        requestContentType: "application/json",
        contentType: "application/json",
        textParser: true,
		timeout: 300
    ]
	def result
    httpGet(params) { resp ->
        result = resp.data.text
    }
	return result	
}

def installApp(appCode) {
	def params = [
		uri: "http://127.0.0.1:8080",
		path: "/app/save",
		requestContentType: "application/x-www-form-urlencoded",
		headers: [
			"Cookie": state.cookie
		],
		body: [
			id: "",
			version: "",
			create: "",
			source: appCode
		],
		timeout: 300
	]
	def result
	httpPost(params) { resp ->
		result = resp.headers."Location".replaceAll("http://127.0.0.1:8080/app/editor/","")
	}
	return result
}

def enableOAuth(id) {
	def params = [
		uri: "http://127.0.0.1:8080",
		path: "/app/edit/update",
		requestContentType: "application/x-www-form-urlencoded",
		headers: [
			"Cookie": state.cookie
		],
		body: [
			id: id,
			version: getAppVersion(id),
			oauthEnabled: "true",
			webServerRedirectUri: "",
			displayLink: "",
			_action_update: "Update"
		],
		timeout: 300
	]
	def result = false
	httpPost(params) { resp ->
		result = true
	}
	return result
}

def getAppVersion(id) {
	def params = [
		uri: "http://127.0.0.1:8080",
		path: "/app/ajax/code",
		requestContentType: "application/x-www-form-urlencoded",
		headers: [
			"Cookie": state.cookie
		],
		query: [
			id: id
		]
	]
	def result
	httpGet(params) { resp ->
		result = resp.data.version
	}
	return result
}

def getDriverVersion(id) {
	def params = [
		uri: "http://127.0.0.1:8080",
		path: "/driver/ajax/code",
		requestContentType: "application/x-www-form-urlencoded",
		headers: [
			"Cookie": state.cookie
		],
		query: [
			id: id
		]
	]
	def result
	httpGet(params) { resp ->
		result = resp.data.version
	}
	return result
}

def upgradeApp(id,appCode) {
	def params = [
		uri: "http://127.0.0.1:8080",
		path: "/app/ajax/update",
		requestContentType: "application/x-www-form-urlencoded",
		headers: [
			"Cookie": state.cookie
		],
		body: [
			id: id,
			version: getAppVersion(id),
			source: appCode
		],
		timeout: 300
	]
	def result = false
	httpPost(params) { resp ->
		result = resp.data.status == "success"
	}
	return result
}

def upgradeDriver(id,appCode) {
	def params = [
		uri: "http://127.0.0.1:8080",
		path: "/driver/ajax/update",
		requestContentType: "application/x-www-form-urlencoded",
		headers: [
			"Cookie": state.cookie
		],
		body: [
			id: id,
			version: getDriverVersion(id),
			source: appCode
		],
		timeout: 300
	]
	def result = false
	httpPost(params) { resp ->
		result = resp.data.status == "success"
	}
	return result
}

def uninstallApp(id) {
	try {
		def params = [
			uri: "http://127.0.0.1:8080",
			path: "/app/edit/update",
			requestContentType: "application/x-www-form-urlencoded",
			headers: [
				"Cookie": state.cookie
			],
			body: [
				id: id,
				"_action_delete": "Delete"
			],
			timeout: 300
		]
		httpPost(params) { resp ->
		}
	}
	catch (e) {
	}
}

def installDriver(driverCode) {
	def params = [
		uri: "http://127.0.0.1:8080",
		path: "/driver/save",
		requestContentType: "application/x-www-form-urlencoded",
		headers: [
			"Cookie": state.cookie
		],
		body: [
			id: "",
			version: "",
			create: "",
			source: driverCode
		],
		timeout: 300
	]
	def result
	httpPost(params) { resp ->
		result = resp.headers."Location".replaceAll("http://127.0.0.1:8080/driver/editor/","")
	}
	return result
}

def uninstallDriver(id) {
		def params = [
			uri: "http://127.0.0.1:8080",
			path: "/driver/editor/update",
			requestContentType: "application/x-www-form-urlencoded",
			headers: [
				"Cookie": state.cookie
			],
			body: [
				id: id,
				"_action_delete": "Delete"
			],
			timeout: 300
		]
		httpPost(params) { resp ->
		}

}

def getOptionalAppsFromManifest(manifest) {
	def appsList = [:]
	for (app in manifest.apps) {
		if (app.required == false)
			appsList << ["${app.id}":app.name]
	}
	return appsList
}

def getOptionalDriversFromManifest(manifest) {
	def driversList = [:]
	for (driver in manifest.drivers) {
		if (driver.required == false)
			driversList << ["${driver.id}":driver.name]
	}
	return driversList
}

def getRequiredAppsFromManifest(manifest) {
	def appsList = [:]
	for (app in manifest.apps) {
		if (app.required == true)
			appsList << ["${app.id}":app]
	}
	return appsList
}

def getRequiredDriversFromManifest(manifest) {
	def driversList = [:]
	for (driver in manifest.drivers) {
		if (driver.required == true)
			driversList << ["${driver.id}":driver]
	}
	return driversList
}

def getInstalledManifest(pkgId) {
	for (pkg in state.manifests) {
		if (pkg.key == pkgId)
			return pkg.value
	}
	return null
}

def getManifestFile(uri) {
	try
	{
		def result
		def params = [
			uri: uri,
			requestContentType: "application/json",
			contentType: "application/json",
			textParser: true
		]
		
		httpGet(params) { resp ->
			result = new groovy.json.JsonSlurper().parseText(resp.data.text)
		}
		if (result.packageName == null || result.packageId == null)
			return null
		return result
	}
	catch (e)
	{
		return null
	}
	
}

def verifyHEVersion(versionStr) {
	def installedVersionParts = location.hub.firmwareVersionString.split(/\./)
	def requiredVersionParts = versionStr.split(/\./)

	for (def i = 0; i < requiredVersionParts.size(); i++) {
		if (i >= installedVersionParts.size()) {
			return false
		}
		def installedPart = installedVersionParts[i].toInteger()
		def requiredPart = requiredVersionParts[i].toInteger()
		if (installedPart < requiredPart) {
			return false
		}
		else if (installedPart > requiredPart) {
			return true
		}
	}
	return true
}

def newVersionAvailable(versionStr, installedVersionStr) {
	def installedVersionParts = installedVersionStr.split(/\./)
	def newVersionParts = versionStr.split(/\./)

	for (def i = 0; i < newVersionParts.size(); i++) {
		if (i >= installedVersionParts.size()) {
			return true
		}
		def installedPart = installedVersionParts[i].toInteger()
		def newPart = newVersionParts[i].toInteger()
		if (installedPart < newPart) {
			return true
		}
	}
	return false
}





def login() {
	if (hpmSecurity)
    {
		httpPost(
			[
				uri: "http://127.0.0.1:8080",
				path: "/login",
				query: 
				[
					loginRedirect: "/"
				],
				body:
				[
					username: hpmUsername,
					password: hpmPassword,
					submit: "Login"
				]
			]
		)
		{ resp ->
            state.cookie = resp?.headers?.'Set-Cookie'?.split(';')?.getAt(0)
        }
	}
}

def getInstalledApps() {
	login()
	
	def params = [
    	uri: "http://127.0.0.1:8080/app/list",
        textParser: true,
		headers: [
			Cookie: cookie
		]
	]
    try {
        httpGet(params) { resp ->    
            state.appList = []
            def matcherText = resp.data.text.replace("\n","").replace("\r","")
            def matcher = matcherText.findAll(/(<tr class=\"app-row\" data-app-id=\"[^<>]+\">.*?<\/tr>)/).each {
                def href = it.find(/href="([^"]+)/) { match,h -> return h }
                def title = it.find(/title="([^"]+)/) { match,t -> return t }
                state.appList += [title:title,href:href]
            }
        }
    } catch (e) {
        log.debug "e: ${e}"
    }
    return state.appList
}


def logDebug(msg) {
    if (settings?.debugOutput) {
		log.debug msg
	}
}