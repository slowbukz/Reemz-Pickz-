package com.example

import android.app.Application
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.SportsScore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.firebase.FirestoreRepository
import com.example.data.local.CorrEdgeDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.repository.SportsMarketRepository
import com.example.domain.model.QuantFilterPreferences
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutUser
import com.example.ui.components.GameDetailSheet
import com.example.ui.components.ParlayDetailSheet
import com.example.ui.components.PlayerPropDetailSheet
import com.example.ui.screens.EvScannerScreen
import com.example.ui.screens.LiveSlateScreen
import com.example.ui.screens.PlayerPropsScreen
import com.example.ui.screens.QuantLabScreen
import com.example.ui.screens.TopParlaysScreen
import com.example.ui.theme.CorrEdgeTheme
import com.example.ui.theme.LocalQuantColors
import com.example.ui.viewmodel.CorrEdgeUiState
import com.example.ui.viewmodel.CorrEdgeViewModel
import com.example.ui.viewmodel.LiveSlateSubTab
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_TOP_PARLAYS = "top_parlays"
const val ROUTE_PLAYER_PROPS = "player_props"
const val ROUTE_LIVE_SLATE = "live_slate"
const val ROUTE_EV_SCANNER = "ev_scanner"
const val ROUTE_QUANT_LAB = "quant_lab"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val Destinations = listOf(
    NavDestination(
        route = ROUTE_TOP_PARLAYS,
        labelRes = R.string.nav_top_parlays,
        selectedIcon = Icons.Filled.Bolt,
        unselectedIcon = Icons.Outlined.Bolt,
        testTag = "nav_tab_top_parlays"
    ),
    NavDestination(
        route = ROUTE_PLAYER_PROPS,
        labelRes = R.string.nav_player_props,
        selectedIcon = Icons.Filled.PersonSearch,
        unselectedIcon = Icons.Outlined.PersonSearch,
        testTag = "nav_tab_player_props"
    ),
    NavDestination(
        route = ROUTE_LIVE_SLATE,
        labelRes = R.string.nav_live_slate,
        selectedIcon = Icons.Filled.SportsScore,
        unselectedIcon = Icons.Outlined.SportsScore,
        testTag = "nav_tab_live_slate"
    ),
    NavDestination(
        route = ROUTE_EV_SCANNER,
        labelRes = R.string.nav_ev_scanner,
        selectedIcon = Icons.Filled.AutoGraph,
        unselectedIcon = Icons.Outlined.AutoGraph,
        testTag = "nav_tab_ev_scanner"
    ),
    NavDestination(
        route = ROUTE_QUANT_LAB,
        labelRes = R.string.nav_quant_lab,
        selectedIcon = Icons.Filled.Tune,
        unselectedIcon = Icons.Outlined.Tune,
        testTag = "nav_tab_quant_lab"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val app = context.applicationContext as Application
            val prefsRepo = remember(app) { UserPreferencesRepository(app) }
            val prefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = QuantFilterPreferences()
            )
            val darkTheme = prefs.darkTheme

            val currentUser by Firebase.auth.authStateFlow()
                .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

            SideEffect {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            CorrEdgeTheme(darkTheme = darkTheme) {
                val user = currentUser
                if (user == null) {
                    AuthScreen(onAuthSuccess = {})
                } else {
                    key(user.uid) {
                        val viewModel: CorrEdgeViewModel = viewModel(
                            key = user.uid,
                            factory = viewModelFactory {
                                initializer {
                                    val db = CorrEdgeDatabase.getInstance(app)
                                    val firestore = FirebaseFirestore.getInstance(
                                        app.getString(R.string.firestore_database_id)
                                    )
                                    CorrEdgeViewModel(
                                        currentUserId = user.uid,
                                        displayName = user.displayName ?: "Reemz Quant",
                                        marketRepo = SportsMarketRepository(db.corrEdgeDao()),
                                        firestoreRepo = FirestoreRepository(firestore, Firebase.auth),
                                        prefsRepo = UserPreferencesRepository(app)
                                    )
                                }
                            }
                        )
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                        CorrEdgeAppShell(
                            viewModel = viewModel,
                            uiState = uiState,
                            userDisplayName = user.displayName.orEmpty(),
                            userEmail = user.email.orEmpty()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CorrEdgeAppShell(
    viewModel: CorrEdgeViewModel,
    uiState: CorrEdgeUiState,
    userDisplayName: String,
    userEmail: String
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_TOP_PARLAYS
    val quant = LocalQuantColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.firestoreActionError) {
        val msg = uiState.firestoreActionError
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFirestoreError()
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        Destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val label = stringResource(dest.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = quant.positiveEv,
                                    selectedTextColor = quant.positiveEv,
                                    indicatorColor = quant.positiveEvContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val label = stringResource(dest.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = quant.positiveEv,
                                    selectedTextColor = quant.positiveEv,
                                    indicatorColor = quant.positiveEvContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_TOP_PARLAYS,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_TOP_PARLAYS) {
                        TopParlaysScreen(
                            uiState = uiState,
                            onRefresh = viewModel::refreshMarketData,
                            onOpenParlay = viewModel::openParlayDetail,
                            onToggleSave = viewModel::toggleSaveParlay,
                            onOpenQuantLab = { navigateTo(ROUTE_QUANT_LAB) },
                            onClickPlayerLeg = viewModel::openLegPlayerDetail,
                            onOpenNbaNews = {
                                viewModel.setLiveSlateSubTab(LiveSlateSubTab.NBA_NEWS)
                                navigateTo(ROUTE_LIVE_SLATE)
                            }
                        )
                    }
                    composable(ROUTE_PLAYER_PROPS) {
                        PlayerPropsScreen(
                            uiState = uiState,
                            onSelectLeague = viewModel::setPropsLeague,
                            onSelectDirection = viewModel::setPropDirectionFilter,
                            onInspectGame = viewModel::openGameDetail,
                            onInspectProp = viewModel::openPropDetail,
                            onToggleSaveProp = viewModel::toggleSaveProp,
                            onTogglePropInSandbox = viewModel::togglePropInSandbox,
                            onSelectTabMode = viewModel::setPropsTabMode,
                            onOpenPlayerResearch = viewModel::openPlayerResearchFromProp,
                            onSelectResearchLeague = viewModel::setResearchLeagueFilter,
                            onSelectResearchGame = { game -> viewModel.selectResearchGame(game) },
                            onSelectResearchCandidate = { candidate -> viewModel.selectResearchCandidate(candidate) },
                            onSelectResearchPropStat = viewModel::selectResearchPropStat,
                            onAdjustResearchLine = viewModel::adjustResearchLine,
                            onSetResearchCustomLine = viewModel::setResearchCustomLine,
                            onSelectPreviousGameBar = viewModel::selectPreviousGameBar,
                            onSelectResearchSimScenario = viewModel::setResearchSimScenario,
                            onRerunResearchSimulation = viewModel::rerunResearchSimulation
                        )
                    }
                    composable(ROUTE_LIVE_SLATE) {
                        LiveSlateScreen(
                            uiState = uiState,
                            onSelectLeague = viewModel::setSlateLeague,
                            onInspectGame = viewModel::openGameDetail,
                            onSelectSubTab = viewModel::setLiveSlateSubTab,
                            onToggleAutoRefresh = viewModel::toggleAutoRefresh,
                            onRefreshScoresNow = viewModel::refreshLiveScoresNow,
                            onRefreshNbaIntel = { viewModel.refreshNbaIntel(forceRefresh = true) }
                        )
                    }
                    composable(ROUTE_EV_SCANNER) {
                        EvScannerScreen(
                            uiState = uiState,
                            onSelectLeague = viewModel::setScannerLeague,
                            onSelectSort = viewModel::setEvSortMode,
                            onToggleSandboxLeg = viewModel::toggleSandboxLeg,
                            onClearSandbox = viewModel::clearSandbox,
                            onInspectSandbox = viewModel::openParlayDetail
                        )
                    }
                    composable(ROUTE_QUANT_LAB) {
                        QuantLabScreen(
                            uiState = uiState,
                            userDisplayName = userDisplayName,
                            userEmail = userEmail,
                            onSignOut = {
                                signOutUser(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            },
                            onToggleLeaguePref = viewModel::toggleLeaguePref,
                            onMinProbChange = viewModel::setMinHitProbability,
                            onMinRhoChange = viewModel::setMinCorrelationRho,
                            onParlaySizeChange = viewModel::setParlaySizePreference,
                            onDevigMethodChange = viewModel::setDevigMethod,
                            onOddsFormatChange = viewModel::setOddsFormat,
                            onDarkThemeChange = viewModel::setDarkTheme,
                            onOpenSavedParlayDoc = viewModel::openSavedParlayDoc,
                            onDeleteSavedParlayDoc = viewModel::deleteSavedParlayDoc,
                            onDeleteSavedPropDoc = viewModel::deleteSavedPropDoc,
                            onOpenSavedPropDoc = viewModel::openSavedPropDoc
                        )
                    }
                }
            }
        }
    }

    uiState.activeDetailParlay?.let { detailParlay ->
        ParlayDetailSheet(
            parlay = detailParlay,
            oddsFormat = uiState.preferences.oddsFormat,
            onDismiss = viewModel::closeParlayDetail,
            onToggleSave = { viewModel.toggleSaveParlay(detailParlay) },
            onClickPlayerLeg = { leg ->
                viewModel.openLegPlayerDetail(leg)
            }
        )
    }

    uiState.selectedGameForDetail?.let { selectedGame ->
        val sandboxIds = remember(uiState.sandboxLegs) {
            uiState.sandboxLegs.map { it.id }.toSet()
        }
        GameDetailSheet(
            event = selectedGame,
            gameProps = uiState.selectedGameProps,
            gameLines = uiState.selectedGameLines,
            gameParlays = uiState.selectedGameParlays,
            sandboxLegIds = sandboxIds,
            oddsFormat = uiState.preferences.oddsFormat,
            onDismiss = viewModel::closeGameDetail,
            onOpenParlay = viewModel::openParlayDetail,
            onToggleSaveParlay = viewModel::toggleSaveParlay,
            onToggleSaveProp = viewModel::toggleSaveProp,
            onToggleSandboxLeg = viewModel::toggleSandboxLeg,
            onTogglePropInSandbox = viewModel::togglePropInSandbox,
            onInspectProp = viewModel::openPropDetail,
            onResearchPlayer = { prop ->
                viewModel.closeGameDetail()
                viewModel.openPlayerResearchFromProp(prop)
                navigateTo(ROUTE_PLAYER_PROPS)
            },
            onResearchMatchup = { game ->
                viewModel.closeGameDetail()
                viewModel.openPlayerResearchForGame(game)
                navigateTo(ROUTE_PLAYER_PROPS)
            }
        )
    }

    uiState.selectedPropForDetail?.let { propDetail ->
        val isInSandbox = remember(uiState.sandboxLegs, propDetail.id) {
            uiState.sandboxLegs.any { it.id == propDetail.id }
        }
        PlayerPropDetailSheet(
            prop = propDetail,
            event = uiState.selectedPropDetailEvent,
            researchProfile = uiState.selectedPropDetailProfile,
            matchupInjuries = uiState.selectedPropDetailInjuries,
            isLoadingContext = uiState.selectedPropDetailLoading,
            isInSandbox = isInSandbox,
            oddsFormat = uiState.preferences.oddsFormat,
            onDismiss = viewModel::closePropDetail,
            onToggleSave = { viewModel.toggleSaveProp(propDetail) },
            onToggleSandbox = { viewModel.togglePropInSandbox(propDetail) },
            onOpenFullResearch = {
                viewModel.closePropDetail()
                viewModel.closeGameDetail()
                viewModel.closeParlayDetail()
                viewModel.openPlayerResearchFromProp(propDetail)
                navigateTo(ROUTE_PLAYER_PROPS)
            }
        )
    }
}
