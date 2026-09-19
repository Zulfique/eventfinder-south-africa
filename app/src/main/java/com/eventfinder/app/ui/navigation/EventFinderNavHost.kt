package com.eventfinder.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eventfinder.app.R
import com.eventfinder.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import com.eventfinder.app.ui.screens.auth.LoginScreen
import com.eventfinder.app.ui.screens.auth.RegisterScreen
import com.eventfinder.app.ui.screens.create.CreateEventScreen
import com.eventfinder.app.ui.screens.detail.EventDetailScreen
import com.eventfinder.app.ui.screens.editprofile.EditProfileScreen
import com.eventfinder.app.ui.screens.favorites.FavoritesScreen
import com.eventfinder.app.ui.screens.home.HomeScreen
import com.eventfinder.app.ui.screens.profile.ProfileScreen
import com.eventfinder.app.ui.screens.search.SearchScreen
import com.eventfinder.app.ui.screens.settings.SettingsScreen
import com.eventfinder.app.ui.screens.splash.SplashScreen

/** Centralised route constants for the navigation graph. */
object AppDestinations {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val EDIT_PROFILE = "editProfile"
    const val EVENT_DETAIL = "event/{eventId}"
    const val EDIT_EVENT = "editEvent/{eventId}"

    fun eventDetail(eventId: String) = "event/$eventId"

    fun editEvent(eventId: String) = "editEvent/$eventId"
}

/**
 * Root navigation graph. The splash screen routes the user to Login or the main
 * experience based on the persisted auth session (FR-01 session persistence).
 *
 * [deepLinkEventId] carries an event id coming from a reminder notification tap;
 * once consumed it is reset so it is not replayed on recomposition.
 */
@Composable
fun EventFinderNavHost(
    container: AppContainer,
    deepLinkEventId: MutableStateFlow<String?>? = null
) {
    val navController = rememberNavController()

    val pendingEventId = deepLinkEventId?.collectAsState()?.value
    LaunchedEffect(pendingEventId, navController.currentBackStackEntry) {
        val eventId = pendingEventId ?: return@LaunchedEffect
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (currentRoute == AppDestinations.MAIN) {
            navController.navigate(AppDestinations.eventDetail(eventId))
            deepLinkEventId.value = null
        }
    }

    NavHost(navController = navController, startDestination = AppDestinations.SPLASH) {

        composable(AppDestinations.SPLASH) {
            SplashScreen(onFinished = { loggedIn ->
                navController.navigate(if (loggedIn) AppDestinations.MAIN else AppDestinations.LOGIN) {
                    popUpTo(AppDestinations.SPLASH) { inclusive = true }
                }
            })
        }

        composable(AppDestinations.LOGIN) {
            LoginScreen(
                container = container,
                onLoggedIn = {
                    navController.navigate(AppDestinations.MAIN) {
                        popUpTo(AppDestinations.LOGIN) { inclusive = true }
                    }
                },
                onCreateAccount = { navController.navigate(AppDestinations.REGISTER) }
            )
        }

        composable(AppDestinations.REGISTER) {
            RegisterScreen(
                container = container,
                onRegistered = {
                    navController.navigate(AppDestinations.MAIN) {
                        popUpTo(AppDestinations.REGISTER) { inclusive = true }
                    }
                },
                onLogin = { navController.popBackStack() }
            )
        }

        composable(AppDestinations.MAIN) {
            MainScreen(container = container, navController = navController)
        }

        composable(
            route = AppDestinations.EVENT_DETAIL,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { entry ->
            EventDetailScreen(
                container = container,
                eventId = entry.arguments?.getString("eventId").orEmpty(),
                onBack = { navController.popBackStack() },
                onEditEvent = { navController.navigate(AppDestinations.editEvent(it)) }
            )
        }

        composable(
            route = AppDestinations.EDIT_EVENT,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { entry ->
            CreateEventScreen(
                container = container,
                onClose = { navController.popBackStack() },
                eventId = entry.arguments?.getString("eventId")
            )
        }

        composable(AppDestinations.SETTINGS) {
            SettingsScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(AppDestinations.LOGIN) {
                        popUpTo(AppDestinations.MAIN) { inclusive = true }
                    }
                }
            )
        }

        composable(AppDestinations.EDIT_PROFILE) {
            EditProfileScreen(container = container, onBack = { navController.popBackStack() })
        }
    }
}

/** Bottom navigation destinations (Home, Search, Create, Favorites, Profile). */
private data class BottomTab(val labelRes: Int, val icon: ImageVector)

@Composable
private fun bottomTabs(): List<BottomTab> = listOf(
    BottomTab(R.string.discover_events, Icons.Outlined.Home),
    BottomTab(R.string.search_title, Icons.Outlined.Search),
    BottomTab(R.string.create_event_title, Icons.Filled.AddCircle),
    BottomTab(R.string.favorites_title, Icons.Outlined.Favorite),
    BottomTab(R.string.profile_title, Icons.Outlined.Person)
)

/** Scaffold hosting the five primary tabs. */
@Composable
private fun MainScreen(container: AppContainer, navController: NavHostController) {
    val tabs = bottomTabs()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                        label = { Text(stringResource(tab.labelRes)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (selectedTab) {
                0 -> HomeScreen(
                    container = container,
                    onEventClick = { navController.navigate(AppDestinations.eventDetail(it)) }
                )
                1 -> SearchScreen(
                    container = container,
                    onEventClick = { navController.navigate(AppDestinations.eventDetail(it)) }
                )
                2 -> CreateEventScreen(container = container, onClose = { selectedTab = 0 })
                3 -> FavoritesScreen(
                    container = container,
                    onEventClick = { navController.navigate(AppDestinations.eventDetail(it)) }
                )
                4 -> ProfileScreen(
                    container = container,
                    onEditProfile = { navController.navigate(AppDestinations.EDIT_PROFILE) },
                    onSettings = { navController.navigate(AppDestinations.SETTINGS) },
                    onEventClick = { navController.navigate(AppDestinations.eventDetail(it)) },
                    onLoggedOut = {
                        navController.navigate(AppDestinations.LOGIN) {
                            popUpTo(AppDestinations.MAIN) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}