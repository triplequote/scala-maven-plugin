package scala_maven;

import com.license4j.ActivationStatus;
import com.license4j.License;
import com.triplequote.license.HydraLicenseStore;
import com.triplequote.license.LicenseMessages;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;

public abstract class HydraLicenseMojo extends AbstractMojo {
    /**
     * The Maven Session Object
     *
     * @parameter property="session"
     * @required
     * @readonly
     */
    protected MavenSession session;

    /**
     * @parameter property="hydra.license.activationServerUrl" default-value="https://activation.triplequote.com/algas/"
     */
    protected String activationServerUrl;

    /**
     * @parameter property="hydra.license.store" default-value="${user.home}/.triplequote/hydra.license"
     */
    protected String licenseStore;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (session.getCurrentProject() == session.getTopLevelProject()) {
            try {
                operateOnLicense();
            } catch (MalformedURLException e) {
                throw new MojoExecutionException(
                        String.format("Failed to parse URL %s : %s", activationServerUrl, e.getMessage()),
                        e);
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }

    private void operateOnLicense() throws MojoFailureException, MojoExecutionException, IOException {
        HydraLicenseStore store = new HydraLicenseStore(
                new URL(activationServerUrl),
                Paths.get(licenseStore));

        License license = executeLicenseOperation(store);

        switch (license.getValidationStatus()) {
            case LICENSE_VALID:
                getLog().debug("License is valid.");
                break;

            case LICENSE_INVALID:
                throw new MojoFailureException("License is invalid.");

            case LICENSE_EXPIRED:
                throw new MojoFailureException("License has expired. Please contact " + LicenseMessages.SUPPORT_EMAIL);

            default:
                throw new MojoFailureException("License validity: " + license.getValidationStatus());
        }

        ActivationStatus activationStatus = license.getActivationStatus();

        switch (activationStatus) {
            case ACTIVATION_COMPLETED:
            case DEACTIVATION_COMPLETED:
                getLog().info(LicenseMessages.activationMessage(activationStatus));
                break;

            default:
                throw new MojoExecutionException(LicenseMessages.activationMessage(activationStatus));
        }
    }

    /**
     * Perform the license operation on the given store. Error checking is performed by caller.
     * @param store The store that contains Hydra licenses
     * @return A license that was activated/deactivated. Status can be checked on the license for error reporting
     * @throws IOException
     */
    protected abstract License executeLicenseOperation(HydraLicenseStore store) throws IOException;
}
