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
	documentationLink: "https://github.com/dcmeglio/hubitat-packagemanager/blob/master/README.md")

preferences {
    page(name: "prefSettings")
	page(name: "prefOptions")
    page(name: "prefPkgInstall")
	page(name: "prefPkgInstallUrl")
	page(name: "prefPkgInstallRepository")
	page(name: "prefPkgInstallRepositoryChoose")
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
	page(name: "prefUninstall")
	page(name: "prefPkgVerifyUpdates")
	page(name: "prefPkgUpdatesComplete")
}

import groovy.transform.Field
@Field static String repositoryListing = "https://raw.githubusercontent.com/dcmeglio/hubitat-packagerepositories/master/repositories.json"
@Field static List categories = [] 
@Field static List allPackages = []
@Field static groovy.json.internal.LazyMap listOfRepositories = [:]
@Field static groovy.json.internal.LazyMap completedActions = [:]

def installed() {
    initialize()
}

def updated() {
	unschedule()
    initialize()
}

def initialize() {
	schedule("01 00 00 ? * *", checkForUpdates)
}

def uninstalled() {
	logDebug "uninstalling app"
	unschedule()
}

def prefSettings(params) {
	if (state.manifests == null)
		state.manifests = [:]
	clearStateSettings(true)
	logDebug "Refreshing repository list"
	
	listOfRepositories = getJSONFile(repositoryListing)
	installHPMManifest()
	if (app.getInstallationState() == "COMPLETE" && params?.force != true)
		return prefOptions()
	else {
		return dynamicPage(name: "prefSettings", title: "Hubitat Connection Configuration", nextPage: "prefOptions", install: false, uninstall: false) {
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
}
def prefOptions() {
	state.installationCom
	return dynamicPage(name: "prefOptions", title: "Package Options", install: true, uninstall: false) {
		section {
			paragraph "What would you like to do?"
			href(name: "prefPkgInstall", title: "Install", required: false, page: "prefPkgInstall", description: "Install a new package")
			href(name: "prefPkgModify", title: "Modify", required: false, page: "prefPkgModify", description: "Modify an already installed package")
			href(name: "prefPkgUninstall", title: "Uninstall", required: false, page: "prefPkgUninstall", description: "Uninstall a package")
            href(name: "prefPkgUpdate", title: "Update", required: false, page: "prefPkgUpdate", description: "Check for updates")
			href(name: "prefSettings", title: "Package Manager Settings", required: false, page: "prefSettings", params: [force:true], description: "Modify Hubitat Package Manager Settings")
		}
	}
}

// Install a package pathway
def prefPkgInstall() {
	logDebug "prefPkg"
	return dynamicPage(name: "prefPkgInstall", title: "Install a Package", install: true, uninstall: false) {
		section {
			paragraph "How would you like to install this package?"
			href(name: "prefPkgInstallRepository", title: "From a Repository", required: false, page: "prefPkgInstallRepository", description: "Choose a package from a repository")
			href(name: "prefPkgInstallUrl", title: "From a URL", required: false, page: "prefPkgInstallUrl", description: "Install a package using a URL to a specific package")
			
		}
	}
}

def prefPkgInstallUrl() {
	logDebug "Install by URL"
	return dynamicPage(name: "prefPkgInstallUrl", title: "Install a Package", nextPage: "prefInstallChoices", install: false, uninstall: false) {
		section {
			input "pkgInstall", "text", title: "Enter the URL of a package you wish to install"
		}
	}
}

def prefPkgInstallRepository() {
	if (atomicState.error == true) {
		return buildErrorPage(atomicState.errorTitle, atomicState.errorMessage)
	}
	if (atomicState.inProgress == null) {
		logDebug "Install by Repository"
		atomicState.inProgress = true
		runInMillis(1,performRepositoryRefresh)
	}
	if (atomicState.inProgress != false) {
		return dynamicPage(name: "prefPkgInstallRepository", title: "Install a Package", nextPage: "prefPkgInstallRepository", install: false, uninstall: false, refreshInterval: 2) {
			section {
				paragraph "Refreshing repositories... Please wait..."
				paragraph getBackgroundStatusMessage()
			}
		}
	}
	else {
		return dynamicPage(name: "prefPkgInstallRepository", title: "Choose a category", nextPage: "prefPkgInstallRepositoryChoose", install: false, uninstall: false) {
			section {
				input "pkgCategory", "enum", options: categories, required: true
			}
		}
	}	
}

def performRepositoryRefresh() {
	allPackages = []
	categories = []

	for (repo in listOfRepositories.repositories) {
		setBackgroundStatusMessage("Refreshing ${repo.name}")
		def fileContents = getJSONFile(repo.location)
		if (!fileContents)
			return triggerError("Error Refreshing Repository","Error refreshing ${repo.name}")
		for (pkg in fileContents.packages) {
			def pkgDetails = [
				repository: repo.name,
				author: fileContents.author,
				githubUrl: fileContents.gitHubUrl,
				payPalUrl: fileContents.payPalUrl,
				name: pkg.name,
				description: pkg.description,
				location: pkg.location,
				category: pkg.category
			]
			allPackages << pkgDetails
			if (!categories.contains(pkgDetails.category))
				categories << pkgDetails.category
		}
	}
	categories = categories.sort()
	atomicState.inProgress = false
}

def prefPkgInstallRepositoryChoose() {
	atomicState.statusMessage = ""
	atomicState.inProgress = null
	atomicState.error = null
	atomicState.errorTitle = null
	atomicState.errorMessage = null
	def matchingPackages = [:]
	for (pkg in allPackages) {
		if (pkg.category == pkgCategory)
			matchingPackages << ["${pkg.location}":"${pkg.name} - ${pkg.description}"]
	}
	logDebug "Chose category ${pkgCategory}"
	return dynamicPage(name: "prefPkgInstallRepositoryChoose", title: "Choose a package", nextPage: "prefInstallChoices", install: false, uninstall: false) {
		section {
			input "pkgInstall", "enum", options: matchingPackages, required: true
		}
	}	
}

def prefInstallChoices() {
	logDebug "Manifest chosen ${pkgInstall}"
    def manifest = getJSONFile(pkgInstall)
	
	if (manifest == null) {
		return buildErrorPage("Invalid Package File", "${pkgInstall} does not appear to be a valid Hubitat Package or does not exist.")
	}
	if (state.manifests[pkgInstall] != null)
	{
		return buildErrorPage("Package Already Installed", "${pkgInstall} has already been installed. If you would like to look for upgrades, use the Update function.")
	}
	
	if (!verifyHEVersion(manifest.minimumHEVersion)) {
		return buildErrorPage("Unsupported Hubitat Firmware", "Your Hubitat Elevation firmware is not supported. You are running ${location.hub.firmwareVersionString} and this package requires  at least ${manifest.minimumHEVersion}. Please upgrade your firmware to continue installing.")
	} 
	else {
		def apps = getOptionalAppsFromManifest(manifest)
		def drivers = getOptionalDriversFromManifest(manifest)
		def title = "Choose the components to install"
		def githubUrl = null
		def paypalUrl = null
		if (apps.size() == 0 && drivers.size() == 0)
			title = "Ready to install"
		def packageFromRepo = allPackages.find { i -> i.location == pkgInstall}

		if (packageFromRepo != null) {
			githubUrl = packageFromRepo.githubUrl
			paypalUrl = packageFromRepo.payPalUrl
		}
		
		return dynamicPage(name: "prefInstallChoices", title: title, nextPage: "prefInstallVerify", install: false, uninstall: false) {
			section {
				if (githubUrl != null)
					paragraph "Author's Github: <a href='${githubUrl}' target='_blank'>${githubUrl}</a>"
				if (paypalUrl != null)
					paragraph "Author's Paypal: <a href='${paypalUrl}' target='_blank'><img src='https://www.paypalobjects.com/webstatic/mktg/logo/pp_cc_mark_37x23.jpg' border=0></a>"
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
	logDebug "Options chosen"
    return dynamicPage(name: "prefInstallVerify", title: "Ready to install", nextPage: "prefInstall", install: false, uninstall: false) {
		section {
			def manifest = getJSONFile(pkgInstall)
			if (manifest.licenseFile) {
				def license = downloadFile(manifest.licenseFile)
				paragraph "By clicking next you accept the below license agreement:"
				paragraph "<textarea rows=20 cols=80 readonly='true'>${license}</textarea>"
				paragraph "Click next to continue. This make take some time..."
			}
			else
				paragraph "Click the next button to install your selections. This may take some time..."
		}
	}
}

def prefInstall() {
	if (atomicState.error == true) {
		return buildErrorPage(atomicState.errorTitle, atomicState.errorMessage)
	}
	if (atomicState.inProgress == null) {
		logDebug "Install beginning"
		atomicState.inProgress = true
		runInMillis(1,performInstallation)
	}
	if (atomicState.inProgress != false) {
		return dynamicPage(name: "prefInstall", title: "Ready to install", nextPage: "prefInstall", install: false, uninstall: false, refreshInterval: 2) {
			section {
				paragraph "Your installation is currently in progress... Please wait..."
				paragraph getBackgroundStatusMessage()
			}
		}
	}
	else {
		return complete("Installation complete", "The package was sucessfully installed, click Done.")
	}
}

def performInstallation() {
	login()
	def manifest = getJSONFile(pkgInstall)
	state.manifests[pkgInstall] = manifest
	
	// Download all files first to reduce the chances of a network error
	def appFiles = [:]
	def driverFiles = [:]
	
	def requiredApps = getRequiredAppsFromManifest(manifest)
	def requiredDrivers = getRequiredDriversFromManifest(manifest)
	
	for (requiredApp in requiredApps) {
		setBackgroundStatusMessage("Downloading ${requiredApp.value.name}")
		def fileContents = downloadFile(requiredApp.value.location)
		if (fileContents == null) {
			state.manifests.remove(pkgInstall)
			return triggerError("Error downloading file", "An error occurred downloading ${requiredApp.value.location}")
		}
		appFiles[requiredApp.value.location] = fileContents
	}
	for (appToInstall in appsToInstall) {
		def matchedApp = manifest.apps.find { it.id == appToInstall}
		if (matchedApp != null) {
			setBackgroundStatusMessage("Downloading ${matchedApp.name}")
			def fileContents = downloadFile(matchedApp.location)
			if (fileContents == null) {
				state.manifests.remove(pkgInstall)
				return triggerError("Error downloading file", "An error occurred downloading ${matchedApp.location}")
			}
			appFiles[matchedApp.location] = fileContents
		}
	}
	for (requiredDriver in requiredDrivers) {
		setBackgroundStatusMessage("Downloading ${requiredDriver.value.name}")
		def fileContents = downloadFile(requiredDriver.value.location)
		if (fileContents == null) {
			state.manifests.remove(pkgInstall)
			return triggerError("Error downloading file", "An error occurred downloading ${requiredDriver.value.location}")
		}
		driverFiles[requiredDriver.value.location] = fileContents
	}
	
	for (driverToInstall in driversToInstall) {
		def matchedDriver = manifest.drivers.find { it.id == driverToInstall}
		if (matchedDriver != null) {
			setBackgroundStatusMessage("Downloading ${matchedDriver.name}")
			def fileContents = downloadFile(matchedDriver.location)
			if (fileContents == null) {
				state.manifests.remove(pkgInstall)
				return triggerError("Error downloading file", "An error occurred downloading ${matchedDriver.location}")
			}
			driverFiles[matchedDriver.location] = fileContents
		}
	}

	initializeRollbackState("install")
	// All files downloaded, execute installs.
	for (requiredApp in requiredApps) {
		setBackgroundStatusMessage("Installing ${requiredApp.value.name}")
		def id = installApp(appFiles[requiredApp.value.location])
		if (id == null) {
			state.manifests.remove(pkgInstall)
			return rollback("Failed to install app ${requiredApp.value.location}")
		}
		requiredApp.value.heID = id
		if (requiredApp.value.oauth)
			enableOAuth(requiredApp.value.heID)
	}
	
	for (appToInstall in appsToInstall) {
		def matchedApp = manifest.apps.find { it.id == appToInstall}
		if (matchedApp != null) {
			setBackgroundStatusMessage("Installing ${matchedApp.name}")
			def id = installApp(appFiles[matchedApp.location])
			if (id == null) {
				state.manifests.remove(pkgInstall)
				return rollback("Failed to install app ${matchedApp.location}")
			}
			matchedApp.heID = id
			if (matchedApp.oauth)
				enableOAuth(matchedApp.heID)
		}
	}
	
	for (requiredDriver in requiredDrivers) {
		setBackgroundStatusMessage("Installing ${requiredDriver.value.name}")
		def id = installDriver(driverFiles[requiredDriver.value.location])
		if (id == null) {
			state.manifests.remove(pkgInstall)
			return rollback("Failed to install driver ${requiredDriver.value.location}")
		}
		requiredDriver.value.heID = id
	}
	
	for (driverToInstall in driversToInstall) {
		def matchedDriver = manifest.drivers.find { it.id == driverToInstall}
		if (matchedDriver != null) {
			setBackgroundStatusMessage("Installing ${matchedDriver.name}")
			def id = installDriver(driverFiles[matchedDriver.location])
			if (id == null) {
				state.manifests.remove(pkgInstall)
				return rollback("Failed to install driver ${matchedDriver.location}")
			}
			matchedDriver.heID = id
		}
	}
	atomicState.inProgress = false
}

// Modify a package pathway
def prefPkgModify() {
	logDebug "Modify package chosen"
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
	logDebug "Modification choices made"
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
			appsToInstallStr += "<li>${getAppById(manifest,optApp).name}</li>"
			state.appsToInstall << optApp
			hasChanges = true
		}
	}
	appsToInstallStr += "</ul>"
	for (optDriver in driversToModify) {
		if (!isDriverInstalled(manifest,optDriver)) {
			driversToInstallStr += "<li>${getDriverById(manifest,optDriver).name}</li>"
			state.driversToInstall << optDriver
			hasChanges = true
		}
	}
	driversToInstallStr += "</ul>"
	
	def installedApps = getInstalledOptionalApps(manifest)
	def installedDrivers = getInstalledOptionalDrivers(manifest)
	for (installedApp in installedApps) {
		if (!appsToModify?.contains(installedApp)) {
			appsToUninstallStr += "<li>${getAppById(manifest,installedApp).name}</li>"
			state.appsToUninstall << installedApp
			hasChanges = true
		}
	}
	appsToUninstallStr += "</ul>"
	
	for (installedDriver in installedDrivers) {
		if (!driversToModify?.contains(installedDriver)) {
			driversToUninstallStr += "<li>${getDriverById(manifest,installedDriver).name}</li>"
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
	if (atomicState.error == true) {
		return buildErrorPage(atomicState.errorTitle, atomicState.errorMessage)
	}
	if (atomicState.inProgress == null) {
		logDebug "Executing modify"
		atomicState.inProgress = true
		runInMillis(1,performModify)
	}
	if (atomicState.inProgress != false) {
		return dynamicPage(name: "prefMakePackageChanges", title: "Ready to install", nextPage: "prefInstall", install: false, uninstall: false, refreshInterval: 2) {
			section {
				paragraph "Your installation is currently in progress... Please wait..."
				paragraph getBackgroundStatusMessage()
			}
			
		}
	}
	else {
		return complete("Installation complete", "The package was sucessfully modified, click Done.")
	}
}

def performModify() {
	login()
	
	// Download all files first to reduce the chances of a network error
	def appFiles = [:]
	def driverFiles = [:]
	def manifest = getInstalledManifest(pkgModify)
	
	for (appToInstall in state.appsToInstall) {
		def app = getAppById(manifest, appToInstall)
		setBackgroundStatusMessage("Downloading ${app.name}")
		def fileContents = downloadFile(app.location)
		if (fileContents == null) {
			return triggerError("Error downloading file", "An error occurred downloading ${app.location}")
		}
		appFiles[app.location] = fileContents
	}
	for (driverToInstall in state.driversToInstall) {
		def driver = getDriverById(manifest, driverToInstall)
		setBackgroundStatusMessage("Downloading ${driver.name}")
		def fileContents = downloadFile(driver.location)
		if (fileContents == null) {
			return triggerError("Error downloading file", "An error occurred downloading ${driver.location}")
		}
		driverFiles[driver.location] = fileContents
	}
	
	initializeRollbackState("modify")
	for (appToInstall in state.appsToInstall) {
		def app = getAppById(manifest, appToInstall)
		setBackgroundStatusMessage("Installing ${app.name}")
		def id = installApp(appFiles[app.location])
		if (id != null)
		{
			app.heID = id
			completedActions["appInstalls"] << id
			if (app.oauth)
				enableOAuth(app.heID)
		}
		else
			return rollback("Failed to install app ${app.location}")
	}
	for (appToUninstall in state.appsToUninstall) {
		def app = getAppById(manifest, appToUninstall)
		def sourceCode = getDriverSource(app.heID)
		setBackgroundStatusMessage("Uninstalling ${app.name}")
		if (uninstallApp(app.heID)) {
			completedActions["appUninstalls"] << [id:app.id,source:sourceCode]
			app.heID = null
		}
		else
			return rollback("Failed to uninstall app ${app.location}")
	}
	
	for (driverToInstall in state.driversToInstall) {
		def driver = getDriverById(manifest, driverToInstall)
		setBackgroundStatusMessage("Installing ${driver.name}")
		def id = installDriver(driverFiles[driver.location])
		if (id != null) {
			driver.heID = id
		}
		else
			return rollback("Failed to install driver ${driver.location}")
		
	}
	for (driverToUninstall in state.driversToUninstall) {
		def driver = getDriverById(manifest, driverToUninstall)
		def sourceCode = getDriverSource(driver.heID)
		setBackgroundStatusMessage("Uninstalling ${driver.name}")
		if (uninstallDriver(driver.heID)) {
			completedActions["driverUninstalls"] << [id:driver.id,source:sourceCode]
			driver.heID = null
		}
		else
			return rollback("Failed to uninstall driver ${driver.location}")
	}
	atomicState.inProgress = false
}

// Uninstall a package pathway
def prefPkgUninstall() {
	logDebug "Uninstall chosen"
	def pkgsToList = getInstalledPackages()

	return dynamicPage(name: "prefPkgUninstall", title: "Choose the package to uninstall", nextPage: "prefPkgUninstallConfirm", install: false, uninstall: false) {
		section {
			input "pkgUninstall", "enum", options: pkgsToList, required: true
		}
	}
}

def prefPkgUninstallConfirm() {
	logDebug "Confirming uninstall of ${pkgUninstall}"
	return dynamicPage(name: "prefPkgUninstallConfirm", title: "Choose the package to uninstall", nextPage: "prefUninstall", install: false, uninstall: false) {
		section {
			paragraph "The following apps and drivers will be removed:"
			
			def str = "<ul>"
			def pkg = state.manifests[pkgUninstall]
			for (app in pkg.apps) {
				if (app.heID != null)
					str += "<li>${app.name}</li>"
			}
			
			for (driver in pkg.drivers) {
				if (driver.heID != null)
					str += "<li>${driver.name}</li>"
			}
			str += "</ul>"
			paragraph str
		}
	}
}

def prefUninstall() {
	if (atomicState.error == true) {
		return buildErrorPage(atomicState.errorTitle, atomicState.errorMessage)
	}
	if (atomicState.inProgress == null) {
		logDebug "Performing uninstall"
		atomicState.inProgress = true
		runInMillis(1,performUninstall)
	}
	if (atomicState.inProgress != false) {
		return dynamicPage(name: "prefUninstall", title: "Uninstall in progress", nextPage: "prefUninstall", install: false, uninstall: false, refreshInterval: 2) {
			section {
				paragraph "Your uninstall is currently in progress... Please wait..."
				paragraph getBackgroundStatusMessage()
			}
			
		}
	}
	else {
		return complete("Installation complete", "The package was sucessfully uninstalled, click Done.")
	}
}

def performUninstall() {
	login()
	def pkg = state.manifests[pkgUninstall]
	
	initializeRollbackState("uninstall")
			
	for (app in pkg.apps) {
		if (app.heID != null) {
			def sourceCode = getAppSource(app.heID)
			setBackgroundStatusMessage("Uninstalling ${app.name}")
			if (uninstallApp(app.heID))
			{
				completedActions["appUninstalls"] << [id:app.id,source:sourceCode]
			}
			else 
				return rollback("Failed to uninstall app ${app.location}")
		}
	}
	
	for (driver in pkg.drivers) {
		if (driver.heID != null) {
			def sourceCode = getDriverSource(driver.heID)
			setBackgroundStatusMessage("Uninstalling ${driver.name}")
			if (uninstallDriver(driver.heID)) {
				completedActions["driverUninstalls"] << [id:driver.id,source:sourceCode]
			}
			else 
				return rollback("Failed to uninstall driver ${driver.location}")
		}

	}
	state.manifests.remove(pkgUninstall)
	atomicState.inProgress = false
}	

def prefPkgUpdate() {
	if (atomicState.error == true) {
		return buildErrorPage(atomicState.errorTitle, atomicState.errorMessage)
	}
	if (atomicState.inProgress == null) {
		logDebug "Update chosen"
		atomicState.inProgress = true
		runInMillis(1,performUpdateCheck)
	}
	if (atomicState.inProgress != false) {
		return dynamicPage(name: "prefPkgUpdate", title: "Checking for updates", nextPage: "prefPkgUpdate", install: false, uninstall: false, refreshInterval: 2) {
			section {
				paragraph "Checking for updates... Please wait..."
				paragraph getBackgroundStatusMessage()
			}
			
		}
	}
	else {
		if (state.needsUpdate.size() > 0) {
			logDebug "Updates available"
			return dynamicPage(name: "prefPkgUpdate", title: "Updates Available", nextPage: "prefPkgVerifyUpdates", install: false, uninstall: false) {
				section {
					paragraph "Updates are available."
					input "pkgsToUpdate", "enum", title: "Which packages do you want to update?", multiple: true, required: true, options:state.needsUpdate
				}
			}
		}
		else {
			logDebug "No updates available"
			return dynamicPage(name: "prefPkgUpdate", title: "No Updates Available", install: true, uninstall: true) {
				section {
					paragraph "All packages are up to date."
				}
			}
		}
	}
}

// Update packages pathway
def performUpdateCheck() {
	state.needsUpdate = [:]
	state.specificPackageItemsToUpgrade = [:]

	for (pkg in state.manifests) {
		setBackgroundStatusMessage("Checking for updates for ${state.manifests[pkg.key].packageName}")
		def manifest = getJSONFile(pkg.key)
		
		if (newVersionAvailable(manifest.version, state.manifests[pkg.key].version)) {
			state.needsUpdate << ["${pkg.key}": "${state.manifests[pkg.key].packageName} (installed: ${state.manifests[pkg.key].version} current: ${manifest.version})"]
			logDebug "Updates found for package ${pkg.key}"
		} 
		else {
			def appOrDriverNeedsUpdate = false
			for (app in manifest.apps) {
				def installedApp = getAppById(state.manifests[pkg.key], app.id)
				if (app.version != null && installedApp.version != null) {
					if (newVersionAvailable(app.version, installedApp.version)) {
						if (!appOrDriverNeedsUpdate) // Only add a package to the list once
							state.needsUpdate << ["${pkg.key}": "${state.manifests[pkg.key].packageName} (driver or app has a new version)"]
						appOrDriverNeedsUpdate = true
						if (state.specificPackageItemsToUpgrade[pkg.key] == null)
							state.specificPackageItemsToUpgrade[pkg.key] = []
						state.specificPackageItemsToUpgrade[pkg.key] << app.id
						logDebug "Updates found for app ${app.location} -> ${pkg.key}"
					}
				}
			}
			for (driver in manifest.drivers) {
				def installedDriver = getDriverById(state.manifests[pkg.key], driver.id)
				if (driver.version != null && installedDriver.version != null) {
					if (newVersionAvailable(driver.version, installedDriver.version)) {
						if (!appOrDriverNeedsUpdate) // Only add a package to the list once
							state.needsUpdate << ["${pkg.key}": "${state.manifests[pkg.key].packageName} (driver or app has a new version)"]
						appOrDriverNeedsUpdate = true
						if (state.specificPackageItemsToUpgrade[pkg.key] == null)
							state.specificPackageItemsToUpgrade[pkg.key] = []
						state.specificPackageItemsToUpgrade[pkg.key] << driver.id
						logDebug "Updates found for driver ${driver.location} -> ${pkg.key}"
					}
				}
			}
		}
	}
	atomicState.inProgress = false
}

def prefPkgVerifyUpdates() {
	logDebug "Verifying update choices"
	atomicState.statusMessage = ""
	atomicState.inProgress = null
	atomicState.error = null
	atomicState.errorTitle = null
	atomicState.errorMessage = null

	def updatesToInstall = "<ul>"
	
	if (pkgsToUpdate.size() == state.needsUpdate.size())
		app.updateLabel("Hubitat Package Manager")
	else
		app.updateLabel("Hubitat Package Manager <span style='color:green'>Updates Available</span>")
	
	for (pkg in pkgsToUpdate) {
		updatesToInstall += "<li>${state.manifests[pkg].packageName}</li>"
	}
	updatesToInstall += "</ul>"
	return dynamicPage(name: "prefPkgVerifyUpdates", title: "Install Updates?", nextPage: "prefPkgUpdatesComplete", install: false, uninstall: false) {
		section {
			paragraph "The following updates will be installed: ${updatesToInstall} Click next to continue. This may take some time."
		}
	}
}
def prefPkgUpdatesComplete() {
	if (atomicState.error == true) {
		return buildErrorPage(atomicState.errorTitle, atomicState.errorMessage)
	}
	if (atomicState.inProgress == null) {
		logDebug "Performing update"
		atomicState.inProgress = true
		runInMillis(1,performUpdates)
	}
	if (atomicState.inProgress != false) {
		return dynamicPage(name: "prefPkgUpdatesComplete", title: "Installing updates", nextPage: "prefPkgUpdatesComplete", install: false, uninstall: false, refreshInterval: 2) {
			section {
				paragraph "Installing updates... Please wait..."
				paragraph getBackgroundStatusMessage()
			}
		}
	}
	else {
		return complete("Installation complete", "The updates have been installed, click Done.")
	}
}

def shouldUpgrade(pkg, id) {
	
	if (state.specificPackageItemsToUpgrade[pkg] == null)
		return true
	return state.specificPackageItemsToUpgrade[pkg].contains(id)
}

def performUpdates() {
	login()
	// Download all files first to reduce the chances of a network error
	def downloadedManifests = [:]
	def appFiles = [:]
	def driverFiles = [:]
	
	for (pkg in pkgsToUpdate) {
		def manifest = getJSONFile(pkg)
		def installedManifest = state.manifests[pkg]
		
		downloadedManifests[pkg] = manifest

		if (manifest) {
			for (app in manifest.apps) {
				if (isAppInstalled(installedManifest,app.id)) {
					if (shouldUpgrade(pkg, app.id)) {
						setBackgroundStatusMessage("Downloading ${app.name}")
						def fileContents = downloadFile(app.location)
						if (fileContents == null) {
							return triggerError("Error downloading file", "An error occurred downloading ${app.location}")
						}
						appFiles[app.location] = fileContents	
					}
				}
				else if (app.required) {
					setBackgroundStatusMessage("Downloading ${app.name}")
					def fileContents = downloadFile(app.location)
					if (fileContents == null) {
						return triggerError("Error downloading file", "An error occurred downloading ${app.location}")
					}
					appFiles[app.location] = fileContents
				}
			}
			for (driver in manifest.drivers) {
				if (isDriverInstalled(installedManifest,driver.id)) {
					if (shouldUpgrade(pkg, driver.id)) {
						setBackgroundStatusMessage("Downloading ${driver.name}")
						def fileContents = downloadFile(driver.location)
						if (fileContents == null) {
							return triggerError("Error downloading file", "An error occurred downloading ${driver.location}")
						}
						driverFiles[driver.location] = fileContents
					}
				}
				else if (driver.required) {
					setBackgroundStatusMessage("Downloading ${driver.name}")
					def fileContents = downloadFile(driver.location)
					if (fileContents == null) {
						return triggerError("Error downloading file", "An error occurred downloading ${driver.location}")
					}
					driverFiles[driver.location] = fileContents
				}
			}
		}
		else {
			return triggerError("Error downloading file", "The manifest file ${pkg} no longer seems to be valid.")
		}
	}
	
	for (pkg in pkgsToUpdate) {
		def manifest = downloadedManifests[pkg]
		def installedManifest = state.manifests[pkg]
		
		if (manifest) {
			initializeRollbackState("update")
			
			state.updateManifest = manifest
			for (app in manifest.apps) {
				if (isAppInstalled(installedManifest,app.id)) {
					if (shouldUpgrade(pkg, app.id)) {
						app.heID = getAppById(installedManifest, app.id).heID
						def sourceCode = getAppSource(app.heID)
						setBackgroundStatusMessage("Upgrading ${app.name}")
						if (upgradeApp(app.heID, appFiles[app.location])) {
							completedActions["appUpgrades"] << [id:app.heID,source:sourceCode]
							if (app.oauth)
								enableOAuth(app.heID)
						}
						else
							return rollback("Failed to upgrade app ${app.location}")
					}
				}
				else if (app.required) {
					setBackgroundStatusMessage("Installing ${app.name}")
					def id = installApp(appFiles[app.location])
					if (id != null) {
						app.heID = id
						if (app.oauth)
							enableOAuth(app.heID)
					}
					else
						return rollback("Failed to install app ${app.location}")
				}
			}
			
			for (driver in manifest.drivers) {
				if (isDriverInstalled(installedManifest,driver.id)) {
					if (shouldUpgrade(pkg, driver.id)) {
						driver.heID = getDriverById(installedManifest, driver.id).heID
						def sourceCode = getDriverSource(driver.heID)
						setBackgroundStatusMessage("Upgrading ${driver.name}")
						if (upgradeDriver(driver.heID, driverFiles[driver.location])) {
							completedActions["driverUpgrades"] << [id:driver.heID,source:sourceCode]
						}
						else
							return rollback("Failed to upgrade driver ${driver.location}")
					}
				}
				else if (driver.required) {
					setBackgroundStatusMessage("Installing ${driver.name}")
					def id = installDriver(driverFiles[driver.location])
					if (id != null) {
						driver.heID = id
					}
					else
						return rollback("Failed to install driver ${driver.location}")
				}
			}
			state.manifests[pkg] = manifest
		}
		else {
		}
	}
	atomicState.inProgress = false
}

def buildErrorPage(title, message) {
	return dynamicPage(name: "prefError", title: title, install: true, uninstall: false) {
		section {
			paragraph message
		}
	}
}

def checkForUpdates() {
	def updates = false
	for (pkg in state.manifests) {
		def manifest = getJSONFile(pkg.key)
		
		if (newVersionAvailable(manifest.version, state.manifests[pkg.key].version)) {
			updates = true
			break
		} 
		else {
			for (app in manifest.apps) {
				def installedApp = getAppById(state.manifests[pkg.key], app.id)
				if (app.version != null && installedApp.version != null) {
					if (newVersionAvailable(app.version, installedApp.version)) {
						updates = true
						break
					}
				}
			}
			if (updates)
				break
			for (driver in manifest.drivers) {
				def installedDriver = getDriverById(state.manifests[pkg.key], driver.id)
				if (driver.version != null && installedDriver.version != null) {
					if (newVersionAvailable(driver.version, installedDriver.version)) {
						updates = true
						break
					}
				}
			}
			if (updates)
				break
		}
	}
	if (!updates)
		app.updateLabel("Hubitat Package Manager")
	else
		app.updateLabel("Hubitat Package Manager <span style='color:green'>Updates Available</span>")

}

def clearStateSettings(clearProgress) {
	app.removeSetting("pkgInstall")
	app.removeSetting("appsToInstall")
	app.removeSetting("driversToInstall")
	app.removeSetting("pkgModify")
	app.removeSetting("appsToModify")
	app.removeSetting("driversToModify")
	app.removeSetting("pkgUninstall")
	app.removeSetting("pkgsToUpdate")
	app.removeSetting("pkgCategory")
	state.needsUpdate = [:]
	state.specificPackageItemsToUpgrade = [:]
	if (clearProgress) {
		atomicState.statusMessage = ""
		atomicState.inProgress = null
		atomicState.error = null
		atomicState.errorTitle = null
		atomicState.errorMessage = null
	}
}

def initializeRollbackState(action) {
	state.action = action
	completedActions = [:]
	completedActions["appInstalls"] = []
	completedActions["driverInstalls"] = []
	completedActions["appUninstalls"] = []
	completedActions["driverUninstalls"] = []
	completedActions["appUpgrades"] = []
	completedActions["driverUpgrades"] = []
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

def getAppByHEId(manifest, id) {
	for (app in manifest.apps) {
		if (app.heID == id) {
			return app
		}
	}
	return null
}

def getDriverByHEId(manifest, id) {
	for (driver in manifest.drivers) {
		if (driver.heID == id) {
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
	try
	{
		def params = [
			uri: file,
			requestContentType: "application/json",
			contentType: "application/json",
			textParser: true,
			timeout: 300
		]
		def result = null
		httpGet(params) { resp ->
			result = resp.data.text
		}
		return result
	}
	catch (e) {
		log.error "Error downloading ${file}: ${e}"
		return null
	}
}

def getJSONFile(uri) {
	try
	{
		def fileContents = downloadFile(uri)
		return new groovy.json.JsonSlurper().parseText(fileContents)
	}
	catch (e) {
		return null
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

// App installation methods
def installApp(appCode) {
	try
	{
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
			if (resp.headers."Location" != null) {
				result = resp.headers."Location".replaceAll("http://127.0.0.1:8080/app/editor/","")
				completedActions["appInstalls"] << result
			}
			else
				result = null
		}
		return result
	}
	catch (e) {
		log.error "Error installing app: ${e}"
	}
	return null	
}

def upgradeApp(id,appCode) {
	try
	{
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
	catch (e) {
		log.error "Error upgrading app: ${e}"
	}
	return null
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
		return true
	}
	catch (e) {
		log.error "Error uninstalling app ${e}"
		return false
	}
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

def getAppSource(id) {
	try
	{
		def params = [
			uri: "http://127.0.0.1:8080",
			path: "/app/ajax/code",
			requestContentType: "application/x-www-form-urlencoded",
			headers: [
				"Cookie": state.cookie
			],
			query: [
				id: id
			],
			timeout: 300
		]
		def result
		httpGet(params) { resp ->
			result = resp.data.source
		}
		return result
	}
	catch (e) {
		log.error "Error retrieving app source: ${e}"
	}
	return null	
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

// Driver installation methods
def installDriver(driverCode) {
	try
	{
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
			if (resp.headers."Location" != null) {
				result = resp.headers."Location".replaceAll("http://127.0.0.1:8080/driver/editor/","")
				completedActions["driverInstalls"] << result
			}
			else
				result = null
		}
		return result
	}
	catch (e) {
		log.error "Error installing driver: ${e}"
	}
	return null
}

def upgradeDriver(id,appCode) {
	try
	{
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
	catch (e) {
		log.error "Error upgrading driver ${e}"
	}
	return null
}

def uninstallDriver(id) {
	try
	{
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
		return true
	}
	catch (e)
	{
		log.error "Error uninstalling driver: ${e}"
		return false
	}
}

def getDriverSource(id) {
	try
	{
		def params = [
			uri: "http://127.0.0.1:8080",
			path: "/driver/ajax/code",
			requestContentType: "application/x-www-form-urlencoded",
			headers: [
				"Cookie": state.cookie
			],
			query: [
				id: id
			],
			timeout: 300
		]
		def result
		httpGet(params) { resp ->
			result = resp.data.source
		}
		return result
	}
	catch (e) {
		log.error "Error retrieving driver source: ${e}"
	}
	return null	
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

def setBackgroundStatusMessage(msg) {
	if (atomicState.statusMessage == null)
		atomicState.statusMessage = ""
	log.info msg
	atomicState.statusMessage += "${msg}<br>"
}

def getBackgroundStatusMessage() {
	return atomicState.statusMessage
}

def triggerError(title, message) {
	atomicState.error = true
	atomicState.errorTitle = title
	atomicState.errorMessage = message
}

def complete(title, message) {
	state.action = null
	completedActions = null
	state.updateManifest = null
	clearStateSettings(false)
	
	return dynamicPage(name: "prefComplete", title: title, install: true, uninstall: false) {
		section {
			paragraph message
		}
	}
}

def rollback(error) {
	def manifest = null
	if (state.action == "modify")
		manifest = getInstalledManifest(pkgModify)
	else if (state.action == "uninstall")
		manifest = getInstalledManifest(pkgUninstall)
	else if (state.action == "update")
		manifest = state.updateManifest
	setBackgroundStatusMessage("Fatal error occurred, rolling back")
	if (state.action == "install" || state.action == "modify" || state.action == "update") {
		for (installedApp in completedActions["appInstalls"])
			uninstallApp(installedApp)
		for (installedDriver in completedActions["driverInstalls"])
			uninstallDriver(installedDriver)
	}
	if (state.action == "modify" || state.action == "update") {
		for (installedApp in completedActions["appInstalls"])
			getAppByHEId(manifest, installedApp).heID = null
		for (installedDriver in completedActions["driverInstalls"])
			getDriverByHEId(manifest, installedDriver).heID = null
	}
	if (state.action == "modify" || state.action == "uninstall") {
		for (uninstalledApp in completedActions["appUninstalls"]) {
			def newHeID = installApp(uninstalledApp.source)
			def app = getAppById(manifest, uninstalledApp.id)
			if (app.oauth)
				enableOAuth(newHeID)
			app.heID = newHeID
		}
		for (uninstalledDriver in completedActions["driverUninstalls"]) {
			def newHeID = installDriver(uninstalledDriver.source)
			getDriverById(manifest, uninstalledDriver.id).heID = newHeID
		}
	}
	if (state.action == "update") {
		for (upgradedApp in completedActions["appUpgrades"]) {
			upgradeApp(upgradedApp.heID,upgradedApp.source)
		}
		for (upgradedDriver in completedActions["driverUpgrades"]) {
			upgradeDriver(upgradedDriver.heID,upgradedDriver.source)
		}
	}
	state.action = null
	completedActions = null
	state.updateManifest = null
	return triggerError("Error Occurred During Installation", "An error occurred while installing the package: ${error}.")
}

def installHPMManifest()
{
	if (state.manifests[listOfRepositories.hpm.location] == null) {
		logDebug "Grabbing list of installed apps"
		login()
		def appsInstalled = getAppList()
		
		logDebug "Installing HPM Manifest"
		def manifest = getJSONFile(listOfRepositories.hpm.location)
		if (manifest == null) {
			log.error "Error installing HPM manifest"
			return false
		}
		def appId = appsInstalled.find { i -> i.title == "Hubitat Package Manager" && i.namespace == "dcm.hpm"}?.id
		if (appId != null) {
			manifest.apps[0].heID = appId
			state.manifests[listOfRepositories.hpm.location] = manifest
		}
		else
			log.error "Unable to get the app ID of the package manager"
	}
	return true
}

def logDebug(msg) {
	// For initial releases, hard coding debug mode to on.
    //if (settings?.debugOutput) {
		log.debug msg
	//}
}

// Thanks to gavincampbell for the code below!
def getAppList() {
    def params = [
    	uri: "http://127.0.0.1:8080/app/list",
        textParser: true,
        headers: [
			Cookie: state.cookie
		]
      ]
    
	def result = []
    try {
        httpGet(params) { resp ->     
            def matcherText = resp.data.text.replace("\n","").replace("\r","")
            def matcher = matcherText.findAll(/(<tr class="app-row" data-app-id="[^<>]+">.*?<\/tr>)/).each {
                def allFields = it.findAll(/(<td .*?<\/td>)/) // { match,f -> return f } 
                def id = it.find(/data-app-id="([^"]+)"/) { match,i -> return i.trim() }
                def title = allFields[0].find(/title="([^"]+)/) { match,t -> return t.trim() }
                def namespace = allFields[1].find(/>([^"]+)</) { match,ns -> return ns.trim() }
                result += [id:id,title:title,namespace:namespace]
            }
        }
    } catch (e) {
		log.error "Error retrieving installed apps: ${e}"
    }
	return result
}

def getDriverList() {
    def params = [
    	uri: "http://127.0.0.1:8080/driver/list",
        textParser: true,
	    headers: [
			Cookie: state.cookie
		]
      ]
    def result = []
    try {
        httpGet(params) { resp ->
            def matcherText = resp.data.text.replace("\n","").replace("\r","")
            def matcher = matcherText.findAll(/(<tr class="driver-row" data-app-id="[^<>]+">.*?<\/tr>)/).each {
                def allFields = it.findAll(/(<td .*?<\/td>)/) // { match,f -> return f } 
                def title = it.find(/title="([^"]+)/) { match,t -> return t }
                def id = it.find(/data-app-id="([^"]+)"/) { match,i -> return i.trim() }
                def namespace = allFields[1].find(/>([^"]+)</) { match,ns -> return ns.trim() }
                result += [id:id,title:title,namespace:namespace]
			}
        }
    } catch (e) {
        log.error "Error retrieving installed drivers: ${e}"
    }
	return result
}