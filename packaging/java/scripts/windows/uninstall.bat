@ECHO OFF

@ECHO Stopping Comm360 ...
net stop comm360

@ECHO Uninstalling Comm360 ...
"%~dp0"comm360.exe uninstall

@ECHO DONE.