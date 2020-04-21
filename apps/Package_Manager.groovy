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
	page(name: "prefPkgMatchUp")
	page(name: "prefPkgMatchUpVerify")
	page(name: "prefPkgMatchUpComplete")
	page(name: "prefPkgView")
}

import groovy.transform.Field
@Field static String repositoryListing = "https://raw.githubusercontent.com/dcmeglio/hubitat-packagerepositories/master/repositories.json"
@Field static List categories = [] 
@Field static List allPackages = []
@Field static groovy.json.internal.LazyMap listOfRepositories = [:]
@Field static groovy.json.internal.LazyMap completedActions = [:]
@Field static groovy.json.internal.LazyMap manifestForRollback = null


@Field static String installAction = ""
@Field static String installMode = ""
@Field static String statusMessage = ""
@Field static String errorTitle = ""
@Field static String errorMessage = ""
@Field static boolean errorOccurred = false
@Field static groovy.json.internal.LazyMap packagesWithUpdates = [:]
@Field static groovy.json.internal.LazyMap optionalItemsToShow = [:]
@Field static groovy.json.internal.LazyMap updateDetails = [:]


@Field static List appsToInstallForModify = []
@Field static List appsToUninstallForModify = []
@Field static List driversToInstallForModify = []
@Field static List driversToUninstallForModify = []
@Field static List packagesMatchingInstalledEntries = []

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

def appButtonHandler(btn) {
	switch (btn) {
		case "btnMainMenu":
			state.mainMenu = true
			break
		case "btnAddRepo":
			state.customRepo = true
			break
	}
}

def prefOptions() {
	state.remove("mainMenu")
	if (state.customRepo && customRepo != "" && customRepo != null) {
		def repoListing = getJSONFile(customRepo)
		if (repoListing == null) {
			clearStateSettings(true)
			return buildErrorPage("Error loading repository", "The repository file you specified could not be loaded.")
		} else
		{
			installedRepositories << customRepo
			if (state.customRepositories == null)
				state.customRepositories = [:]
			if (state.customRepositories[customRepo] == null)
				state.customRepositories << ["${customRepo}":repoListing.author]
		}
	}
	if (state.firstRun == true)
		return prefPkgMatchUp()
	else
		clearStateSettings(true)
	if (installedRepositories == null) {
		logDebug "No installed repositories, grabbing all"
		def repos = [] as List
		listOfRepositories.repositories.each { it -> repos << it.location }
		app.updateSetting("installedRepositories", repos)
	}
	return dynamicPage(name: "prefOptions", title: "", install: true, uninstall: false) {
        displayHeader()
		section {
			paragraph "What would you like to do?"
			href(name: "prefPkgInstall", title: "Install", required: false, page: "prefPkgInstall", description: "Install a new package.")
			href(name: "prefPkgModify", title: "Modify", required: false, page: "prefPkgModify", description: "Modify an already installed package. This allows you to add or remove optional components.")
			href(name: "prefPkgUninstall", title: "Uninstall", required: false, page: "prefPkgUninstall", description: "Uninstall a package.")
            href(name: "prefPkgUpdate", title: "Update", required: false, page: "prefPkgUpdate", description: "Check for updates for your installed packages.")
			href(name: "prefPkgMatchUp", title: "Match Up", required: false, page: "prefPkgMatchUp", description: "Match up the apps and drivers you already have installed with packages available so that you can use the package manager to get future updates.")
			href(name: "prefPkgView", title: "View Apps and Drivers", required: false, page: "prefPkgView", description: "View the apps and drivers that are managed by packages.")
			href(name: "prefSettings", title: "Package Manager Settings", required: false, page: "prefSettings", params: [force:true], description: "Modify Hubitat Package Manager Settings.")
		}
		displayFooter()
	}
}

def prefSettings(params) {
	if (state.manifests == null)
		state.manifests = [:]
	
	updateRepositoryListing()

	installHPMManifest()
	if (app.getInstallationState() == "COMPLETE" && params?.force != true) 
		return prefOptions()
	else {
		def showInstall = app.getInstallationState() == "INCOMPLETE"
		if (showInstall)
			state.firstRun = true
		return dynamicPage(name: "prefSettings", title: "", nextPage: "prefOptions", install: showInstall, uninstall: false) {
            displayHeader()
			section ("Hub Security") {
                paragraph "<b>Hubitat Connection Configuration</b>"
				paragraph "In order to automatically install apps and drivers you must specify your Hubitat admin username and password if Hub Security is enabled."
				input "hpmSecurity", "bool", title: "Hub Security Enabled", submitOnChange: true
				if (hpmSecurity)
				{
					input "hpmUsername", "string", title: "Hub Security username", required: true
					input "hpmPassword", "password", title: "Hub Security password", required: true
				}
				if (showInstall)
					paragraph "Please click Done and restart the app to continue."
			}
			if (!state.firstRun) {
				section ("Settings") {
					input "debugOutput", "bool", title: "Enable debug logging", defaultValue: true
				}
				def reposToShow = [:]
				listOfRepositories.repositories.each { r -> reposToShow << ["${r.location}":r.name] }
				if (state.customRepositories != null)
					state.customRepositories.each { r -> reposToShow << ["${r.key}":r.value] }
				reposToShow = reposToShow.sort { r -> r.value }
				section ("Repositories")
				{
					input "installedRepositories", "enum", title: "Available repositories", options: reposToShow, multiple: true, required: true
					if (!state.customRepo)
					input "btnAddRepo", "button", title: "Add a Custom Repository", submitOnChange: false
					if (state.customRepo)
						input "customRepo", "text", title: "Enter the URL of the repository's directory listing file", required: true
				}
			}
		}
	}
}

// Install a package pathway
def prefPkgInstall() {
	if (state.mainMenu)
		return prefOptions()
	logDebug "prefPkgInstall"
	
	return dynamicPage(name: "prefPkgInstall", title: "", install: true, uninstall: false) {
        displayHeader()
		section {
            paragraph "<b>Install a Package</b>"
			paragraph "How would you like to install this package?"
			href(name: "prefPkgInstallRepository", title: "From a Repository", required: false, page: "prefPkgInstallRepository", description: "Choose a package from a repository.")
			href(name: "prefPkgInstallUrl", title: "From a URL", required: false, page: "prefPkgInstallUrl", description: "Install a package using a URL to a specific package. This is an advanced feature, only use it if you know how to find a package's manifest manually.")
			
		}
		section {
            paragraph "<hr>"
            input "btnMainMenu", "button", title: "Main Menu", width: 3
        }
	}
}

def prefPkgInstallUrl() {
	if (state.mainMenu)
		return prefOptions()
	logDebug "prefPkgInstallUrl"
	installMode = "url"

	return dynamicPage(name: "prefPkgInstallUrl", title: "", nextPage: "prefInstallChoices", install: false, uninstall: false) {
        displayHeader()
		section {
            paragraph "<b>Install a Package from URL</b>"
			input "pkgInstall", "text", title: "Enter the URL of a package you wish to install (this should be a path to a <code>packageManifest.json</code> file).", required: true
		}
		section {
            paragraph "<hr>"
			input "btnMainMenu", "button", title: "Main Menu", width: 3
        }
	}
}

