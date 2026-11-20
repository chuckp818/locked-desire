package de.kopffrei.app.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import de.kopffrei.app.ui.BrainDumpScreen
import de.kopffrei.app.ui.MoreScreen
import de.kopffrei.app.ui.SosScreen
import de.kopffrei.core.common.DeepLinks
import de.kopffrei.core.common.model.MoreEntry
import de.kopffrei.core.common.model.QuickAction
import de.kopffrei.core.common.settings.UserSettings
import de.kopffrei.core.designsystem.theme.LocalBottomBarVisible
import de.kopffrei.feature.calendar.CalendarScreen
import de.kopffrei.feature.calendar.EventEditorScreen
import de.kopffrei.feature.dashboard.DashboardScreen
import de.kopffrei.feature.focus.FocusScreen
import de.kopffrei.feature.journal.JournalEditorScreen
import de.kopffrei.feature.journal.JournalScreen
import de.kopffrei.feature.notes.NoteEditorScreen
import de.kopffrei.feature.notes.NotesScreen
import de.kopffrei.feature.settings.SettingsScreen
import de.kopffrei.feature.tasks.TaskEditorScreen
import de.kopffrei.feature.tasks.TasksScreen

private class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.TODAY, "Heute", Icons.Rounded.Home),
    Tab(Routes.NOTES, "Notizen", Icons.Rounded.StickyNote2),
    Tab(Routes.CALENDAR, "Kalender", Icons.Rounded.CalendarMonth),
    Tab(Routes.FOCUS, "Fokus", Icons.Rounded.Timer),
    Tab(Routes.MORE, "Mehr", Icons.Rounded.MoreHoriz),
)

/**
 * Navigation der ganzen App: Hauptnavigation Heute | Notizen | Kalender | Fokus | Mehr, darüber die
 * Editoren und weiteren Bildschirme. Die Feature-Module kennen sich nicht – hier werden ihre
 * Rückrufe verbunden.
 */
@Composable
fun KopfFreiNavHost(
    settings: UserSettings,
    pendingDeepLink: Intent?,
    onDeepLinkHandled: () -> Unit,
) {
    val navController = rememberNavController()

    // Ein Link, der schon beim Aufbau vorlag, wird vom NavController selbst aus dem Intent der Activity
    // geöffnet; nur spätere Links (onNewIntent) müssen hier weitergereicht werden.
    val initialDeepLink = remember { pendingDeepLink }
    LaunchedEffect(pendingDeepLink) {
        val intent = pendingDeepLink ?: return@LaunchedEffect
        if (intent !== initialDeepLink) navController.handleDeepLink(intent)
        onDeepLinkHandled()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentBase = Routes.baseOf(backStackEntry?.destination?.route)
    val imeVisible = WindowInsets.isImeVisible
    // Bei geöffneter Tastatur verschwinden Leiste und Knopf, damit Platz zum Schreiben bleibt.
    val showBottomBar = currentBase in Routes.TOP_LEVEL && !imeVisible
    val showQuickAction = currentBase in Routes.WITH_QUICK_ACTION && !imeVisible

    val reduce = settings.reduceAnimations
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reduce) EnterTransition.None else fadeIn(tween(TRANSITION_MS))
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reduce) ExitTransition.None else fadeOut(tween(TRANSITION_MS))
    }

    CompositionLocalProvider(LocalBottomBarVisible provides showBottomBar) {
        Scaffold(
            // imePadding: Mit Edge-to-Edge schiebt sich der Inhalt sonst nicht über die Tastatur.
            modifier = Modifier.imePadding(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomBar) {
                    MainBottomBar(currentBase = currentBase) { route -> navController.navigateTopLevel(route) }
                }
            },
            floatingActionButton = {
                if (showQuickAction) {
                    QuickActionButton(settings.quickAction) { navController.runQuickAction(settings.quickAction) }
                }
            },
        ) { inner ->
            NavHost(
                navController = navController,
                startDestination = Routes.TODAY,
                modifier = Modifier.padding(inner),
                enterTransition = enter,
                exitTransition = exit,
                popEnterTransition = enter,
                popExitTransition = exit,
            ) {
                tabDestinations(navController)
                detailDestinations(navController)
            }
        }
    }
}

private const val TRANSITION_MS = 180

// ---------------------------------------------------------------- Ziele

