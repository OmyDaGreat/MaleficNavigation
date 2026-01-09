package xyz.malefic.compose.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import moe.tlaster.precompose.navigation.NavHost
import moe.tlaster.precompose.navigation.Navigator
import moe.tlaster.precompose.navigation.rememberNavigator
import xyz.malefic.ext.precompose.gate

/** Manages the routes for the application. */
object RouteManager {
    private var isInitialized = false
    val dynamicRoutes: MutableList<DynamicRoute> = mutableListOf()
    val staticRoutes: MutableList<StaticRoute> = mutableListOf()
    val allRoutes: List<Route>
        get() = staticRoutes + dynamicRoutes

    lateinit var navi: Navigator
    lateinit var startupRoute: String

    /**
     * Initializes the RouteManager with the provided navigator and route configuration builder.
     *
     * @param navi An optional navigator to use.
     * @param builder A lambda function to build the route configuration.
     */
    fun initialize(
        navi: Navigator? = null,
        builder: ConfigDSL.() -> Unit,
    ) {
        if (!isInitialized) {
            val config = ConfigDSL().apply(builder).build()
            config.applyConfig()
        }
        navi?.let { this.navi = it }
    }

    /**
     * Applies the given configuration to the RouteManager.
     *
     * @receiver A pair containing the startup route name and a list of routes.
     */
    private fun Pair<String, List<Route>>.applyConfig() {
        val routes = this.second
        routes.forEach { route ->
            when (route) {
                is DynamicRoute -> dynamicRoutes.add(route)
                is StaticRoute -> staticRoutes.add(route)
            }
        }
        val startupRouteFromConfig = this.first
        startupRoute = routes.firstOrNull { it.name == startupRouteFromConfig }?.name ?: "default"
        isInitialized = true
    }

    /**
     * Ensures that the RouteManager is initialized.
     *
     * @throws IllegalStateException if the RouteManager is not initialized.
     */
    private fun ensureInitialized() {
        check(isInitialized) {
            error("RouteManager is not initialized. Call RouteManager.initialize() first.")
        }
    }

    /**
     * Ensures that the RouteManager is initialized in a composable context.
     *
     * @throws IllegalStateException if the RouteManager is not initialized.
     */
    @Composable
    private fun composableEnsureInitialized() {
        ensureInitialized()
        if (!::navi.isInitialized) {
            navi = rememberNavigator()
        }
    }

    /**
     * Displays the navigation host with the provided startup route.
     *
     * @param startupRoute The initial route to display.
     */
    @Composable
    fun RoutedNavHost(
        startupRoute: String = this.startupRoute,
        modifier: Modifier = Modifier,
    ) {
        composableEnsureInitialized()
        NavHost(navi, initialRoute = startupRoute, modifier = modifier) {
            staticRoutes.forEach { route ->
                Logger.d("Adding static route ${route.name}")
                scene(route.name) { route.composable(emptyList()) }
            }
            dynamicRoutes.forEach { route ->
                Logger.d("Adding dynamic route ${route.name}")
                scene(route.fullName) { params -> route.composable(params.pathMap.values.toList()) }
            }
        }
    }

    /** Displays the sidebar with buttons for each eligible route. */
    @Composable
    fun RoutedSidebar(modifier: Modifier = Modifier) {
        composableEnsureInitialized()
        Column(
            modifier = Modifier.width(200.dp).fillMaxHeight().then(modifier),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            getEligibleRoutes().forEach { route ->
                Button(onClick = { navi gate route.name }) { Text(route.name.capitalize(Locale.current)) }
            }
        }
    }

    /**
     * Returns a list of all eligible routes (routes with no parameters or only optional parameters).
     *
     * @return A list of eligible routes.
     */
    fun getEligibleRoutes(): List<Route> {
        ensureInitialized()
        return allRoutes.filter { route ->
            when (route) {
                is StaticRoute -> true
                is DynamicRoute -> route.params.all { it.endsWith("?") }
                else -> false
            }
        }
    }
}