def prefPkgInstallRepository() {
	if (errorOccurred == true) {
		return buildErrorPage(errorTitle, errorMessage)
	}
	if (atomicState.backgroundActionInProgress == null) {
		logDebug "prefPkgInstallRepository"
		atomicState.backgroundActionInProgress = true
		runInMillis(1,performRepositoryRefresh)
	}
	if (atomicState.backgroundActionInProgress != false) {
		return dynamicPage(name: "prefPkgInstallRepository", title: "", nextPage: "prefPkgInstallRepository", install: false, uninstall: false, refreshInterval: 2) {
			section {
                paragraph "<b>Install a Package</b>"
				paragraph "Refreshing repositories... Please wait..."
				paragraph getBackgroundStatusMessage()
			}
		}
	}
	else {
		installMode = "repository"
		prefInstallChoices()
	}
}

def prefInstallChoices() {
	if (state.mainMenu)
		return prefOptions()
	logDebug "prefInstallChoices"
	atomicState.backgroundActionInProgress = null
	statusMessage = ""		
	errorOccurred = null
	errorTitle = null
	errorMessage = null
	
    return dynamicPage(name: "prefInstallChoices", title: "", nextPage: "prefInstallVerify", install: false, uninstall: false) {
        displayHeader()
		section {
            paragraph "<b>Install a Package from a Repository</b>"
			if (installMode == "repository")
			{
				input "pkgCategory", "enum", title: "Choose a category", options: categories, required: true, submitOnChange: true
			
				if(pkgCategory) {
					input "sortBy", "bool", title: "Sort packages by Author?", description: "Sorting", defaultValue: false, submitOnChange: true
					input "pkgFilterInstalled", "bool", title: "Filter packages that are already installed?", submitOnChange: true
					
					def matchingPackages = [:]
					for (pkg in allPackages) {
						if (pkgFilterInstalled && state.manifests.containsKey(pkg.location))
							continue
						if (pkg.category == pkgCategory) {
							if(sortBy) matchingPackages << ["${pkg.location}":"(${pkg.author}) - ${pkg.name} - ${pkg.description}"]
							if(!sortBy) matchingPackages << ["${pkg.location}":"${pkg.name} - (${pkg.author}) - ${pkg.description}"]
						}
					}
					def sortedMatchingPackages = matchingPackages.sort { a, b -> a.value <=> b.value }
					input "pkgInstall", "enum", title: "Choose a package", options: sortedMatchingPackages, required: true, submitOnChange: true
				}
			}
		}
        
        if(pkgInstall) {
            if (state.manifests == null)
            state.manifests = [:]
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
                if (apps.size() == 0 && drivers.size() == 0)
                title = "Ready to install"

                section("${title}") {
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
		section {
            paragraph "<hr>"
            input "btnMainMenu", "button", title: "Main Menu", width: 3
        }
    }	
}

def getRepoName(location) {
	return listOfRepositories.repositories.find { it -> it.location == location }?.name
}

def performRepositoryRefresh() {
	allPackages = []
	categories = []

	for (repo in installedRepositories) {
		def repoName = getRepoName(repo)
		setBackgroundStatusMessage("Refreshing ${repoName}")
		def fileContents = getJSONFile(repo)
		if (!fileContents) {
			log.warn "Error refreshing ${repoName}"
			setBackgroundStatusMessage("Failed to refresh ${repoName}")
			continue
		}
		for (pkg in fileContents.packages) {
			def pkgDetails = [
				repository: repoName,
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
	allPackages = allPackages.sort()
	categories = categories.sort()
	atomicState.backgroundActionInProgress = false
}

def prefInstallVerify() {
	if (state.mainMenu)
		return prefOptions()
	logDebug "prefInstallVerify"
	
    return dynamicPage(name: "prefInstallVerify", title: "", nextPage: "prefInstall", install: false, uninstall: false) {
        displayHeader()
		section {
            paragraph "<b>Ready to install</b>"
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
		section {
            paragraph "<hr>"
            input "btnMainMenu", "button", title: "Main Menu", width: 3
        }
	}
}

def prefInstall() {
	if (state.mainMenu)
		return prefOptions()
	if (errorOccurred == true) {
		return buildErrorPage(errorTitle, errorMessage)
	}
	if (atomicState.backgroundActionInProgress == null) {
		logDebug "prefInstall"
		logDebug "Install beginning"
		atomicState.backgroundActionInProgress = true
		runInMillis(1,performInstallation)
	}
	if (atomicState.backgroundActionInProgress != false) {
		return dynamicPage(name: "prefInstall", title: "", nextPage: "prefInstall", install: false, uninstall: false, refreshInterval: 2) {
            displayHeader()
			section {
                paragraph "<b>Installing</b>"
				paragraph "Your installation is currently in progress... Please wait..."
				paragraph getBackgroundStatusMessage()
			}
		}
	}
	else {
		return complete("Installation complete", "The package was sucessfully installed, click Next to return to the Main Menu.")
	}
}

def performInstallation() {
	if (!login())
		return triggerError("Error logging in to hub", "An error occurred logging into the hub. Please verify your Hub Security username and password.")
	def manifest = getJSONFile(pkgInstall)
	state.manifests[pkgInstall] = manifest
	minimizeStoredManifests()
	
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
	atomicState.backgroundActionInProgress = false
}

// Modify a package pathway
def prefPkgModify() {
	if (state.mainMenu)
		return prefOptions()
	logDebug "prefPkgModify"
	def pkgsToList = getInstalledPackages(true)
	return dynamicPage(name: "prefPkgModify", title: "", nextPage: "prefPkgModifyChoices", install: false, uninstall: false) {
        displayHeader()
		section {
            paragraph "<b>Modify a Package</b>"
			paragraph "Only packages that have optional components are shown below."
			input "pkgModify", "enum", title: "Choose the package to modify", options: pkgsToList, required: true
		}
		section {
            paragraph "<hr>"
            input "btnMainMenu", "button", title: "Main Menu", width: 3
        }
	}
}

def prefPkgModifyChoices() {
	if (state.mainMenu)
		return prefOptions()
	logDebug "prefPkgModifyChoices"
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
		
		return dynamicPage(name: "prefPkgModifyChoices", title: "", nextPage: "prefVerifyPackageChanges", install: false, uninstall: false) {
            displayHeader()
			section {
                paragraph "<b>Modify a Package</b>"
				paragraph "Items below that are checked are currently installed. Those that are not checked are currently <b>not</b> installed."
				if (optionalApps.size() > 0)
					input "appsToModify", "enum", title: "Select the apps to install/uninstall", options: optionalApps, hideWhenEmpty: true, multiple: true, defaultValue: installedOptionalApps
				if (optionalDrivers.size() > 0)
					input "driversToModify", "enum", title: "Select the drivers to install/uninstall", options: optionalDrivers, hideWhenEmpty: true, multiple: true, defaultValue: installedOptionalDrivers
			}
			section {
				paragraph "<hr>"
				input "btnMainMenu", "button", title: "Main Menu", width: 3
			}
		}
	}
	else {
		return dynamicPage(name: "prefPkgModifyChoices", title: "", install: true, uninstall: false) {
			section {
                paragraph "<b>Nothing to modify</b>"
				paragraph "This package does not have any optional components that you can modify."
			}
			section {
				paragraph "<hr>"
				input "btnMainMenu", "button", title: "Main Menu", width: 3
			}
		}
	}
}

def prefVerifyPackageChanges() {
	if (state.mainMenu)
		return prefOptions()
	logDebug "prefVerifyPackageChanges"
	def appsToUninstallStr = "<ul>"
	def appsToInstallStr = "<ul>"
	def driversToUninstallStr = "<ul>"
	def driversToInstallStr = "<ul>"
	appsToUninstallForModify = []
	appsToInstallForModify = []
	driversToUninstallForModify = []
	driversToInstallForModify = []
	def hasChanges = false
	
	def manifest = getInstalledManifest(pkgModify)
	for (optApp in appsToModify) {
		if (!isAppInstalled(manifest,optApp)) {
			appsToInstallStr += "<li>${getAppById(manifest,optApp).name}</li>"
			appsToInstallForModify << optApp
			hasChanges = true
		}
	}
	appsToInstallStr += "</ul>"
	for (optDriver in driversToModify) {
		if (!isDriverInstalled(manifest,optDriver)) {
			driversToInstallStr += "<li>${getDriverById(manifest,optDriver).name}</li>"
			driversToInstallForModify << optDriver
			hasChanges = true
		}
	}
	driversToInstallStr += "</ul>"
	
	def installedApps = getInstalledOptionalApps(manifest)
	def installedDrivers = getInstalledOptionalDrivers(manifest)
	for (installedApp in installedApps) {
		if (!appsToModify?.contains(installedApp)) {
			appsToUninstallStr += "<li>${getAppById(manifest,installedApp).name}</li>"
			appsToUninstallForModify << installedApp
			hasChanges = true
		}
	}
	appsToUninstallStr += "</ul>"
	
	for (installedDriver in installedDrivers) {
		if (!driversToModify?.contains(installedDriver)) {
			driversToUninstallStr += "<li>${getDriverById(manifest,installedDriver).name}</li>"
			driversToUninstallForModify << installedDriver
			hasChanges = true
		}
	}
	driversToUninstallStr += "</ul>"

	if (hasChanges) {
		return dynamicPage(name: "prefVerifyPackageChanges", title: "", nextPage: "prefMakePackageChanges", install: false, uninstall: false) {
            displayHeader()
			section {
                paragraph "<b>Modify a Package</b>"
				paragraph "The following changes will be made. Click next when you are ready. This may take some time."
				if (appsToUninstallStr != "<ul></ul>")
					paragraph "The following apps will be uninstalled: ${appsToUninstallStr}"
				if (appsToInstallStr != "<ul></ul>")
					paragraph "The following apps will be installed: ${appsToInstallStr}"
				if (driversToUninstallStr != "<ul></ul>")
					paragraph "The following drivers will be uninstalled: ${driversToUninstallStr}"
				if (driversToInstallStr != "<ul></ul>")
					paragraph "The following drivers will be installed: ${driversToInstallStr}"
				
				if (driversToUninstallStr != "<ul></ul>" || appsToUninstallStr != "<ul></ul>")
					paragraph "Please be sure that the apps and drivers to be uninstalled are not in use before clicking Next."
			}
			section {
				paragraph "<hr>"
				input "btnMainMenu", "button", title: "Main Menu", width: 3
			}
		}
	}
	else {
		return dynamicPage(name: "prefVerifyPackageChanges", title: "", install: true, uninstall: false) {
            displayHeader()
			section {
                paragraph "<b>Nothing to modify</b>"
				paragraph "You did not make any changes."
			}
			section {
				paragraph "<hr>"
				input "btnMainMenu", "button", title: "Main Menu", width: 3
			}
		}
	}
}

def prefMakePackageChanges() {
	if (state.mainMenu)
		return prefOptions()
	if (errorOccurred == true) {
		return buildErrorPage(errorTitle, errorMessage)
	}
	if (atomicState.backgroundActionInProgress == null) {
		logDebug "Executing modify"
		atomicState.backgroundActionInProgress = true
		runInMillis(1,performModify)
	}
	if (atomicState.backgroundActionInProgress != false) {
		return dynamicPage(name: "prefMakePackageChanges", title: "", nextPage: "prefInstall", install: false, uninstall: false, refreshInterval: 2) {
            displayHeader()
			section {
                paragraph "<b>Modifying Package</b>"
				paragraph "Your changes are currently in progress... Please wait..."
				paragraph getBackgroundStatusMessage()
			}
		}
	}
	else {
		return complete("Modification complete", "The package was sucessfully modified, click Next to return to the Main Menu.")
	}
}

def performModify() {
	if (!login())
		return triggerError("Error logging in to hub", "An error occurred logging into the hub. Please verify your Hub Security username and password.")
	
	// Download all files first to reduce the chances of a network error
	def appFiles = [:]
	def driverFiles = [:]
	def manifest = getInstalledManifest(pkgModify)
	
	for (appToInstall in appsToInstallForModify) {
		def app = getAppById(manifest, appToInstall)
		setBackgroundStatusMessage("Downloading ${app.name}")
		def fileContents = downloadFile(app.location)
		if (fileContents == null) {
			return triggerError("Error downloading file", "An error occurred downloading ${app.location}")
		}
		appFiles[app.location] = fileContents
	}
	for (driverToInstall in driversToInstallForModify) {
		def driver = getDriverById(manifest, driverToInstall)
		setBackgroundStatusMessage("Downloading ${driver.name}")
		def fileContents = downloadFile(driver.location)
		if (fileContents == null) {
			return triggerError("Error downloading file", "An error occurred downloading ${driver.location}")
		}
		driverFiles[driver.location] = fileContents
	}
	
	initializeRollbackState("modify")
	for (appToInstall in appsToInstallForModify) {
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
	for (appToUninstall in appsToUninstallForModify) {
		def app = getAppById(manifest, appToUninstall)
		def sourceCode = getDriverSource(app.heID)
		setBackgroundStatusMessage("Uninstalling ${app.name}")
		if (uninstallApp(app.heID)) {
			completedActions["appUninstalls"] << [id:app.id,source:sourceCode]
			app.heID = null
		}
		else
			return rollback("Failed to uninstall app ${app.location}, it may be in use. Please delete all instances of this app before uninstalling the package.")
	}
	
	for (driverToInstall in driversToInstallForModify) {
		def driver = getDriverById(manifest, driverToInstall)
		setBackgroundStatusMessage("Installing ${driver.name}")
		def id = installDriver(driverFiles[driver.location])
		if (id != null) {
			driver.heID = id
		}
		else
			return rollback("Failed to install driver ${driver.location}, it may be in use.")
		
	}
	for (driverToUninstall in driversToUninstallForModify) {
		def driver = getDriverById(manifest, driverToUninstall)
		def sourceCode = getDriverSource(driver.heID)
		setBackgroundStatusMessage("Uninstalling ${driver.name}")
		if (uninstallDriver(driver.heID)) {
			completedActions["driverUninstalls"] << [id:driver.id,source:sourceCode]
			driver.heID = null
		}
		else
			return rollback("Failed to uninstall driver ${driver.location}. Please delete all instances of this device before uninstalling the package.")
	}
	atomicState.backgroundActionInProgress = false
}

// Uninstall a package pathway
def prefPkgUninstall() {
	if (state.mainMenu)
		return prefOptions()
	logDebug "prefPkgUninstall"
	def pkgsToList = getInstalledPackages(false)

	return dynamicPage(name: "prefPkgUninstall", title: "", nextPage: "prefPkgUninstallConfirm", install: false, uninstall: false) {
        displayHeader()
		section {
            paragraph "<b>Uninstall a Package</b>"
			input "pkgUninstall", "enum", title: "Choose the package to uninstall", options: pkgsToList, required: true
		}
		section {
            paragraph "<hr>"
            input "btnMainMenu", "button", title: "Main Menu", width: 3
        }
	}
}

def prefPkgUninstallConfirm() {
	if (state.mainMenu)
		return prefOptions()
	logDebug "prefPkgUninstallConfirm"
	return dynamicPage(name: "prefPkgUninstallConfirm", title: "", nextPage: "prefUninstall", install: false, uninstall: false) {
        displayHeader()
		section {
            paragraph "<b>Uninstall a Package</b>"
			paragraph "The following apps and drivers will be removed:"
			
			def str = "<ul>"
			def pkg = state.manifests[pkgUninstall]
			for (app in pkg.apps) {
				if (app.heID != null)
					str += "<li>${app.name} (App)</li>"
			}
			
			for (driver in pkg.drivers) {
				if (driver.heID != null)
					str += "<li>${driver.name} (Device Driver)</li>"
			}
			str += "</ul>"
			paragraph str
			paragraph "Please be sure that the app and device drivers are not in use, then click Next to continue."
		}
		section {
            paragraph "<hr>"
            input "btnMainMenu", "button", title: "Main Menu", width: 3
        }
	}
}

def prefUninstall() {
	if (state.mainMenu)
		return prefOptions()
	if (errorOccurred == true) {
		return buildErrorPage(errorTitle, errorMessage)
	}
	if (atomicState.backgroundActionInProgress == null) {
		logDebug "Performing uninstall"
		atomicState.backgroundActionInProgress = true
		runInMillis(1,performUninstall)
	}
	if (atomicState.backgroundActionInProgress != false) {
		return dynamicPage(name: "prefUninstall", title: "", nextPage: "prefUninstall", install: false, uninstall: false, refreshInterval: 2) {
            displayHeader()
			section {
                paragraph "<b>Uninstall in progress</b>"
				paragraph "Your uninstall is currently in progress... Please wait..."
				paragraph getBackgroundStatusMessage()
			}
		}
	}
	else {
		return complete("Uninstall complete", "The package was sucessfully uninstalled, click Next  to return to the Main Menu.")
	}
}

def performUninstall() {
	if (!login())
		return triggerError("Error logging in to hub", "An error occurred logging into the hub. Please verify your Hub Security username and password.")
		
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
				return rollback("Failed to uninstall app ${app.location}, it may be in use. Please delete all instances of this app before uninstalling the package.")
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
				return rollback("Failed to uninstall driver ${driver.location}. Please delete all instances of this device before uninstalling the package.")
		}

	}
	state.manifests.remove(pkgUninstall)
	atomicState.backgroundActionInProgress = false
}	

def addUpdateDetails(pkgId, pkgName, releaseNotes, updateType, item) {
	if (updateDetails[pkgId] == null)
		updateDetails[pkgId] = [name: null, releaseNotes: null, items: []]
	if (pkgName != null)
		updateDetails[pkgId].name = pkgName
	if (releaseNotes != null)
		updateDetails[pkgId].releaseNotes = releaseNotes
	updateDetails[pkgId].items << [type: updateType,  id: item?.id, name: item?.name]
	
	logDebug "Updates found ${updateType} for ${pkgId} -> ${item?.name}"
}
// Update packages pathway
def performUpdateCheck() {
	packagesWithUpdates = [:]

	for (pkg in state.manifests) {
		setBackgroundStatusMessage("Checking for updates for ${state.manifests[pkg.key].packageName}")
		def manifest = getJSONFile(pkg.key)
		
		if (manifest == null) {
			log.warn "Found a bad manifest ${pkg.key}"
			continue
		}

		if (newVersionAvailable(manifest.version, state.manifests[pkg.key].version)) {
			packagesWithUpdates << ["${pkg.key}": "${state.manifests[pkg.key].packageName} (installed: ${state.manifests[pkg.key].version} current: ${manifest.version})"]
			logDebug "Updates found for package ${pkg.key}"
			addUpdateDetails(pkg.key, manifest.packageName, manifest.releaseNotes, "package", null)
		} 
		else {
			def appOrDriverNeedsUpdate = false
			for (app in manifest.apps) {
                try {
				def installedApp = getAppById(state.manifests[pkg.key], app.id)
				if (app?.version != null && installedApp?.version != null) {
					if (newVersionAvailable(app.version, installedApp.version)) {
						if (!appOrDriverNeedsUpdate) { // Only add a package to the list once
							packagesWithUpdates << ["${pkg.key}": "${state.manifests[pkg.key].packageName} (driver or app has a new version)"]
						}
						appOrDriverNeedsUpdate = true
						addUpdateDetails(pkg.key, manifest.packageName, manifest.releaseNotes, "specificapp", app)
					}
				}
				else if ((!installedApp || (!installedApp.required && installedApp.heID == null)) && app.required) {
					if (!appOrDriverNeedsUpdate) { // Only add a package to the list once
						packagesWithUpdates << ["${pkg.key}": "${state.manifests[pkg.key].packageName} (driver or app has a new requirement)"]
					}
					appOrDriverNeedsUpdate = true
					addUpdateDetails(pkg.key, manifest.packageName, manifest.releaseNotes, "reqapp", app)
				}
				else if (!installedApp && !app.required) {
					if (!appOrDriverNeedsUpdate) { // Only add a package to the list once
						packagesWithUpdates << ["${pkg.key}": "${state.manifests[pkg.key].packageName} (new optional app or driver is available)"]
					}
					appOrDriverNeedsUpdate = true
					addUpdateDetails(pkg.key, manifest.packageName, manifest.releaseNotes, "optapp", app)
				}
                }
                catch (any) { log.warn "Bad manifest for ${state.manifests[pkg.key].packageName}.  Please notify developer. "}
			}
			for (driver in manifest.drivers) {
        try {
				def installedDriver = getDriverById(state.manifests[pkg.key], driver.id)
				if (driver?.version != null && installedDriver?.version != null) {
					if (newVersionAvailable(driver.version, installedDriver.version)) {
						if (!appOrDriverNeedsUpdate) {// Only add a package to the list once
							packagesWithUpdates << ["${pkg.key}": "${state.manifests[pkg.key].packageName} (driver or app has a new version)"]
						}
						appOrDriverNeedsUpdate = true
						addUpdateDetails(pkg.key, manifest.packageName, manifest.releaseNotes, "specificdriver", driver)
					}
				}
				else if ((!installedDriver || (!installedDriver.required && installedDriver.heID == null)) && driver.required) {
					if (!appOrDriverNeedsUpdate) { // Only add a package to the list once
						packagesWithUpdates << ["${pkg.key}": "${state.manifests[pkg.key].packageName} (driver or app has a new requirement)"]
					}
					appOrDriverNeedsUpdate = true
					addUpdateDetails(pkg.key, manifest.packageName, manifest.releaseNotes, "reqdriver", driver)
				}
				else if (!installedDriver && !driver.required) {
					addUpdateDetails(pkg.key, manifest.packageName, manifest.releaseNotes, "optdriver", driver)
					if (!appOrDriverNeedsUpdate) { // Only add a package to the list once
						packagesWithUpdates << ["${pkg.key}": "${state.manifests[pkg.key].packageName} (new optional app or driver is available)"]
					}
					appOrDriverNeedsUpdate = true
				}
                }
                catch (any) {log.warn "Bad manifest for ${state.manifests[pkg.key].packageName}.  Please notify developer."}
			}
		}
	}
	packagesWithUpdates = packagesWithUpdates.sort { it -> it.value }
	atomicState.backgroundActionInProgress = false
}

def prefPkgUpdate() {
	if (state.mainMenu)
		return prefOptions()
	if (errorOccurred == true) {
		return buildErrorPage(errorTitle, errorMessage)
	}
	if (atomicState.backgroundActionInProgress == null) {
		logDebug "Update chosen"
		updateDetails = [:]
		optionalItemsToShow = [:]
		atomicState.backgroundActionInProgress = true
		runInMillis(1,performUpdateCheck)
	}
	if (atomicState.backgroundActionInProgress != false) {
		return dynamicPage(name: "prefPkgUpdate", title: "", nextPage: "prefPkgUpdate", install: false, uninstall: false, refreshInterval: 2) {
            displayHeader()
			section {
                paragraph "<b>Checking for updates</b>"
				paragraph "Checking for updates... Please wait..."
				paragraph getBackgroundStatusMessage()
			}
		}
	}
	else if (atomicState.backgroundActionInProgress == false) {
		if (packagesWithUpdates.size() > 0) {
			logDebug "Updates available"
			return dynamicPage(name: "prefPkgUpdate", title: "", nextPage: "prefPkgVerifyUpdates", install: false, uninstall: false) {
                displayHeader()
				section {
                    paragraph "<b>Updates Available</b>"
					paragraph "Updates are available."
					input "pkgsToUpdate", "enum", title: "Which packages do you want to update?", multiple: true, required: true, options:packagesWithUpdates, submitOnChange: true
				}
				if (updateDetails?.size() > 0 && pkgsToUpdate != null) {
					for (pkgToUpdate in pkgsToUpdate) {
						def updateDetailsForPkg = updateDetails[pkgToUpdate]
						for (details in updateDetailsForPkg.items) {
							if (details.type == "optapp" || details.type == "optdriver") {
								optionalItemsToShow["${pkgToUpdate}~${details.id}"] = "${details.name} (${updateDetailsForPkg.name})"
							}
						}
					}
					if (optionalItemsToShow?.size() > 0) {
						section {
							input "pkgsToAddOpt", "enum", title: "One or more packages has new optional components. Choose which ones to add", multiple: true, options:optionalItemsToShow
						}
					}
				}
				section {
					paragraph "<hr>"
					input "btnMainMenu", "button", title: "Main Menu", width: 3
				}
			}
		}
		else {
			logDebug "No updates available"
			return dynamicPage(name: "prefPkgUpdate", title: "", install: true, uninstall: false) {
                displayHeader()
				section {
                    paragraph "<b>No Updates Available</b>"
					paragraph "All packages are up to date."
				}
				section {
					paragraph "<hr>"
					input "btnMainMenu", "button", title: "Main Menu", width: 3
				}
			}
		}
	}
}

def prefPkgVerifyUpdates() {
	if (state.mainMenu)
		return prefOptions()
	logDebug "prefPkgVerifyUpdates"
	statusMessage = ""
	atomicState.backgroundActionInProgress = null
	errorOccurred = null
	errorTitle = null
	errorMessage = null

	def updatesToInstall = "<ul>"
	
	if (pkgsToUpdate.size() == packagesWithUpdates.size())
		app.updateLabel("Hubitat Package Manager")
	else
		app.updateLabel("Hubitat Package Manager <span style='color:green'>Updates Available</span>")
	
	
	for (pkg in pkgsToUpdate) {
		updatesToInstall += "<li>${state.manifests[pkg].packageName}"
		
		if (updateDetails[pkg].releaseNotes != null) {
			updatesToInstall += "<br>"
			updatesToInstall += "<textarea rows=6 cols=80 readonly='true'>${updateDetails[pkg].releaseNotes}</textarea>"
		}
		
		updatesToInstall += "</li>"
	}
	
	updatesToInstall += "</ul>"
	def optStrToInstall = "<ul>"
	for (optItem in pkgsToAddOpt) {
		optStrToInstall += "<li>${optionalItemsToShow[optItem]}</li>"
	}
	optStrToInstall += "</ul>"
	
	return dynamicPage(name: "prefPkgVerifyUpdates", title: "", nextPage: "prefPkgUpdatesComplete", install: false, uninstall: false) {
        displayHeader()
		section {
            paragraph "<b>Install Updates?</b>"
			paragraph "The following updates will be installed: ${updatesToInstall}"
			if (optStrToInstall != "<ul></ul>")
				paragraph "The following optional items will be added: ${optStrToInstall}"
			paragraph "Click Next to continue. This may take some time."
		}
		section {
            paragraph "<hr>"
            input "btnMainMenu", "button", title: "Main Menu", width: 3
        }
	}
}
def prefPkgUpdatesComplete() {
	if (state.mainMenu)
		return prefOptions()
	
	if (errorOccurred == true) {
		return buildErrorPage(errorTitle, errorMessage)
	}
	if (atomicState.backgroundActionInProgress == null) {
		logDebug "Performing update"
		atomicState.backgroundActionInProgress = true
		runInMillis(1,performUpdates)
	}
	if (atomicState.backgroundActionInProgress != false) {
		return dynamicPage(name: "prefPkgUpdatesComplete", title: "", nextPage: "prefPkgUpdatesComplete", install: false, uninstall: false, refreshInterval: 2) {
            displayHeader()
			section {
                paragraph "<b>Installing Updates</b>"
				paragraph "Installing updates... Please wait..."
				paragraph getBackgroundStatusMessage()
			}
		}
	}
	else {
		return complete("Updates complete", "The updates have been installed, click Next to return to the Main Menu.")
	}
}

def shouldUpgrade(pkg, id) {
	def pkgUpdateDetails = updateDetails[pkg]

	for (updateItem in pkgUpdateDetails.items) {
		if (updateItem.type == "package")
			return true
		else if ((updateItem.type == "specificapp" || updateItem.type == "specificdriver") && updateItem.id == id)
			return true
	}
	return false
}

def optionalItemsOnly(pkg) {
	def pkgUpdateDetails = updateDetails[pkg]
	for (updateItem in pkgUpdateDetails.items) {
		if (updateItem.type == "package" || updateItem.type == "specificapp" || updateItem.type == "specificdriver" || updateItem.type == "reqapp" || updateItem.type == "reqdriver")
			return false
	}
	return true
}

def performUpdates() {
	if (!login())
		return triggerError("Error logging in to hub", "An error occurred logging into the hub. Please verify your Hub Security username and password.")
		
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
				else if (app.required && !optionalItemsOnly(pkg)) {
					setBackgroundStatusMessage("Downloading ${app.name} because it is required and not installed")
					def fileContents = downloadFile(app.location)
					if (fileContents == null) {
						return triggerError("Error downloading file", "An error occurred downloading ${app.location}")
					}
					appFiles[app.location] = fileContents
				}
				else {
					for (optItem in pkgsToAddOpt) {
						def splitParts = optItem.split('~')
						if (splitParts[0] == pkg) {
							if (splitParts[1] == app.id) {
								setBackgroundStatusMessage("Downloading optional component ${app.name}")
								def fileContents = downloadFile(app.location)
								if (fileContents == null) {
									return triggerError("Error downloading file", "An error occurred downloading ${app.location}")
								}
								appFiles[app.location] = fileContents
							}
						}
					}
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
				else if (driver.required && !optionalItemsOnly(pkg)) {
					setBackgroundStatusMessage("Downloading ${driver.name} because it is required and not installed")
					def fileContents = downloadFile(driver.location)
					if (fileContents == null) {
						return triggerError("Error downloading file", "An error occurred downloading ${driver.location}")
					}
					driverFiles[driver.location] = fileContents
				}
				else {
					for (optItem in pkgsToAddOpt) {
						def splitParts = optItem.split(':')
						if (splitParts[0] == pkg && splitParts[1] == driver.id) {
							setBackgroundStatusMessage("Downloading optional component ${driver.name}")
							def fileContents = downloadFile(driver.location)
							if (fileContents == null) {
								return triggerError("Error downloading file", "An error occurred downloading ${driver.location}")
							}
							driverFiles[driver.location] = fileContents
						}
					}
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
			
			manifestForRollback = manifest
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
				else if (app.required && !optionalItemsOnly(pkg)) {
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
				else {
					for (optItem in pkgsToAddOpt) {
						def splitParts = optItem.split('~')
						if (splitParts[0] == pkg) {
							if (splitParts[1] == app.id) {
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
					}
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
				else if (driver.required && !optionalItemsOnly(pkg)) {
					setBackgroundStatusMessage("Installing ${driver.name}")
					def id = installDriver(driverFiles[driver.location])
					if (id != null) {
						driver.heID = id
					}
					else
						return rollback("Failed to install driver ${driver.location}")
				}
				else {
					for (optItem in pkgsToAddOpt) {
						def splitParts = optItem.split('~')
						if (splitParts[0] == pkg) {
							if (splitParts[1] == driver.id) {
								setBackgroundStatusMessage("Installing ${driver.name}")
								def id = installApp(appFiles[driver.location])
								if (id != null) {
									driver.heID = id
								}
								else
									return rollback("Failed to install driver ${driver.location}")
							}
						}
					}
				}
			}
			if (state.manifests[pkg] != null)
				copyInstalledItemsToNewManifest(state.manifests[pkg], manifest)
			state.manifests[pkg] = manifest
			minimizeStoredManifests()
		}
		else {
		}
	}
	atomicState.backgroundActionInProgress = false
}

def prefPkgMatchUp() {
	if (state.mainMenu)
		return prefOptions()
	logDebug "prefPkgMatchUp"

	return dynamicPage(name: "prefPkgMatchUp", title: "", nextPage: "prefPkgMatchUpVerify", install: false, uninstall: false) {
        displayHeader()
		section {
            paragraph "<b>Match Installed Apps and Drivers</b>"
			paragraph "This will go through all of the apps and drivers you currently have installed in Hubitat and attempt to find matching packages. This process can take minutes or even hours depending on how many apps and drivers you have installed. Click Next to continue."
		}
		if (!state.firstRun) {
			section {
				paragraph "<hr>"
				input "btnMainMenu", "button", title: "Main Menu", width: 3
			}
		}
	}
}

def prefPkgMatchUpVerify() {
	if (state.mainMenu)
		return prefOptions()
	if (errorOccurred == true) {
		return buildErrorPage(errorTitle, errorMessage)
	}
	if (atomicState.backgroundActionInProgress == null) {
		logDebug "Performing Package Matching"
		atomicState.backgroundActionInProgress = true
		runInMillis(1,performPackageMatchup)
	}
	if (atomicState.backgroundActionInProgress != false) {
		return dynamicPage(name: "prefPkgMatchUpVerify", title: "", nextPage: "prefPkgMatchUpVerify", install: false, uninstall: false, refreshInterval: 2) {
            displayHeader()
			section {
                paragraph "<b>Matching Installed Apps and Drivers</b>"
				paragraph "Matching packages... Please wait..."
				paragraph getBackgroundStatusMessage()
			}
		}
	}
	else {
		if (packagesMatchingInstalledEntries?.size() > 0)
		{
			def itemsForList = [:]
			for (pkg in packagesMatchingInstalledEntries) {
				
				def appAndDriverMatches = ((pkg.matchedApps?.collect { it -> it.title } ?: []) + (pkg.matchedDrivers?.collect { it -> it.title } ?: [])).join(", ")			
				itemsForList << ["${pkg.location}":"${pkg.name} - matched (${appAndDriverMatches})"]
			}
			itemsForList = itemsForList.sort { it-> it.value}
			return dynamicPage(name: "prefPkgMatchUpVerify", title: "", nextPage: "prefPkgMatchUpComplete", install: false, uninstall: false) {
                displayHeader()
				section {
                    paragraph "<b>Found Matching Packages</b>"
					paragraph "The following matches were found. There is a possibility that some may have matched incorrectly. Only check off the items that you believe are correct."
					input "pkgMatches", "enum", title: "Choose packages to match", required: true, multiple: true, options: itemsForList
					input "pkgUpToDate", "bool", title: "Assume that packages are up-to-date? If set, the currently installed version will be marked as up-to-date. If not set, next time you run an update check this package will be updated."
				}
				if (!state.firstRun) {
					section {
						paragraph "<hr>"
						input "btnMainMenu", "button", title: "Main Menu", width: 3
					}
				}
			}			
		}
		else {
			state.firstRun = false
			return complete("Match Up Complete", "No matching packages were found, click Next to return to the Main Menu.")
		}

	}	
}

def performPackageMatchup() {
	if (!login())
		return triggerError("Error logging in to hub", "An error occurred logging into the hub. Please verify your Hub Security username and password.")
		
	setBackgroundStatusMessage("Retrieving list of installed apps")
	def allInstalledApps = getAppList()
	setBackgroundStatusMessage("Retrieving list of installed drivers")
	def allInstalledDrivers = getDriverList()
	
	// Filter out anything that already has an associated package
	for (manifest in state.manifests) {
		for (app in manifest.value.apps) {
			if (app.heID != null)
				allInstalledApps.removeIf {it -> it.id == app.heID}
		}
		for (driver in manifest.value.drivers) {
			if (driver.heID != null)
				allInstalledDrivers.removeIf {it -> it.id == driver.heID}
		}
	}
	
	def packagesToMatchAgainst = []
	for (repo in installedRepositories) {
		def repoName = getRepoName(repo)
		setBackgroundStatusMessage("Refreshing ${repoName}")
		def fileContents = getJSONFile(repo)
		if (!fileContents) {
			log.warn "Error refreshing ${repoName}"
			setBackgroundStatusMessage("Failed to refresh ${repoName}")
			continue
		}
		for (pkg in fileContents.packages) {
			def manifestContents = getJSONFile(pkg.location)
			if (manifestContents == null)
				log.warn "Found a bad manifest ${pkg.location}"
			else {
				def pkgDetails = [
					repository: repoName,
					name: pkg.name,
					location: pkg.location,
					manifest: manifestContents
				]
				packagesToMatchAgainst << pkgDetails
			}
		}
	}
	
	packagesMatchingInstalledEntries = []
	setBackgroundStatusMessage("Matching up packages")
	for (pkg in packagesToMatchAgainst) {
		def matchedInstalledApps = []
		def matchedInstalledDrivers = []
		
		for (app in pkg.manifest.apps) {
			def appsToAdd = allInstalledApps.find { it -> it.title == app.name && it.namespace == app.namespace}
			if (appsToAdd != null)
				matchedInstalledApps << appsToAdd
		}
		for (driver in pkg.manifest.drivers) {
			def driversToAdd = allInstalledDrivers.find { it -> it.title == driver.name && it.namespace == driver.namespace}
			if (driversToAdd != null)
				matchedInstalledDrivers << driversToAdd
		}
		if (matchedInstalledApps?.size() > 0 || matchedInstalledDrivers?.size() > 0) {
			pkg.matchedApps = matchedInstalledApps
			pkg.matchedDrivers = matchedInstalledDrivers
			packagesMatchingInstalledEntries << pkg
		}
	}
	
	atomicState.backgroundActionInProgress = false
}

def prefPkgMatchUpComplete() {
	if (state.mainMenu)
		return prefOptions()
	logDebug "prefPkgMatchUpComplete"
	
	for (match in pkgMatches) {
		def matchFromState = packagesMatchingInstalledEntries.find {it -> it.location == match}
		if (matchFromState) {
			def manifest = matchFromState.manifest
			def installedApps = matchFromState.matchedApps
			def installedDrivers = matchFromState.matchedDrivers
			if (!pkgUpToDate && manifest.version != null)
				manifest.version = "0.0"
			for (app in manifest.apps) {
				def installedApp = installedApps.find { it -> it.title == app.name && it.namespace == app.namespace }
				if (installedApp != null) {
					app.heID = installedApp.id
					if (!pkgUpToDate && app.version != null)
						app.version = "0.0"
				}
			}
			
			for (driver in manifest.drivers) {
				def installedDriver = installedDrivers.find { it -> it.title == driver.name && it.namespace == driver.namespace }
				if (installedDriver != null) {
					driver.heID = installedDriver.id
					if (!pkgUpToDate && driver.version != null)
						driver.version = "0.0"
				}
			}
			if (state.manifests[match])
				copyInstalledItemsToNewManifest(state.manifests[match], manifest)
			
			state.manifests[match] = manifest
			minimizeStoredManifests()
		}
	}
	state.firstRun = false
	return dynamicPage(name: "prefPkgMatchUpComplete", title: "", install: true, uninstall: false) {
        displayHeader()
		section {
            paragraph "<b>Match Up Complete</b>"
			if (pkgUpToDate)
				paragraph "The selected packages have been marked as installed. Click Done to continue."
			else
				paragraph "The selected packages have been marked as installed. Click Done to continue. If you wish to update the packages to the latest version, run an <b>Update</b>."
		}
		section {
            paragraph "<hr>"
            input "btnMainMenu", "button", title: "Main Menu", width: 3
        }
	}
}

def prefPkgView() {
	if (state.mainMenu)
		return prefOptions()
	logDebug "prefPkgView"

	def appsManaged = []
	def driversManaged = []
	
	def str = "<ul>"
	
	def sortedPkgs = state.manifests.sort{ it-> it.value.packageName}
	for (pkg in sortedPkgs) {
		str += "<li><b>${pkg.value.packageName}</b>"
		if (pkg.value.documentationLink != null)
			str += " <a href='${pkg.value.documentationLink}' target='_blank'>Documentation</a>"
		if (pkg.value.communityLink != null)
			str += " <a href='${pkg.value.communityLink}' target='_blank'>Community Thread</a>"
		str += "<ul>"
		for (app in pkg.value.apps?.sort { it -> it.name}) {
			str += "<li>${app.name} (app)</li>"
		}
		for (driver in pkg.value.drivers?.sort { it -> it.name}) {
			str += "<li>${driver.name} (driver)</li>"
		}
		str += "</ul></li>"
	}
	str += "</ul>"


	return dynamicPage(name: "prefPkgView", title: "", install: true, uninstall: false) {
        displayHeader()
		section {
            paragraph "<b>View Apps and Drivers</b>"
			paragraph "The apps and drivers listed below are managed by the Hubitat Package Manager."
			paragraph str
		}
		section {
			paragraph "<hr>"
			input "btnMainMenu", "button", title: "Main Menu", width: 3
		}
	}
}

def buildErrorPage(title, message) {
	return dynamicPage(name: "prefError", title: "", install: true, uninstall: false) {
        displayHeader()
		section {
            paragraph "<b>${title}</b>"
			paragraph message
		}
		section {
            paragraph "<hr>"
            input "btnMainMenu", "button", title: "Main Menu", width: 3
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
				if (app?.version != null && installedApp?.version != null) {
					if (newVersionAvailable(app.version, installedApp.version)) {
						updates = true
						break
					}
				}
				else if ((!installedApp || (!installedApp.required && installedApp.heID == null)) && app.required) {
					updates = true
					break
				}
				else if (!installedApp && !app.required) {
					updates = true
					break
				}
			}
			if (updates)
				break
			for (driver in manifest.drivers) {
				def installedDriver = getDriverById(state.manifests[pkg.key], driver.id)
				if (driver?.version != null && installedDriver?.version != null) {
					if (newVersionAvailable(driver.version, installedDriver.version)) {
						updates = true
						break
					}
				}
				else if ((!installedDriver || (!installedDriver.required && installedDriver.heID == null)) && driver.required) {
					updates = true
					break
				}
				else if (!installedDriver && !driver.required) {
					updates = true
					break
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
	installMode = null
	app.removeSetting("pkgInstall")
	app.removeSetting("appsToInstall")
	app.removeSetting("driversToInstall")
	app.removeSetting("pkgModify")
	app.removeSetting("appsToModify")
	app.removeSetting("driversToModify")
	app.removeSetting("pkgUninstall")
	app.removeSetting("pkgsToUpdate")
	app.removeSetting("pkgsToAddOpt")
	app.removeSetting("pkgCategory")
	app.removeSetting("pkgMatches")
	app.removeSetting("pkgUpToDate")
	packagesWithUpdates = [:]
	updateDetails = [:]
	packagesMatchingInstalledEntries = []
	optionalItemsToShow = [:]
	state.customRepo = false
	app.removeSetting("customRepo")
	if (clearProgress) {
		statusMessage = ""
		atomicState.backgroundActionInProgress = null
		errorOccurred = null
		errorTitle = null
		errorMessage = null
	}
	
	// Things that used to be in state that are not any longer. Clean up
	state.remove("action")
	atomicState.remove("statusMessage")
	atomicState.remove("inProgress")
	atomicState.remove("errorTitle")
	atomicState.remove("errorMessage")
	atomicState.remove("error")
	atomicState.remove("completedActions")
	state.remove("releaseNotes")
	state.remove("needsUpdate")
	state.remove("packageToInstall")
	state.remove("specificPackageItemsToUpgrade")
	state.remove("appsToInstall")
	state.remove("appsToUninstall")
	state.remove("driversToInstall")
	state.remove("driversToUninstall")
	state.remove("packagesWithMatches")
	state.remove("updateManifest")
}

def initializeRollbackState(action) {
	installAction = action
	completedActions = [:]
	completedActions["appInstalls"] = []
	completedActions["driverInstalls"] = []
	completedActions["appUninstalls"] = []
	completedActions["driverUninstalls"] = []
	completedActions["appUpgrades"] = []
	completedActions["driverUpgrades"] = []
}

def getInstalledPackages(onlyWithOptional) {
	def pkgsToList = [:]
	for (pkg in state.manifests) {
		if (!onlyWithOptional || pkg.value.apps?.find {it -> it.required == false } || pkg.value.drivers?.find {it -> it.required == false })
			pkgsToList[pkg.key] = pkg.value.packageName
	}
	pkgsToList = pkgsToList.sort { it -> it.value }
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
	if (versionStr == null)
		return false
	versionStr = versionStr.replaceAll("[^\\d.]", "")
    installedVersionStr = installedVersionStr?.replaceAll("[^\\d.]", "")
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
		def result = false
		try
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
					],
					textParser: true
				]
			)
			{ resp ->
				if (resp.data?.text?.contains("The login information you supplied was incorrect."))
					result = false
				else {
					state.cookie = resp?.headers?.'Set-Cookie'?.split(';')?.getAt(0)
					result = true
				}
			}
		}
		catch (e)
		{
			log.error "Error logging in: ${e}"
			result = false
		}
		return result
	}
	else
		return true
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
				getAppSource(result)
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
			timeout: 300,
			textParser: true
		]
		def result = true
		httpPost(params) { resp ->
			if (resp.data == null)
				result = true
			else {
				def matcherText = resp.data.text.replace("\n","").replace("\r","")
				def matcher = matcherText.find(/<div class="alert-close close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;<\/span><\/div>(.+?)<\/div>/)
				if (matcher)
					result = false
			}
		}
		return result
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
			timeout: 300,
			textParser: true
		]
		def result = true
		httpPost(params) { resp ->
			if (resp.data == null)
				result = true
			else {
				def matcherText = resp.data.text.replace("\n","").replace("\r","")
				def matcher = matcherText.find(/<div class="alert-close close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;<\/span><\/div>(.+?)<\/div>/)
				if (matcher)
					result = false
			}
		}
		return result
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
	if (statusMessage == null)
		statusMessage = ""
	log.info msg
	statusMessage += "${msg}<br>"
}

def getBackgroundStatusMessage() {
	return statusMessage
}

def triggerError(title, message) {
	errorOccurred = true
	errorTitle = title
	errorMessage = message
}

def complete(title, message) {
	installAction = null
	completedActions = null
	manifestForRollback = null
	clearStateSettings(false)
	
	return dynamicPage(name: "prefComplete", title: "", install: false, uninstall: false, nextPage: "prefOptions") {
        displayHeader()
		section {
            paragraph "<b>${title}</b>"
			paragraph message
		}
	}
}

def rollback(error) {
	def manifest = null
	if (installAction == "modify")
		manifest = getInstalledManifest(pkgModify)
	else if (installAction == "uninstall")
		manifest = getInstalledManifest(pkgUninstall)
	else if (installAction == "update")
		manifest = manifestForRollback
	setBackgroundStatusMessage("Fatal error occurred, rolling back")
	if (installAction == "install" || installAction == "modify" || installAction == "update") {
		for (installedApp in completedActions["appInstalls"])
			uninstallApp(installedApp)
		for (installedDriver in completedActions["driverInstalls"])
			uninstallDriver(installedDriver)
	}
	if (installAction == "modify" || installAction == "update") {
		for (installedApp in completedActions["appInstalls"])
			getAppByHEId(manifest, installedApp).heID = null
		for (installedDriver in completedActions["driverInstalls"])
			getDriverByHEId(manifest, installedDriver).heID = null
	}
	if (installAction == "modify" || installAction == "uninstall") {
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
	if (installAction == "update") {
		for (upgradedApp in completedActions["appUpgrades"]) {
			upgradeApp(upgradedApp.heID,upgradedApp.source)
		}
		for (upgradedDriver in completedActions["driverUpgrades"]) {
			upgradeDriver(upgradedDriver.heID,upgradedDriver.source)
		}
	}
	installAction = null
	completedActions = null
	manifestForRollback = null
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
			minimizeStoredManifests()
		}
		else
			log.error "Unable to get the app ID of the package manager"
	}
	return true
}

def updateRepositoryListing()
{
	logDebug "Refreshing repository list"
	def oldListOfRepositories = listOfRepositories
	listOfRepositories = getJSONFile(repositoryListing)
	if (installedRepositories == null) {
		def repos = [] as List
		listOfRepositories.repositories.each { it -> repos << it.location }
		app.updateSetting("installedRepositories", repos)
	}
	else {
		for (newRepo in listOfRepositories.repositories) {
			if (oldListOfRepositories.size() > 0 && !oldListOfRepositories.repositories.find { it -> it.location == newRepo.location} && !installedRepositories.contains(newRepo.location)) {
				logDebug "Found new repository ${newRepo.location}"
				installedRepositories << newRepo.location
			}
		}
		app.updateSetting("installedRepositories", installedRepositories)
	}
}

def copyInstalledItemsToNewManifest(srcManifest, destManifest) {
	def srcInstalledApps = srcManifest.apps?.findAll { it -> it.heID != null }
	def srcInstalledDrivers = srcManifest.drivers?.findAll { it -> it.heID != null  && destManifest.drivers?.find { dit -> dit.id == it.id && dit.heID == null}}
	
	for (app in srcInstalledApps) {
		def destApp = destManifest.apps?.find { it -> it.id == app.id }
		if (destApp && destApp.heID == null)
			destApp.heID = app.heID
	}
	
	for (driver in srcInstalledDrivers) {
		def destDriver = destManifest.drivers?.find { it -> it.id == driver.id }
		if (destDriver && destDriver.heID == null)
			destDriver.heID = driver.heID
	}
}

def minimizeStoredManifests() {
	for (manifest in state.manifests) {
		if (manifest.value.licenseFile != null)
			manifest.value.remove("licenseFile")
		if (manifest.value.releaseNotes != null)
			manifest.value.remove("releaseNotes")
		if (manifest.value.dateReleased != null)
			manifest.value.remove("dateReleased")
		if (manifest.value.minimumHEVersion != null)
			manifest.value.remove("minimumHEVersion")
	}
}

def logDebug(msg) {
	// For initial releases, hard coding debug mode to on.
    if (settings?.debugOutput) {
		log.debug msg
	}
}

def getDriverList() {
    def params = [
    	uri: "http://127.0.0.1:8080/device/drivers",
	    headers: [
			Cookie: state.cookie
		]
      ]
    def result = []
    try {
        httpGet(params) { resp ->
			for (driver in resp.data.drivers) {
				if (driver.type == "usr")
					result += [id:driver.id.toString(),title:driver.name,namespace:driver.namespace]
			}
        }
    } catch (e) {
        log.error "Error retrieving installed drivers: ${e}"
    }
	return result
}

def getImage(type) {					// Modified from @Stephack Code
    def loc = "<img src=https://raw.githubusercontent.com/bptworld/Hubitat/master/resources/images/"
    if(type == "Blank") return "${loc}blank.png height=40 width=5}>"
    //if(type == "logo") return "${loc}logo.png height=60>"
}

def getFormat(type, myText=""){			// Modified from @Stephack Code   
    if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
    if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}

def displayHeader() {
    section (getFormat("title", "${getImage("Blank")}" + " Hubitat Package Manager")) {
		paragraph getFormat("line")
	}
}

def displayFooter(){
	section() {
		paragraph getFormat("line")
		paragraph "<div style='color:#1A77C9;text-align:center'>Hubitat Package Manager<br><a href='https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=7LBRPJRLJSDDN&source=url' target='_blank'><img src='https://www.paypalobjects.com/webstatic/mktg/logo/pp_cc_mark_37x23.jpg' border='0' alt='PayPal Logo'></a><br><br>Please consider donating. This app took a lot of work to make.<br>If you find it valuable, I'd certainly appreciate it!</div>"
	}       
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
