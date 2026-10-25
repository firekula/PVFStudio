; Build through tools/build-release.ps1 with Inno Setup 6.3 or later.
#ifndef AppVersion
  #error AppVersion is required
#endif
#ifndef SourceDir
  #error SourceDir is required
#endif
#ifndef ReleaseDir
  #error ReleaseDir is required
#endif

[Setup]
AppId={{86787856-E98D-4673-9887-25C9974AE7CB}
AppName=PVF Studio
AppVersion={#AppVersion}
AppVerName=PVF Studio {#AppVersion}
VersionInfoVersion={#AppVersion}
DefaultDirName={localappdata}\Programs\PVFStudio
DefaultGroupName=PVF Studio
AllowNoIcons=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
OutputDir={#ReleaseDir}
OutputBaseFilename=PVFStudio-{#AppVersion}-windows-x64-setup
Compression=lzma2/normal
SolidCompression=yes
LZMANumBlockThreads=4
DiskSpanning=no
WizardStyle=modern
SetupIconFile=launcher\PVFStudio.ico
DisableProgramGroupPage=yes
DisableDirPage=no
UninstallDisplayName=PVF Studio
UninstallDisplayIcon={app}\PVFStudio.exe
LicenseFile={#SourceDir}\app\LICENSE
CloseApplications=no
RestartApplications=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; Product modules, vanilla JBR, and all required third-party licenses and notices.
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[InstallDelete]
; Versioned plugin JARs must not accumulate and shadow a newer release on upgrade.
Type: files; Name: "{app}\app\lib\pvf-studio-*.jar"
; Remove only the old batch entry point when upgrading an earlier package.
Type: files; Name: "{app}\PVFStudio.bat"

[Icons]
Name: "{group}\PVF Studio"; Filename: "{app}\PVFStudio.exe"; WorkingDir: "{app}"
Name: "{autodesktop}\PVF Studio"; Filename: "{app}\PVFStudio.exe"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\PVFStudio.exe"; WorkingDir: "{app}"; Description: "{cm:LaunchProgram,PVF Studio}"; Flags: postinstall skipifsilent nowait

; User drafts and platform settings live under ~/.pvf-studio and are not removed.

[Code]
function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  Result := '';
  if (FileExists(ExpandConstant('{app}\app\pvf-studio.properties')) and
      FileExists(ExpandConstant('{app}\app\runtime\bin\idea64.exe'))) or
     (FileExists(ExpandConstant('{app}\pvf-studio.properties')) and
      FileExists(ExpandConstant('{app}\runtime\bin\idea64.exe'))) then
    Result := 'This directory contains the old full-IDE prototype. Uninstall it first or select a new installation directory. Existing drafts and backups are kept.';
end;
