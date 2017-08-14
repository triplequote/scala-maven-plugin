package scala_maven;

/**
 * Coordinates of the Scala compiler.
 */
public interface CompilerInstance {
    String getGroupId();

    String getArtifactId() throws Exception;

    String getVersion() throws Exception;

    String getMainClass();

    String getCompilerBridgeGroupId();

    String getCompilerBridgeArtifactId(String scalaVersion);

    String getCompilerBridgeVersion(String zincVersion);
}
