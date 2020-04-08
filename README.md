# hubitat-packagemanager
 The Hubitat Package Manager provides tools to install, uninstall, and upgrade 3rd party packages to add to your Hubitat Elevation environment. This document includes information for how to use this app as well as how to contribute your own packages.
 
 ## Initial Configuration
 If you use Hub Security you will need to provide the admin username and password of your hub. If you do not, simply turn the toggle off.
 
 ![Initial Setup Screen](https://github.com/dcmeglio/hubitat-packagemanager/raw/master/imgs/MainPage1.PNG)
 
 ## Installing a Package
 There are two ways to install a package. You can choose a package from a list of pre-configured repositories by choosing _From a Repository_, or, if you know the URL of a package you can choose _From a URL_
 
 ![Install Options Screen](https://github.com/dcmeglio/hubitat-packagemanager/raw/master/imgs/Install1.PNG)
 
 If you choose to install from a repository you will first be presented with a list of categories, then the list of packages within that category. If you chose to enter a URL you will be prompted to enter the URL.
 
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
 
 ## Developer Information
 The information below is intended for app and driver developers who wish to use Hubitat Package Manager to provide your apps and drivers. Two things are needed, each package must provide a _manifest_ and you must provide a _repository_ that lists your packages.
 
 ### Package Manifest 
 The package manifest is a JSON file that lists the apps and drivers that are part of your package. Note that the manifest requires you to create GUIDs to uniquely identify your apps and drivers. You can use [guidgenerator.com](https://guidgenerator.com/) to generate your own.
 
 Within the root of the JSON, define the _packageName_ which will be displayed to the user, the _minimumHEVersion_ which is the minimum firmware version supported (use 0 if you support all versions), _author_ to list the author name, and _dateReleased_ to indicate when this release was made. The _version_ is one of the most important entries. You can either version your entire package as a whole or each individual component. Versioning each component is preferred. If you are versioning the whole package, specify the version in the root. Otherwise specify a version for each app and driver. The _Update_ functionality will automatically look for a higher version number (which must be all numeric separated by dots) to detect a version. [SemVer](https://semver.org/) is a great versioning system that will work with this system. 
 
 Next, if your package includes apps, create an array called _apps_. Each app consists of an _id_ which is a GUID, a _name_ which is the name of the app, and _location_ which specifies a URL where the Groovy file for the app will be found. Additionally, you can specify two boolean values. If the app is required, set _required_ to true. If it is optional, set _required_ to false. Optional apps will allow the user to choose whether or not to install them when adding the package. Finally, if the app requires OAuth access, set _oauth_ to true, otherwise set it to false.
 
  Finally, if your package includes drivers, create an array called _drivers_. Each driver consists of an _id_ which is a GUID, a _name_ which is the name of the driver, and _location_ which specifies a URL where the Groovy file for the driver will be found. Additionally, you can specify a boolean value to indicate if the driver is required. set _required_ to true if required, or if it is optional, set _required_ to false. Below is an example of a package manifest:
 
 #### Example
 ```json
 {
	"packageName": "My Package",
	"minimumHEVersion": "2.1.9",
	"author": "Dominick Meglio",
	"version": "1.0",
	"dateReleased": "2020-04-07",
	"apps" : [
		{
			"id" : "67d9cc01-a5cb-453c-832a-e78c5a6b978b",
			"name": "The App",
			"location": "https://raw.githubusercontent.com/app.groovy",
			"required": true,
			"oauth": false
		}
	],
	"drivers" : [
		{
			"id": "22597029-98db-490b-b8b9-c23b972ee5f2",
			"name": "Required Driver",
			"location": "https://raw.githubusercontent.com/driver1.groovy",
			"required": true
		},
		{
			"id": "e012ffff-7959-466b-a2ae-3181a33010f9",
			"name": "Optional Driver",
			"location": "https://raw.githubusercontent.com/driver2.groovy",
			"required": false
		}
	]
}
 ```