package `in`.c1ph3rj.scanly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.SnackbarHostState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import `in`.c1ph3rj.scanly.feature.home.HomeScreen
import `in`.c1ph3rj.scanly.feature.home.HomeUiState
import `in`.c1ph3rj.scanly.navigation.ScanlyNavHost
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanlyApp()
        }
    }
}

@Composable
private fun ScanlyApp() {
    val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
    val themeMode by appSettingsViewModel.themeMode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val navController = rememberNavController()
    ScanlyTheme(
        darkTheme = themeMode.resolveDarkTheme(systemDark),
    ) {
        ScanlyNavHost(navController = navController)
    }
}

@Preview(showBackground = true)
@Composable
private fun ScanlyAppPreview() {
    ScanlyTheme {
        HomeScreen(
            uiState = HomeUiState.initial(),
            snackbarHostState = SnackbarHostState(),
            onCreateDocument = {},
            onRenameDocument = { _, _ -> },
            onDeleteDocument = {},
            onOpenDocument = {},
            onOpenSettings = {},
            onOpenScanSession = {},
        )
    }
}

private fun ThemeMode.resolveDarkTheme(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
