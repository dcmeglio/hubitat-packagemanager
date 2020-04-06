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
    page(name: "prefPkgUpdate")
	page(name: "prefPkgUninstall")
    page(name: "prefInstallChoices")
	page(name: "prefInstallVerify")
	page(name: "prefInstall")
	page(name: "prefPkgUnistallConfirm")
	page(name: "prefPkgUnistallComplete")

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
			href(name: "prefPkgUninstall", title: "Uninstall", required: false, page: "prefPkgUninstall", description: "Uninstall a package")
            href(name: "prefPkgUpdate", title: "Update", required: false, page: "prefPkgUpdate", description: "Check for updates")
		}
	}
}

def prefPkgInstall() {
	return dynamicPage(name: "prefPkgInstall", title: "Install a Package", nextPage: "prefInstallChoices", install: false, uninstall: false) {
		section {
			input "pkgInstall", "text", title: "Enter the URL of a package manifest you wish to install"
		}
	}
}

def prefInstallChoices() {
	if (state.manifests == null)
		state.manifests = [:]
    def manifest = getManifestFile(pkgInstall)
	
	state.manifests[pkgInstall] = manifest
	
	def apps = getAppsForInput(manifest)
	def drivers = getDriversForInput(manifest)
	
	if (!verifyHEVersion(manifest.minimumHEVersion)) {
		return dynamicPage(name: "prefInstallChoices", title: "Unsupported Hubitat Firmware", install: true, uninstall: true) {
			section {
				paragraph "Your Hubitat Elevation firmware is not supported. You are running ${location.hub.firmwareVersionString} and this package requires  at least ${manifest.minimumHEVersion}. Please upgrade your firmware to continue installing"
			}
		}
	} 
	else { 
		return dynamicPage(name: "prefInstallChoices", title: "Choose the components to install", nextPage: "prefInstallVerify", install: false, uninstall: false) {
			section {
				input "appsToInstall", "enum", title: "Select the apps to install", options: apps, hideWhenEmpty: true, multiple: true
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
	
	
	for (appToInstall in appsToInstall) {
		def matchedApp = manifest.apps.find { it.id == appToInstall}
		def fileContents = downloadFile(matchedApp.location)
		matchedApp.heID = installApp(fileContents)
	}
	for (driverToInstall in driversToInstall) {
		def matchedDriver = manifest.drivers.find { it.id == driverToInstall}
		def fileContents = downloadFile(matchedDriver.location)
		matchedDriver.heID = installDriver(fileContents)
	}
	log.debug manifest
    return dynamicPage(name: "prefInstall", title: "Ready to install", install: true, uninstall: true) {
		section {
			paragraph "Installation successful, click done."
		}
	}
}

def downloadFile(file) {
	def params = [
        uri: file,
        requestContentType: "application/json",
        contentType: "application/json",
        textParser: true
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
		]
	]
	def result
	httpPost(params) { resp ->
		result = resp.headers."Location".replaceAll("http://127.0.0.1:8080/app/editor/","")
	}
	return result
}

def uninstallApp(id) {
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
		]
	]
	httpPost(params) { resp ->
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
		]
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
		path: "/driver/edit/update",
		requestContentType: "application/x-www-form-urlencoded",
		headers: [
			"Cookie": state.cookie
		],
		body: [
			id: id,
			"_action_delete": "Delete"
		]
	]
	httpPost(params) { resp ->
	}
}

def getAppsForInput(manifest) {
	def appsList = [:]
	for (app in manifest.apps) {
        appsList << ["${app.id}":app.name]
	}
	return appsList
}

def getDriversForInput(manifest) {
	def driversList = [:]
	for (driver in manifest.drivers) {
        driversList << ["${driver.id}":driver.name]
	}
	return driversList
}

def getManifestFile(uri) {
    def params = [
        uri: uri,
        requestContentType: "application/json",
        contentType: "application/json",
        textParser: true
    ]
	def result
    httpGet(params) { resp ->
        result = new groovy.json.JsonSlurper().parseText(resp.data.text)
    }
	return result
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

def prefPkgUpdate() {
	def needsUpdate = [:]
	def needsUpdateStr = "<ul>"
	for (pkg in state.manifests) {
		def manifest = getManifestFile(pkg.key)
		
		
		if (newVersionAvailable(manifest.version, state.manifests[pkg.key].version)) {
			needsUpdateStr += "<li>${manifest.packageName} - (installed: ${state.manifests[pkg.key].version} current: ${manifest.version})</li>"
			needsUpdate << manifest
		}
	}
	
	return dynamicPage(name: "prefPkgUpdate", title: "Updates Available", nextPage: "prefPkgUnistallConfirm", install: false, uninstall: false) {
		section {
			paragraph needsUpdateStr
		}
	}
}

def prefPkgUninstall() {
	def pkgsToList = [:]
	for (pkg in state.manifests) {
		log.debug pkg
		pkgsToList[pkg.key] = pkg.value.packageName
	}
	return dynamicPage(name: "prefPkgUninstall", title: "Choose the package to uninstall", nextPage: "prefPkgUnistallConfirm", install: false, uninstall: false) {
		section {
			input "pkgUninstall", "enum", options: pkgsToList, required: true
		}
	}
}


def prefPkgUnistallConfirm() {
	return dynamicPage(name: "prefPkgUnistallConfirm", title: "Choose the package to uninstall", nextPage: "prefPkgUnistallComplete", install: false, uninstall: false) {
		section {
			paragraph "The following apps and drivers will be removed:"
			
			def str = "<ul>"
			def pkg = state.manifests[pkgUninstall]
			for (app in pkg.apps) {
				
				str += "<li>" + app.name + "</li>"
			}
			
			for (driver in pkg.drivers) {
				
				str += "<li>" + driver.name + "</li>"
			}
			str += "</ul>"
			paragraph str
		}
	}
}

def prefPkgUnistallComplete() {
	def pkg = state.manifests[pkgUninstall]
			
	for (app in pkg.apps) {
		
		uninstallApp(app.heID)
	}
	
	for (driver in pkg.drivers) {
		
		uninstallDriver(driver.heID)
	}
	state.manifests.remove(pkgUninstall)
	
	return dynamicPage(name: "prefPkgUnistallConfirm", title: "Uninstall complete", install: true, uninstall: true) {
		section {
			paragraph "Package successfully removed."
		}
	}
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
			log.debug state.cookie + "cookie"
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
            log.debug "response.status: ${resp.status}"            
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