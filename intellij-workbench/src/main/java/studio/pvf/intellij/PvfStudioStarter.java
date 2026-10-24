package studio.pvf.intellij;

import com.intellij.openapi.application.ApplicationStarter;
import java.util.List;

/** Starts our own workbench window after IntelliJ has initialized its editor services. */
public final class PvfStudioStarter implements ApplicationStarter {
    @Override public boolean isHeadless() { return false; }

    @Override public void main(List<String> args) {
        String archive = args.size() > 1 ? args.get(1) : null;
        PvfStudioFrame frame = new PvfStudioFrame();
        frame.setVisible(true);
        if (archive != null) frame.openArchive(java.nio.file.Path.of(archive));
    }
}
