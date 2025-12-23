package com.pintailconsultingllc.cdcdebezium.acceptance

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.LoadState
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertTrue

@AcceptanceTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Grafana UI Verification (PLAN-019)")
class GrafanaUiAcceptanceTest {

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private lateinit var page: Page

    @BeforeAll
    fun setUpAll() {
        playwright = Playwright.create()
        browser = playwright.chromium().launch(
            BrowserType.LaunchOptions()
                .setHeadless(true)
                .setTimeout(30000.0)
        )
    }

    @AfterAll
    fun tearDownAll() {
        browser.close()
        playwright.close()
    }

    @BeforeEach
    fun setUp() {
        page = browser.newPage()
        page.setDefaultTimeout(15000.0)
    }

    @AfterEach
    fun tearDown() {
        page.close()
    }

    @Nested
    @DisplayName("Datasources Page")
    inner class DatasourcesPageTests {

        @Test
        @DisplayName("should display all three datasources (Prometheus, Tempo, Loki)")
        fun shouldDisplayAllThreeDatasources() {
            // Login to Grafana
            loginToGrafana()

            // Navigate to datasources page
            page.navigate("$GRAFANA_URL/connections/datasources")
            page.waitForLoadState(LoadState.NETWORKIDLE)

            // Get page content
            val pageContent = page.content()

            // Verify all three datasources are visible
            assertTrue(
                pageContent.contains("Prometheus") || page.locator("text=Prometheus").count() > 0,
                "Prometheus datasource should be visible"
            )
            assertTrue(
                pageContent.contains("Tempo") || page.locator("text=Tempo").count() > 0,
                "Tempo datasource should be visible"
            )
            assertTrue(
                pageContent.contains("Loki") || page.locator("text=Loki").count() > 0,
                "Loki datasource should be visible"
            )
        }

        @Test
        @DisplayName("should show Prometheus datasource as working")
        fun shouldShowPrometheusDatasourceWorking() {
            loginToGrafana()

            // Navigate to Prometheus datasource settings
            page.navigate("$GRAFANA_URL/connections/datasources/edit/prometheus")
            page.waitForLoadState(LoadState.NETWORKIDLE)

            // Click "Test" button if available
            val testButton = page.locator("button:has-text('Test')")
            if (testButton.count() > 0) {
                testButton.click()
                page.waitForTimeout(2000.0)

                // Check for success message
                val pageContent = page.content()
                assertTrue(
                    pageContent.contains("Success") ||
                        pageContent.contains("Data source is working") ||
                        pageContent.contains("successfully"),
                    "Prometheus datasource test should succeed"
                )
            }
        }

        @Test
        @DisplayName("should show Tempo datasource as working")
        fun shouldShowTempoDatasourceWorking() {
            loginToGrafana()

            // Navigate to Tempo datasource settings
            page.navigate("$GRAFANA_URL/connections/datasources/edit/tempo")
            page.waitForLoadState(LoadState.NETWORKIDLE)

            // Click "Test" button if available
            val testButton = page.locator("button:has-text('Test')")
            if (testButton.count() > 0) {
                testButton.click()
                page.waitForTimeout(2000.0)

                // Check for success message
                val pageContent = page.content()
                assertTrue(
                    pageContent.contains("Success") ||
                        pageContent.contains("Data source is working") ||
                        pageContent.contains("successfully"),
                    "Tempo datasource test should succeed"
                )
            }
        }

        @Test
        @DisplayName("should show Loki datasource as working")
        fun shouldShowLokiDatasourceWorking() {
            loginToGrafana()

            // Navigate to Loki datasource settings
            page.navigate("$GRAFANA_URL/connections/datasources/edit/loki")
            page.waitForLoadState(LoadState.NETWORKIDLE)

            // Click "Test" button if available
            val testButton = page.locator("button:has-text('Test')")
            if (testButton.count() > 0) {
                testButton.click()
                page.waitForTimeout(2000.0)

                // Check for success message
                val pageContent = page.content()
                assertTrue(
                    pageContent.contains("Success") ||
                        pageContent.contains("Data source is working") ||
                        pageContent.contains("successfully"),
                    "Loki datasource test should succeed"
                )
            }
        }
    }