private fun NavGraphBuilder.tabDestinations(nav: NavController) {
    composable(Routes.TODAY, deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.TODAY })) {
        DashboardScreen(
            onNewNote = { voice -> nav.navigate(Routes.noteEdit(voice = voice)) },
            onNewTask = { nav.navigate(Routes.taskEdit()) },
            onNewEvent = { nav.navigate(Routes.eventEdit()) },
            onBrainDump = { nav.navigate(Routes.BRAIN_DUMP) },
            onOverwhelmed = { nav.navigate(Routes.SOS) },
            onOpenEvent = { id -> nav.navigate(Routes.eventEdit(eventId = id)) },
            onOpenTask = { id -> nav.navigate(Routes.taskEdit(taskId = id)) },
            onOpenTasks = { nav.navigate(Routes.TASKS) },
            onOpenDeepLink = { link -> nav.openLink(link) },
            onStartFocus = { minutes -> nav.startFocus(minutes) },
            onOpenMore = { entry -> nav.openMoreEntry(entry) },
        )
    }

    composable(Routes.NOTES, deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.NOTES })) {
        NotesScreen(
            onOpenNote = { id -> nav.navigate(Routes.noteEdit(noteId = id)) },
            onNewNote = { voice, thought -> nav.navigate(Routes.noteEdit(voice = voice, thought = thought)) },
            onThoughtToTask = { id -> nav.navigate(Routes.taskEdit(fromNote = id)) },
        )
    }

    composable(Routes.CALENDAR, deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.CALENDAR })) {
        CalendarScreen(
            onOpenEvent = { id -> nav.navigate(Routes.eventEdit(eventId = id)) },
            onNewEvent = { dayStart -> nav.navigate(Routes.eventEdit(date = dayStart)) },
            onOpenTask = { id -> nav.navigate(Routes.taskEdit(taskId = id)) },
        )
    }

    composable(
        route = Routes.FOCUS_PATTERN,
        arguments = listOf(
            stringArg(Routes.ARG_MINUTES),
            stringArg(Routes.ARG_AUTOSTART),
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = Routes.LINK_FOCUS },
            navDeepLink { uriPattern = DeepLinks.FOCUS_BASE },
        ),
    ) {
        FocusScreen()
    }

    composable(Routes.MORE) {
        MoreScreen(
            onOpen = { entry -> nav.openMoreEntry(entry) },
            onBrainDump = { nav.navigate(Routes.BRAIN_DUMP) },
            onOverwhelmed = { nav.navigate(Routes.SOS) },
        )
    }
}

private fun NavGraphBuilder.detailDestinations(nav: NavController) {
    composable(Routes.TASKS, deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.TASKS })) {
        TasksScreen(
            onOpenTask = { id -> nav.navigate(Routes.taskEdit(taskId = id)) },
            onNewTask = { nav.navigate(Routes.taskEdit()) },
            onBack = { nav.popBackStack() },
        )
    }

    composable(Routes.JOURNAL, deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.JOURNAL })) {
        JournalScreen(
            onOpenEntry = { id -> nav.navigate(Routes.journalEdit(entryId = id)) },
            onNewEntry = { nav.navigate(Routes.journalEdit()) },
            onBack = { nav.popBackStack() },
        )
    }

    composable(Routes.SETTINGS, deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.SETTINGS })) {
        SettingsScreen(onBack = { nav.popBackStack() })
    }

    composable(Routes.SOS) {
        SosScreen(
            onBack = { nav.popBackStack() },
            onBrainDump = { nav.navigate(Routes.BRAIN_DUMP) { popUpTo(Routes.SOS) { inclusive = true } } },
            onStartFocus = { minutes -> nav.startFocus(minutes) },
            onOpenTasks = { nav.navigate(Routes.TASKS) },
        )
    }

    composable(Routes.BRAIN_DUMP, deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.BRAIN_DUMP })) {
        BrainDumpScreen(onBack = { nav.popBackStack() })
    }

    composable(
        route = Routes.NOTE_EDIT_PATTERN,
        arguments = listOf(
            stringArg(Routes.ARG_NOTE_ID),
            boolArg(Routes.ARG_VOICE),
            boolArg(Routes.ARG_THOUGHT),
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = Routes.LINK_NOTE_NEW },
            navDeepLink { uriPattern = Routes.LINK_NOTE_OPEN },
        ),
    ) {
        NoteEditorScreen(
            onClose = { nav.popBackStack() },
            onConvertToTask = { id -> nav.navigate(Routes.taskEdit(fromNote = id)) },
            onConvertToEvent = { id -> nav.navigate(Routes.eventEdit(fromNote = id)) },
        )
    }

    composable(
        route = Routes.TASK_EDIT_PATTERN,
        arguments = listOf(stringArg(Routes.ARG_TASK_ID), stringArg(Routes.ARG_FROM_NOTE)),
        deepLinks = listOf(
            navDeepLink { uriPattern = DeepLinks.NEW_TASK },
            navDeepLink { uriPattern = Routes.LINK_TASK_OPEN },
        ),
    ) {
        TaskEditorScreen(onClose = { nav.popBackStack() })
    }

    composable(
        route = Routes.EVENT_EDIT_PATTERN,
        arguments = listOf(
            stringArg(Routes.ARG_EVENT_ID),
            stringArg(Routes.ARG_FROM_NOTE),
            stringArg(Routes.ARG_DATE),
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = DeepLinks.NEW_EVENT },
            navDeepLink { uriPattern = Routes.LINK_EVENT_OPEN },
        ),
    ) {
        EventEditorScreen(onClose = { nav.popBackStack() })
    }

    composable(
        route = Routes.JOURNAL_EDIT_PATTERN,
        arguments = listOf(stringArg(Routes.ARG_ENTRY_ID)),
    ) {
        JournalEditorScreen(onClose = { nav.popBackStack() })
    }
}

