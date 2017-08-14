package scala_maven;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class FileUtils extends org.codehaus.plexus.util.FileUtils {

    /**
     * @param canonical
     *            Should use CanonicalPath to normalize path (true =>
     *            getCanonicalPath, false =&gt; getAbsolutePath)
     * @see <a href="https://github.com/davidB/maven-scala-plugin/issues/50">#50</a>
     */
    static String pathOf(File f, boolean canonical) throws Exception {
        return canonical ? f.getCanonicalPath() : f.getAbsolutePath();
    }

    /**
     * @param canonical
     *            Should use CanonicalPath to normalize path (true =>
     *            getCanonicalPath, false =&gt; getAbsolutePath)
     * @see <a href="https://github.com/davidB/maven-scala-plugin/issues/50">#50</a>
     */
    static File fileOf(File f, boolean canonical) throws Exception {
        return canonical ? f.getCanonicalFile() : f.getAbsoluteFile();
    }

    public static List<String> filesOf(List<File> files, boolean canonical) throws Exception {
        List<String> result = new ArrayList<String>(files.size());
        for (File f : files) {
            result.add(fileOf(f, canonical).toString());
        }
        return result;
    }
}
