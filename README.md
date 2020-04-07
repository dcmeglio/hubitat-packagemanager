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