private fun stringArg(name: String) = navArgument(name) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

private fun boolArg(name: String) = navArgument(name) {
    type = NavType.BoolType
    defaultValue = false
}

// ---------------------------------------------------------------- Navigationshilfen

/** Wechsel zwischen den Hauptbereichen: kein Stapel aus Kopien, jeder Bereich merkt sich seinen Zustand. */
private fun NavController.navigateTopLevel(route: String, restore: Boolean = true) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = restore }
        launchSingleTop = true
        restoreState = restore
    }
}

/** Öffnet den Fokus-Timer mit vorgewählter Dauer und startet ihn (ohne alten gemerkten Zustand). */
private fun NavController.startFocus(minutes: Int) {
    navigateTopLevel(Routes.focus(minutes, autostart = true), restore = false)
}

private fun NavController.openMoreEntry(entry: MoreEntry) {
    when (entry) {
        MoreEntry.JOURNAL -> navigate(Routes.JOURNAL)
        MoreEntry.TASKS -> navigate(Routes.TASKS)
        MoreEntry.SETTINGS -> navigate(Routes.SETTINGS)
        // Noch nicht verfügbar (spätere Ausbaustufen): bewusst keine Aktion.
        MoreEntry.ROUTINES, MoreEntry.AI, MoreEntry.COMMUNITY, MoreEntry.STATS, MoreEntry.PARTNER -> Unit
    }
}

/** Öffnet einen App-Link (z. B. aus einer Erinnerungskarte). Die Hauptbereiche wechseln den Tab. */
private fun NavController.openLink(link: String) {
    val tab = when (link) {
        DeepLinks.TODAY -> Routes.TODAY
        DeepLinks.NOTES -> Routes.NOTES
        DeepLinks.CALENDAR -> Routes.CALENDAR
        DeepLinks.FOCUS_BASE -> Routes.FOCUS
        else -> null
    }
    if (tab != null) {
        navigateTopLevel(tab)
        return
    }
    try {
        navigate(Uri.parse(link))
    } catch (_: IllegalArgumentException) {
        // Unbekannter Link: ignorieren statt abzustürzen.
    }
}

private fun NavController.runQuickAction(action: QuickAction) {
    when (action) {
        QuickAction.NOTE -> navigate(Routes.noteEdit())
        QuickAction.VOICE -> navigate(Routes.noteEdit(voice = true))
        QuickAction.TASK -> navigate(Routes.taskEdit())
        QuickAction.EVENT -> navigate(Routes.eventEdit())
        QuickAction.BRAIN_DUMP -> navigate(Routes.BRAIN_DUMP)
    }
}

// ---------------------------------------------------------------- Oberfläche

@Composable
private fun MainBottomBar(currentBase: String?, onSelect: (String) -> Unit) {
    NavigationBar {
        TABS.forEach { tab ->
            NavigationBarItem(
                selected = currentBase == tab.route,
                onClick = { onSelect(tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label, maxLines = 1) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun QuickActionButton(action: QuickAction, onClick: () -> Unit) {
    val (icon, label) = when (action) {
        QuickAction.NOTE -> Icons.Rounded.EditNote to "Neue Notiz"
        QuickAction.VOICE -> Icons.Rounded.Mic to "Einsprechen"
        QuickAction.TASK -> Icons.Rounded.TaskAlt to "Neue Aufgabe"
        QuickAction.EVENT -> Icons.Rounded.Event to "Neuer Termin"
        QuickAction.BRAIN_DUMP -> Icons.Rounded.Inbox to "Kopf ausschütten"
    }
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(label) },
    )
}
