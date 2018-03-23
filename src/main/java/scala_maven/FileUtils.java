package scala_maven;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class FileUtils extends org.codehaus.plexus.util.FileUtils {
  
  /**
   * @param canonical Should use CanonicalPath to normalize path (true => getCanonicalPath, false => getAbsolutePath)
   * @see https://github.com/davidB/maven-scala-plugin/issues/50
   */
  public static String pathOf(File f, boolean canonical) throws IOException {
    return canonical? f.getCanonicalPath() : f.getAbsolutePath();
  }

  /**
   * @param canonical Should use CanonicalPath to normalize path (true => getCanonicalPath, false => getAbsolutePath)
   * @see https://github.com/davidB/maven-scala-plugin/issues/50
   */
  public static File fileOf(File f, boolean canonical) throws IOException {
    return canonical? f.getCanonicalFile() : f.getAbsoluteFile();
  }

  public static List<String> filesOf(List<File> files, boolean canonical) throws IOException {
    List<String> result = new ArrayList<String>(files.size());
    for (File f : files) {
      result.add(fileOf(f, canonical).toString());
    }
    return result;
  }
}
