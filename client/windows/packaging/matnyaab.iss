; نصب‌کنندهٔ متن‌یاب (Inno Setup 6) — در CI ساخته می‌شود
#ifndef AppVersion
  #define AppVersion "2.1"
#endif
#ifndef SourceDir
  #define SourceDir "..\release\Matnyaab"
#endif
#ifndef OutputDir
  #define OutputDir "..\release"
#endif

[Setup]
AppId={{8F4B2C1E-5D7A-4E39-9C61-6A2B7D0E3F15}
AppName=متن‌یاب
AppVersion={#AppVersion}
AppVerName=متن‌یاب {#AppVersion}
AppPublisher=Matnyaab
AppPublisherURL=https://www.matnyaab.ir
AppSupportURL=https://www.matnyaab.ir/#contact
DefaultDirName={autopf}\Matnyaab
DefaultGroupName=متن‌یاب
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
OutputDir={#OutputDir}
OutputBaseFilename=Matnyaab-Setup-{#AppVersion}
SetupIconFile=matnyaab.ico
UninstallDisplayIcon={app}\Matnyaab.exe
UninstallDisplayName=متن‌یاب {#AppVersion}
Compression=lzma2/ultra64
SolidCompression=yes
LZMANumBlockThreads=4
WizardStyle=modern
CloseApplications=yes
VersionInfoVersion={#AppVersion}.0.0
VersionInfoProductName=Matnyaab

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\متن‌یاب"; Filename: "{app}\Matnyaab.exe"
Name: "{group}\{cm:UninstallProgram,متن‌یاب}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\متن‌یاب"; Filename: "{app}\Matnyaab.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Matnyaab.exe"; Description: "{cm:LaunchProgram,Matnyaab}"; Flags: nowait postinstall skipifsilent
