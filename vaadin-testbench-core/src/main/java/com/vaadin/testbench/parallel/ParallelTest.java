/**
 * Copyright (C) 2012 Vaadin Ltd
 *
 * This program is available under Commercial Vaadin Add-On License 3.0
 * (CVALv3).
 *
 * See the file licensing.txt distributed with this software for more
 * information about licensing.
 *
 * You should have received a copy of the license along with this program.
 * If not, see <http://vaadin.com/license/cval-3>.
 */
package com.vaadin.testbench.parallel;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;

import com.vaadin.testbench.Parameters;
import com.vaadin.testbench.ScreenshotOnFailureRule;
import com.vaadin.testbench.TestBenchTestCase;
import com.vaadin.testbench.annotations.BrowserConfiguration;
import com.vaadin.testbench.annotations.RunLocally;
import com.vaadin.testbench.annotations.RunOnHub;
import com.vaadin.testbench.parallel.setup.SetupDriver;

/**
 * Unit tests should extend {@link ParallelTest} if they are to be run in
 * several browser configurations. For each browser configuration, a
 * {@link WebDriver} is properly created with the desired configuration.
 * <p>
 * You can configure your tests to be run in Sauce Labs. See details at
 * <a href="https://wiki.saucelabs.com">https://wiki.saucelabs.com</a> and
 * <a href=
 * "https://github.com/vaadin/testbench-demo">https://github.com/vaadin/testbench-demo</a>.
 * </p>
 */
@RunWith(ParallelRunner.class)
public class ParallelTest extends TestBenchTestCase {

    @Rule
    public ScreenshotOnFailureRule screenshotOnFailure = new ScreenshotOnFailureRule(
            this, true);

    private static final Logger logger = Logger
            .getLogger(ParallelTest.class.getName());
    private static final String SAUCE_USERNAME_ENV = "SAUCE_USERNAME";
    private static final String SAUCE_USERNAME_PROP = "sauce.user";
    private static final String SAUCE_ACCESS_KEY_ENV = "SAUCE_ACCESS_KEY";
    private static final String SAUCE_ACCESS_KEY_PROP = "sauce.sauceAccessKey";
    private SetupDriver driverConfiguration = new SetupDriver();

    /**
     * <p>
     * Returns the complete URL of the hub where the tests will be run on. Used
     * by {@link #setup()}, for the creation of the {@link WebDriver}.
     * </p>
     * <p>
     * This method uses {@link #getHubHostname()} to build the complete address
     * of the Hub. Override in order to define a different hub address.<br>
     * </p>
     * <p>
     * You can provide sauce.user and sauce.sauceAccessKey system properties or
     * SAUCE_USERNAME and SAUCE_ACCESS_KEY environment variables to run the
     * tests in Sauce Labs. If both system property and environment variable is
     * defined, system property is prioritised.
     * </p>
     *
     * @return the complete URL of the hub where the tests will be run on. Used
     *         by {@link #setup()}, for the creation of the {@link WebDriver}.
     */
    protected String getHubURL() {
        String username = getSauceUser();
        String accessKey = getSauceAccessKey();

        if (username == null) {
            logger.log(Level.FINE,
                    "You can give a Sauce Labs user name using -D"
                            + SAUCE_USERNAME_PROP + "=<username> or by "
                            + SAUCE_USERNAME_ENV + " environment variable.");
        }
        if (accessKey == null) {
            logger.log(Level.FINE,
                    "You can give a Sauce Labs access key using -D"
                            + SAUCE_ACCESS_KEY_PROP + "=<accesskey> or by "
                            + SAUCE_ACCESS_KEY_ENV + " environment variable.");
        }
        if (username != null && accessKey != null) {
            return "http://" + username + ":" + accessKey
                    + "@localhost:4445/wd/hub";
        } else {
            return "http://" + getHubHostname() + ":4444/wd/hub";
        }
    }

    /**
     * <p>
     * Returns the hostname of the hub where test is to be run on. If unit test
     * is annotated by {@link RunLocally}, this method returns localhost.
     * Otherwise, it will return the host defined by the
     * {@code com.vaadin.testbench.Parameters.hubHostname} system parameter or
     * the host defined using a {@link RunOnHub} annotation.
     * </p>
     * <p>
     * This method is used by {@link #getHubURL()} to get the full URL of the
     * hub to run tests on.
     * </p>
     *
     * @return the hostname of the hub where test is to be run on.
     */
    protected String getHubHostname() {
        String hubSystemProperty = Parameters.getHubHostname();
        if (hubSystemProperty != null) {
            return hubSystemProperty;
        }

        RunLocally runLocally = getClass().getAnnotation(RunLocally.class);
        if (runLocally != null) {
            return "localhost";
        }

        RunOnHub runOnHub = getRunOnHub(getClass());
        return runOnHub.value();
    }

