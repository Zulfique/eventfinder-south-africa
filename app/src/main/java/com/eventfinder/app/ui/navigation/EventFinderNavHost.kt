package com.eventfinder.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import com.eventfinder.app.ui.screens.groups.GroupPlanningScreen
import com.eventfinder.app.ui.screens.home.HomeScreen
import com.eventfinder.app.ui.screens.tickets.TicketScannerScreen
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
    const val GROUP_PLANNING = "groupPlanning/{eventId}"
    const val TICKET_SCANNER = "ticketScanner/{eventId}"

    fun eventDetail(eventId: String) = "event/$eventId"
    fun editEvent(eventId: String) = "editEvent/$eventId"
    fun groupPlanning(eventId: String) = "groupPlanning/$eventId"
    fun ticketScanner(eventId: String) = "ticketScanner/$eventId"
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

    // After Activity recreation (e.g. language change), restore the pending route.
    LaunchedEffect(Unit) {
        val pendingRoute = container.preferences.consumePendingNavigationRoute()
        if (pendingRoute != null) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute == AppDestinations.MAIN || currentRoute == AppDestinations.SPLASH) {
                navController.navigate(pendingRoute)
            }
        }
    }

    NavHost(navController = navController, startDestination = AppDestinations.SPLASH) {

        composable(AppDestinations.SPLASH) {
            SplashScreen(
                container = container,
                onFinished = { loggedIn ->
                navController.navigate(if (loggedIn) AppDestinations.MAIN else AppDestinations.LOGIN) {
                    popUpTo(AppDestinations.SPLASH) { inclusive = true }
                }
            }
            )
        }

        composable(AppDestinations.LOGIN) {
            var requestGuestLogin by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

            if (requestGuestLogin) {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    container.authRepository.continueAsGuest()
                    navController.navigate(AppDestinations.MAIN) {
                        popUpTo(AppDestinations.LOGIN) { inclusive = true }
                    }
                }
            }

            LoginScreen(
                container = container,
                onLoggedIn = {
                    navController.navigate(AppDestinations.MAIN) {
                        popUpTo(AppDestinations.LOGIN) { inclusive = true }
                    }
                },
                onCreateAccount = { navController.navigate(AppDestinations.REGISTER) },
                onGuestLogin = { requestGuestLogin = true }
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
                                onEditEvent = { navController.navigate(AppDestinations.editEvent(it)) },
                                onPlanWithFriends = { navController.navigate(AppDestinations.groupPlanning(it)) },
                                onOpenScanner = { navController.navigate(AppDestinations.ticketScanner(it)) }
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
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

                        composable(AppDestinations.EDIT_PROFILE) {
                            EditProfileScreen(container = container, onBack = { navController.popBackStack() })
                        }

                        composable(
                            route = AppDestinations.GROUP_PLANNING,
                            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
                        ) { entry ->
                            GroupPlanningScreen(
                                container = container,
                                eventId = entry.arguments?.getString("eventId").orEmpty(),
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = AppDestinations.TICKET_SCANNER,
                            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
                        ) { entry ->
                            TicketScannerScreen(
                                container = container,
                                eventId = entry.arguments?.getString("eventId").orEmpty(),
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }

/** Bottom navigation destinations (Home, Search, Create, Favorites, Profile). */
private enum class EventFinderDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    HOME(
        "home",
        R.string.discover_events,
        Icons.Outlined.Explore,
        Icons.Filled.Explore
    ),
    SEARCH(
        "search",
        R.string.search_title,
        Icons.Outlined.Search,
        Icons.Filled.Search
    ),
    CREATE(
        "create",
        R.string.create_event_title,
        Icons.Outlined.AddCircle,
        Icons.Filled.AddCircle
    ),
    FAVORITES(
        "favorites",
        R.string.favorites_title,
        Icons.Outlined.FavoriteBorder,
        Icons.Filled.Favorite
    ),
    PROFILE(
        "profile",
        R.string.profile_title,
        Icons.Outlined.Person,
        Icons.Filled.Person
    )
}

@Composable
private fun EventFinderBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 10.dp,
                    vertical = 9.dp
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EventFinderDestination.entries.forEach { destination ->

                val selected = currentRoute == destination.route
                val isCreate = destination == EventFinderDestination.CREATE

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            if (!selected) onNavigate(destination.route)
                        }
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isCreate) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = destination.selectedIcon,
                                contentDescription = stringResource(destination.labelRes),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(27.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (selected) {
                                    destination.selectedIcon
                                } else {
                                    destination.icon
                                },
                                contentDescription = stringResource(destination.labelRes),
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(23.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text = stringResource(destination.labelRes),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected || isCreate) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Medium
                        },
                        color = when {
                            isCreate -> MaterialTheme.colorScheme.primary
                            selected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

/** Scaffold hosting the five primary tabs. */
@Composable
private fun MainScreen(container: AppContainer, navController: NavHostController) {
    var selectedTab by rememberSaveable { mutableStateOf(EventFinderDestination.HOME.route) }

    Scaffold(
        bottomBar = {
            EventFinderBottomBar(
                currentRoute = selectedTab,
                onNavigate = { route -> selectedTab = route }
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (selectedTab) {
                EventFinderDestination.HOME.route -> HomeScreen(
                    container = container,
                    onEventClick = { navController.navigate(AppDestinations.eventDetail(it)) }
                )
                EventFinderDestination.SEARCH.route -> SearchScreen(
                    container = container,
                    onEventClick = { navController.navigate(AppDestinations.eventDetail(it)) }
                )
                EventFinderDestination.CREATE.route -> CreateEventScreen(
                    container = container,
                    onClose = { selectedTab = EventFinderDestination.HOME.route }
                )
                EventFinderDestination.FAVORITES.route -> FavoritesScreen(
                    container = container,
                    onEventClick = { navController.navigate(AppDestinations.eventDetail(it)) }
                )
                EventFinderDestination.PROFILE.route -> ProfileScreen(
                    container = container,
                    onEditProfile = { navController.navigate(AppDestinations.EDIT_PROFILE) },
                    onSettings = { navController.navigate(AppDestinations.SETTINGS) },
                    onEventClick = { navController.navigate(AppDestinations.eventDetail(it)) },
                    onLogin = { navController.navigate(AppDestinations.LOGIN) },
                    onLoggedOut = {
                        navController.navigate(AppDestinations.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}




