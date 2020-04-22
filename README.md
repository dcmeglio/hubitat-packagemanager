# hubitat-packagemanager
The Hubitat Package Manager provides tools to install, uninstall, and upgrade 3rd party packages to add to your Hubitat Elevation environment. This document includes information for how to use this app as well as how to contribute your own packages.

## Initial Configuration
If you use Hub Security you will need to provide the admin username and password of your hub. If you do not, simply turn the toggle off.

![Initial Setup Screen](https://github.com/dcmeglio/hubitat-packagemanager/raw/master/imgs/MainPage1.PNG)

The app will then perform a _Match Up_. This will automatically search all of the apps and drivers you have installed and compare it to a list of those that have packages. If a match is found, the package manager will begin monitoring that package for updates.

## Installing a Package
There are two ways to install a package. You can choose a package from a list of pre-configured repositories by choosing _From a Repository_, or, if you know the URL of a package you can choose _From a URL_

![Install Options Screen](https://github.com/dcmeglio/hubitat-packagemanager/raw/master/imgs/Install1.PNG)

If you choose to install from a repository you will first be presented with a list of categories, then the list of packages within that category. If you chose to enter a URL you will be prompted to enter the URL. _This is not the URL of a regular Hubitat app/driver. It is the URL of a package JSON. If you do not know what this is, you should probably choose to install from a repository_.

If the package includes any optional apps or drivers you will be prompted to choose which ones you'd like to install. If you need to change your decision later you can always return and perform a _Modify_ which will let you change the optional parameters.

![Install Optional Addons](https://github.com/dcmeglio/hubitat-packagemanager/raw/master/imgs/Install2.PNG)

After you make your decisions you'll be prompted to confirm by clicking _Next_. Once you do the package will be installed.

## Modifying a Package
If you need to add or remove an optional component from a package later, choose the _Modify_ option. You will be asked to choose which package you'd like to modify.

![Modify Optional Addons](https://github.com/dcmeglio/hubitat-packagemanager/raw/master/imgs/Modify1.PNG)

Choose the components to add and/or remove. You will then be asked to confirm before the changes are made. Click _Next_ to complete the changes.

## Uninstalling a Package
If you'd like to uninstall a package, choose the _Uninstall_ option. Choose the package to uninstall and click _Next_. You will then be prompted to confirm the components that are to be removed. Note, this option cannot be undone. If you would like the package to be reinstalled you must do so by running an _Install_ and you will have to reconfigure all of your settings again.

![Uninstall Package](https://github.com/dcmeglio/hubitat-packagemanager/raw/master/imgs/Uninstall1.PNG)

## Updating Packages
When an author releases a new version of a package, to install it, choose the _Update_ option. If updates are available, choose the packages you wish to update and then click _Next_. You will then be able to confirm your selections and install the updates by clicking _Next_.

![Update Package](https://github.com/dcmeglio/hubitat-packagemanager/raw/master/imgs/Update1.PNG)

## Match Up
A Match Up will search through all of the apps and drivers you have installed on your hub and attempt to figure out if there are packages available that match these apps and drivers. This isn't an exact science. The Hubitat Package Manager will show you matches it found and allow you to confirm those that appear to be correct. This will then cause the selected apps and drivers to be monitored for updates. Unfortunately, the package manager has no way to know what version of a previously installed app or driver was installed. You have two options. You can either tell the package manager to assume that the version you have installed is up to date, or not. If it is set to assume it is up to date, you will not receive update notifications until the next time a new version is available. If it is set to not be up to date, you can immediately perform an _Update_ which will ensure the latest version is installed.

![Match Up Packages](https://github.com/dcmeglio/hubitat-packagemanager/raw/master/imgs/MatchUp1.PNG)

## View Apps and Drivers
This will show you all of the apps and drivers you have installed that are currently being managed by the Hubitat Package Manager. You should _Not_ uninstall or update these apps and drivers manually. You should only modify them using the Hubitat Package Manager.

![View Apps and Drivers](https://github.com/dcmeglio/hubitat-packagemanager/raw/master/imgs/ViewApps1.PNG)

## Package Manager Settings
This is where you can control the settings of the Hubitat Package Manager. You can modify your Hub Security settings, choose which repositories you want to use, enable or disable debug logging, manage updates, and also _Add a Custom Repository_.

![Package Manager Settings](https://github.com/dcmeglio/hubitat-packagemanager/raw/master/imgs/Settings1.PNG)

The updates include several options. You can configure when the update checker runs (Note: you should not run the checker during the Hubitat maintenance window at 3am), whether or not you'd like to receive a notification if updates are available, and if the updates should be installed automatically. You can choose to install all updates automatically, or just for certain packages. Additionally you can receive a push notification when an update either succeeds or fails.
![Package Manager Settings](https://github.com/dcmeglio/hubitat-packagemanager/raw/master/imgs/Settings2.PNG)

## Developer Information
The information below is intended for app and driver developers who wish to use Hubitat Package Manager to provide your apps and drivers. Two things are needed, each package must provide a _manifest_ and you must provide a _repository_ that lists your packages. A small little tool called [Hubitat Package Manager Tools](https://github.com/dcmeglio/hubitat-packagemanagertools/releases) has been provided which assists in the creation of these files. You will need the .NET Core Framework which can be downloaded from Microsoft at:

* [Windows](https://docs.microsoft.com/en-us/dotnet/core/install/runtime?pivots=os-windows)
* [MacOS](https://docs.microsoft.com/en-us/dotnet/core/install/runtime?pivots=os-macos) 
* [Linux](https://docs.microsoft.com/en-us/dotnet/core/install/runtime?pivots=os-linux)

Once installed, on Windows you can simply run `hpm --help` from a command line. On MacOS or Linux use `dotnet hpm.dll --help`


### Package Manifest 
The package manifest is a JSON file that lists the apps and drivers that are part of your package. A recommendation when versioning your packages is to use [SemVer](https://semver.org/). This will ensure that the Hubitat Package Manager is always able to detect updates properly. You can either version the entire package as a whole, or each app/driver can be versioned, but don't mix-and-match within the same package.
 
#### Example
```json
{
	"packageName": "My Package",
	"minimumHEVersion": "2.1.9",
	"author": "Dominick Meglio",
	"version": "1.0",
	"dateReleased": "2020-04-07",
	"licenseFile": "",
	"releaseNotes": "",
	"apps" : [
		{
			"id" : "67d9cc01-a5cb-453c-832a-e78c5a6b978b",
			"name": "The App",
			"namespace": "abc.theapp",
			"location": "https://raw.githubusercontent.com/app.groovy",
			"required": true,
			"oauth": false
		}
	],
	"drivers" : [
		{
			"id": "22597029-98db-490b-b8b9-c23b972ee5f2",
			"name": "Required Driver",
			"namespace": "abc.reqdriver",
			"location": "https://raw.githubusercontent.com/driver1.groovy",
			"required": true
		},
		{
			"id": "e012ffff-7959-466b-a2ae-3181a33010f9",
			"name": "Optional Driver",
			"namespace": "abc.optdriver",
			"location": "https://raw.githubusercontent.com/driver2.groovy",
			"required": false
		}
	]
}
```

#### Commands Used
    hpm manifest-create packageManifest.json --name="My Package" --author="Dominick Meglio" --version=1.0 --heversion=2.1.9 --datereleased=2019-04-07
	hpm manifest-add-app packageManifest.json --location=https://raw.githubusercontent.com/app.groovy --required=true --oauth=false
	hpm manifest-add-driver packageManifest.json --location=https://raw.githubusercontent.com/driver1.groovy --required=true
	hpm manifest-add-driver packageManifest.json --location=https://raw.githubusercontent.com/driver2.groovy --required=false

### Repository File
The repository file is another JSON file that can be created using the [Hubitat Package Manager Tools](https://github.com/dcmeglio/hubitat-packagemanagertools/releases). This lets you list all of the packages you have available in your repository. When assigning a category to your packages please reference the list below.

#### Categories
To prevent the list of packages from getting unwieldy, they are divided into categories. The following categories are currently available:

* Control - lighting/motion/presence/locks/etc.
* Convenience - A general category that's mainly a catch all.
* Integrations - devices/applications/services 
* Notifications - speakers/TTS/music/etc.
* Security - presence/camera/sensors/etc.
* Utility - Data/Button controllers/IR control/etc.

If you feel the need to add additional categories, that's fine but I'd request you submit a Pull Request and update this README file. The idea is, if every developer uses the same set of categories we will be in a much better place where the list of categories doesn't get too large and unruly.

#### Publishing your Repository
When your repository file is ready to go, submit a Pull Request against https://raw.githubusercontent.com/dcmeglio/hubitat-packagerepositories/master/repositories.json which includes the _name_ of your new repository and the _location_ of your repository JSON. Once your pull request is merged it will become available to all Hubitat Package Manager users.

#### Example
```json
{
	"author": "Dominick Meglio",
	"gitHubUrl": "https://github.com/dcmeglio",
	"payPalUrl": "https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=7LBRPJRLJSDDN&source=url",
	"packages": [
		{
			"name": "Package 1",
			"category": "Integrations",
			"location": "https://raw.githubusercontent.com/package1/packageManifest.json",
			"description": "This is Package 1"
		},
		{
			"name": "Package 2",
			"category": "Convenience",
			"location": "https://raw.githubusercontent.com/package2/packageManifest.json",
			"description": "This is Package 2"
		}
	]
	
}
```

#### Commands Used
    hpm repository-create repository.json --author="Dominick Meglio" --githuburl=https://github.com/dcmeglio --paypalurl="https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=7LBRPJRLJSDDN&source=url"
	hpm repository-add-package repository.json --manifest=https://raw.githubusercontent.com/package1/packageManifest.json --name="Package 1" --category=Integrations --description="This is Package 1"
	hpm repository-add-package repository.json --manifest=https://raw.githubusercontent.com/package2/packageManifest.json --name="Package 2" --category=Convenience --description="This is Package 2"