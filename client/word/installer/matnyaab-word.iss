; نصب‌کنندهٔ «متن‌یاب برای ورد» (Inno Setup 6) — در CI ساخته و آزموده می‌شود
; اصول بی‌خطایی:
;  - فقط برای کاربر جاری (بدون نیاز به دسترسی مدیر؛ خطای 740 ممکن نیست)
;  - ثبت COM در هر دو نمای رجیستری (آفیس ۳۲ و ۶۴ بیتی)
;  - هر نسخه در پوشهٔ جدا: حتی با ورد باز، فایل قفل‌شده‌ای بازنویسی نمی‌شود
;  - مسیر غیرلاتین (نام کاربری فارسی) با مسیر کوتاه 8.3 ثبت می‌شود
;  - افزونه از فهرست «غیرفعال‌شده‌ها»ی ورد خارج و در فهرست «هرگز غیرفعال نشود» ثبت می‌شود
#ifndef AddinVersion
  #define AddinVersion "1.0.0"
#endif
#ifndef SourceDir
  #define SourceDir "..\MatnyaabWord\bin\Release\net472"
#endif
#ifndef OutputDir
  #define OutputDir "."
#endif
#define ConnectClsid "{{3B1E5F7A-8C2D-4E6B-A9F0-5D7C1B2E4A61}"
#define PaneClsid "{{9D2A4C6E-1F3B-4A57-8E20-6B4D3C5F7A82}"
#define AsmName "MatnyaabWord, Version=1.0.0.0, Culture=neutral, PublicKeyToken=null"

