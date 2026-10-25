package studio.pvf.product;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.platform.settings.SettingsController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Non-graphical product wiring check, run before graphical integration tests. */
public final class PvfProductCheckStarter implements ApplicationStarter {
    @Override public boolean isHeadless() { return true; }
    @Override public void main(List<String> arguments) {
        try {
            if (!"PVS".equals(ApplicationInfo.getInstance().getBuild().getProductCode()))
                throw new AssertionError("An upstream IDEA product descriptor was loaded");
            var icon = com.intellij.ui.AppUIUtil.loadApplicationIcon(com.intellij.ui.scale.ScaleContext.create(), 32);
            if (icon == null || icon.getIconWidth() <= 0) throw new AssertionError("Product application icon is missing");
            var iconImage = new java.awt.image.BufferedImage(icon.getIconWidth(), icon.getIconHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var iconGraphics = iconImage.createGraphics();
            try { icon.paintIcon(null, iconGraphics, 0, 0); } finally { iconGraphics.dispose(); }
            if ((iconImage.getRGB(iconImage.getWidth() / 2, iconImage.getHeight() / 2) >>> 24) < 200)
                throw new AssertionError("Product application icon rendered blank");
            if (ApplicationManager.getApplication().getService(SettingsController.class) == null)
                throw new AssertionError("Product settings service is missing");
            ManagingFS.getInstance();
            if (PvfStudioMain.class.getClassLoader() != ApplicationManager.class.getClassLoader())
                throw new AssertionError("PVF is not part of the product core class loader");
            List<String> plugins = PluginManagerCore.getLoadedPlugins().stream().map(p -> p.getPluginId().getIdString()).toList();
            if (!plugins.equals(List.of("com.intellij"))) throw new AssertionError("Unexpected plugins: " + plugins);
            if (arguments.size() == 2) Files.writeString(Path.of(arguments.get(1)), "productCode=PVS\ncoreClassLoader=PASS\nloadedPlugins=" + plugins + "\nstatus=PASS\n");
            System.out.println("PVF_PRODUCT_CHECK_OK core class loader, product identity, settings, and zero bundled plugins");
            ApplicationManager.getApplication().invokeLater(() ->
                ((ApplicationEx)ApplicationManager.getApplication()).exit(ApplicationEx.EXIT_CONFIRMED));
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
