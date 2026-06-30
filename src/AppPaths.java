import java.io.File;
import java.net.URISyntaxException;

public class AppPaths {
    private static final File BASE_DIR = resolveBaseDir();

    public static File baseDir() {
        return BASE_DIR;
    }

    public static File resultsDir() {
        return ensureDir(new File(BASE_DIR, "results"));
    }

    public static File screenshotsDir() {
        return ensureDir(new File(BASE_DIR, "screenshots"));
    }

    private static File resolveBaseDir() {
        File codeDir = getCodeDir();
        if (codeDir != null) {
            File normalized = normalizeCodeDir(codeDir);
            if (isWritableDirectory(normalized)) {
                return normalized;
            }
        }

        File userDir = new File(System.getProperty("user.dir", ".")).getAbsoluteFile();
        if (isWritableDirectory(userDir)) {
            return userDir;
        }

        File documentsDir = new File(System.getProperty("user.home"), "Documents");
        return ensureDir(new File(documentsDir, "MCScanner"));
    }

    private static File getCodeDir() {
        try {
            File location = new File(AppPaths.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getAbsoluteFile();
            return location.isFile() ? location.getParentFile() : location;
        } catch (URISyntaxException | SecurityException e) {
            return null;
        }
    }

    private static File normalizeCodeDir(File codeDir) {
        File dir = codeDir.getAbsoluteFile();
        if ("classes".equalsIgnoreCase(dir.getName())) {
            File parent = dir.getParentFile();
            if (parent != null && "build".equalsIgnoreCase(parent.getName())) {
                File projectDir = parent.getParentFile();
                if (projectDir != null) {
                    return projectDir;
                }
            }
        }
        return dir;
    }

    private static boolean isWritableDirectory(File dir) {
        return dir != null && dir.isDirectory() && dir.canWrite();
    }

    private static File ensureDir(File dir) {
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        return dir;
    }
}
