#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#define _WIN32_WINNT 0x0600
#include <windows.h>
#include <shlobj.h>
#include <wchar.h>
#include <stdlib.h>

static void fail(const wchar_t *message) {
    MessageBoxW(NULL, message, L"PVF Studio", MB_OK | MB_ICONERROR);
    ExitProcess(1);
}

static wchar_t *append(const wchar_t *base, const wchar_t *suffix) {
    size_t length = wcslen(base) + wcslen(suffix) + 1;
    wchar_t *result = calloc(length, sizeof(wchar_t));
    if (!result) fail(L"无法分配启动所需的内存。");
    wcscpy(result, base);
    wcscat(result, suffix);
    return result;
}

static void extend(wchar_t **value, const wchar_t *suffix) {
    wchar_t *next = append(*value, suffix);
    free(*value);
    *value = next;
}

static BOOL exists(const wchar_t *path) {
    WIN32_FIND_DATAW data;
    HANDLE match = FindFirstFileW(path, &data);
    if (match == INVALID_HANDLE_VALUE) return FALSE;
    FindClose(match);
    return !(data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY);
}

static void require_file(const wchar_t *root, const wchar_t *relative) {
    wchar_t *path = append(root, relative);
    BOOL found = exists(path);
    free(path);
    if (!found) {
        fail(L"PVF Studio 的运行文件不完整。\n\n"
             L"请将 PVFStudio.exe 和 app 文件夹放在同一目录，"
             L"并重新解压完整发行包。");
    }
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR arguments, int show) {
    (void)instance;
    (void)previous;
    (void)show;

    wchar_t root[32768];
    DWORD size = GetModuleFileNameW(NULL, root, ARRAYSIZE(root));
    if (!size || size >= ARRAYSIZE(root)) fail(L"无法确定 PVF Studio 的安装路径。");
    wchar_t *separator = wcsrchr(root, L'\\');
    if (!separator) fail(L"无法确定 PVF Studio 的安装目录。");
    *separator = L'\0';

    require_file(root, L"\\app\\jbr\\bin\\javaw.exe");
    require_file(root, L"\\app\\jbr\\bin\\server\\jvm.dll");
    require_file(root, L"\\app\\bin\\pvf-studio.vmoptions");
    require_file(root, L"\\app\\lib\\pvf-studio.jar");
    require_file(root, L"\\app\\lib\\platform-loader.jar");
    require_file(root, L"\\app\\lib\\nio-fs.jar");
    require_file(root, L"\\app\\modules\\module-descriptors.dat");

    PWSTR profile = NULL;
    if (FAILED(SHGetKnownFolderPath(&FOLDERID_Profile, 0, NULL, &profile))) {
        fail(L"无法读取当前用户目录。");
    }
    wchar_t *config = append(profile, L"\\.pvf-studio\\platform\\product\\config");
    CoTaskMemFree(profile);
    int created = SHCreateDirectoryExW(NULL, config, NULL);
    free(config);
    if (created != ERROR_SUCCESS && created != ERROR_ALREADY_EXISTS) {
        fail(L"无法创建 PVF Studio 的用户配置目录。");
    }

    wchar_t *launcher = append(root, L"\\app\\jbr\\bin\\javaw.exe");

    // Preserve the caller's working directory so relative PVF paths still work.
    // Forward the original Unicode argument tail directly; never invoke a shell.
    wchar_t *command = append(L"\"", launcher);
    extend(&command, L"\" @\"");
    extend(&command, root);
    extend(&command, L"\\app\\bin\\pvf-studio.vmoptions\" \"-Xbootclasspath/a:");
    extend(&command, root);
    extend(&command, L"\\app\\lib\\nio-fs.jar\" \"-Dpvf.studio.home=");
    extend(&command, root);
    extend(&command, L"\\app\" -cp \"");
    extend(&command, root);
    extend(&command, L"\\app\\lib\\platform-loader.jar;");
    extend(&command, root);
    extend(&command, L"\\app\\lib\\pvf-studio.jar\" studio.pvf.product.PvfStudioMain ");
    extend(&command, arguments);
    if (wcslen(command) >= 32767) fail(L"启动参数过长，请缩短 PVF 文件路径。");
    STARTUPINFOW startup = {0};
    startup.cb = sizeof(startup);
    PROCESS_INFORMATION process = {0};
    if (!CreateProcessW(launcher, command, NULL, NULL, FALSE, 0, NULL, NULL, &startup, &process)) {
        fail(L"无法启动 PVF Studio。请检查完整发行包是否已正确解压。");
    }
    CloseHandle(process.hThread);
    free(command);
    free(launcher);

    WaitForSingleObject(process.hProcess, INFINITE);
    DWORD exit_code = 1;
    GetExitCodeProcess(process.hProcess, &exit_code);
    CloseHandle(process.hProcess);
    return (int)exit_code;
}
