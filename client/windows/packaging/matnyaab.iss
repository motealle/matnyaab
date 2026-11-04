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
DefaultDirName={userpf}\Matnyaab
DefaultGroupName=متن‌یاب
DisableProgramGroupPage=yes
; فقط نصب برای کاربر جاری: هیچ‌وقت به دسترسی مدیر ویندوز نیاز ندارد
PrivilegesRequired=lowest
UsePreviousAppDir=no
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
OutputDir={#OutputDir}
OutputBaseFilename=Matnyaab-Setup-{#AppVersion}
SetupIconFile=matnyaab.ico
UninstallDisplayIcon={app}\Matnyaab.exe
UninstallDisplayName=متن‌یاب {#AppVersion}
Compression=lzma2/max
SolidCompression=yes
LZMAUseSeparateProcess=yes
LZMANumBlockThreads=2
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
Filename: "{app}\Matnyaab.exe"; Description: "{cm:LaunchProgram,Matnyaab}"; Flags: nowait postinstall skipifsilent shellexec

[Code]
// اگر نسخهٔ پیشین «برای همهٔ کاربران» (Program Files) نصب شده، پیشنهاد حذف آن داده می‌شود
function InitializeSetup(): Boolean;
var
  S: String;
  R: Integer;
begin
  Result := True;
  if RegQueryStringValue(HKLM, 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{8F4B2C1E-5D7A-4E39-9C61-6A2B7D0E3F15}_is1', 'UninstallString', S) then
  begin
    if SuppressibleMsgBox('A previous all-users copy of Matnyaab was found in Program Files.' + #13#10 +
      'Remove it now to avoid two copies? (Your indexes and settings are kept.)', mbConfirmation, MB_YESNO, IDNO) = IDYES then
      ShellExec('runas', RemoveQuotes(S), '/SILENT /SUPPRESSMSGBOXES /NORESTART', '', SW_SHOW, ewWaitUntilTerminated, R);
  end;
end;
