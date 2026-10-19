#define MyAppVersion "3.0.22"
#define MyAppName "AetherPort"
#define MyAppPublisher "AetherPort"
#define MyAppExeName "AetherPort.exe"

[Setup]
AppId={{22D37AC4-7A13-4E95-A164-B1C40E10939E}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\AetherPort
DefaultGroupName=AetherPort
DisableProgramGroupPage=yes
OutputDir=..\dist
OutputBaseFilename=AetherPort-Windows-Setup-v{#MyAppVersion}
SetupIconFile=..\assets\aetherport.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
CloseApplications=yes
RestartApplications=no
ChangesAssociations=no

[Files]
Source: "..\dist\AetherPort\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\AetherPort"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\AetherPort"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Shortcuts:"; Flags: unchecked

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch AetherPort"; Flags: nowait postinstall skipifsilent
