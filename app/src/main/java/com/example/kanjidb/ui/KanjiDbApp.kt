package com.example.kanjidb.ui

import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.kanjidb.ui.training.TrainingState
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.kanjidb.ui.search.RecommendedState
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.kanjidb.R
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.kanjidb.ui.about.AboutScreen
import com.example.kanjidb.ui.openalpha.FeedbackScreen
import com.example.kanjidb.ui.openalpha.OpenAlphaNotice
import com.example.kanjidb.ui.details.KanjiDetailsScreen
import com.example.kanjidb.ui.details.WordDetailsScreen
import com.example.kanjidb.ui.lists.MyKanjiScreen
import com.example.kanjidb.ui.search.SearchScreen
import com.example.kanjidb.ui.search.SearchState
import androidx.lifecycle.ViewModelProvider
import com.example.kanjidb.ui.training.TrainingScreen
import com.example.kanjidb.ui.settings.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SEARCH = "search"
private const val ABOUT = "about"
private const val FEEDBACK = "open_alpha_feedback"
private const val SETTINGS = "settings"
private const val MY_KANJI = "my_kanji"
private const val TRAINING = "training"
private const val KANJI_ID = "kanjiId"
private const val WORD_DETAILS = "word/{entryId}/{written}?sourceKanji={sourceKanji}&matchedGlossId={matchedGlossId}&matchedMeaning={matchedMeaning}"
private const val DETAILS = "details/{$KANJI_ID}"

private val topLevelDestinations = listOf(
    SEARCH to R.string.search_title,
    MY_KANJI to R.string.my_kanji_title,
    TRAINING to R.string.training_title
)