[Setup]
AppId={{5C9E2A41-7D3B-4F18-A6E2-0B9C4D1E8F37}
AppName=متن‌یاب برای ورد
AppVersion={#AddinVersion}
AppVerName=متن‌یاب برای ورد {#AddinVersion}
AppPublisher=Matnyaab
AppPublisherURL=https://www.matnyaab.ir
AppSupportURL=https://www.matnyaab.ir/#contact
DefaultDirName={localappdata}\Matnyaab\WordAddin
DisableDirPage=yes
DisableProgramGroupPage=yes
DisableReadyPage=yes
PrivilegesRequired=lowest
UsePreviousAppDir=no
OutputDir={#OutputDir}
OutputBaseFilename=Matnyaab-Word-Setup-{#AddinVersion}
SetupIconFile=..\..\windows\packaging\matnyaab.ico
UninstallDisplayIcon={app}\{#AddinVersion}\matnyaab.ico
UninstallDisplayName=متن‌یاب برای ورد {#AddinVersion}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
WizardSizePercent=110
CloseApplications=no
RestartApplications=no
VersionInfoVersion={#AddinVersion}.0
VersionInfoProductName=Matnyaab for Word

[Messages]
WelcomeLabel2=افزونهٔ «متن‌یاب برای ورد» روی ورد شما نصب می‌شود:%n%n• جست‌وجو در کتابخانهٔ متن‌یاب از درون ورد و درج با منبع (APA)%n• اصطلاحات لاتین ↔ پانویس، تشخیص سرفصل‌ها، یکدست‌سازی متن%n• قالب‌های پایان‌نامه و مقاله، دستیار هوشمند نگارش%n%nنیازی به دسترسی مدیر نیست. اگر ورد باز است، پس از نصب یک بار ببندید و باز کنید.
FinishedLabel=نصب تمام شد 🌱%n%nورد را باز کنید؛ زبانهٔ «متن‌یاب» در نوار بالا منتظر شماست.

[Files]
Source: "{#SourceDir}\MatnyaabWord.dll"; DestDir: "{app}\{#AddinVersion}"; Flags: ignoreversion
Source: "{#SourceDir}\Fonts\*.ttf"; DestDir: "{app}\{#AddinVersion}\Fonts"; Flags: ignoreversion skipifsourcedoesntexist
Source: "..\..\windows\packaging\matnyaab.ico"; DestDir: "{app}\{#AddinVersion}"; Flags: ignoreversion

[Registry]
; ---- کلاس افزونه (نمای ۶۴ بیتی و ۳۲ بیتی)
Root: HKCU64; Subkey: "Software\Classes\Matnyaab.Word.Connect"; ValueType: string; ValueData: "Matnyaab.Word.Com.Connect"; Flags: uninsdeletekey; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\Matnyaab.Word.Connect\CLSID"; ValueType: string; ValueData: "{#ConnectClsid}"; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\CLSID\{#ConnectClsid}"; ValueType: string; ValueData: "Matnyaab.Word.Com.Connect"; Flags: uninsdeletekey; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\CLSID\{#ConnectClsid}\ProgId"; ValueType: string; ValueData: "Matnyaab.Word.Connect"; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\CLSID\{#ConnectClsid}\Implemented Categories\{{62C8FE65-4EBB-45e7-B440-6E39B2CDBF29}"; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\CLSID\{#ConnectClsid}\InprocServer32"; ValueType: string; ValueData: "mscoree.dll"; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\CLSID\{#ConnectClsid}\InprocServer32"; ValueType: string; ValueName: "ThreadingModel"; ValueData: "Both"; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\CLSID\{#ConnectClsid}\InprocServer32"; ValueType: string; ValueName: "Class"; ValueData: "Matnyaab.Word.Com.Connect"; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\CLSID\{#ConnectClsid}\InprocServer32"; ValueType: string; ValueName: "Assembly"; ValueData: "{#AsmName}"; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\CLSID\{#ConnectClsid}\InprocServer32"; ValueType: string; ValueName: "RuntimeVersion"; ValueData: "v4.0.30319"; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\CLSID\{#ConnectClsid}\InprocServer32"; ValueType: string; ValueName: "CodeBase"; ValueData: "{code:CodeBase}"; Check: IsWin64
Root: HKCU32; Subkey: "Software\Classes\Matnyaab.Word.Connect"; ValueType: string; ValueData: "Matnyaab.Word.Com.Connect"; Flags: uninsdeletekey
Root: HKCU32; Subkey: "Software\Classes\Matnyaab.Word.Connect\CLSID"; ValueType: string; ValueData: "{#ConnectClsid}"
Root: HKCU32; Subkey: "Software\Classes\CLSID\{#ConnectClsid}"; ValueType: string; ValueData: "Matnyaab.Word.Com.Connect"; Flags: uninsdeletekey
Root: HKCU32; Subkey: "Software\Classes\CLSID\{#ConnectClsid}\ProgId"; ValueType: string; ValueData: "Matnyaab.Word.Connect"
Root: HKCU32; Subkey: "Software\Classes\CLSID\{#ConnectClsid}\Implemented Categories\{{62C8FE65-4EBB-45e7-B440-6E39B2CDBF29}"
Root: HKCU32; Subkey: "Software\Classes\CLSID\{#ConnectClsid}\InprocServer32"; ValueType: string; ValueData: "mscoree.dll"
Root: HKCU32; Subkey: "Software\Classes\CLSID\{#ConnectClsid}\InprocServer32"; ValueType: string; ValueName: "ThreadingModel"; ValueData: "Both"
Root: HKCU32; Subkey: "Software\Classes\CLSID\{#ConnectClsid}\InprocServer32"; ValueType: string; ValueName: "Class"; ValueData: "Matnyaab.Word.Com.Connect"
Root: HKCU32; Subkey: "Software\Classes\CLSID\{#ConnectClsid}\InprocServer32"; ValueType: string; ValueName: "Assembly"; ValueData: "{#AsmName}"
Root: HKCU32; Subkey: "Software\Classes\CLSID\{#ConnectClsid}\InprocServer32"; ValueType: string; ValueName: "RuntimeVersion"; ValueData: "v4.0.30319"
Root: HKCU32; Subkey: "Software\Classes\CLSID\{#ConnectClsid}\InprocServer32"; ValueType: string; ValueName: "CodeBase"; ValueData: "{code:CodeBase}"

; ---- کنترل پنل کناری
Root: HKCU64; Subkey: "Software\Classes\Matnyaab.Word.Pane"; ValueType: string; ValueData: "Matnyaab.Word.Com.PaneControl"; Flags: uninsdeletekey; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\Matnyaab.Word.Pane\CLSID"; ValueType: string; ValueData: "{#PaneClsid}"; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\CLSID\{#PaneClsid}"; ValueType: string; ValueData: "Matnyaab.Word.Com.PaneControl"; Flags: uninsdeletekey; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\CLSID\{#PaneClsid}\ProgId"; ValueType: string; ValueData: "Matnyaab.Word.Pane"; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\CLSID\{#PaneClsid}\Implemented Categories\{{62C8FE65-4EBB-45e7-B440-6E39B2CDBF29}"; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\CLSID\{#PaneClsid}\InprocServer32"; ValueType: string; ValueData: "mscoree.dll"; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\CLSID\{#PaneClsid}\InprocServer32"; ValueType: string; ValueName: "ThreadingModel"; ValueData: "Both"; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\CLSID\{#PaneClsid}\InprocServer32"; ValueType: string; ValueName: "Class"; ValueData: "Matnyaab.Word.Com.PaneControl"; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\CLSID\{#PaneClsid}\InprocServer32"; ValueType: string; ValueName: "Assembly"; ValueData: "{#AsmName}"; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\CLSID\{#PaneClsid}\InprocServer32"; ValueType: string; ValueName: "RuntimeVersion"; ValueData: "v4.0.30319"; Check: IsWin64
Root: HKCU64; Subkey: "Software\Classes\CLSID\{#PaneClsid}\InprocServer32"; ValueType: string; ValueName: "CodeBase"; ValueData: "{code:CodeBase}"; Check: IsWin64
Root: HKCU32; Subkey: "Software\Classes\Matnyaab.Word.Pane"; ValueType: string; ValueData: "Matnyaab.Word.Com.PaneControl"; Flags: uninsdeletekey
Root: HKCU32; Subkey: "Software\Classes\Matnyaab.Word.Pane\CLSID"; ValueType: string; ValueData: "{#PaneClsid}"
Root: HKCU32; Subkey: "Software\Classes\CLSID\{#PaneClsid}"; ValueType: string; ValueData: "Matnyaab.Word.Com.PaneControl"; Flags: uninsdeletekey
Root: HKCU32; Subkey: "Software\Classes\CLSID\{#PaneClsid}\ProgId"; ValueType: string; ValueData: "Matnyaab.Word.Pane"
Root: HKCU32; Subkey: "Software\Classes\CLSID\{#PaneClsid}\Implemented Categories\{{62C8FE65-4EBB-45e7-B440-6E39B2CDBF29}"
Root: HKCU32; Subkey: "Software\Classes\CLSID\{#PaneClsid}\InprocServer32"; ValueType: string; ValueData: "mscoree.dll"
Root: HKCU32; Subkey: "Software\Classes\CLSID\{#PaneClsid}\InprocServer32"; ValueType: string; ValueName: "ThreadingModel"; ValueData: "Both"
Root: HKCU32; Subkey: "Software\Classes\CLSID\{#PaneClsid}\InprocServer32"; ValueType: string; ValueName: "Class"; ValueData: "Matnyaab.Word.Com.PaneControl"
Root: HKCU32; Subkey: "Software\Classes\CLSID\{#PaneClsid}\InprocServer32"; ValueType: string; ValueName: "Assembly"; ValueData: "{#AsmName}"
Root: HKCU32; Subkey: "Software\Classes\CLSID\{#PaneClsid}\InprocServer32"; ValueType: string; ValueName: "RuntimeVersion"; ValueData: "v4.0.30319"
Root: HKCU32; Subkey: "Software\Classes\CLSID\{#PaneClsid}\InprocServer32"; ValueType: string; ValueName: "CodeBase"; ValueData: "{code:CodeBase}"

; ---- معرفی افزونه به ورد (همهٔ نسخه‌های ۲۰۱۰ تا ۳۶۵)
Root: HKCU; Subkey: "Software\Microsoft\Office\Word\Addins\Matnyaab.Word.Connect"; ValueType: string; ValueName: "FriendlyName"; ValueData: "متن‌یاب"; Flags: uninsdeletekey
Root: HKCU; Subkey: "Software\Microsoft\Office\Word\Addins\Matnyaab.Word.Connect"; ValueType: string; ValueName: "Description"; ValueData: "متن‌یاب برای ورد: جست‌وجو، استناد، قالب و ابزار نگارش"
Root: HKCU; Subkey: "Software\Microsoft\Office\Word\Addins\Matnyaab.Word.Connect"; ValueType: dword; ValueName: "LoadBehavior"; ValueData: "3"
Root: HKCU; Subkey: "Software\Microsoft\Office\Word\Addins\Matnyaab.Word.Connect"; ValueType: dword; ValueName: "CommandLineSafe"; ValueData: "0"
Root: HKCU; Subkey: "Software\Microsoft\Office\16.0\Word\Resiliency\DoNotDisableAddinList"; ValueType: dword; ValueName: "Matnyaab.Word.Connect"; ValueData: "1"; Flags: uninsdeletevalue
Root: HKCU; Subkey: "Software\Microsoft\Office\15.0\Word\Resiliency\DoNotDisableAddinList"; ValueType: dword; ValueName: "Matnyaab.Word.Connect"; ValueData: "1"; Flags: uninsdeletevalue
Root: HKCU; Subkey: "Software\Matnyaab"; ValueType: string; ValueName: "WordAddinVersion"; ValueData: "{#AddinVersion}"; Flags: uninsdeletevalue

[UninstallDelete]
Type: filesandordirs; Name: "{app}"

[Code]
const
  NetRelease472 = 461808;

function IsAscii(const S: String): Boolean;
var I: Integer;
begin
  Result := True;
  for I := 1 to Length(S) do
    if Ord(S[I]) > 127 then begin Result := False; Exit; end;
end;

// مسیر DLL به صورت file:/// ؛ اگر مسیر نویسهٔ غیرلاتین دارد، مسیر کوتاه 8.3 به کار می‌رود
function CodeBase(Param: String): String;
var P, S: String;
begin
  P := ExpandConstant('{app}\{#AddinVersion}\MatnyaabWord.dll');
  if not IsAscii(P) then
  begin
    S := GetShortName(P);
    if (S <> '') and IsAscii(S) then P := S;
  end;
  StringChangeEx(P, '\', '/', True);
  Result := 'file:///' + P;
end;

function NetOk(): Boolean;
var R: Cardinal;
begin
  Result := RegQueryDWordValue(HKLM, 'SOFTWARE\Microsoft\NET Framework Setup\NDP\v4\Full', 'Release', R) and (R >= NetRelease472);
end;

function InitializeSetup(): Boolean;
begin
  Result := True;
  if not NetOk() then
  begin
    SuppressibleMsgBox('برای این افزونه «.NET Framework 4.7.2» یا تازه‌تر لازم است (در ویندوز ۱۰ و ۱۱ معمولاً از پیش نصب است).' + #13#10 +
      'لطفاً آن را از سایت مایکروسافت نصب کنید و دوباره این نصب‌کننده را اجرا کنید.', mbError, MB_OK, IDOK);
    Result := False;
  end;
end;

// خارج‌کردن افزونه از «Disabled Items» ورد (اگر پیش‌تر غیرفعال شده باشد)
procedure CleanDisabledItems(const Ver: String);
var
  Key: String;
  Names: TArrayOfString;
  I, J: Integer;
  Data: AnsiString;
  Plain: String;
begin
  Key := 'Software\Microsoft\Office\' + Ver + '\Word\Resiliency\DisabledItems';
  if not RegGetValueNames(HKCU, Key, Names) then Exit;
  for I := 0 to GetArrayLength(Names) - 1 do
    if RegQueryBinaryValue(HKCU, Key, Names[I], Data) then
    begin
      Plain := '';
      for J := 1 to Length(Data) do
        if Ord(Data[J]) <> 0 then Plain := Plain + Lowercase(Chr(Ord(Data[J])));
      if Pos('matnyaab', Plain) > 0 then RegDeleteValue(HKCU, Key, Names[I]);
    end;
end;

// پاک‌کردن پوشهٔ نسخه‌های قدیمی (اگر ورد هنوز آن‌ها را باز نگه داشته، بی‌صدا رد می‌شود)
procedure CleanOldVersions();
var
  FR: TFindRec;
  Base: String;
begin
  Base := ExpandConstant('{app}');
  if FindFirst(Base + '\*', FR) then
  try
    repeat
      if (FR.Attributes and FILE_ATTRIBUTE_DIRECTORY <> 0) and (FR.Name <> '.') and (FR.Name <> '..') and (FR.Name <> '{#AddinVersion}') then
        DelTree(Base + '\' + FR.Name, True, True, True);
    until not FindNext(FR);
  finally
    FindClose(FR);
  end;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    CleanDisabledItems('16.0');
    CleanDisabledItems('15.0');
    CleanDisabledItems('14.0');
    // اگر ورد قبلاً افزونه را «نرم» غیرفعال کرده بود، LoadBehavior=3 دوباره فعالش می‌کند
    CleanOldVersions();
  end;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  if (CurPageID = wpFinished) and (FindWindowByClassName('OpusApp') <> 0) and not WizardSilent() then
    MsgBox('ورد هم‌اکنون باز است؛ یک بار آن را ببندید و دوباره باز کنید تا زبانهٔ «متن‌یاب» ظاهر شود.', mbInformation, MB_OK);
end;