    /**
     * <p>
     * Sets the driver for this test instance. Uses
     * {@link SetupDriver#setupRemoteDriver(String)} or
     * {@link SetupDriver#setupLocalDriver(Browser)} according to the
     * annotations found in current test case.
     * </p>
     * <p>
     * {@link RunOnHub} annotation can be used on the test case class to define
     * a test hub's hostname for the driver to connect to it.<br>
     * {@link RunLocally} annotation can be used on the test case class to force
     * the driver to connect to localhost ({@link RunLocally} annotation
     * overrides {@link RunOnHub} annotation).
     * </p>
     *
     * @throws Exception
     *             if unable to instantiate {@link WebDriver}
     */
    @Before
    public void setup() throws Exception {
        // Always give priority to @RunLocally annotation
        if ((getRunLocallyBrowser() != null)) {
            WebDriver driver = driverConfiguration.setupLocalDriver(
                    getRunLocallyBrowser(), getRunLocallyBrowserVersion());
            setDriver(driver);
        } else if (Parameters.isLocalWebDriverUsed()) {
            WebDriver driver = driverConfiguration.setupLocalDriver();
            setDriver(driver);
        } else if (isConfiguredForSauceLabs()) {
            checkSauceConnectExists();
            WebDriver driver = driverConfiguration
                    .setupRemoteDriver(getHubURL());
            setDriver(driver);

        } else if (getRunOnHub(getClass()) != null
                || Parameters.getHubHostname() != null) {
            WebDriver driver = driverConfiguration
                    .setupRemoteDriver(getHubURL());
            setDriver(driver);
        } else {
            logger.log(Level.INFO,
                    "Did not find a configuration to run locally, on Sauce Labs or on other test grid. Falling back to running locally on Chrome.");
            WebDriver driver = driverConfiguration
                    .setupLocalDriver(Browser.CHROME);
            setDriver(driver);
        }
    }

    /**
     * @return Value of the {@link RunOnHub} annotation of current Class, or
     *         null if annotation is not present.
     */
    protected RunOnHub getRunOnHub(Class<?> klass) {
        if (klass == null) {
            return null;
        }

        return klass.getAnnotation(RunOnHub.class);
    }

    /**
     * @return Browser value of the {@link RunLocally} annotation of current
     *         Class, or null if annotation is not present.
     */
    protected Browser getRunLocallyBrowser() {
        return ParallelRunner.getRunLocallyBrowserName(getClass());
    }

    /**
     * @return Version value of the {@link RunLocally} annotation of current
     *         Class, or empty empty String if annotation is not present.
     */
    protected String getRunLocallyBrowserVersion() {
        return ParallelRunner.getRunLocallyBrowserVersion(getClass());
    }

    /**
     *
     * @return default capabilities, used if no {@link BrowserConfiguration}
     *         method was found
     */
    public static List<DesiredCapabilities> getDefaultCapabilities() {
        return Collections.singletonList(BrowserUtil.firefox());
    }

    /**
     * Sets the requested {@link DesiredCapabilities} (usually browser name and
     * version)
     *
     * @param desiredCapabilities
     */
    public void setDesiredCapabilities(
            DesiredCapabilities desiredCapabilities) {
        String sauceOptions = System.getProperty("sauce.options");
        if (sauceOptions != null) {
            final String tunnelManagerClassName = "com.saucelabs.ci.sauceconnect.AbstractSauceTunnelManager";
            try {
                Class<?> tunnelManager = ParallelTest.class.getClassLoader()
                        .loadClass(tunnelManagerClassName);
                Method getTunnelIdentifier = tunnelManager.getDeclaredMethod(
                        "getTunnelIdentifier", String.class, String.class);
                String tunnelId = (String) getTunnelIdentifier.invoke(null,
                        sauceOptions, null);
                if (tunnelId != null) {
                    desiredCapabilities.setCapability("tunnelIdentifier",
                            tunnelId);
                }
            } catch (ClassNotFoundException e) {
                logger.log(Level.WARNING, "Sauce options defined, but "
                        + tunnelManagerClassName
                        + " not found. Are you missing a Sauce Labs dependency?");
            } catch (Exception e) {
                logger.log(Level.WARNING,
                        "Sauce options defined, but failed to get tunnel identifier.");

            }
        }
        driverConfiguration.setDesiredCapabilities(desiredCapabilities);
    }

    @BrowserConfiguration
    public List<DesiredCapabilities> getDefaultBrowserConfiguration() {
        String browsers = System.getenv("TESTBENCH_BROWSERS");
        List<DesiredCapabilities> finalList = new ArrayList<>();
        if (browsers != null) {
            for (String browserStr : browsers.split(",")) {
                String[] browserStrSplit = browserStr.split("-");
                Browser browser = Browser.valueOf(
                        browserStrSplit[0].toUpperCase(Locale.ENGLISH).trim());
                DesiredCapabilities capabilities = browser
                        .getDesiredCapabilities();
                if (browserStrSplit.length > 1) {
                    capabilities.setVersion(browserStrSplit[1].trim());
                }
                finalList.add(capabilities);
            }
        } else {
            finalList.add(BrowserUtil.chrome());
        }
        return finalList;
    }

    /**
     * Gets the {@link DesiredCapabilities} (usually browser name and version)
     *
     * @return
     */
    protected DesiredCapabilities getDesiredCapabilities() {
        return driverConfiguration.getDesiredCapabilities();
    }

    protected boolean isConfiguredForSauceLabs() {
        return getSauceUser() != null && getSauceAccessKey() != null;
    }

    protected String getSauceUser() {
        return getSystemPropertyOrEnv(SAUCE_USERNAME_PROP, SAUCE_USERNAME_ENV);
    }

    protected String getSauceAccessKey() {
        return getSystemPropertyOrEnv(SAUCE_ACCESS_KEY_PROP,
                SAUCE_ACCESS_KEY_ENV);
    }

    private String getSystemPropertyOrEnv(String propertyKey, String envName) {
        String env = System.getenv(envName);
        String prop = System.getProperty(propertyKey);
        return (prop != null) ? prop : env;
    }

    private void checkSauceConnectExists() {
        final String klass = "com.saucelabs.ci.sauceconnect.SauceTunnelManager";
        try {
            ParallelTest.class.getClassLoader().loadClass(klass);
        } catch (ClassNotFoundException e) {
            logger.warning(
                    "Tests are configured for Sauce Labs, but ci-sauce dependency seems to be missing.");
        }
    }
}
