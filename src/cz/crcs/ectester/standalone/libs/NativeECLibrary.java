package cz.crcs.ectester.standalone.libs;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Provider;

/**
 * @author Jan Jancar johny@neuromancer.sk
 */
public abstract class NativeECLibrary extends ProviderECLibrary {
    private String resource;
    private String libname;

    public NativeECLibrary(String resource, String libname) {
        this.resource = resource;
        this.libname = libname;
    }

    @Override
    public boolean initialize() {
        try {
            String suffix;
            Path appData;
            if (System.getProperty("os.name").startsWith("Windows")) {
                suffix = "dll";
                appData = Paths.get(System.getenv("AppData"));
            } else {
                suffix = "so";
                if (System.getProperty("os.name").startsWith("Linux")) {
                    appData = Paths.get(System.getenv("XDG_DATA_HOME"));
                    if (appData == null) {
                        appData = Paths.get(System.getProperty("user.home"), ".local", "share");
                    }
                } else {
                    appData = Paths.get(System.getProperty("user.home"), ".local", "share");
                }
            }
            Path libDir = appData.resolve("ECTesterStandalone");
            File libDirFile = libDir.toFile();
            Path libPath = libDir.resolve(libname + "." + suffix);
            File libFile = libPath.toFile();

            URL jarURL = NativeECLibrary.class.getResource("/cz/crcs/ectester/standalone/libs/" + resource + "." + suffix);
            if (jarURL == null) {
                return false;
            }
            URLConnection jarConnection = jarURL.openConnection();

            boolean write = false;
            if (libDirFile.isDirectory() && libFile.isFile()) {
                long jarModified = jarConnection.getLastModified();

                long libModified = Files.getLastModifiedTime(libPath).toMillis();
                if (jarModified > libModified) {
                    write = true;
                }
            } else {
                libDir.toFile().mkdirs();
                libFile.createNewFile();
                write = true;
            }

            if (write) {
                Files.copy(jarConnection.getInputStream(), libPath, StandardCopyOption.REPLACE_EXISTING);
            }
            jarConnection.getInputStream().close();

            System.load(libPath.toString());

            provider = createProvider();
            return super.initialize();
        } catch (IOException ignored) {

        }
        return false;
    }

    abstract Provider createProvider();
}
