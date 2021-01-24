/**
 *
 *  Hubitat Package Manager v1.8.0
 *
 *  Copyright 2020 Dominick Meglio
 *
 *    If you find this useful, donations are always appreciated 
 *    https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=7LBRPJRLJSDDN&source=url
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
	documentationLink: "https://github.com/dcmeglio/hubitat-packagemanager/blob/master/README.md",
	singleInstance: true)

preferences {
	page(name: "prefSettings")
	page(name: "prefOptions")
	page(name: "prefPkgInstall")
	page(name: "prefPkgInstallUrl")
	page(name: "prefInstallRepositorySearch")
	page(name: "prefInstallRepositorySearchResults")
	page(name: "prefPkgInstallRepository")
	page(name: "prefPkgInstallRepositoryChoose")
	page(name: "prefPkgModify")
	page(name: "prefPkgRepair")
	page(name: "prefPkgRepairExecute")
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
import java.util.regex.Matcher

@Field static String repositoryListing = "https://raw.githubusercontent.com/dcmeglio/hubitat-packagerepositories/master/repositories.json"
@Field static String settingsFile = "https://raw.githubusercontent.com/dcmeglio/hubitat-packagerepositories/master/settings.json"
@Field static String searchApiUrl = "https://hubitatpackagemanager.azurewebsites.net/graphql"
@Field static List categories = [] 
@Field static List allPackages = []
@Field static def completedActions = [:]
@Field static def manifestForRollback = null

@Field static def downloadQueue = [:]
@Field static Integer maxDownloadQueueSize = 10


@Field static String installAction = ""
@Field static String installMode = ""
@Field static String statusMessage = ""
@Field static String errorTitle = ""
@Field static String errorMessage = ""
@Field static Boolean errorOccurred = false
@Field static def packagesWithUpdates = [:]
@Field static def optionalItemsToShow = [:]
@Field static def updateDetails = [:]


@Field static List appsToInstallForModify = []
@Field static List appsToUninstallForModify = []
@Field static List driversToInstallForModify = []
@Field static List driversToUninstallForModify = []
@Field static List packagesMatchingInstalledEntries = []

@Field static List iconTags = ["ZWave", "Zigbee", "Cloud", "LAN"]

def installed() {
	initialize()
}

def updated() {
	unschedule()
	initialize()
}

def initialize() {
	def timeOfDayForUpdateChecks
	if (updateCheckTime == null)
		timeOfDayForUpdateChecks = timeToday("00:00")
	else
		timeOfDayForUpdateChecks = timeToday(updateCheckTime, location.timeZone)
	schedule("00 ${timeOfDayForUpdateChecks.minutes} ${timeOfDayForUpdateChecks.hours} ? * *", checkForUpdates)
	
	performMigrations()
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
		case "btnBack":
			state.back = true
		case "btnAddRepo":
			state.customRepo = true
			break
		case ~/^btnDeleteRepo(\d+)/:
			deleteCustomRepository(Matcher.lastMatcher[0][1].toInteger())
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
		} 
		else {
			installedRepositories << customRepo
			if (state.customRepositories == null)
				state.customRepositories = [:]
			if (state.customRepositories[customRepo] == null)
				state.customRepositories << ["${customRepo}":repoListing.author]
		}
	}

	if (state.firstRun == true)
		return prefPkgMatchUp()
	else {
		clearStateSettings(true)
		initialize()
		installHPMManifest()
	}
	if (installedRepositories == null) {
		logDebug "No installed repositories, grabbing all"
		def repos = [] as List
		state.repositoryListingJSON.repositories.each { it -> repos << it.location }
		app.updateSetting("installedRepositories", repos)
	}

	state.categoriesAndTags = loadSettingsFile()
	return dynamicPage(name: "prefOptions", title: "", install: true, uninstall: false) {
		displayHeader()

		if (isHubSecurityEnabled() && !hpmSecurity) {
			section {
				paragraph "<b>Hub Security appears to be enabled but is not configured in HPM. Please configure hub security."
			}
		}

		if (state.newRepoMessage != "") {
			section {
				paragraph state.newRepoMessage
				state.newRepoMessage = ""
			}
		}
		section {
			paragraph "What would you like to do?"
			href(name: "prefPkgInstall", title: "Install", required: false, page: "prefPkgInstall", description: "Install a new package.")
			href(name: "prefPkgUpdate", title: "Update", required: false, page: "prefPkgUpdate", description: "Check for updates for your installed packages.")
			href(name: "prefPkgModify", title: "Modify", required: false, page: "prefPkgModify", description: "Modify an already installed package. This allows you to add or remove optional components.")
			href(name: "prefPkgRepair", title: "Repair", required: false, page: "prefPkgRepair", description: "Repair a package by ensuring all of the newest versions are installed in case something went wrong.")
			href(name: "prefPkgUninstall", title: "Uninstall", required: false, page: "prefPkgUninstall", description: "Uninstall packages.")
			href(name: "prefPkgMatchUp", title: "Match Up", required: false, page: "prefPkgMatchUp", description: "Match up the apps and drivers you already have installed with packages available so that you can use the package manager to get future updates.")
			href(name: "prefPkgView", title: "View Apps and Drivers", required: false, page: "prefPkgView", description: "View the apps and drivers that are managed by packages.")
			href(name: "prefSettings", title: "Package Manager Settings", required: false, page: "prefSettings", params: [force:true], description: "Modify Hubitat Package Manager Settings.")
		}
		displayFooter()
	}
}

def prefSettings(params) {
	def showSettingsForSecurityEnablement = false
	state.newRepoMessage = ""
	if (state.manifests == null)
		state.manifests = [:]

	performMigrations()
	if (updateRepositoryListing()?.size() > 0) {
		state.newRepoMessage = "<b>One or more new repositories have been added. You may want to do a Match Up to ensure all of your packages are detected.</b>"
	}

	if (isHubSecurityEnabled() && !hpmSecurity) {
		showSettingsForSecurityEnablement = true
	}

	installHPMManifest()
	if (app.getInstallationState() == "COMPLETE" && params?.force != true && !showSettingsForSecurityEnablement) 
		return prefOptions()
	else {
		def showInstall = app.getInstallationState() == "INCOMPLETE"
		if (showInstall)
			state.firstRun = true
		return dynamicPage(name: "prefSettings", title: "", nextPage: "prefOptions", install: showInstall, uninstall: false) {
			displayHeader()
			
			section ("Hub Security") {
				if (showSettingsForSecurityEnablement) {
					paragraph "<b>Hub Security appears to be enabled on your hub but is not enabled within HPM. Please configure hub security below</b>"
				}
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
				section ("General") {
					input "debugOutput", "bool", title: "Enable debug logging", defaultValue: true
					input "includeBetas", "bool", title: "When updating, install pre-release versions. Note: Pre-releases often include more bugs and should be considered beta software"
				}
				section ("Package Updates") {
					input "updateCheckTime", "time", title: "Specify what time update checking should be performed", defaultValue: "00:00", required: true        

					input "notifyUpdatesAvailable", "bool", title: "Notify me when updates are available", submitOnChange: true
					if (notifyUpdatesAvailable)
						input "notifyDevices", "capability.notification", title: "Devices to notify", required: true, multiple: true
					
					input "autoUpdateMode", "enum", title: "Install updates automatically when", options: ["Always":"Always", "Never":"Never", "Include":"Only those I list", "Exclude":"All packages except the ones I list"], submitOnChange: true
					if (autoUpdateMode != "Never") {
						if (autoUpdateMode == "Include") {
							def listOfPackages = getInstalledPackages(false)
							input "appsToAutoUpdate", "enum", title: "Which packages should be automatically updated?", required: true, multiple: true, options:listOfPackages
						}
						else if (autoUpdateMode == "Exclude") {
							def listOfPackages = getInstalledPackages(false)
							input "appsNotToAutoUpdate", "enum", title: "Which packages should NOT be automatically updated?", required: true, multiple: true, options:listOfPackages
						}
						
						input "notifyOnSuccess", "bool", title: "Notify me if automatic updates are successful", submitOnChange: true
						if (notifyOnSuccess)
							input "notifyUpdateSuccessDevices", "capability.notification", title: "Devices to notify", required: true, multiple: true
						input "notifyOnFailure", "bool", title: "Notify me if automatic updates are unsuccessful", submitOnChange: true
						if (notifyOnFailure)
							input "notifyUpdateFailureDevices", "capability.notification", title: "Devices to notify", required: true, multiple: true
					}
					
					if (notifyUpdatesAvailable || notifyOnSuccess || notifyOnFailure) 
						input "notifyIncludeHubName", "bool", title: "Include hub name in notifications", defaultValue: false
					if (notifyOnSuccess || notifyOnFailure)
						input "notifySpecificPackages", "bool", title: "Send notifications for each specific package", defaultValue: false
				}
				def reposToShow = [:]
				state.repositoryListingJSON.repositories.each { r -> reposToShow << ["${r.location}":r.name] }
				if (state.customRepositories != null)
					state.customRepositories.each { r -> reposToShow << ["${r.key}":r.value] }
				reposToShow = reposToShow.sort { r -> r.value }
				section ("Repositories")
				{
					input "installedRepositories", "enum", title: "Available repositories", options: reposToShow, multiple: true, required: true

					if (state.customRepositories && state.customRepositories.size()) {
						def i = 0
						for (customRepo in state.customRepositories.keySet()) {
							paragraph "${state.customRepositories[customRepo]} - ${customRepo}", width: 10
							input "btnDeleteRepo${i}", "button", title: "Remove", width: 2
							i++
						}
					}

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
			href(name: "prefInstallRepositorySearch", title: "Search by Keywords", required: false, page: "prefInstallRepositorySearch", description: "Search for packages by searching for keywords. <b>This will only include the standard repositories, <i>not</i> custom repositories.</b>")
			href(name: "prefPkgInstallRepository", title: "Browse by Tags", required: false, page: "prefPkgInstallRepository", description: "Choose a package from a repository browsing by tags. <b>This will include both the standard repositories and any custom repositories you have setup.</b>")
			href(name: "prefPkgInstallUrl", title: "From a URL", required: false, page: "prefPkgInstallUrl", description: "Install a package using a URL to a specific package. This is an advanced feature, only use it if you know how to find a package's manifest manually.")
			
		}
		section {
			paragraph "<hr>"
			input "btnMainMenu", "button", title: "Main Menu", width: 3
		}
	}
}

def prefInstallRepositorySearch() {
	if (state.mainMenu)
		return prefOptions()
	state.remove("back")
	logDebug "prefInstallRepositorySearch"
	installMode = "search"

	return dynamicPage(name: "prefInstallRepositorySearch", title: "", nextPage: "prefInstallRepositorySearchResults", install: false, uninstall: false) {
		displayHeader()
		section {
			paragraph "<b>Search</b>"
			input "pkgSearch", "text", title: "Enter your search criteria", required: true
		}
		section {
			paragraph "<hr>"
			input "btnMainMenu", "button", title: "Main Menu", width: 3
		}
	}
}

def prefInstallRepositorySearchResults() {
	if (state.mainMenu)
		return prefOptions()
	if (state.back)
		return prefInstallRepositorySearch()
	logDebug "prefInstallRepositorySearchResults"
	installMode = "search"
	
	def params = [
		uri: searchApiUrl,
		contentType: "application/json",
		requestContentType: "application/json",
		body: [
			"operationName": null,
			"variables": [
				"searchQuery": pkgSearch
			],
			"query": 'query Search($searchQuery: String) { repositories { author, gitHubUrl, payPalUrl, packages (search: $searchQuery) {name, description, location, tags}}}'
		]
	]
	
	def result = null
	httpPost(params) { resp -> 
		result = resp.data
	}

	if (result?.data?.repositories) {
		def searchResults = []
		for (repo in result.data.repositories) {
			for (packageItem in repo.packages) {
				packageItem << [author: repo.author, gitHubUrl: repo.gitHubUrl, payPalUrl: repo.payPalUrl, installed: state.manifests[packageItem.location] != null]
				searchResults << packageItem
			}
		}
		searchResults = searchResults.sort { it -> it.name }
		return dynamicPage(name: "prefInstallRepositorySearch", title: "", nextPage: "prefInstallRepositorySearchResults", install: false, uninstall: false) {
			displayHeader()
			
			section {
				paragraph "<b>Search Results for ${pkgSearch}</b>"
				addCss()
			}    
			section {
				if (searchResults.size() > 0) {
					def i = 0
					for (searchResult in searchResults) {
						renderPackageButton(searchResult,i)
						i++
					}
				}
				else
					paragraph "No matching packages were found. Click Back to return to the search screen."
			}
			section {
				paragraph "<hr>"
				input "btnMainMenu", "button", title: "Main Menu", width: 3
				input "btnBack", "button", title: "Back", width: 3
			}
			
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
		getMultipleJSONFiles(installedRepositories, performRepositoryRefreshComplete, performRepositoryRefreshStatus)
	}
	if (atomicState.backgroundActionInProgress != false) {
		return dynamicPage(name: "prefPkgInstallRepository", title: "", nextPage: "prefPkgInstallRepository", install: false, uninstall: false, refreshInterval: 2) {
			section {
				showHideNextButton(false)
				paragraph "<b>Install a Package</b>"
				paragraph "Refreshing repositories... Please wait..."
				paragraph getBackgroundStatusMessage()
			}
		}
	}
	else {
		installMode = "repository"
		return prefInstallChoices(null)
	}
}

def renderTags(pkgList) {
	def tags = []

	for (pkg in pkgList) {
		
		if (state.categoriesAndTags.categories.contains(pkg.category)) {
			if (!tags.contains(pkg.category))
				tags << pkg.category
		}
		else
			log.warn "Invalid category found ${pkg.category} for ${pkg.location}"
		for (tag in pkg.tags) {
			if (state.categoriesAndTags.tags.contains(tag)) {
				if (!tags.contains(tag))
					tags << tag
			}
			else
				log.warn "Invalid tag found ${tag} for ${pkg.location}"
		}
	}
	tags = tags.sort()
	input "pkgTags", "enum", title: "Choose tag(s)", options: tags, submitOnChange: true, multiple: true
}

def renderSortBy() {
	input "pkgSortBy", "enum", title: "Sort By", options: ["Author", "Name"], submitOnChange: true, defaultValue: "Name"
}

def prefInstallChoices(params) {
	if (state.mainMenu)
		return prefOptions()
	logDebug "prefInstallChoices"

	return dynamicPage(name: "prefInstallChoices", title: "", nextPage: "prefInstallVerify", install: false, uninstall: false) {
		displayHeader()
		section {
			addCss()
			paragraph "<b>Install a Package from a Repository</b>"
			if (installMode == "repository" && params == null)
			{
				//input "pkgCategory", "enum", title: "Choose a category", options: categories, required: true, submitOnChange: true
				renderTags(allPackages)
				if(pkgTags) {
					renderSortBy()
					
					def matchingPackages = []
					for (pkg in allPackages) {
						if (state.manifests.containsKey(pkg.location)) {
							pkg.installed = true
						}
						if (pkgTags.contains(pkg.category) || pkg.tags.find { pkgTags.contains(it)}) {
							matchingPackages << pkg
						}
					}
					def sortedMatchingPackages
					if (pkgSortBy == "Name")
						 sortedMatchingPackages = matchingPackages.sort { x -> x.name }
					else
						sortedMatchingPackages = matchingPackages.sort { y -> y.author }

					if (sortedMatchingPackages.size() > 0) {
						def i = 0
						for (pkg in sortedMatchingPackages) {                        
							renderPackageButton(pkg,i)
							i++
						}
					}
					else
						paragraph "No matching packages were found. Please choose a different category."
					}
			}
		}
		
		if (installMode == "search" || (installMode == "repository" && params != null)) { 
			pkgInstall = params.location
			app.updateSetting("pkgInstall", params.location)
			state.payPalUrl = params.payPalUrl
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

			if (manifest.minimumHEVersion != null && !verifyHEVersion(manifest.minimumHEVersion)) {
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
	return state.repositoryListingJSON.repositories.find { it -> it.location == location }?.name
}

def performRepositoryRefreshStatus(uri, data)
{
	def repoName = getRepoName(uri)
	setBackgroundStatusMessage("Refreshed ${repoName}")
}

def performRepositoryRefreshComplete(results, data)
{
	allPackages = []
	categories = []

	for (uri in results.keySet()) {
		def repoName = getRepoName(uri)
		def result = results[uri]
		def fileContents = result.result
		if (fileContents == null) {
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
				category: pkg.category,
				tags: pkg.tags
			]
			
			allPackages << pkgDetails
			if (!categories.contains(pkgDetails.category))
				categories << pkgDetails.category
		}
	}

	allPackages = allPackages.sort()
	categories = categories.sort()
	atomicState.backgroundActionInProgress = false
	logDebug "Repositories refreshed"
}

def prefInstallVerify() {
	if (state.mainMenu)
		return prefOptions()
	logDebug "prefInstallVerify"
	
	atomicState.backgroundActionInProgress = null
	statusMessage = ""        
	errorOccurred = null
	errorTitle = null
	errorMessage = null
	
	
	return dynamicPage(name: "prefInstallVerify", title: "", nextPage: "prefInstall", install: false, uninstall: false) {
		displayHeader()
		section {
			paragraph "<b>Ready to install</b>"
			def manifest = getJSONFile(pkgInstall)
			if (manifest.licenseFile) {
				def license = downloadFile(manifest.licenseFile)
				paragraph "By clicking next you accept the below license agreement:"
				paragraph "<textarea rows=20 class='mdl-textfield' readonly='true'>${license}</textarea>"
				paragraph "Click next to continue. This make take some time..."
			}
			else
				paragraph "Click the next button to install your selections. This may take some time..."
			
			def primaryApp = manifest?.apps?.find { item -> item.primary == true }    
	
			if (primaryApp)
				input "launchInstaller", "bool", defaultValue: true, title: "Configure the installed package after installation completes."
			
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
				showHideNextButton(false)
				paragraph "<b>Installing</b>"
				paragraph "Your installation is currently in progress... Please wait..."
				paragraph getBackgroundStatusMessage()
			}
		}
	}
	else {
		def primaryApp = state.manifests[pkgInstall]?.apps?.find {it -> it.primary == true }
		if (primaryApp == null || !launchInstaller)
			return complete("Installation complete", "The package was sucessfully installed, click Next to return to the Main Menu.")
		else
			return complete("Installation complete", "The package was sucessfully installed, click Next to configure your new package.", false, primaryApp.heID)
		
	}
}

def performInstallation() {
	if (!login())
		return triggerError("Error logging in to hub", "An error occurred logging into the hub. Please verify your Hub Security username and password.", false)
	def manifest = getJSONFile(pkgInstall)
	
	if (shouldInstallBeta(manifest)) {
		manifest = getJSONFile(getItemDownloadLocation(manifest))
		manifest.beta = true
	}
	else
		manifest.beta = false

	state.manifests[pkgInstall] = manifest
	state.manifests[pkgInstall].payPalUrl = state.payPalUrl
	state.payPalUrl = null
	minimizeStoredManifests()
	
	// Download all files first to reduce the chances of a network error
	def appFiles = [:]
	def driverFiles = [:]
	def fileManagerFiles = [:]
	def fileMgrResults = downloadFileManagerFiles(manifest)
	if (fileMgrResults.success)
		fileManagerFiles = fileMgrResults.files
	else 
		return triggerError("Error downloading file", "An error occurred downloading ${fileMgrResults.name}", false)
	
	def requiredApps = getRequiredAppsFromManifest(manifest)
	def requiredDrivers = getRequiredDriversFromManifest(manifest)
	
	for (requiredApp in requiredApps) {
		def location = getItemDownloadLocation(requiredApp.value)
		setBackgroundStatusMessage("Downloading ${requiredApp.value.name}")
		def fileContents = downloadFile(location)
		if (fileContents == null) {
			state.manifests.remove(pkgInstall)
			return triggerError("Error downloading file", "An error occurred downloading ${location}", false)
		}
		appFiles[location] = fileContents
	}
	for (appToInstall in appsToInstall) {
		def matchedApp = manifest.apps.find { it.id == appToInstall}
		if (matchedApp != null) {
			def location = getItemDownloadLocation(matchedApp)
			setBackgroundStatusMessage("Downloading ${matchedApp.name}")
			def fileContents = downloadFile(location)
			if (fileContents == null) {
				state.manifests.remove(pkgInstall)
				return triggerError("Error downloading file", "An error occurred downloading ${location}", false)
			}
			appFiles[location] = fileContents
		}
	}
	for (requiredDriver in requiredDrivers) {
		def location = getItemDownloadLocation(requiredDriver.value)
		setBackgroundStatusMessage("Downloading ${requiredDriver.value.name}")
		def fileContents = downloadFile(location)
		if (fileContents == null) {
			state.manifests.remove(pkgInstall)
			return triggerError("Error downloading file", "An error occurred downloading ${location}", false)
		}
		driverFiles[location] = fileContents
	}
	
	for (driverToInstall in driversToInstall) {
		def matchedDriver = manifest.drivers.find { it.id == driverToInstall}
		if (matchedDriver != null) {
			def location = getItemDownloadLocation(matchedDriver)
			setBackgroundStatusMessage("Downloading ${matchedDriver.name}")
			def fileContents = downloadFile(location)
			if (fileContents == null) {
				state.manifests.remove(pkgInstall)
				return triggerError("Error downloading file", "An error occurred downloading ${location}", false)
			}
			driverFiles[location] = fileContents
		}
	}

	initializeRollbackState("install")
	// All files downloaded, execute installs.
	for (requiredApp in requiredApps) {
		def location = getItemDownloadLocation(requiredApp.value)
		setBackgroundStatusMessage("Installing ${requiredApp.value.name}")
		def id = installApp(appFiles[location])
		if (id == null) {
			state.manifests.remove(pkgInstall)
			return rollback("Failed to install app ${location}. Please notify the package developer.", false)
		}
		requiredApp.value.heID = id
		requiredApp.value.beta = shouldInstallBeta(requiredApp.value)
		if (requiredApp.value.oauth)
			enableOAuth(requiredApp.value.heID)
	}
	
	for (appToInstall in appsToInstall) {
		def matchedApp = manifest.apps.find { it.id == appToInstall}
		if (matchedApp != null) {
			def location = getItemDownloadLocation(matchedApp)
			setBackgroundStatusMessage("Installing ${matchedApp.name}")
			def id = installApp(appFiles[location])
			if (id == null) {
				state.manifests.remove(pkgInstall)
				return rollback("Failed to install app ${location}. Please notify the package developer.", false)
			}
			matchedApp.heID = id
			matchedApp.beta = shouldInstallBeta(matchedApp)
			if (matchedApp.oauth)
				enableOAuth(matchedApp.heID)
		}
	}
	
	for (requiredDriver in requiredDrivers) {
		def location = getItemDownloadLocation(requiredDriver.value)
		setBackgroundStatusMessage("Installing ${requiredDriver.value.name}")
		def id = installDriver(driverFiles[location])
		if (id == null) {
			state.manifests.remove(pkgInstall)
			return rollback("Failed to install driver ${location}. Please notify the package developer.", false)
		}
		requiredDriver.value.heID = id
		requiredDriver.value.beta = shouldInstallBeta(requiredDriver.value)
	}
	
	for (driverToInstall in driversToInstall) {
		def matchedDriver = manifest.drivers.find { it.id == driverToInstall}
		if (matchedDriver != null) {
			def location = getItemDownloadLocation(matchedDriver)
			setBackgroundStatusMessage("Installing ${matchedDriver.name}")
			def id = installDriver(driverFiles[location])
			if (id == null) {
				state.manifests.remove(pkgInstall)
				return rollback("Failed to install driver ${location}. Please notify the package developer.", false)
			}
			matchedDriver.heID = id
			matchedDriver.beta = shouldInstallBeta(matchedDriver)
		}
	}

	for (fileToInstall in manifest.files) {
		def location = getItemDownloadLocation(fileToInstall)
		def fileContents = fileManagerFiles[location]
		setBackgroundStatusMessage("Installing ${location}")
		if (!installFile(fileToInstall.id, fileToInstall.name, fileContents)) {
			state.manifests.remove(pkgInstall)
			return rollback("Failed to install file ${location}. Please notify the package developer.", false)
		}
		else
			completedActions["fileInstalls"] << fileToInstall
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
				showHideNextButton(false)
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
		return triggerError("Error logging in to hub", "An error occurred logging into the hub. Please verify your Hub Security username and password.", false)
	
	// Download all files first to reduce the chances of a network error
	def appFiles = [:]
	def driverFiles = [:]
	def manifest = getInstalledManifest(pkgModify)
	
	for (appToInstall in appsToInstallForModify) {
		def app = getAppById(manifest, appToInstall)
		def location = getItemDownloadLocation(app)
		setBackgroundStatusMessage("Downloading ${app.name}")
		def fileContents = downloadFile(location)
		if (fileContents == null) {
			return triggerError("Error downloading file", "An error occurred downloading ${location}", false)
		} 
		appFiles[location] = fileContents
	}
	for (driverToInstall in driversToInstallForModify) {
		def driver = getDriverById(manifest, driverToInstall)
		def location = getItemDownloadLocation(driver)
		setBackgroundStatusMessage("Downloading ${driver.name}")
		def fileContents = downloadFile(location)
		if (fileContents == null) {
			return triggerError("Error downloading file", "An error occurred downloading ${location}", false)
		}
		driverFiles[location] = fileContents
	}
	
	initializeRollbackState("modify")
	for (appToInstall in appsToInstallForModify) {
		def app = getAppById(manifest, appToInstall)
		def location = getItemDownloadLocation(app)
		setBackgroundStatusMessage("Installing ${app.name}")
		def id = installApp(appFiles[location])
		if (id != null)
		{
			app.heID = id
			completedActions["appInstalls"] << id
			if (app.oauth)
				enableOAuth(app.heID)
		}
		else
			return rollback("Failed to install app ${location}. Please notify the package developer.", false)
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
			return rollback("Failed to uninstall app ${app.name}, it may be in use. Please delete all instances of this app before uninstalling the package.", false)
	}
	
	for (driverToInstall in driversToInstallForModify) {
		def driver = getDriverById(manifest, driverToInstall)
		def location = getItemDownloadLocation(driver)
		setBackgroundStatusMessage("Installing ${driver.name}")
		def id = installDriver(driverFiles[location])
		if (id != null) {
			driver.heID = id
		}
		else
			return rollback("Failed to install driver ${location}. Please notify the package developer.", false)
		
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
			return rollback("Failed to uninstall driver ${driver.name}. Please delete all instances of this device before uninstalling the package.", false)
	}
	atomicState.backgroundActionInProgress = false
}

// Repair a package pathway
def prefPkgRepair() {
	if (state.mainMenu)
		return prefOptions()
	logDebug "prefPkgModify"
	def pkgsToList = getInstalledPackages(false)
	return dynamicPage(name: "prefPkgRepair", title: "", nextPage: "prefPkgRepairExecute", install: false, uninstall: false) {
		displayHeader()
		section {
			paragraph "<b>Repair a Package</b>"
			input "pkgRepair", "enum", title: "Choose the package to repair", options: pkgsToList, required: true
		}
		section {
			paragraph "<hr>"
			input "btnMainMenu", "button", title: "Main Menu", width: 3
		}
	}
}

def prefPkgRepairExecute() {
	if (state.mainMenu)
		return prefOptions()
	if (errorOccurred == true) {
		return buildErrorPage(errorTitle, errorMessage)
	}
	if (atomicState.backgroundActionInProgress == null) {
		logDebug "Executing repair"
		atomicState.backgroundActionInProgress = true
		if (pkgRepair == state.repositoryListingJSON.hpm.location)
			atomicState.hpmUpgraded = true
		else
			atomicState.hpmUpgraded = false
		runInMillis(1,performRepair)
	}
	if (atomicState.backgroundActionInProgress != false) {
		return dynamicPage(name: "prefPkgRepairExecute", title: "", nextPage: "prefPkgRepairExecute", install: false, uninstall: false, refreshInterval: 2) {
			displayHeader()
			section {
				showHideNextButton(false)
				paragraph "<b>Repairing Package</b>"
				paragraph "Your changes are currently in progress... Please wait..."
				paragraph getBackgroundStatusMessage()
			}
		}
	}
	else {
		if (!atomicState.hpmUpgraded)
			return complete("Repair complete", "The package was sucessfully repaired, click Next to return to the Main Menu.")
		else {
			return complete("Repair complete", "The package was sucessfully repaired, click Done to continue.", true)
		}
	}
}

def performRepair() {
	if (!login())
		return triggerError("Error logging in to hub", "An error occurred logging into the hub. Please verify your Hub Security username and password.", runInBackground)
	
	def installedApps = getAppList()
	def installedDrivers = getDriverList()
	
	// Download all files first to reduce the chances of a network error
	def appFiles = [:]
	def driverFiles = [:]
	def fileManagerFiles = [:]
	
	def installedManifest = state.manifests[pkgRepair]
	def manifest = getJSONFile(pkgRepair)
	
	if (manifest) {
		def fileMgrResults = downloadFileManagerFiles(manifest)
		if (fileMgrResults.success)
			fileManagerFiles = fileMgrResults.files
		else
			return triggerError("Error downloading file", "An error occurred downloading ${fileMgrResults.name}", false)
		for (app in manifest.apps) {
			def appHeID = getAppById(installedManifest,app.id)?.heID
			if (isAppInstalled(installedManifest,app.id) && installedApps.find { it -> it.id == appHeID }) {
				setBackgroundStatusMessage("Downloading ${app.name}")
				def location = getItemDownloadLocation(app)
				def fileContents = downloadFile(location)
				if (fileContents == null) {
					return triggerError("Error downloading file", "An error occurred downloading ${location}", runInBackground)
				}
				appFiles[location] = fileContents    
			}
			else if (app.required) {
				setBackgroundStatusMessage("Downloading ${app.name} because it is required and not installed")
				def location = getItemDownloadLocation(app)
				def fileContents = downloadFile(location)
				if (fileContents == null) {
					return triggerError("Error downloading file", "An error occurred downloading ${location}", runInBackground)
				}
				appFiles[location] = fileContents
			}
		}
		for (driver in manifest.drivers) {
			def driverHeID = getDriverById(installedManifest,driver.id)?.heID
			if (isDriverInstalled(installedManifest,driver.id) && installedDrivers.find { it -> it.id == driverHeID }) {
				def location = getItemDownloadLocation(driver)
				setBackgroundStatusMessage("Downloading ${driver.name}")
				def fileContents = downloadFile(location)
				if (fileContents == null) {
					return triggerError("Error downloading file", "An error occurred downloading ${location}", runInBackground)
				}
				driverFiles[location] = fileContents
			}
			else if (driver.required) {
				setBackgroundStatusMessage("Downloading ${driver.name} because it is required and not installed")
				def location = getItemDownloadLocation(driver)
				def fileContents = downloadFile(location)
				if (fileContents == null) {
					return triggerError("Error downloading file", "An error occurred downloading ${location}", runInBackground)
				}
				driverFiles[location] = fileContents
			}
		}
	}
	else {
		return triggerError("Error downloading file", "The manifest file ${pkg} no longer seems to be valid.", runInBackground)
	}
	
	if (manifest) {
		initializeRollbackState("update")
		
		manifestForRollback = installedManifest
		for (app in manifest.apps) {
			def appHeID = getAppById(installedManifest,app.id)?.heID
			if (isAppInstalled(installedManifest,app.id) && installedApps.find { it -> it.id == appHeID }) {
				app.heID = getAppById(installedManifest, app.id).heID
				app.beta = shouldInstallBeta(app)
				def location = getItemDownloadLocation(app)
				def sourceCode = getAppSource(app.heID)
				setBackgroundStatusMessage("Reinstalling ${app.name}")
				if (upgradeApp(app.heID, appFiles[location])) {
					completedActions["appUpgrades"] << [id:app.heID,source:sourceCode]
					if (app.oauth)
						enableOAuth(app.heID)
				}
				else
					return rollback("Failed to upgrade app ${location}.  Please notify the package developer.", runInBackground)
			}
			else if (app.required) {
				def location = getItemDownloadLocation(app)
				setBackgroundStatusMessage("Installing ${app.name}")
				def id = installApp(appFiles[location])
				if (id != null) {
					app.heID = id
					app.beta = shouldInstallBeta(app)
					if (app.oauth)
						enableOAuth(app.heID)
				}
				else
					return rollback("Failed to install app ${location}.  Please notify the package developer.", runInBackground)
			}
		}
		
		for (driver in manifest.drivers) {
			def driverHeID = getDriverById(installedManifest,driver.id)?.heID
			if (isDriverInstalled(installedManifest,driver.id) && installedDrivers.find { it -> it.id == driverHeID }) {
				def location = getItemDownloadLocation(driver)
				driver.heID = getDriverById(installedManifest, driver.id).heID
				driver.beta = shouldInstallBeta(driver)
				def sourceCode = getDriverSource(driver.heID)
				setBackgroundStatusMessage("Reinstalling ${driver.name}")
				if (upgradeDriver(driver.heID, driverFiles[location])) {
					completedActions["driverUpgrades"] << [id:driver.heID,source:sourceCode]
				}
				else
					return rollback("Failed to upgrade driver ${location}.  Please notify the package developer.", runInBackground)
			}
			else if (driver.required) {
				def location = getItemDownloadLocation(driver)
				setBackgroundStatusMessage("Installing ${driver.name}")
				def id = installDriver(driverFiles[location])
				if (id != null) {
					driver.heID = id
					driver.beta = shouldInstallBeta(driver)
				}
				else
					return rollback("Failed to install driver ${location}.  Please notify the package developer.", runInBackground)
			}
		}

		for (fileToInstall in manifest.files) {
			def location = getItemDownloadLocation(fileToInstall)
			def fileContents = fileManagerFiles[location]
			setBackgroundStatusMessage("Installing ${location}")
			if (!installFile(fileToInstall.id, fileToInstall.name, fileContents)) {
				return rollback("Failed to install file ${location}. Please notify the package developer.", false)
			}
			else
				completedActions["fileInstalls"] << fileToInstall
		}

		if (state.manifests[pkgRepair] != null)
			copyInstalledItemsToNewManifest(state.manifests[pkgRepair], manifest)
		state.manifests[pkgRepair] = manifest
		minimizeStoredManifests()
	}
	else {
	}
	
	logDebug "Repair complete"
	if (runInBackground != true)
		atomicState.backgroundActionInProgress = false
	else
		return true
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
			paragraph "<b>Uninstall Packages</b>"
			input "pkgUninstall", "enum", title: "Choose the package(s) to uninstall", options: pkgsToList, required: true, multiple: true
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
			paragraph "<b>Uninstall Packages</b>"
			paragraph "The following apps and drivers will be removed:"
			
			def str = "<ul>"
			for (pkgToUninstall in pkgUninstall) {
				def pkg = state.manifests[pkgToUninstall]
				for (app in pkg.apps) {
					if (app.heID != null)
						str += "<li>${app.name} (App)</li>"
				}
				
				for (driver in pkg.drivers) {
					if (driver.heID != null)
						str += "<li>${driver.name} (Device Driver)</li>"
				}
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
				showHideNextButton(false)
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
		return triggerError("Error logging in to hub", "An error occurred logging into the hub. Please verify your Hub Security username and password.", false)
	
	for (pkgToUninstall in pkgUninstall) {
		def pkg = state.manifests[pkgToUninstall]
	
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
					return rollback("Failed to uninstall app ${app.location}, it may be in use. Please delete all instances of this app before uninstalling the package.", false)
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
					return rollback("Failed to uninstall driver ${driver.location}. Please delete all instances of this device before uninstalling the package.", false)
			}

		}

		for (file in pkg.files) {
			setBackgroundStatusMessage("Uninstalling ${file.name}")
			if (uninstallFile(file.id, file.name)) {
				completedActions["fileUninstalls"] << file
			}
			else {
				return rollback("Failed to uninstall file ${file.name}.", false)
			}
		}
		state.manifests.remove(pkgToUninstall)
	}
	
	atomicState.backgroundActionInProgress = false
}    

def addUpdateDetails(pkgId, pkgName, releaseNotes, updateType, item, forceProduction = false) {
	if (updateDetails[pkgId] == null)
		updateDetails[pkgId] = [name: null, releaseNotes: null, items: [], forceProduction: null]
	if (pkgName != null)
		updateDetails[pkgId].name = pkgName
	if (releaseNotes != null)
		updateDetails[pkgId].releaseNotes = releaseNotes
	updateDetails[pkgId].items << [type: updateType,  id: item?.id, name: item?.name, forceProduction: forceProduction]
	
	logDebug "Updates found ${updateType} for ${pkgId} -> ${item?.name} (force production: ${forceProduction})"
}
// Update packages pathway
def performUpdateCheck() {
	packagesWithUpdates = [:]

	for (pkg in state.manifests) {
		try
		{
			setBackgroundStatusMessage("Checking for updates for ${state.manifests[pkg.key].packageName}")
			def manifest = getJSONFile(pkg.key)
			if (shouldInstallBeta(manifest)) {
				manifest = getJSONFile(getItemDownloadLocation(manifest))
				if (manifest)
					manifest.beta = true
				else {
					manifest = getJSONFile(pkg.key)
					manifest.beta = false
				}
			}
			else
				manifest.beta = false
			
			if (manifest == null) {
				log.warn "Found a bad manifest ${pkg.key}, please notify the package developer."
				continue
			}

			if (manifest.minimumHEVersion != null && !verifyHEVersion(manifest.minimumHEVersion)) {
				log.warn "New version of ${manifest.packageName} found but requires ${manifest.minimumHEVersion}, please update your firmware to upgrade."
				continue
			} 

			def newVersionResult = newVersionAvailable(manifest, state.manifests[pkg.key])
			if (newVersionResult.newVersion) {
				def version = includeBetas && manifest.betaVersion != null ? manifest.betaVersion : manifest.version
				packagesWithUpdates << ["${pkg.key}": "${state.manifests[pkg.key].packageName} (installed: ${state.manifests[pkg.key].version ?: "N/A"} current: ${version})"]
				logDebug "Updates found for package ${pkg.key}"
				addUpdateDetails(pkg.key, manifest.packageName, manifest.releaseNotes, "package", null, newVersionResult.forceProduction)
			} 
			else {
				def appOrDriverNeedsUpdate = false
				for (app in manifest.apps) {
					try {
						def installedApp = getAppById(state.manifests[pkg.key], app.id)
						if (app?.version != null && installedApp?.version != null) {
							newVersionResult = newVersionAvailable(app, installedApp)
							if (newVersionResult.newVersion) {
								if (!appOrDriverNeedsUpdate) { // Only add a package to the list once
									packagesWithUpdates << ["${pkg.key}": "${state.manifests[pkg.key].packageName} (driver or app has a new version)"]
								}
								appOrDriverNeedsUpdate = true
								addUpdateDetails(pkg.key, manifest.packageName, manifest.releaseNotes, "specificapp", app, newVersionResult.forceProduction)
							}
						}
						else if ((!installedApp || (!installedApp.required && installedApp.heID == null)) && app.required) {
							if (!appOrDriverNeedsUpdate) { // Only add a package to the list once
								packagesWithUpdates << ["${pkg.key}": "${state.manifests[pkg.key].packageName} (driver or app has a new requirement)"]
							}
							appOrDriverNeedsUpdate = true
							addUpdateDetails(pkg.key, manifest.packageName, manifest.releaseNotes, "reqapp", app, false)
						}
						else if (!installedApp && !app.required) {
							if (!appOrDriverNeedsUpdate) { // Only add a package to the list once
								packagesWithUpdates << ["${pkg.key}": "${state.manifests[pkg.key].packageName} (new optional app or driver is available)"]
							}
							appOrDriverNeedsUpdate = true
							addUpdateDetails(pkg.key, manifest.packageName, manifest.releaseNotes, "optapp", app, false)
						}
					}
					catch (any) { 
						log.error "Bad manifest for ${state.manifests[pkg.key].packageName}. Please notify the package developer."
					}
				}
				for (driver in manifest.drivers) {
					try {
						def installedDriver = getDriverById(state.manifests[pkg.key], driver.id)
						if (driver?.version != null && installedDriver?.version != null) {
							newVersionResult = newVersionAvailable(driver, installedDriver)
							if (newVersionResult.newVersion) {
								if (!appOrDriverNeedsUpdate) {// Only add a package to the list once
									packagesWithUpdates << ["${pkg.key}": "${state.manifests[pkg.key].packageName} (driver or app has a new version)"]
								}
								appOrDriverNeedsUpdate = true
								addUpdateDetails(pkg.key, manifest.packageName, manifest.releaseNotes, "specificdriver", driver, newVersionResult.forceProduction)
							}
						}
						else if ((!installedDriver || (!installedDriver.required && installedDriver.heID == null)) && driver.required) {
							if (!appOrDriverNeedsUpdate) { // Only add a package to the list once
								packagesWithUpdates << ["${pkg.key}": "${state.manifests[pkg.key].packageName} (driver or app has a new requirement)"]
							}
							appOrDriverNeedsUpdate = true
							addUpdateDetails(pkg.key, manifest.packageName, manifest.releaseNotes, "reqdriver", driver, false)
						}
						else if (!installedDriver && !driver.required) {
							addUpdateDetails(pkg.key, manifest.packageName, manifest.releaseNotes, "optdriver", driver, false)
							if (!appOrDriverNeedsUpdate) { // Only add a package to the list once
								packagesWithUpdates << ["${pkg.key}": "${state.manifests[pkg.key].packageName} (new optional app or driver is available)"]
							}
							appOrDriverNeedsUpdate = true
						}
					}
					catch (e) {
						log.error "Bad manifest for ${state.manifests[pkg.key].packageName}. ${e} Please notify the package developer."
					}
				}
			}    
		}
		catch (e) {
			log.error "Bad manifest for ${state.manifests[pkg.key].packageName}. ${e} Please notify the package developer."
		}
	}
	packagesWithUpdates = packagesWithUpdates.sort { it -> it.value }
	atomicState.backgroundActionInProgress = false
	return packagesWithUpdates
}

def prefPkgUpdate() {
	if (state.mainMenu)
		return prefOptions()
	if (errorOccurred == true) {
		return buildErrorPage(errorTitle, errorMessage)
	}
	if (atomicState.backgroundActionInProgress == null) {
		logDebug "Update chosen"
		state.updatesNotified = false
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
				showHideNextButton(false)
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
					if (updateDetails?.size() > 0 && pkgsToUpdate != null)
						showHideNextButton(true)
					else
						showHideNextButton(false)
				}
				section {
					paragraph "<hr>"
					input "btnMainMenu", "button", title: "Main Menu", width: 3
					
				}
			}
		}
		else {
			logDebug "No updates available"
			app.updateLabel("Hubitat Package Manager")
			return complete("No Updates Available", "All packages are up to date, click Next to return to the Main Menu.")
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
			updatesToInstall += "<textarea rows=6 class='mdl-textfield' readonly='true'>${updateDetails[pkg].releaseNotes}</textarea>"
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
		atomicState.hpmUpgraded = false
		for (pkg in pkgsToUpdate) {
			if (pkg == state.repositoryListingJSON.hpm.location) {
				atomicState.hpmUpgraded = true
				break
			}
		}
		atomicState.backgroundActionInProgress = true
		runInMillis(1,performUpdates)
	}
	if (atomicState.backgroundActionInProgress != false) {
		return dynamicPage(name: "prefPkgUpdatesComplete", title: "", nextPage: "prefPkgUpdatesComplete", install: false, uninstall: false, refreshInterval: 2) {
			displayHeader()
			section {
				showHideNextButton(false)
				paragraph "<b>Installing Updates</b>"
				paragraph "Installing updates... Please wait..."
				paragraph getBackgroundStatusMessage()
			}
		}
	}
	else {    
		if (!atomicState.hpmUpgraded)
			return complete("Updates complete", "The updates have been installed, click Next to return to the Main Menu.")
		else {
			return complete("Updates complete", "The updates have been installed, click Done to continue.", true)
		}
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

def forceProduction(pkg, id) {
	def pkgUpdateDetails = updateDetails[pkg]

	for (updateItem in pkgUpdateDetails.items) {
		if (updateItem.type == "package")
			return updateItem.forceProduction
		else if ((updateItem.type == "specificapp" || updateItem.type == "specificdriver") && updateItem.id == id)
			return updateItem.forceProduction
	}
	return false
}

def performUpdates(runInBackground) {
	def resultData = [success: null, succeeded: [], failed: [], message: null]
	if (!login()) {
		resultData.message = triggerError("Error logging in to hub", "An error occurred logging into the hub. Please verify your Hub Security username and password.", runInBackground)
		resultData.success = false
		return resultData
	}
		
	// Download all files first to reduce the chances of a network error
	def downloadedManifests = [:]
	def appFiles = [:]
	def driverFiles = [:]
	def fileManagerFiles = [:]
	
	for (pkg in pkgsToUpdate) {
		def manifest = getJSONFile(pkg)
		
		if (shouldInstallBeta(manifest)) {
			manifest = getJSONFile(getItemDownloadLocation(manifest))
			if (manifest)
				manifest.beta = true
			else 
				manifest = getJSONFile(pkg)
		}
		else
			manifest.beta = false
		def installedManifest = state.manifests[pkg]
		
		downloadedManifests[pkg] = manifest
		if (manifest) {
			def fileMgrResults = downloadFileManagerFiles(manifest)
			if (fileMgrResults.success)
				fileManagerFiles = fileManagerFiles + fileMgrResults.files
			else
				return triggerError("Error downloading file", "An error occurred downloading ${fileMgrResults.name}", false)
			for (app in manifest.apps) {
				if (isAppInstalled(installedManifest,app.id)) {
					if (shouldUpgrade(pkg, app.id)) {
						def location
						if (!forceProduction(pkg, app.id))
							location = getItemDownloadLocation(app)
						else
							location = app.location
						setBackgroundStatusMessage("Downloading ${app.name}")
						def fileContents = downloadFile(location)
						if (fileContents == null) {
							resultData.success = false
							resultData.failed << pkg
							resultData.message = triggerError("Error downloading file", "An error occurred downloading ${location}", runInBackground)
							return resultData
						}
						appFiles[location] = fileContents    
					}
				}
				else if (app.required && !optionalItemsOnly(pkg)) {
					def location
					if (!forceProduction(pkg, app.id))
						location = getItemDownloadLocation(app)
					else
						location = app.location
					setBackgroundStatusMessage("Downloading ${app.name} because it is required and not installed")
					def fileContents = downloadFile(location)
					if (fileContents == null) {
						resultData.success = false
						resultData.failed << pkg
						resultData.message = triggerError("Error downloading file", "An error occurred downloading ${location}", runInBackground)
						return resultData
					}
					appFiles[location] = fileContents
				}
				else {
					def location
					if (!forceProduction(pkg, app.id))
						location = getItemDownloadLocation(app)
					else
						location = app.location
					for (optItem in pkgsToAddOpt) {
						def splitParts = optItem.split('~')
						if (splitParts[0] == pkg) {
							if (splitParts[1] == app.id) {
								setBackgroundStatusMessage("Downloading optional component ${app.name}")
								def fileContents = downloadFile(location)
								if (fileContents == null) {
									resultData.success = false
									resultData.failed << pkg
									resultData.message = triggerError("Error downloading file", "An error occurred downloading ${location}", runInBackground)
									return resultData
								}
								appFiles[location] = fileContents
							}
						}
					}
				}
			}
			for (driver in manifest.drivers) {
				if (isDriverInstalled(installedManifest,driver.id)) {
					if (shouldUpgrade(pkg, driver.id)) {
						def location
						if (!forceProduction(pkg, driver.id))
							location = getItemDownloadLocation(driver)
						else
							location = driver.location
						setBackgroundStatusMessage("Downloading ${driver.name}")
						def fileContents = downloadFile(location)
						if (fileContents == null) {
							resultData.success = false
							resultData.failed << pkg
							resultData.message = triggerError("Error downloading file", "An error occurred downloading ${location}", runInBackground)
							return resultData
						}
						driverFiles[location] = fileContents
					}
				}
				else if (driver.required && !optionalItemsOnly(pkg)) {
					def location
					if (!forceProduction(pkg, driver.id))
						location = getItemDownloadLocation(driver)
					else
						location = driver.location
					setBackgroundStatusMessage("Downloading ${driver.name} because it is required and not installed")
					def fileContents = downloadFile(location)
					if (fileContents == null) {
						resultData.success = false
						resultData.failed << pkg
						resultData.message = triggerError("Error downloading file", "An error occurred downloading ${location}", runInBackground)
						return resultData
					}
					driverFiles[location] = fileContents
				}
				else {
					def location
					if (!forceProduction(pkg, driver.id))
						location = getItemDownloadLocation(driver)
					else
						location = driver.location
					for (optItem in pkgsToAddOpt) {
						def splitParts = optItem.split('~')
						if (splitParts[0] == pkg && splitParts[1] == driver.id) {
							setBackgroundStatusMessage("Downloading optional component ${driver.name}")
							def fileContents = downloadFile(location)
							if (fileContents == null) {
								resultData.success = false
								resultData.failed << pkg
								resultData.message = triggerError("Error downloading file", "An error occurred downloading ${location}", runInBackground)
								return resultData
							}
							driverFiles[location] = fileContents
						}
					}
				}
			}
			if (pkg == state.repositoryListingJSON.hpm?.location)
				sendLocationEvent(name: "hpmVersion", value: manifest.version)
		}
		else {
			resultData.success = false
			resultData.failed << pkg
			resultData.message = triggerError("Error downloading file", "The manifest file ${pkg} no longer seems to be valid.", runInBackground)
			return resultData
		}
	}
	
	for (pkg in pkgsToUpdate) {
		def manifest = downloadedManifests[pkg]
		def installedManifest = state.manifests[pkg]
		
		if (manifest) {
			initializeRollbackState("update")
			
			manifestForRollback = manifest
			if (manifest.betaVersion && includeBetas)
				manifest.beta = true
			
			for (app in manifest.apps) {
				if (isAppInstalled(installedManifest,app.id)) {
					if (shouldUpgrade(pkg, app.id)) {
						def location
						if (!forceProduction(pkg, app.id))
							location = getItemDownloadLocation(app)
						else
							location = app.location
						app.heID = getAppById(installedManifest, app.id).heID
						app.beta = shouldInstallBeta(app) && !forceProduction(pkg, app.id)
						def sourceCode = getAppSource(app.heID)
						setBackgroundStatusMessage("Upgrading ${app.name}")
						if (upgradeApp(app.heID, appFiles[location])) {
							completedActions["appUpgrades"] << [id:app.heID,source:sourceCode]
							if (app.oauth)
								enableOAuth(app.heID)
						}
						else {
							resultData.success = false
							resultData.failed << pkg
							resultData.message = rollback("Failed to upgrade app ${location}", runInBackground)
							return resultData
						}
					}
				}
				else if (app.required && !optionalItemsOnly(pkg)) {
					def location
					if (!forceProduction(pkg, app.id))
						location = getItemDownloadLocation(app)
					else
						location = app.location
					setBackgroundStatusMessage("Installing ${app.name}")
					def id = installApp(appFiles[location])
					if (id != null) {
						app.heID = id
						app.beta = shouldInstallBeta(app) && !forceProduction(pkg, app.id)
						if (app.oauth)
							enableOAuth(app.heID)
					}
					else {
						resultData.success = false
						resultData.failed << pkg
						resultData.message = rollback("Failed to install app ${location}", runInBackground)
						return resultData
					}
				}
				else {
					def location
					if (!forceProduction(pkg, app.id))
						location = getItemDownloadLocation(app)
					else
						location = app.location
					for (optItem in pkgsToAddOpt) {
						def splitParts = optItem.split('~')
						if (splitParts[0] == pkg) {
							if (splitParts[1] == app.id) {
								setBackgroundStatusMessage("Installing ${app.name}")
								def id = installApp(appFiles[location])
								if (id != null) {
									app.heID = id
									app.beta = shouldInstallBeta(app) && !forceProduction(pkg, app.id)
									if (app.oauth)
										enableOAuth(app.heID)
								}
								else {
									resultData.success = false
									resultData.failed << pkg
									resultData.message = rollback("Failed to install app ${location}", runInBackground)
									return resultData
								}
							}
						}
					}
				}
			}
			
			for (driver in manifest.drivers) {
				if (isDriverInstalled(installedManifest,driver.id)) {
					if (shouldUpgrade(pkg, driver.id)) {
						def location
						if (!forceProduction(pkg, driver.id))
							location = getItemDownloadLocation(driver)
						else
							location = driver.location
						driver.heID = getDriverById(installedManifest, driver.id).heID
						driver.beta = shouldInstallBeta(driver) && !forceProduction(pkg, driver.id)
						def sourceCode = getDriverSource(driver.heID)
						setBackgroundStatusMessage("Upgrading ${driver.name}")
						if (upgradeDriver(driver.heID, driverFiles[location])) {
							completedActions["driverUpgrades"] << [id:driver.heID,source:sourceCode]
						}
						else {
							resultData.success = false
							resultData.failed << pkg
							resultData.message = rollback("Failed to upgrade driver ${location}", runInBackground)
							return resultData
						}
					}
				}
				else if (driver.required && !optionalItemsOnly(pkg)) {
					def location
					if (!forceProduction(pkg, driver.id))
						location = getItemDownloadLocation(driver)
					else
						location = driver.location
					setBackgroundStatusMessage("Installing ${driver.name}")
					def id = installDriver(driverFiles[location])
					if (id != null) {
						driver.heID = id
						driver.beta = shouldInstallBeta(driver) && !forceProduction(pkg, driver.id)
					}
					else {
						resultData.success = false
						resultData.failed << pkg
						resultData.message = rollback("Failed to install driver ${location}", runInBackground)
						return resultData
					}
				}
				else {
					def location
					if (!forceProduction(pkg, driver.id))
						location = getItemDownloadLocation(driver)
					else
						location = driver.location
					for (optItem in pkgsToAddOpt) {
						def splitParts = optItem.split('~')
						if (splitParts[0] == pkg) {
							if (splitParts[1] == driver.id) {
								setBackgroundStatusMessage("Installing ${driver.name}")
								def id = installDriver(driverFiles[location])
								if (id != null) {
									driver.heID = id
									driver.beta = shouldInstallBeta(driver) && !forceProduction(pkg, driver.id)
								}
								else {
									resultData.success = false
									resultData.failed << pkg
									resultData.message = rollback("Failed to install driver ${location}", runInBackground)
									return resultData
								}
							}
						}
					}
				}
			}

			for (fileToInstall in manifest.files) {
				def location = getItemDownloadLocation(fileToInstall)
				def fileContents = fileManagerFiles[location]
				setBackgroundStatusMessage("Installing ${location}")
				if (!installFile(fileToInstall.id, fileToInstall.name, fileContents)) {
					return rollback("Failed to install file ${location}. Please notify the package developer.", false)
				}
				else
					completedActions["fileInstalls"] << fileToInstall
			}

			if (state.manifests[pkg] != null)
				copyInstalledItemsToNewManifest(state.manifests[pkg], manifest)
			resultData.succeeded << pkg
			state.manifests[pkg] = manifest
			minimizeStoredManifests()
		}
		else {
		}
	}
	state.updatesNotified = false
	logDebug "Updates complete"
	if (runInBackground != true)
		atomicState.backgroundActionInProgress = false
	else {
		resultData.success = true
		return resultData
	}
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
		performPackageMatchup()
	}
	if (atomicState.backgroundActionInProgress != false) {
		return dynamicPage(name: "prefPkgMatchUpVerify", title: "", nextPage: "prefPkgMatchUpVerify", install: false, uninstall: false, refreshInterval: 2) {
			displayHeader()
			section {
				showHideNextButton(false)
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
		return triggerError("Error logging in to hub", "An error occurred logging into the hub. Please verify your Hub Security username and password.", false)

	updateRepositoryListing()

	getMultipleJSONFiles(installedRepositories, performMatchupRepoRefreshComplete, performMatchupRepoRefreshStatus)
}

def performMatchupRepoRefreshComplete(results, data) {
	def packageManifests = []
	def packageNames = [:]
	setBackgroundStatusMessage("Downloading package manifests")
	def manifestData = [:]
	for (uri in results.keySet()) {
		def repoName = getRepoName(uri)
		def result = results[uri]
		def fileContents = result.result
		if (fileContents == null) {
			log.warn "Error refreshing ${repoName}"
			setBackgroundStatusMessage("Failed to refresh ${repoName}")
			continue
		}

		for (pkg in fileContents.packages) {
			packageManifests << pkg.location
			packageNames[pkg.location] = pkg.name
			manifestData[pkg.location] = [url: uri, githubUrl: fileContents.githubUrl, payPalUrl: fileContents.payPalUrl]
		}
	}
	getMultipleJSONFiles(packageManifests, performMatchupManifestsComplete, performMatchupManifestsStatus, [manifestData: manifestData, packageNames: packageNames])
}

def performMatchupRepoRefreshStatus(uri, data) {
	def repoName = getRepoName(uri)
	setBackgroundStatusMessage("Refreshed ${repoName}")
}

def performMatchupManifestsStatus(uri, data) {

}

def performMatchupManifestsComplete(results, data) {
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
	for (uri in results.keySet()) {
		def result = results[uri]
		def manifestContents = result.result
		if (manifestContents == null)
			log.error "Found a bad manifest ${pkg.location}. Please notify the package developer."
		else {
			def pkgDetails = [
				gitHubUrl: data.manifestData[uri].gitHubUrl,
				payPalUrl: data.manifestData[uri].payPalUrl,
				repository: getRepoName(data.manifestData[uri].url),
				name: data.packageNames[uri],
				location: uri,
				manifest: manifestContents
			]
			packagesToMatchAgainst << pkgDetails
		}
	}

	packagesMatchingInstalledEntries = []
	setBackgroundStatusMessage("Matching up packages")
	for (pkg in packagesToMatchAgainst) {
		def matchedInstalledApps = []
		def matchedInstalledDrivers = []
		
		for (app in pkg.manifest.apps) {
			def appsToAdd = findMatchingAppOrDriver(allInstalledApps, app)
			if (appsToAdd != null)
				matchedInstalledApps << appsToAdd
		}
		for (driver in pkg.manifest.drivers) {
			def driversToAdd = findMatchingAppOrDriver(allInstalledDrivers, driver)
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
				def installedApp = findMatchingAppOrDriver(installedApps, app)
				if (installedApp != null) {
					app.heID = installedApp.id
					if (!pkgUpToDate && app.version != null)
						app.version = "0.0"
				}
			}
			
			for (driver in manifest.drivers) {
				def installedDriver = findMatchingAppOrDriver(installedDrivers, driver)
				if (installedDriver != null) {
					driver.heID = installedDriver.id
					if (!pkgUpToDate && driver.version != null)
						driver.version = "0.0"
				}
			}
			if (state.manifests[match])
				copyInstalledItemsToNewManifest(state.manifests[match], manifest)
			if (matchFromState.gitHubUrl != null)
				manifest.gitHubUrl = matchFromState.gitHubUrl
			if (matchFromState.payPalUrl != null)
				manifest.payPalUrl = matchFromState.payPalUrl
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
		def prependBar = false
		str += "<li><b>${pkg.value.packageName}</b>"
		if (pkg.value.documentationLink != null) {
			str += " <a href='${pkg.value.documentationLink}' target='_blank'>Documentation</a> "
			prependBar = true
		}
		if (pkg.value.communityLink != null) {
			if (prependBar)
				str += "|"
			str += " <a href='${pkg.value.communityLink}' target='_blank'>Community Thread</a> "
			prependBar = true
		}
		if (pkg.value.payPalUrl != null) {
			if (prependBar)
				str += "|"
			str += " <a href='${pkg.value.payPalUrl}' target='_blank'>Donate</a>"
		}
		str += "<ul>"
		for (app in pkg.value.apps?.sort { it -> it.name}) {
			if (app.heID != null)
				str += "<li>${app.name} v${getItemVersion(app) ?: getItemVersion(pkg.value)} (app)</li>"
		}
		for (driver in pkg.value.drivers?.sort { it -> it.name}) {
			if (driver.heID != null)
				str += "<li>${driver.name} v${getItemVersion(driver) ?: getItemVersion(pkg.value)} (driver)</li>"
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
			showHideNextButton(true)
		}
		section {
			paragraph "<hr>"
			input "btnMainMenu", "button", title: "Main Menu", width: 3
		}
	}
}

def buildNotification(text) {
	if (notifyIncludeHubName)
		return "${location.name} - ${text}"
	else
		return text
}

def checkForUpdates() {
	updateRepositoryListing()
	def allUpgradeCount = 0
	def packagesWithLabels = performUpdateCheck()
	def packagesWithUpdates = packagesWithLabels?.keySet() as List
	
	
	if (packagesWithUpdates?.size() == 0)
		app.updateLabel("Hubitat Package Manager")
	else {
		allUpgradeCount = packagesWithUpdates?.size()?: 0
		
		if (notifyUpdatesAvailable && !state.updatesNotified) {
			logDebug "Sending update notification"
			state.updatesNotified = true
			notifyDevices*.deviceNotification(buildNotification("Hubitat Package updates are available"))
		}
		
		if (autoUpdateMode != "Never") {
			if (isHubSecurityEnabled() && !hpmSecurity) {
				log.error "Hub security is enabled but not configured in Package Manager. Updates cannot be installed."
				if (notifyOnFailure) {
					notifyUpdateFailureDevices*.deviceNotification(buildNotification("Hub security is enabled but not configured in Package Manager. Updates cannot be installed."))
				}
				return
			}

			if (autoUpdateMode == "Include")
				packagesWithUpdates.removeIf { hasUpdate -> appsToAutoUpdate.find { it == hasUpdate} == null }
			else if (autoUpdateMode == "Exclude")
				packagesWithUpdates.removeIf { hasUpdate -> appsNotToAutoUpdate.find { it == hasUpdate} != null }

			if (packagesWithUpdates?.size() > 0) {
				app.updateSetting("pkgsToUpdate", packagesWithUpdates)
				pkgsToUpdate = packagesWithUpdates
				def result = null
				try
				{
					result = performUpdates(true)
					if (result.success == true) {
						if (notifyOnSuccess) {
							if (!notifySpecificPackages)
								notifyUpdateSuccessDevices*.deviceNotification(buildNotification("The packages were updated successfully"))
							if (packagesWithUpdates.size() < allUpgradeCount)
								app.updateLabel("Hubitat Package Manager <span style='color:green'>Updates Available</span>")
							else
								app.updateLabel("Hubitat Package Manager")
						}
					}
					else {
						log.error "Automatic update failure: ${result}"
						if (notifyOnFailure) {
							if (notifySpecificPackages)
								notifyUpdateFailureDevices*.deviceNotification(buildNotification("One or more packages failed to automatically update. Check the logs for more information"))
							app.updateLabel("Hubitat Package Manager <span style='color:green'>Updates Available</span>")
						}
					}
				}
				catch (e) {
					log.error "Automatic update failure: ${e}"
					if (notifyOnFailure) {
						if (notifySpecificPackages)
							notifyUpdateFailureDevices*.deviceNotification(buildNotification("One or more packages failed to automatically update. Check the logs for more information"))
						app.updateLabel("Hubitat Package Manager <span style='color:green'>Updates Available</span>")
					}
				}
				if (notifySpecificPackages) {
					if (notifyOnSuccess) {
						for (successful in result.succeeded) {
							log.debug successful
							log.debug state.manifests[successful].packageName
							notifyUpdateSuccessDevices*.deviceNotification(buildNotification("${state.manifests[successful].packageName} was updated successfully"))
						}
					}
					if (notifyOnFailure) {
						for (failed in result.failed) {
							notifyUpdateFailureDevices*.deviceNotification(buildNotification("${state.manifests[failed].packageName} failed to update"))
						}
					}
				}
			}
		}
		else
			app.updateLabel("Hubitat Package Manager <span style='color:green'>Updates Available</span>")
	}
}

def clearStateSettings(clearProgress) {
	installMode = null
	app.removeSetting("pkgInstall")
	app.removeSetting("appsToInstall")
	app.removeSetting("driversToInstall")
	app.removeSetting("pkgModify")
	app.removeSetting("pkgRepair")
	app.removeSetting("appsToModify")
	app.removeSetting("driversToModify")
	app.removeSetting("pkgUninstall")
	app.removeSetting("pkgsToUpdate")
	app.removeSetting("pkgsToAddOpt")
	app.removeSetting("pkgCategory")
	app.removeSetting("pkgTags")
	app.removeSetting("pkgMatches")
	app.removeSetting("pkgUpToDate")
	app.removeSetting("pkgSearch")
	app.removeSetting("launchInstaller")
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
	completedActions["fileUninstalls"] = []
	completedActions["fileInstalls"] = []
	completedActions["fileUpgrades"] = []
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

def downloadFileAsync(String file, String callback, Map data = null) {
	try
	{
		def params = [
			uri: file,
			requestContentType: "application/json",
			contentType: "text/plain",
			timeout: 300
		]
		asynchttpGet(downloadFileAsyncCallback, params, [callback: callback, data: data, file: file])
	}
	catch (e) {
		log.error "Error downloading ${file}: ${e}"
		return null
	}
}

def downloadFileAsyncCallback(resp, data) {
	def result = null
	try
	{
		if (resp.status >= 200 && resp.status <= 299)
			result = resp.data
		else
			log.error "Error downloading ${data['file']}: Received response ${resp.status}"
	}
	catch (e) {
		log.error "Error downloading ${data['file']}: ${e}"
	}
	this."${data['callback']}"(result, data['data'])
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

def getMultipleJSONFilesCallback(resp, data) {
	def result = null
	"${data.statusCallback}"(data.uri, data.uri)
	synchronized (downloadQueue) {
		downloadQueue[data.batchid].results[data.uri].result = resp
		downloadQueue[data.batchid].results[data.uri].complete = true
		
		def queuedItem = downloadQueue[data.batchid].results.find { k, v -> v.queued == true}
		if (queuedItem != null) {
			def itemValue = queuedItem.value
			itemValue.queued = false
			getJSONFileAsync(queuedItem.key, getMultipleJSONFilesCallback, [batchid: data.batchid, uri: queuedItem.key, callback: itemValue.callback, statusCallback: itemValue.statusCallback, data: itemValue.data])
		}
		else if (downloadQueue[data.batchid].results.count { k, v -> v.complete == true} == downloadQueue[data.batchid].totalBatchSize) {
			result = downloadQueue[data.batchid].results
		}
	}
	if (result != null)
		"${data.callback}"(result, data.data)
}

def getMultipleJSONFiles(uriList, completeCallback, statusCallback, data = null) {
	def batchid = UUID.randomUUID().toString()
	synchronized (downloadQueue) {
		def itemsToRemove = []
		for (batch in downloadQueue.keySet()) {
			if (downloadQueue[batch].totalBatchSize == downloadQueue[batch].results.count { k, v -> v.complete == true}) {
				itemsToRemove << batch
			}
		}
		for (removeItem in itemsToRemove) {
			downloadQueue.remove(removeItem)
		}
		
		downloadQueue[batchid] = [totalBatchSize: uriList.size(), results: [:]]
		
		for (uri in uriList) {
			if (downloadQueue[batchid].results.count { k, v -> v.queued == false} < maxDownloadQueueSize) {
				downloadQueue[batchid].results[uri] = [complete: false, result: null, queued: false]
				getJSONFileAsync(uri, getMultipleJSONFilesCallback, [batchid: batchid, uri: uri, callback: completeCallback, statusCallback: statusCallback, data: data])
			}
			else {
				downloadQueue[batchid].results[uri] = [complete: false, result: null, queued: true, callback: completeCallback, statusCallback: statusCallback, data: data]
			}
		}
	}
}

def getJSONFileAsync(String uri, String callback, Map data = null) {
	try
	{
		downloadFileAsync(uri, getJSONAsyncResult, [callback: callback, data: data])
	}
	catch (e) {
		return null
	}    
}

def getJSONAsyncResult(resp, data) {
	def result = null
	try
	{
		result = new groovy.json.JsonSlurper().parseText(resp)
	}
	catch (e) {

	}
	this."${data['callback']}"(result, data['data'])
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

def compareVersions(oldVersion, newVersion) {
	newVersion = newVersion.replaceAll("[^\\d.]", "")
	oldVersion = oldVersion?.replaceAll("[^\\d.]", "")    
	
	if (newVersion == oldVersion)
		return 0

	def oldVersionParts = oldVersion?.split(/\./)
	def newVersionParts = newVersion.split(/\./)
	
	for (def i = 0; i < newVersionParts.size(); i++) {
		if (i >= oldVersionParts.size()) {
			return 1
		}
		def oldPart = oldVersionParts[i].toInteger()
		def newPart = newVersionParts[i].toInteger()
		if (oldPart < newPart) {
			return 1
		}
	}
	return -1
}

def newVersionAvailable(item, installedItem) {
	def result = [newVersion: false, forceProduction: false]
	def versionStr = includeBetas && item.betaVersion != null ? item?.betaVersion : item?.version
	def installedVersionStr = installedItem.beta ? (installedItem?.betaVersion ?: installedItem?.version) : installedItem?.version
	
	if (versionStr == null)
		return result
	if (installedVersionStr == null) {
		result.newVersion = true
		return result // Version scheme has changed, force an upgrade
	}

	if (compareVersions(versionStr, item?.version) != -1) {
		result.forceProduction = true
		versionStr = item?.version
	}

	if (compareVersions(installedVersionStr, versionStr) > 0) {
		result.newVersion = true
		return result
	}

	if (versionStr == installedVersionStr && installedItem?.beta && versionStr == item?.version) {
		result.forceProduction = true
		result.newVersion = true
		return result
	}
	return result
}

def isHubSecurityEnabled() {
	def hubSecurityEnabled = false
	httpGet(
		[
			uri: "http://127.0.0.1:8080",
			path: "/hub/edit",
			textParser: true,
			ignoreSSLIssues: true
		]
	) {
		resp ->
		hubSecurityEnabled = resp.data?.text?.contains("<title>Login</title>")
	}
	return hubSecurityEnabled
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
					textParser: true,
					ignoreSSLIssues: true
				]
			)
			{ resp ->
			log.debug resp.data?.text
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
			timeout: 300,
			ignoreSSLIssues: true
		]
		def result
		httpPost(params) { resp ->
			if (resp.headers."Location" != null) {
				result = resp.headers."Location".replaceAll("https?://127.0.0.1:(?:8080|8443)/app/editor/","")
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
			timeout: 300,
			ignoreSSLIssues: true
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
			textParser: true,
			ignoreSSLIssues: true
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
		timeout: 300,
		ignoreSSLIssues: true
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
			timeout: 300,
			ignoreSSLIssues: true
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
		],
		ignoreSSLIssues: true
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
			timeout: 300,
			ignoreSSLIssues: true
		]
		def result
		httpPost(params) { resp ->
			if (resp.headers."Location" != null) {
				result = resp.headers."Location".replaceAll("https?://127.0.0.1:(?:8080|8443)/driver/editor/","")
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
			timeout: 300,
			ignoreSSLIssues: true
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
			textParser: true,
			ignoreSSLIssues: true
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
			timeout: 300,
			ignoreSSLIssues: true
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
		],
		ignoreSSLIssues: true
	]
	def result
	httpGet(params) { resp ->
		result = resp.data.version
	}
	return result
}

// File Installation Methods
def installFile(id, fileName, contents) {
	try
	{
		def params = [
			uri: "http://127.0.0.1:8080",
			path: "/hub/fileManager/upload",
			query: [
				"folder": "/"
			],
			headers: [
				"Cookie": state.cookie,
				"Content-Type": "multipart/form-data; boundary=----WebKitFormBoundaryDtoO2QfPwfhTjOuS"
			],
			body: """------WebKitFormBoundaryDtoO2QfPwfhTjOuS
Content-Disposition: form-data; name="uploadFile"; filename="${id}-${fileName}"
Content-Type: text/plain

${contents}

------WebKitFormBoundaryDtoO2QfPwfhTjOuS
Content-Disposition: form-data; name="folder"


------WebKitFormBoundaryDtoO2QfPwfhTjOuS--""",
			timeout: 300,
			ignoreSSLIssues: true
		]
		httpPost(params) { resp ->
			
		}
		return true
	}
	catch (e) {
		log.error "Error installing file: ${e}"
	}
	return false
}

def uninstallFile(id, fileName) {
	try
	{
		def params = [
			uri: "http://127.0.0.1:8080",
			path: "/hub/fileManager/delete",
			contentType: "application/json",
			requestContentType: "application/json",
			headers: [
				"Cookie": state.cookie
			],
			body: groovy.json.JsonOutput.toJson(
				name: "${id}-${fileName}",
				type: "file"
			),
			timeout: 300,
			ignoreSSLIssues: true
		]
		httpPost(params) { resp ->

		}
		return true
	}
	catch (e) {
		log.error "Error uninstalling file: ${e}"
	}
	return false
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

def triggerError(title, message, runInBackground) {
	if (runInBackground == true)
		return message
	errorOccurred = true
	errorTitle = title
	errorMessage = message
}

def complete(title, message, done = false, appID = null) {
	installAction = null
	completedActions = null
	manifestForRollback = null
	clearStateSettings(false)
	
	def nextPage = "prefOptions"
	def install = false
	
	if (done) {
		install = true
		nextPage = ""
	}
	
	return dynamicPage(name: "prefComplete", title: "", install: install, uninstall: false, nextPage: nextPage) {
		displayHeader()
		section {
			paragraph "<b>${title}</b>"
			paragraph message
			showHideNextButton(true)
			if (appID != null)
				redirectToAppInstall(appID)
		}
	}
}

def rollback(error, runInBackground) {
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
		for (installedFile in completedActions["fileInstalls"])
			uninstallFile(installedFile.id, installedFile.name)
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

		for (uninstalledFile in completedActions["fileUninstalls"])
			installFile(uninstalledFile.name, downloadFile(uninstalledFile.location))
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
	return triggerError("Error Occurred During Installation", "An error occurred while installing the package: ${error}.", runInBackground)
}

def loadSettingsFile() {
	return getJSONFile(settingsFile)
}

def installHPMManifest() {
	if (location.hpmVersion == null) {
		logDebug "Initializing HPM version"
		createLocationVariable("hpmVersion")
		sendLocationEvent(name: "hpmVersion", value: "0")
	}
	if (state.manifests[state.repositoryListingJSON.hpm.location] == null) {
		logDebug "Grabbing list of installed apps"
		if (!login()) {
			log.error "Failed to login to hub, please verify the username and password"
			return false
		}
		def appsInstalled = getAppList()
		
		logDebug "Installing HPM Manifest"
		def manifest = getJSONFile(state.repositoryListingJSON.hpm.location)
		if (manifest == null) {
			log.error "Error installing HPM manifest"
			return false
		}
		def appId = appsInstalled.find { i -> i.title == "Hubitat Package Manager" && i.namespace == "dcm.hpm"}?.id
		if (appId != null) {
			manifest.apps[0].heID = appId
			state.manifests[state.repositoryListingJSON.hpm.location] = manifest
			minimizeStoredManifests()
		}
		else
			log.error "Unable to get the app ID of the package manager"
	}
	else if (location.hpmVersion != null && location.hpmVersion != "0") {
		logDebug "Updating HPM version to ${location.hpmVersion} from previous upgrade"
		state.manifests[state.repositoryListingJSON.hpm.location].version = location.hpmVersion
		sendLocationEvent(name: "hpmVersion", value: "0")
	}
	return true
}

def updateRepositoryListing() {
	def addedRepositories = []
	logDebug "Refreshing repository list"
	def oldListOfRepositories = state.repositoryListingJSON.repositories
	state.repositoryListingJSON = getJSONFile(repositoryListing)
	if (state.customRepositories) {
		state.customRepositories.each { r -> 
			state.repositoryListingJSON.repositories << [name: r.value, location: r.key]
		}
	}
	if (installedRepositories == null) {
		def repos = [] as List
		state.repositoryListingJSON.repositories.each { it -> repos << it.location }
		app.updateSetting("installedRepositories", repos)
	}
	else {
		for (newRepo in state.repositoryListingJSON.repositories) {
			if (oldListOfRepositories.size() > 0 && !oldListOfRepositories.find { it -> it.location == newRepo.location} && !installedRepositories.contains(newRepo.location)) {
				log.info "A new repository was added, ${newRepo.location}"
				installedRepositories << newRepo.location
				addedRepositories << newRepo.location
			}
		}
		app.updateSetting("installedRepositories", installedRepositories)
	}
	return addedRepositories
}

def copyInstalledItemsToNewManifest(srcManifest, destManifest) {
	def srcInstalledApps = srcManifest.apps?.findAll { it -> it.heID != null }
	def srcInstalledDrivers = srcManifest.drivers?.findAll { it -> it.heID != null }
	def switchedToPackageVersion = false
	if (srcManifest.version == null && destManifest.version != null)
		switchedToPackageVersion = true
		
	for (app in srcInstalledApps) {
		def destApp = destManifest.apps?.find { it -> it.id == app.id }
		if (destApp && destApp.heID == null)
			destApp.heID = app.heID
		if (switchedToPackageVersion) {
			destApp.remove("version")
			destApp.remove("betaVersion")
		}
	}
	
	for (driver in srcInstalledDrivers) {
		def destDriver = destManifest.drivers?.find { it -> it.id == driver.id }
		if (destDriver && destDriver.heID == null)
			destDriver.heID = driver.heID
		if (switchedToPackageVersion) {
			destDriver.remove("version")
			destDriver.remove("betaVersion")
		}
	}
	
	if (srcManifest.payPalUrl != null)
		destManifest.payPalUrl = srcManifest.payPalUrl
		
	if (srcManifest.gitHubUrl != null)
		destManifest.gitHubUrl = srcManifest.gitHubUrl
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

def downloadFileManagerFiles(manifest) {
	def files = [:]
	for (fileToInstall in manifest.files) {
		def location = getItemDownloadLocation(fileToInstall)
		setBackgroundStatusMessage("Downloading ${location}")
		def fileContents = downloadFile(location)
		if (fileContents == null) {
			return [sucess:false, name: location]
		}
		files[location] = fileContents
	}
	return [success:true, files: files]
}

def findMatchingAppOrDriver(installedList, item) {
	def matchedItem = installedList.find { it -> it.title == item.name && it.namespace == item.namespace}
	if (matchedItem != null)
		return matchedItem
	
	if (item.alternateNames) {
		for (altName in item.alternateNames) {
			matchedItem = installedList.find { it -> it.title == altName.name && it.namespace == altName.namespace}
			if (matchedItem != null)
				return matchedItem
		}
	}
}

def shouldInstallBeta(item) {
	return item.betaLocation != null && includeBetas
}

def getItemDownloadLocation(item) {
	if (item.betaLocation != null && includeBetas)
		return item.betaLocation
	return item.location
}

def getItemVersion(item) {
	if (item == null)
		return null
	if (item.beta)
		return item.betaVersion + "beta"
	return item.version
}

def deleteCustomRepository(customRepositoryIdx) {
	def i = 0
	for (key in state.customRepositories.keySet()) {
		if (i == customRepositoryIdx) {
			state.customRepositories.remove(key)
			state.repositoryListingJSON.repositories.remove(key)
			installedRepositories.removeAll { it -> it == key}
			app.updateSetting("installedRepositories", installedRepositories as List)
			return
		}
	}
}

def logDebug(msg) {
	if (settings?.debugOutput != false) {
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

def getFormat(type, myText=""){            // Modified from @Stephack Code   
	if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
	if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}

def displayHeader() {
	section (getFormat("title", "Hubitat Package Manager")) {
		paragraph getFormat("line")
	}
}

def displayFooter(){
	section() {
		paragraph getFormat("line")
		paragraph "<div style='color:#1A77C9;text-align:center'>Hubitat Package Manager<br><a href='https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=7LBRPJRLJSDDN&source=url' target='_blank'><img src='https://www.paypalobjects.com/webstatic/mktg/logo/pp_cc_mark_37x23.jpg' border='0' alt='PayPal Logo'></a><br><br>Please consider donating. This app took a lot of work to make.<br>If you find it valuable, I'd certainly appreciate it!</div>"
	}       
}

def hasTag(pkg, tag) {
	return pkg.tags.find {it -> it == tag} != null
}

def nonIconTagsHtml(pkg) {
	def tagsHtml = '<div style="display:table-cell;width: 100%;">'
	def i = 0
	for (tag in pkg.tags) {
		if (!state.categoriesAndTags.tags.contains(tag))
			continue
		if (i >= 5) {
			tagsHtml += "... "
			break
		}
		if (!iconTags.contains(tag)) {
			tagsHtml += "<span class='hpmPill'>${tag}</span>"
			i++
		}
	}
	if (tagsHtml != "")
		return tagsHtml + "</div>"
	return ""
}

def renderPackageButton(pkg, i) {
	def badges = ""
	if (hasTag(pkg, "ZWave"))
		badges += '<i class="material-icons he-zwave" title="Z-Wave"></i>'
	if (hasTag(pkg, "Zigbee"))
		badges += '<i class="material-icons he-zigbee" title="Zigbee"></i>'
	if (hasTag(pkg, "Cloud"))
		badges += '<i class="material-icons material-icons-outlined" title="Cloud" style="font-size: 14pt">cloud</i>'
	if (hasTag(pkg, "LAN"))
		badges += '<i class="material-icons material-icons-outlined" title="LAN" style="font-size: 14pt">wifi</i>'
	href(name: "prefPkgInstallPackage${i}", title: "${pkg.name} by ${pkg.author}", required: false, page: "prefInstallChoices", description: pkg.description + " <div>"+nonIconTagsHtml(pkg) +"<div style='text-align: right;display: table-cell;width: 100%;'>" + badges + "</div></div>", params: [location: pkg.location, payPalUrl: pkg.payPalUrl]) 
	if (pkg.installed)
		disableHrefButton("prefPkgInstallPackage${i}")
	else
		styleHrefButton("prefPkgInstallPackage${i}")
}

def addCss() {
	paragraph """<style>
		.hpmNoClick::before { content: ''; font-family: 'Material Icons'; transform: none !important }
		.hpmClick::before { content: '' !important; font-family: 'Material Icons'; transform: none !important }
		.hpmPill { display: inline-block; border: 1px solid #33b5e5; border-radius: 5px 5px; padding-left: 2px; padding-right: 2px; background-color: #33b5e5; color: white; margin-left: 2px; margin-right: 2px; }
		
	</style>"""
}

def disableHrefButton(name) {
	paragraph """<script>
		\$('button[name^="_action_href_${name}|"]').parent().css({'pointer-events': 'none'}); 
		\$('button[name^="_action_href_${name}|"]').addClass('hpmNoClick');
	</script>"""
}

def styleHrefButton(name) {
	paragraph """<script>
		\$('button[name^="_action_href_${name}|"]').addClass('hpmClick');
	</script>"""
}

def showHideNextButton(show) {
	if(show) paragraph "<script>\$('button[name=\"_action_next\"]').show()</script>"  
	else paragraph "<script>\$('button[name=\"_action_next\"]').hide()</script>"
}

def redirectToAppInstall(appID) {
	paragraph "<script>\$('button[name=\"_action_next\"]').prop(\"onclick\", null).off(\"click\").click(function() { location.href = \"/installedapp/create/${appID}\";})</script>"
}

def performMigrations() {
	if (!state.repositoryListingJSON) {
		logDebug "Storing repository listing in state"
		state.repositoryListingJSON = getJSONFile(repositoryListing)
	}
	if (!state.manifestsHavePayPalAndGitHub) {
		logDebug "Adding GitHub and PayPal URLs to manifests..."
		for (repo in installedRepositories) {
			def repoName = getRepoName(repo)
			def fileContents = getJSONFile(repo)
			if (!fileContents) {
				log.warn "Error refreshing ${repoName}"
				setBackgroundStatusMessage("Failed to refresh ${repoName}")
				continue
			}
			for (pkg in fileContents.packages) {
				if (state.manifests[pkg.location]) {
					
					if (fileContents.gitHubUrl != null)
						state.manifests[pkg.location].gitHubUrl = fileContents.gitHubUrl
					if (fileContents.payPalUrl != null)
						state.manifests[pkg.location].payPalUrl = fileContents.payPalUrl
				}
			}
		}
		state.manifestsHavePayPalAndGitHub = true
	}
	if (!settings.autoUpdateMode) {
		autoUpdateMode = "Never"
		logDebug "Migrating auto updater mode"
		if (autoUpdates && autoUpdateAll)
			autoUpdateMode = "Always"
		else if (autoUpdates && !autoUpdateAll)
			autoUpdateMode = "Include"

		app.updateSetting("autoUpdateMode", [type: "enum", value: autoUpdateMode])
		logDebug "Converted update mode to ${autoUpdateMode}"
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