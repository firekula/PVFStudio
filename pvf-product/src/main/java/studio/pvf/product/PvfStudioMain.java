package studio.pvf.product;

import com.intellij.platform.runtime.loader.IntellijLoader;
import java.nio.file.Path;

/** Product entry point. PVF and platform modules share the product's core class loader. */
public final class PvfStudioMain {
    private PvfStudioMain() {}
    public static void main(String[] arguments) throws Throwable {
        Path home = Path.of(System.getProperty("pvf.studio.home")).toAbsolutePath().normalize();
        Path user = Path.of(System.getProperty("user.home"), ".pvf-studio", "platform", "product");
        System.setProperty("idea.home.path", home.toString());
        System.setProperty("idea.platform.prefix", "PvfStudio");
        System.setProperty("idea.vendor.name", "PVF Studio");
        System.setProperty("idea.paths.selector", "PVFStudio2025.2");
        System.setProperty("intellij.platform.load.app.info.from.resources", "true");
        setDefault("idea.config.path", user.resolve("config").toString());
        setDefault("idea.system.path", user.resolve("system").toString());
        setDefault("idea.log.path", user.resolve("log").toString());
        setDefault("idea.plugins.path", user.resolve("product-plugins").toString());
        System.setProperty("intellij.platform.runtime.repository.path", home.resolve("modules/module-descriptors.dat").toString());
        System.setProperty("intellij.platform.root.module", "studio.pvf.product");
        System.setProperty("intellij.platform.product.mode", "monolith");
        System.setProperty("idea.force.use.core.classloader", "true");
        System.setProperty("ide.browser.jcef.enabled", "false");
        System.setProperty("jna.boot.library.path", home.resolve("lib/jna/amd64").toString());
        System.setProperty("pty4j.preferred.native.folder", home.resolve("lib/pty4j").toString());
        boolean smoke = arguments.length > 0 && arguments[0].equals("--smoke");
        boolean check = arguments.length > 0 && arguments[0].equals("--check");
        boolean customCommand = smoke || check;
        String[] command = new String[arguments.length + (customCommand ? 0 : 1)];
        command[0] = smoke ? "pvf-studio-smoke" : check ? "pvf-product-check" : "pvf-studio";
        System.arraycopy(arguments, customCommand ? 1 : 0, command, 1, arguments.length - (customCommand ? 1 : 0));
        IntellijLoader.main(command);
    }
    private static void setDefault(String key, String value) {
        if (System.getProperty(key) == null) System.setProperty(key, value);
    }
}