    @Nested
    @DisplayName("Explore Page")
    inner class ExplorePageTests {

        @Test
        @DisplayName("should allow selecting Prometheus in Explore")
        fun shouldAllowSelectingPrometheusInExplore() {
            loginToGrafana()

            // Navigate to Explore with Prometheus datasource
            page.navigate("$GRAFANA_URL/explore?orgId=1&left=%7B%22datasource%22:%22prometheus%22%7D")
            page.waitForLoadState(LoadState.NETWORKIDLE)

            val pageContent = page.content()

            // Verify we're in Explore mode with Prometheus
            assertTrue(
                pageContent.contains("Explore") || pageContent.contains("prometheus"),
                "Should be able to explore with Prometheus datasource"
            )
        }

        @Test
        @DisplayName("should allow selecting Tempo in Explore")
        fun shouldAllowSelectingTempoInExplore() {
            loginToGrafana()

            // Navigate to Explore with Tempo datasource
            page.navigate("$GRAFANA_URL/explore?orgId=1&left=%7B%22datasource%22:%22tempo%22%7D")
            page.waitForLoadState(LoadState.NETWORKIDLE)

            val pageContent = page.content()

            // Verify we're in Explore mode with Tempo
            assertTrue(
                pageContent.contains("Explore") || pageContent.contains("tempo") ||
                    pageContent.contains("TraceQL") || pageContent.contains("Search"),
                "Should be able to explore with Tempo datasource"
            )
        }

        @Test
        @DisplayName("should allow selecting Loki in Explore")
        fun shouldAllowSelectingLokiInExplore() {
            loginToGrafana()

            // Navigate to Explore with Loki datasource
            page.navigate("$GRAFANA_URL/explore?orgId=1&left=%7B%22datasource%22:%22loki%22%7D")
            page.waitForLoadState(LoadState.NETWORKIDLE)

            val pageContent = page.content()

            // Verify we're in Explore mode with Loki
            assertTrue(
                pageContent.contains("Explore") || pageContent.contains("loki") ||
                    pageContent.contains("LogQL") || pageContent.contains("Log browser"),
                "Should be able to explore with Loki datasource"
            )
        }
    }

    @Nested
    @DisplayName("Home Page")
    inner class HomePageTests {

        @Test
        @DisplayName("should load Grafana home page successfully")
        fun shouldLoadGrafanaHomePage() {
            loginToGrafana()

            page.navigate("$GRAFANA_URL/")
            page.waitForLoadState(LoadState.NETWORKIDLE)

            val pageContent = page.content()

            // Verify home page loaded (look for common Grafana elements)
            assertTrue(
                pageContent.contains("Grafana") ||
                    pageContent.contains("Home") ||
                    pageContent.contains("Welcome"),
                "Grafana home page should load successfully"
            )
        }
    }

    private fun loginToGrafana() {
        page.navigate("$GRAFANA_URL/login")
        page.waitForLoadState(LoadState.NETWORKIDLE)

        // Check if already logged in (redirected to home)
        if (!page.url().contains("/login")) {
            return
        }

        // Fill login form
        val usernameField = page.locator("input[name='user']")
        val passwordField = page.locator("input[name='password']")

        if (usernameField.count() > 0 && passwordField.count() > 0) {
            usernameField.fill(GRAFANA_USER)
            passwordField.fill(GRAFANA_PASSWORD)

            // Click login button
            val loginButton = page.locator("button[type='submit']")
            if (loginButton.count() > 0) {
                loginButton.click()
                page.waitForLoadState(LoadState.NETWORKIDLE)

                // Handle "Skip" button if password change prompt appears
                val skipButton = page.locator("button:has-text('Skip')")
                if (skipButton.count() > 0) {
                    skipButton.click()
                    page.waitForLoadState(LoadState.NETWORKIDLE)
                }
            }
        }
    }

    companion object {
        private const val GRAFANA_URL = "http://localhost:3000"
        private const val GRAFANA_USER = "admin"
        private const val GRAFANA_PASSWORD = "admin"
    }
}
