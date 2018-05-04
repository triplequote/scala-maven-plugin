package scala_maven_executions;

import org.apache.maven.toolchain.Toolchain;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JvmProcess {
    private String javaExec = "";
    private List<String> jvmArgs = new ArrayList<>();
    private List<String> args = new ArrayList<>();
    private List<String> sysProps = new ArrayList<>();
    private String mainClass = "";

    public JvmProcess(Toolchain toolchain, String mainClass) {
        this.mainClass = mainClass;

        if (toolchain != null)
            javaExec = toolchain.findTool("java");

        if (toolchain == null || javaExec == null) {
            javaExec = System.getProperty("java.home");
            if (javaExec == null) {
                javaExec = System.getenv("JAVA_HOME");
                if (javaExec == null) {
                    throw new IllegalStateException("Couldn't locate java, try setting JAVA_HOME environment variable.");
                }
            }
            javaExec += File.separator + "bin" + File.separator + "java";
        }

    }

    public JvmProcess addJvmArgs(String... args) {
        jvmArgs.addAll(Arrays.asList(args));
        return this;
    }

    public JvmProcess addArgs(String... _args) {
        args.addAll(Arrays.asList(_args));
        return this;
    }

    public JvmProcess addSysProp(String key, String value) {
        sysProps.add("-D" + key + "=" + value);
        return this;
    }


    public void spawn(boolean displayCmd) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(javaExec);
        command.addAll(jvmArgs);
        command.addAll(sysProps);
        command.add(mainClass);
        command.addAll(args);

        if (displayCmd) {
            System.out.print("\nRunning command: ");
            for (String arg : command)
                System.out.print(arg + " ");
        }
        new ProcessBuilder()
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .command(command).start();
    }
}
