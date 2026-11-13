#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <wchar.h>

static int fail(const char *message) {
    fprintf(stderr, "PVFStudioCLI: %s\n", message);
    return 3;
}

static const wchar_t *argument_tail(void) {
    const wchar_t *cursor = GetCommandLineW();
    if (*cursor == L'"') {
        cursor++;
        while (*cursor && *cursor != L'"') cursor++;
        if (*cursor == L'"') cursor++;
    } else {
        while (*cursor && *cursor != L' ' && *cursor != L'\t') cursor++;
    }
    return cursor;
}

int wmain(void) {
    wchar_t root[32768];
    DWORD size = GetModuleFileNameW(NULL, root, ARRAYSIZE(root));
    if (!size || size >= ARRAYSIZE(root)) return fail("Cannot locate the installation directory.");
    wchar_t *separator = wcsrchr(root, L'\\');
    if (!separator) return fail("Cannot locate the installation directory.");
    *separator = L'\0';

    wchar_t java[32768];
    if (swprintf(java, ARRAYSIZE(java), L"%ls\\app\\jbr\\bin\\java.exe", root) < 0
            || GetFileAttributesW(java) == INVALID_FILE_ATTRIBUTES) {
        return fail("Bundled Java is missing. Keep the launcher and app directory together.");
    }
    const wchar_t *args = argument_tail();
    const wchar_t *format = L"\"%ls\" -Djava.awt.headless=true -cp \"%ls\\app\\lib\\pvf-studio.jar\" studio.pvf.cli.Main%ls";
    wchar_t *command = calloc(32767, sizeof(wchar_t));
    if (!command) return fail("Cannot allocate the command line.");
    if (swprintf(command, 32767, format, java, root, args) < 0) {
        free(command);
        return fail("Command line is too long. Use stage-text with a UTF-8 file for long edits.");
    }

    STARTUPINFOW startup = {0};
    startup.cb = sizeof(startup);
    startup.dwFlags = STARTF_USESTDHANDLES;
    startup.hStdInput = GetStdHandle(STD_INPUT_HANDLE);
    startup.hStdOutput = GetStdHandle(STD_OUTPUT_HANDLE);
    startup.hStdError = GetStdHandle(STD_ERROR_HANDLE);
    PROCESS_INFORMATION process = {0};
    BOOL launched = CreateProcessW(java, command, NULL, NULL, TRUE, 0, NULL, NULL, &startup, &process);
    free(command);
    if (!launched) return fail("Could not start bundled Java.");
    CloseHandle(process.hThread);
    DWORD exit_code = 3;
    if (WaitForSingleObject(process.hProcess, INFINITE) == WAIT_OBJECT_0)
        GetExitCodeProcess(process.hProcess, &exit_code);
    CloseHandle(process.hProcess);
    return (int)exit_code;
}