@Composable
fun KanjiDbApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    OpenAlphaNotice()
    val preferences = remember(context) { AppPreferences.getInstance(context) }
    // Resolved once per fresh root and saved alongside NavController state. Preference
    // changes and Android restoration must never replace an existing navigation stack.
    var initialRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var initialTab by rememberSaveable { mutableStateOf(MyKanjiTab.KANJI_GROUPS) }
    var initialTabPending by rememberSaveable { mutableStateOf(true) }
    var startupFailed by remember { mutableStateOf(false) }
    var startupRetry by remember { mutableStateOf(0) }
    val userDao = remember(context) {
        com.example.kanjidb.data.user.UserDatabase.getInstance(context).kanjiStates()
    }
    LaunchedEffect(userDao) { RecommendedState.initialize(DictionaryDatabase(context), userDao) }
    LaunchedEffect(startupRetry) {
        if (initialRoute != null) return@LaunchedEffect
        startupFailed = false
        val settings = preferences.state.value
        try {
            val target = if (settings.startupScreen in listOf(StartupScreen.AUTOMATIC, StartupScreen.TRAINING)) {
                withContext(Dispatchers.IO) {
                    val characters = com.example.kanjidb.data.user.UserDatabase.getInstance(context).backup().snapshot().characters
                    val available = if (characters.isEmpty()) emptySet() else
                        DictionaryDatabase(context).getKanjiGroups().mapTo(hashSetOf()) { it.character }
                    resolveStartup(settings.startupScreen, characters, available, settings.lastMyKanjiTab)
                }
            } else resolveStartup(settings.startupScreen, emptyList(), emptySet(), settings.lastMyKanjiTab)
            initialTab = target.myKanjiTab
            initialRoute = target.route
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            android.util.Log.e("Startup", "Could not resolve startup destination", error)
            startupFailed = true
        }
    }
    if (initialRoute == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (startupFailed) {
                    Text("Could not prepare the startup screen.")
                    TextButton(onClick = { startupRetry++ }) { Text("Retry") }
                } else CircularProgressIndicator()
            }
        }
        return
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(currentRoute) {
        if (currentRoute != null && currentRoute !in listOf(TRAINING, SETTINGS, ABOUT)) TrainingState.clearFixedPool()
    }
    var exitTrainingTo by rememberSaveable { mutableStateOf<String?>(null) }
    // A restored exit dialog must not outlive the process-only session after process death.
    LaunchedEffect(!TrainingState.active) {
        if (!TrainingState.active) exitTrainingTo = null
    }
    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    if (exitTrainingTo != null && TrainingState.active) {
        AlertDialog(
            onDismissRequest = { exitTrainingTo = null },
            title = { Text("End training?") },
            text = { Text("Progress for this training session will be lost.") },
            dismissButton = { TextButton(onClick = { exitTrainingTo = null }) { IconLabel(AppIcons.CirclePlay) { Text("Continue training") } } },
            confirmButton = {
                TextButton(enabled = !TrainingState.saving, onClick = {
                    val target = exitTrainingTo
                    exitTrainingTo = null
                    TrainingState.cancel()
                    if (target != null && target != TRAINING) navigateTopLevel(target)
                }) { IconLabel(AppIcons.CircleX) { Text("End training") } }
            }
        )
    }
    val openDetails: (String) -> Unit = { kanjiId ->
        navController.navigate("details/${Uri.encode(kanjiId)}")
    }
    val openSettings: () -> Unit = { navController.navigate(SETTINGS) { launchSingleTop = true } }

    // Detail navigation always pushes. The first non-detail entry below the chain is its origin.
    // Keeping that entry on the stack preserves its saved state and internal tabs/filters.
    val returnToOrigin: () -> Unit = {
        while (navController.currentBackStackEntry?.destination?.route in listOf(DETAILS, WORD_DETAILS)) {
            if (!navController.popBackStack()) break
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (!TrainingState.active && topLevelDestinations.any { it.first == currentRoute }) {
                NavigationBar {
                    topLevelDestinations.forEach { (route, labelResource) ->
                        val label = stringResource(labelResource)
                        NavigationBarItem(
                            selected = currentRoute == route,
                            enabled = !TrainingState.saving,
                            onClick = {
                                if (currentRoute != route) {
                                    if (TrainingState.active) exitTrainingTo = route
                                    else navigateTopLevel(route)
                                }
                            },
                            icon = { Icon(when (route) {
                                SEARCH -> AppIcons.Search
                                MY_KANJI -> AppIcons.Vocabulary
                                else -> AppIcons.PencilCheck
                            }, contentDescription = null) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = requireNotNull(initialRoute),
            modifier = Modifier.padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .fillMaxSize()
        ) {
            composable(SEARCH) { entry ->
                val searchState = remember(entry) { ViewModelProvider(entry)[SearchState::class.java] }
                DisposableEffect(entry) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            entry.savedStateHandle.remove<String>("recommendedDetails")?.let {
                                RecommendedState.returnedFromDetails(it)
                            }
                        }
                    }
                    entry.lifecycle.addObserver(observer)
                    onDispose { entry.lifecycle.removeObserver(observer) }
                }
                SearchScreen(state = searchState, onOpenRecommended = { character ->
                    entry.savedStateHandle["recommendedDetails"] = character
                    openDetails(character)
                }, onOpenDetails = openDetails, onOpenSettings = openSettings, onOpenFeedback = {
                    navController.navigate(FEEDBACK) { launchSingleTop = true }
                }, onOpenSearchWord = { result ->
                    val word = result.word
                    val gloss = if(result.matchKind == com.example.kanjidb.data.dictionary.WordMatchKind.MEANING) result.matchedGlossId else -1
                    val meaning = if(gloss >= 0) result.matchedMeaning.orEmpty() else ""
                    navController.navigate("word/${word.entryId}/${Uri.encode(word.written)}?matchedGlossId=$gloss&matchedMeaning=${Uri.encode(meaning)}")
                }, onOpenWord = { entryId, written ->
                    navController.navigate("word/$entryId/${Uri.encode(written)}")
                })
            }
            composable(ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable(FEEDBACK) {
                FeedbackScreen(onBack = { navController.popBackStack() })
            }
            composable(SETTINGS) {
                SettingsScreen(preferences, onBack = { navController.popBackStack() },
                    onAbout = { navController.navigate(ABOUT) { launchSingleTop = true } })
            }
            composable(MY_KANJI) { entry ->
                val tab = remember(entry) {
                    if (initialTabPending && initialRoute == MY_KANJI) initialTab else preferences.state.value.lastMyKanjiTab
                }
                LaunchedEffect(entry) { initialTabPending = false }
                MyKanjiScreen(onOpenDetails = openDetails, userDao = userDao, onOpenTraining = { navigateTopLevel(TRAINING) },
                    onOpenSettings = openSettings, initialTab = tab, onTabChanged = preferences::setLastMyKanjiTab)
            }
            composable(TRAINING) {
                TrainingScreen(userDao = userDao, onRequestExit = { exitTrainingTo = TRAINING }, onOpenSettings = openSettings,
                    onOpenKanji = openDetails, onOpenWord = { entryId, written ->
                        navController.navigate("word/$entryId/${Uri.encode(written)}")
                    })
            }
            composable(
                route = WORD_DETAILS,
                arguments = listOf(
                    navArgument("entryId") { type = NavType.LongType },
                    navArgument("written") { type = NavType.StringType },
                    navArgument("sourceKanji") { type = NavType.StringType; defaultValue = "" },
                    navArgument("matchedGlossId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("matchedMeaning") { type = NavType.StringType; defaultValue = "" }
                )
            ) { entry ->
                val arguments = requireNotNull(entry.arguments)
                WordDetailsScreen(
                    entryId = arguments.getLong("entryId"),
                    written = requireNotNull(arguments.getString("written")),
                    sourceKanji = requireNotNull(arguments.getString("sourceKanji")),
                    matchedGlossId = arguments.getLong("matchedGlossId"),
                    matchedMeaning = arguments.getString("matchedMeaning").orEmpty(),
                    onOpenKanji = openDetails,
                    onReturnToOrigin = returnToOrigin
                )
            }
            composable(
                route = DETAILS,
                arguments = listOf(navArgument(KANJI_ID) { type = NavType.StringType })
            ) { entry ->
                val kanjiId = requireNotNull(entry.arguments?.getString(KANJI_ID))
                KanjiDetailsScreen(
                    kanjiId = kanjiId,
                    userDao = userDao,
                    onReturnToOrigin = returnToOrigin,
                    onOpenWord = { entryId, written ->
                        navController.navigate("word/$entryId/${Uri.encode(written)}?sourceKanji=${Uri.encode(kanjiId)}")
                    }
                )
            }
        }
    }
}
