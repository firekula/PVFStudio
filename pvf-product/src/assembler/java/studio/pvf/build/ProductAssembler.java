package studio.pvf.build;

import com.intellij.platform.runtime.repository.serialization.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Selects an upstream platform dependency closure and creates an independent product repository. */
public final class ProductAssembler {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("--generate-resources")) {
            generateResources(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        Path sdk = Path.of(args[0]).toAbsolutePath().normalize();
        Path jbr = Path.of(args[1]).toAbsolutePath().normalize();
        Path jar = Path.of(args[2]).toAbsolutePath().normalize();
        Path output = Path.of(args[3]).toAbsolutePath().normalize();
        String version = args[4];
        String sdkInfo = Files.readString(sdk.resolve("product-info.json"));
        if (!sdkInfo.matches("(?s).*\"productCode\"\\s*:\\s*\"IC\".*")
            || !sdkInfo.matches("(?s).*\"buildNumber\"\\s*:\\s*\"252\\.28539\\.97\".*")) {
            throw new IOException("Use the pinned open-source Community SDK, not an Ultimate distribution.");
        }
        if (!Files.isRegularFile(jbr.resolve("bin/javaw.exe")) || Files.readString(jbr.resolve("release")).contains("jcef")) {
            throw new IOException("Product packaging requires the pinned vanilla JBR without JCEF: " + jbr);
        }
        Files.createDirectories(output);
        Path app = output.resolve("app");
        if (Files.exists(app.resolve("runtime")) || Files.exists(app.resolve("plugins"))) {
            throw new IOException("Use a fresh product directory; an old full-IDE distribution exists here.");
        }
        Files.createDirectories(app.resolve("modules"));
        Files.createDirectories(app.resolve("bin"));

        var upstream = RuntimeModuleRepositorySerialization.loadFromCompactFile(sdk.resolve("modules/module-descriptors.dat"));
        List<String> roots = new ArrayList<>(Files.readAllLines(Path.of(args[5])).stream().map(String::strip)
            .filter(s -> !s.isEmpty() && !s.startsWith("#")).toList());
        // Product service registrations have module dependencies in addition to Java imports.
        try (var resources = new java.util.zip.ZipFile(sdk.resolve("lib/app-client.jar").toFile())) {
            var entry = resources.getEntry("META-INF/essential-modules.xml");
            String xml = new String(resources.getInputStream(entry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            var modules = Pattern.compile("<module\\s+name=\"([^\"]+)\"").matcher(xml);
            while (modules.find()) roots.add(modules.group(1));
        }
        var selected = new LinkedHashMap<String, RawRuntimeModuleDescriptor>();
        var pending = new ArrayDeque<>(roots);
        var resourcePaths = new TreeSet<String>();
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (selected.containsKey(id)) continue;
            var module = upstream.findDescriptor(id);
            if (module == null) throw new IOException("Unresolved platform module: " + id);
            selected.put(id, module);
            pending.addAll(module.getDependencies());
            resourcePaths.addAll(module.getResourcePaths());
        }
        var relocated = new HashMap<String, String>();
        for (String relative : resourcePaths) {
            Path original = sdk.resolve("modules").resolve(relative).normalize();
            if (!original.startsWith(sdk) || !Files.isRegularFile(original)) {
                throw new IOException("Invalid resource root for selected module: " + original);
            }
            Path location = sdk.relativize(original);
            if (location.startsWith("plugins")) {
                // The SDK sometimes stores shared third-party libraries inside a plugin's lib directory.
                // Relocate those libraries; never package the plugin descriptor or plugin implementation.
                try (var library = new java.util.zip.ZipFile(original.toFile())) {
                    if (library.getEntry("META-INF/plugin.xml") != null)
                        throw new IOException("Product unexpectedly requires a bundled plugin: " + location);
                }
                String unique = Integer.toUnsignedString(relative.hashCode(), 16) + "-" + original.getFileName();
                location = Path.of("lib/third-party", unique);
                relocated.put(relative, "../" + location.toString().replace('\\', '/'));
            }
            copy(original, app.resolve(location));
        }
        selected.replaceAll((id, module) -> RawRuntimeModuleDescriptor.create(id,
            module.getResourcePaths().stream().map(p -> relocated.getOrDefault(p, p)).toList(), module.getDependencies()));
        selected.put("studio.pvf.product", RawRuntimeModuleDescriptor.create("studio.pvf.product", List.of("../lib/pvf-studio.jar"), roots));
        RuntimeModuleRepositorySerialization.saveToCompactFile(selected.values(), "intellij.platform.bootstrap",
            app.resolve("modules/module-descriptors.dat"), 1);
        RuntimeModuleRepositorySerialization.saveToJar(selected.values(), "intellij.platform.bootstrap",
            app.resolve("modules/module-descriptors.jar"), 1);
        copy(jar, app.resolve("lib/pvf-studio.jar"));
        copy(Path.of(args[6]), output.resolve("PVFStudio.exe"));
        copy(Path.of(args[7]), app.resolve("LICENSE"));
        copy(sdk.resolve("LICENSE.txt"), app.resolve("licenses/INTELLIJ-LICENSE.txt"));
        copy(sdk.resolve("NOTICE.txt"), app.resolve("licenses/INTELLIJ-NOTICE.txt"));
        copyTree(sdk.resolve("license"), app.resolve("licenses/platform"));
        copyTree(jbr, app.resolve("jbr"));
        try (var nativeFiles = Files.walk(sdk.resolve("lib"))) {
            for (Path path : nativeFiles.filter(Files::isRegularFile).toList()) {
                String relative = sdk.relativize(path).toString().replace('\\', '/');
                if ((relative.endsWith(".dll") || relative.endsWith(".exe")) && !relative.contains("aarch64")
                    && !relative.contains("arm64") && !relative.contains(".x86.")) copy(path, app.resolve(sdk.relativize(path)));
            }
        }
        for (String file : List.of("fsnotifier.exe", "restarter.exe", "winShellIntegration.exe")) {
            Path original = sdk.resolve("bin").resolve(file);
            if (Files.isRegularFile(original)) copy(original, app.resolve("bin").resolve(file));
        }
        String metadata = Files.readString(sdk.resolve("product-info.json"));
        var options = new LinkedHashSet<String>();
        options.addAll(List.of("-Xms128m", "-Xmx2048m", "-XX:ReservedCodeCacheSize=512m",
            "-Didea.platform.prefix=PvfStudio", "-Didea.vendor.name=PVFStudio", "-Didea.paths.selector=PVFStudio2025.2",
            "-Didea.force.use.core.classloader=true", "-Dintellij.platform.root.module=studio.pvf.product",
            "-Dintellij.platform.load.app.info.from.resources=true",
            "-Dintellij.platform.product.mode=monolith",
            "-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader",
            "-Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider",
            "-Dnosplash=true", "-Djna.nosys=true"));
        var matcher = Pattern.compile("\"(--add-opens=[^\"]+)\"").matcher(metadata);
        while (matcher.find()) options.add(matcher.group(1));
        Files.write(app.resolve("bin/pvf-studio.vmoptions"), options);
        Files.writeString(app.resolve("bin/idea.properties"), "# PVF Studio product defaults\nidea.is.internal=false\n");
        Files.writeString(app.resolve("build.txt"), "PVS-252.28539.97\n");
        Files.writeString(app.resolve("product-info.json"), "{\n  \"name\": \"PVF Studio\",\n  \"version\": \"" + version
            + "\",\n  \"productCode\": \"PVS\",\n  \"buildNumber\": \"252.28539.97\",\n"
            + "  \"mainModule\": \"studio.pvf.product\",\n  \"mainClass\": \"studio.pvf.product.PvfStudioMain\",\n"
            + "  \"platformModules\": " + (selected.size() - 1) + ",\n  \"bundledPlugins\": []\n}\n");
        Path report = jar.getParent().getParent().resolve("reports/platform-composition.txt");
        Files.createDirectories(report.getParent());
        Files.write(report, selected.keySet());
        Files.writeString(jar.getParent().getParent().resolve("product-location.txt"), output.toString());
        System.out.println("PVF Studio " + version + ": " + selected.size() + " modules, " + resourcePaths.size()
            + " platform JARs, no bundled plugins; output=" + output);
    }
    private static void copy(Path source, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        if (Files.exists(destination) && Files.size(source) == Files.size(destination) && Files.mismatch(source, destination) == -1) return;
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }
    private static void copyTree(Path source, Path destination) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) copy(file, destination.resolve(source.relativize(file)));
        }
    }
    private static void generateResources(Path sdk, Path output) throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var reader = factory.newDocumentBuilder();
        org.w3c.dom.Document product;
        org.w3c.dom.Document essential;
        try (var jar = new java.util.zip.ZipFile(sdk.resolve("lib/app.jar").toFile())) {
            product = reader.parse(jar.getInputStream(jar.getEntry("META-INF/IdeaPlugin.xml")));
        }
        try (var jar = new java.util.zip.ZipFile(sdk.resolve("lib/app-client.jar").toFile())) {
            essential = reader.parse(jar.getInputStream(jar.getEntry("META-INF/essential-modules.xml")));
        }
        Map<String, String> embedded = new HashMap<>();
        var modules = product.getElementsByTagName("module");
        for (int i = 0; i < modules.getLength(); i++) {
            var module = (org.w3c.dom.Element)modules.item(i);
            if (module.getParentNode().getNodeName().equals("content"))
                embedded.put(module.getAttribute("name"), module.getTextContent().strip());
        }
        modules = essential.getElementsByTagName("module");
        for (int i = 0; i < modules.getLength(); i++) {
            var module = (org.w3c.dom.Element)modules.item(i);
            String descriptor = embedded.get(module.getAttribute("name"));
            if (descriptor == null || descriptor.isBlank())
                throw new IOException("Upstream product has no embedded descriptor for " + module.getAttribute("name"));
            module.appendChild(essential.createCDATASection(descriptor));
        }
        Path file = output.resolve("META-INF/PvfStudioEssential.xml");
        Files.createDirectories(file.getParent());
        var transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
        transformer.transform(new javax.xml.transform.dom.DOMSource(essential), new javax.xml.transform.stream.StreamResult(file.toFile()));
    }
}
