package sbt_inc;

import org.apache.maven.artifact.Artifact;
import sbt.io.AllPassFilter$;
import sbt.io.IO;
import sbt.util.Logger;
import scala.Tuple2;
import scala.collection.JavaConverters;
import scala.compat.java8.functionConverterImpls.*;

import org.apache.maven.plugin.logging.Log;
import sbt.internal.inc.*;
import sbt.internal.inc.FileAnalysisStore;
import sbt.internal.inc.ScalaInstance;
import sbt.internal.inc.classpath.ClassLoaderCache;
import sbt.internal.inc.classpath.ClasspathUtilities;
import scala.Option;
import scala_maven.CompilerInstance;
import scala_maven.MavenArtifactResolver;
import scala_maven.VersionNumber;
import util.FileUtils;
import xsbti.T2;
import xsbti.compile.*;
import xsbti.compile.AnalysisStore;
import xsbti.compile.CompilerCache;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SbtIncrementalCompiler {

    private static final String SBT_GROUP_ID = "org.scala-sbt";
    private static final String JAVA_CLASS_VERSION = System.getProperty("java.class.version");
    public static final String COMPILER_INTERFACE_CLASSIFIER = "sources";
    private static final File DEFAULT_SECONDARY_CACHE_DIR = Paths
        .get(System.getProperty("user.home"), ".sbt", "1.0", "zinc", "org.scala-sbt").toFile();

    private final IncrementalCompiler compiler = ZincUtil.defaultIncrementalCompiler();
    private final CompileOrder compileOrder;
    private final Logger logger;
    private final Compilers compilers;
    private final Setup setup;
    private final AnalysisStore analysisStore;
    private final MavenArtifactResolver resolver;
    private final File secondaryCacheDir;
    private final CompilerInstance compilerInstance;

    /**
     * Cache class loaders, Scala Instance benefits greatly from reusing the
     * classloader between projects.
     */
    private static ClassLoaderCache classLoaderCache = new ClassLoaderCache(ClassLoader.getSystemClassLoader());

    public SbtIncrementalCompiler(CompilerInstance compilerInstance, File libraryJar, File reflectJar, File compilerJar,
        VersionNumber scalaVersion, List<File> extraJars, MavenArtifactResolver resolver, File secondaryCacheDir,
        Log mavenLogger, File cacheFile, CompileOrder compileOrder) throws Exception, MalformedURLException {
        this.compilerInstance = compilerInstance;
        this.compileOrder = compileOrder;
        this.logger = new SbtLogger(mavenLogger);
        mavenLogger.info("Using incremental compilation using " + compileOrder + " compile order");
        this.resolver = resolver;
        this.secondaryCacheDir = secondaryCacheDir != null ? secondaryCacheDir : DEFAULT_SECONDARY_CACHE_DIR;
        this.secondaryCacheDir.mkdirs();

        List<File> allJars = new ArrayList<>(extraJars);
        allJars.add(libraryJar);
        // allJars.add(reflectJar);
        allJars.add(compilerJar);

        List<URL> allURLS = new ArrayList<>();
        allJars.stream().forEach(file -> {
            try {
                allURLS.add(file.toURI().toURL());
            } catch (MalformedURLException e) {
                mavenLogger.info("Invalid URL in extraJars: " + file.toString());
            }
        });

        ScalaInstance scalaInstance = new ScalaInstance( //
            scalaVersion.toString(), // version
            classLoaderCache
                .apply(scala.collection.JavaConverters.iterableAsScalaIterableConverter(allJars).asScala().toList()), // loader
            ClasspathUtilities.rootLoader(), // loaderLibraryOnly
            libraryJar, // libraryJar
            compilerJar, // compilerJar
            allJars.toArray(new File[] {}), // allJars
            Option.apply(scalaVersion.toString()) // explicitActual
        );

        File compilerBridgeJar = getCompiledBridgeJar(scalaInstance, mavenLogger);

        ScalaCompiler scalaCompiler = new AnalyzingCompiler( //
            scalaInstance, // scalaInstance
            ZincCompilerUtil.constantBridgeProvider(scalaInstance, compilerBridgeJar), // provider
            ClasspathOptionsUtil.auto(), // classpathOptions
            new FromJavaConsumer<>(noop -> {
            }), // onArgsHandler
            Option.apply(null) // classLoaderCache
        );

        compilers = ZincUtil.compilers( //
            scalaInstance, //
            ClasspathOptionsUtil.boot(), //
            Option.apply(null), // javaHome
            scalaCompiler);

        PerClasspathEntryLookup lookup = new PerClasspathEntryLookup() {
            @Override
            public Optional<CompileAnalysis> analysis(File classpathEntry) {
                String analysisStoreFileName = null;
                if (classpathEntry.isDirectory()) {
                    if (classpathEntry.getName().equals("classes")) {
                        analysisStoreFileName = "compile";

                    } else if (classpathEntry.getName().equals("test-classes")) {
                        analysisStoreFileName = "test-compile";
                    }
                }

                if (analysisStoreFileName != null) {
                    File analysisStoreFile = Paths.get(classpathEntry.getParent(), "analysis", analysisStoreFileName)
                        .toFile();
                    if (analysisStoreFile.exists()) {
                        return AnalysisStore.getCachedStore(FileAnalysisStore.binary(analysisStoreFile)).get()
                            .map(AnalysisContents::getAnalysis);
                    }
                }
                return Optional.empty();
            }

            @Override
            public DefinesClass definesClass(File classpathEntry) {
                return Locate.definesClass(classpathEntry);
            }
        };

        analysisStore = AnalysisStore.getCachedStore(FileAnalysisStore.binary(cacheFile));

        setup = Setup.of( //
            lookup, // lookup
            false, // skip
            cacheFile, // cacheFile
            CompilerCache.fresh(), // cache
            IncOptions.of(), // incOptions
            new LoggedReporter(100, logger, pos -> pos), // reporter
            Optional.empty(), // optionProgress
            new T2[] {});
    }

    private PreviousResult previousResult() {
        Optional<AnalysisContents> analysisContents = analysisStore.get();
        if (analysisContents.isPresent()) {
            AnalysisContents analysisContents0 = analysisContents.get();
            CompileAnalysis previousAnalysis = analysisContents0.getAnalysis();
            MiniSetup previousSetup = analysisContents0.getMiniSetup();
            return PreviousResult.of(Optional.of(previousAnalysis), Optional.of(previousSetup));
        } else {
            return PreviousResult.of(Optional.empty(), Optional.empty());
        }
    }

    public void compile(List<String> classpathElements, List<File> sources, File classesDirectory,
        List<String> scalacOptions, List<String> javacOptions) {
        List<File> fullClasspath = new ArrayList<>();
        fullClasspath.add(classesDirectory);
        for (String classpathElement : classpathElements) {
            fullClasspath.add(new File(classpathElement));
        }

        CompileOptions options = CompileOptions.of( //
            fullClasspath.toArray(new File[] {}), // classpath
            sources.toArray(new File[] {}), // sources
            classesDirectory, //
            scalacOptions.toArray(new String[] {}), // scalacOptions
            javacOptions.toArray(new String[] {}), // javacOptions
            100, // maxErrors
            pos -> pos, // sourcePositionMappers
            compileOrder, // order
            Optional.empty() // temporaryClassesDirectory
        );

        Inputs inputs = Inputs.of(compilers, options, setup, previousResult());

        CompileResult newResult = compiler.compile(inputs, logger);
        analysisStore.set(AnalysisContents.create(newResult.analysis(), newResult.setup()));
    }

    private String compilerBridgeArtifactId(String scalaVersion) {
        return compilerInstance.getCompilerBridgeArtifactId(scalaVersion);
    }

    private File getCompiledBridgeJar(ScalaInstance scalaInstance, Log mavenLogger) throws Exception {

        // eg
        // org.scala-sbt-compiler-bridge_2.12-1.2.4-bin_2.12.10__52.0-1.2.4_20181015T090407.jar
        String bridgeArtifactId = compilerBridgeArtifactId(scalaInstance.actualVersion());

        // this file is localed in compiler-interface
        Properties properties = new Properties();
        try (InputStream is = getClass().getClassLoader()
            .getResourceAsStream("incrementalcompiler.version.properties")) {
            properties.load(is);
        }

        String zincVersion = properties.getProperty("version");
        String bridgeVersion = compilerInstance.getCompilerBridgeVersion(zincVersion);
        String timestamp = properties.getProperty("timestamp");

        String bridgeGroupId = compilerInstance.getCompilerBridgeGroupId();

        String cacheFileName = bridgeGroupId + '-' + bridgeArtifactId + '-' + bridgeVersion + "-bin_"
            + scalaInstance.actualVersion() + "__" + JAVA_CLASS_VERSION + '-' + bridgeVersion + '_' + timestamp
            + ".jar";

        File cachedCompiledBridgeJar = new File(secondaryCacheDir, cacheFileName);

        if (mavenLogger.isInfoEnabled()) {
            mavenLogger.info("Compiler bridge file: " + cachedCompiledBridgeJar);
        }

        if (!cachedCompiledBridgeJar.exists()) {
            mavenLogger.info("Compiler bridge file is not installed yet");
            // compile and install
            RawCompiler rawCompiler = new RawCompiler(scalaInstance, ClasspathOptionsUtil.auto(), logger);

            File bridgeSources = resolver.getJar(bridgeGroupId, bridgeArtifactId, bridgeVersion, "sources").getFile();

            Set<File> bridgeSourcesDependencies = resolver
                .getJarAndDependencies(bridgeGroupId, bridgeArtifactId, bridgeVersion, "sources") //
                .stream() //
                .filter(artifact -> artifact.getScope() != null && !artifact.getScope().equals("provided")) //
                .map(Artifact::getFile) //
                .collect(Collectors.toSet());

            bridgeSourcesDependencies.addAll(Arrays.asList(scalaInstance.allJars()));

            File sourcesDir = Files.createTempDirectory("scala-maven-plugin-compiler-bridge-sources").toFile();
            File classesDir = Files.createTempDirectory("scala-maven-plugin-compiler-bridge-classes").toFile();

            IO.unzip(bridgeSources, sourcesDir, AllPassFilter$.MODULE$, true);

            try {
                rawCompiler.apply(
                    JavaConverters
                        .iterableAsScalaIterable(FileUtils.listDirectoryContent(sourcesDir.toPath(),
                            file -> file.isFile()
                                && (file.getName().endsWith(".scala") || file.getName().endsWith(".java"))))
                        .seq().toSeq(), // sources:Seq[File]
                    JavaConverters.iterableAsScalaIterable(bridgeSourcesDependencies).seq().toSeq(), // classpath:Seq[File],
                    classesDir, // outputDirectory:File,
                    JavaConverters.collectionAsScalaIterable(Collections.<String>emptyList()).seq().toSeq() // options:Seq[String]
                );

                Manifest manifest = new Manifest();
                Attributes mainAttributes = manifest.getMainAttributes();
                mainAttributes.putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1.0");
                mainAttributes.putValue(Attributes.Name.SPECIFICATION_VENDOR.toString(), SBT_GROUP_ID);
                mainAttributes.putValue(Attributes.Name.SPECIFICATION_TITLE.toString(), "Compiler Bridge");
                mainAttributes.putValue(Attributes.Name.SPECIFICATION_VERSION.toString(), bridgeVersion);

                int classesDirPathLength = classesDir.toString().length();
                Stream<Tuple2<File, String>> stream = FileUtils.listDirectoryContent(classesDir.toPath(), file -> true) //
                    .stream() //
                    .map(file -> {
                        String path = file.toString().substring(classesDirPathLength + 1).replace(File.separator, "/");
                        if (file.isDirectory()) {
                            path = path + "/";
                        }
                        return new Tuple2(file, path);
                    });
                List<Tuple2<File, String>> classes = stream.collect(Collectors.toList());

                IO.jar(JavaConverters.collectionAsScalaIterable(classes), cachedCompiledBridgeJar, new Manifest());

                mavenLogger.info("Compiler bridge installed");

            } finally {
                FileUtils.deleteDirectory(sourcesDir.toPath());
                FileUtils.deleteDirectory(classesDir.toPath());
            }
        }

        return cachedCompiledBridgeJar;
    }
}
