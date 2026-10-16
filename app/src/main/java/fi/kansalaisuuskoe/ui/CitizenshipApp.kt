package fi.kansalaisuuskoe.ui

import android.net.Uri

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import fi.kansalaisuuskoe.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.*
import androidx.navigation.NavType
import androidx.navigation.navArgument
import fi.kansalaisuuskoe.AppContainer
import fi.kansalaisuuskoe.domain.ContentStatus

private data class Destination(val route: String, val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val destinations = listOf(
    Destination("home", R.string.nav_home, Icons.Default.Home),
    Destination("topics", R.string.nav_topics, Icons.AutoMirrored.Filled.List),
    Destination("review", R.string.nav_review, Icons.Default.Refresh),
    Destination("progress", R.string.nav_progress, Icons.Default.CheckCircle),
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitizenshipApp(state: AppUiState, model: AppViewModel, container: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val studying = route.startsWith("study/")
    val settings = route == "settings"
    val material = route == "material"
    val backup = route == "backup"
    val coverage = route == "coverage"
    val lesson = route.startsWith("lesson/")
    val vocabulary = route.startsWith("vocabulary")
    val words = route.startsWith("words")
    val assessment = route.startsWith("assessment")
    val detail = settings || studying || material || coverage || backup || lesson || vocabulary || words || assessment
    if (!state.loading && state.error == null && !state.settings.onboardingSeen) {
        OnboardingScreen(state.contentStatus, model::finishOnboarding)
        return
    }
    fun tab(destination: String) {
        nav.navigate(destination) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(when { assessment -> R.string.assessment_title; material -> R.string.material_title; coverage -> R.string.coverage_title; backup -> R.string.backup_nav_title; settings -> R.string.settings_title; studying -> R.string.practice_title; lesson -> R.string.lesson_title; vocabulary -> R.string.vocabulary_browser_title; words -> R.string.words_title; else -> R.string.app_title }), style = MaterialTheme.typography.titleLarge)
                        // The full disclaimer lives in onboarding and Settings → About; top-level screens keep only this chip.
                        if (!detail && state.contentStatus != ContentStatus.OFFICIAL) NotOfficialChip()
                    }
                },
                navigationIcon = {
                    if (detail) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (!detail) {
                        StudyLanguageSwitch(state.settings.studyLanguage, model::studyLanguage)
                        IconButton(onClick = { nav.navigate("settings") { launchSingleTop = true } }) { Icon(Icons.Default.Settings, stringResource(R.string.settings_title)) }
                    }
                },
            )
        },
        bottomBar = {
            if (!detail) NavigationBar {
                destinations.forEach { d ->
                    NavigationBarItem(selected = route == d.route, onClick = { tab(d.route) }, icon = { Icon(d.icon, null) }, label = { Text(stringResource(d.label), maxLines = 1) })
                }
            }
        },
    ) { padding ->
        // Practice lets its docked Check button and feedback sheet reach behind the gesture bar.
        val inner = if (studying) PaddingValues(top = padding.calculateTopPadding()) else padding
        Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator(Modifier.semanticsLabel(stringResource(R.string.loading_content))) }
                state.error != null -> Page { item { EmptyState(stringResource(R.string.content_failed), stringResource(state.error), stringResource(R.string.retry), model::initialize) } }
                else -> NavHost(navController = nav, startDestination = "home") {
                    composable("home") { HomeScreen(state, { nav.navigate("study/all") }, { tab("topics") }, { nav.navigate("settings") }, { nav.navigate("assessments") }) }
                    composable("topics") { TopicsScreen(state, { nav.navigate("vocabulary") }) { nav.navigate("lesson/$it") } }
                    composable("assessments") {
                        val assessmentModel: AssessmentViewModel = viewModel(factory = viewModelFactory { initializer { AssessmentViewModel(container.assessments) } })
                        val assessmentState by assessmentModel.state.collectAsStateWithLifecycle()
                        AssessmentsScreen(assessmentState, state, { assessmentModel.start(state.settings.studyLanguage) { nav.navigate("assessment/$it") } }, { nav.navigate("assessment/$it") }, assessmentModel::retry, { assessmentModel.delete(it) })
                    }
                    composable("assessment/{sessionId}") { backStack ->
                        val assessmentModel: AssessmentViewModel = viewModel(viewModelStoreOwner = backStack, factory = viewModelFactory { initializer { AssessmentViewModel(container.assessments) } })
                        val assessmentState by assessmentModel.state.collectAsStateWithLifecycle()
                        AssessmentScreen(assessmentState, backStack.arguments?.getString("sessionId").orEmpty(), state, assessmentModel,
                            { nav.navigate("study/assessment?assessmentId=$it") }, { nav.popBackStack() })
                    }
                    composable("lesson/{topicId}") { backStack ->
                        val topicId = backStack.arguments?.getString("topicId").orEmpty()
                        TopicLessonScreen(state, topicId, { nav.navigate("study/topic?topicId=${Uri.encode(topicId)}") }, { nav.navigate("vocabulary/$topicId") })
                    }
                    listOf("vocabulary", "vocabulary/{topicId}").forEach { destination ->
                        composable(destination) { backStack ->
                            val vocabularyModel: VocabularyViewModel = viewModel(viewModelStoreOwner = backStack, factory = viewModelFactory { initializer { VocabularyViewModel(createSavedStateHandle()) } })
                            val query by vocabularyModel.query.collectAsStateWithLifecycle()
                            val topicId by vocabularyModel.topic.collectAsStateWithLifecycle()
                            VocabularyScreen(state, query, topicId, vocabularyModel::search, vocabularyModel::filter) {
                                nav.navigate("words?query=${Uri.encode(query)}" + (topicId?.let { "&topicId=${Uri.encode(it)}" } ?: ""))
                            }
                        }
                    }
                    composable("words?query={query}&topicId={topicId}", arguments = listOf(navArgument("query") { defaultValue = "" }, navArgument("topicId") { type = NavType.StringType; nullable = true; defaultValue = null })) { backStack ->
                        val wordModel: WordPracticeViewModel = viewModel(viewModelStoreOwner = backStack, factory = viewModelFactory { initializer { WordPracticeViewModel(container.learning, createSavedStateHandle(), state.settings.studyLanguage) } })
                        val wordState by wordModel.state.collectAsStateWithLifecycle()
                        WordPracticeScreen(wordState, wordModel::reveal, wordModel::next) { nav.popBackStack() }
                    }
                    composable("review") { ReviewScreen(state, { nav.navigate("study/review") }, { tab("topics") }) }
                    composable("progress") { ProgressScreen(state) }
                    composable("settings") { SettingsScreen(state, model, { nav.navigate("backup") { launchSingleTop = true } }) { nav.navigate("material") { launchSingleTop = true } } }
                    composable("backup") {
                        val backupModel: BackupViewModel = viewModel(factory = viewModelFactory { initializer { BackupViewModel(container.learning) } })
                        val backupState by backupModel.state.collectAsStateWithLifecycle()
                        BackupScreen(backupState, backupModel, state.snapshot.attempts.size)
                    }
                    composable("material") {
                        val importModel: ContentImportViewModel = viewModel(factory = viewModelFactory { initializer { ContentImportViewModel(container.learning) } })
                        val importState by importModel.state.collectAsStateWithLifecycle()
                        ContentImportScreen(importState, importModel) { nav.navigate("coverage") { launchSingleTop = true } }
                    }
                    composable("coverage") {
                        val importModel: ContentImportViewModel = viewModel(factory = viewModelFactory { initializer { ContentImportViewModel(container.learning) } })
                        val importState by importModel.state.collectAsStateWithLifecycle()
                        CoverageScreen(importState, importModel::refresh)
                    }
                    composable("study/{mode}?assessmentId={assessmentId}&topicId={topicId}", arguments = listOf(
                        navArgument("assessmentId") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("topicId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    )) { backStack ->
                        val questionModel: QuestionViewModel = viewModel(viewModelStoreOwner = backStack, factory = viewModelFactory {
                            initializer { QuestionViewModel(container.learning, createSavedStateHandle(), state.settings.studyLanguage, container.assessments) }
                        })
                        val questionState by questionModel.state.collectAsStateWithLifecycle()
                        QuestionScreen(questionState, state.snapshot.topics, state.settings.easyFinnish,
                            questionModel::select, questionModel::submit, questionModel::next,
                            { nav.navigate("home") { popUpTo("home") { inclusive = false }; launchSingleTop = true } }, questionModel::load, state.settings.explanation)
                    }
                }
            }
        }
    }
}
