package studio.pvf.cli;

import java.io.IOException;

final class CliFailure extends IOException {
    private final String code;
    private final String hint;

    CliFailure(String code, String message, String hint) {
        super(message);
        this.code = code;
        this.hint = hint;
    }

    String code() { return code; }
    String hint() { return hint; }
}
