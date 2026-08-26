package com.example.chatapptask.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapptask.core.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StartupDestination {
    data object Resolving : StartupDestination

    data object ProfileSetup : StartupDestination

    data object Chat : StartupDestination

    data object Error : StartupDestination
}

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _destination = MutableStateFlow<StartupDestination>(StartupDestination.Resolving)
    val destination: StateFlow<StartupDestination> = _destination.asStateFlow()

    init {
        resolve()
    }

    fun retry() {
        resolve()
    }

    fun onProfileSaved() {
        _destination.value = StartupDestination.Chat
    }

    private fun resolve() {
        viewModelScope.launch {
            _destination.value = StartupDestination.Resolving
            try {
                val userId = userRepository.getCurrentUserId()
                val profile = userRepository.getUser(userId)
                _destination.value = if (profile == null) {
                    StartupDestination.ProfileSetup
                } else {
                    StartupDestination.Chat
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _destination.value = StartupDestination.Error
            }
        }
    }
}
