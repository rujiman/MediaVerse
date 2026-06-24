[Setup]
AppName=MediaVerse
AppVersion=1.0.0
AppPublisher=Rujiman
DefaultDirName={pf}\MediaVerse
DefaultGroupName=MediaVerse
OutputDir=output
OutputBaseFilename=MediaVerse-1.0.0-Setup
SetupIconFile=C:\Users\rujim\Documents\Proyects\mediaTracker\mediaverse_logo.ico
WizardStyle=modern
PrivilegesRequired=lowest
Compression=lzma
SolidCompression=yes

[Files]
; Java 25
Source: "C:\Users\rujim\Desktop\JDK\*"; DestDir: "{app}\jdk"; Flags: ignoreversion recursesubdirs createallsubdirs

; Maven
Source: "C:\maven\apache-maven-3.9.16-bin\apache-maven-3.9.16\*"; DestDir: "{app}\maven"; Flags: ignoreversion recursesubdirs createallsubdirs

; Proyecto
Source: "C:\Users\rujim\Documents\Proyects\mediaTracker\src\*"; DestDir: "{app}\src"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "C:\Users\rujim\Documents\Proyects\mediaTracker\pom.xml"; DestDir: "{app}"; Flags: ignoreversion
Source: "C:\Users\rujim\Documents\Proyects\mediaTracker\.env.example"; DestDir: "{app}"; Flags: ignoreversion
Source: "C:\Users\rujim\Documents\Proyects\mediaTracker\README.md"; DestDir: "{app}"; Flags: ignoreversion

; Script de inicio
Source: "C:\Users\rujim\Documents\Proyects\mediaTracker\run-mediaverse.bat"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\MediaVerse"; Filename: "{app}\run-mediaverse.bat"; IconFilename: "{app}\mediaverse_logo.ico"
Name: "{commondesktop}\MediaVerse"; Filename: "{app}\run-mediaverse.bat"; IconFilename: "{app}\mediaverse_logo.ico"

[Run]
Filename: "{app}\run-mediaverse.bat"; Description: "Ejecutar MediaVerse"; Flags: nowait postinstall skipifsilent