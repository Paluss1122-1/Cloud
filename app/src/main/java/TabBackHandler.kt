import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// TabNavigationState.kt
data class TabNavigationState(
    val canNavigateBack: Boolean = false,
    val onNavigateBack: () -> Unit = {}
)

// TabNavigationViewModel.kt
class TabNavigationViewModel : ViewModel() {

    private val _navigationState = MutableStateFlow(TabNavigationState())
    val navigationState: StateFlow<TabNavigationState> = _navigationState.asStateFlow()

    fun updateBackState(
        canNavigateBack: Boolean,
        onNavigateBack: (() -> Unit)? = null
    ) {
        _navigationState.update {
            it.copy(
                canNavigateBack = canNavigateBack,
                onNavigateBack = onNavigateBack ?: it.onNavigateBack
            )
        }
    }

    fun reset() {
        _navigationState.update { TabNavigationState() }
    }

    fun triggerBack() {
        _navigationState.value.onNavigateBack()
    }
}