package com.example.chatapptask.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapptask.core.domain.model.User
import com.example.chatapptask.core.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileSetupUiState())
    val uiState: StateFlow<ProfileSetupUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<ProfileSetupEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun onAction(action: ProfileSetupAction) {
        when (action) {
            is ProfileSetupAction.UsernameChanged -> {
                _uiState.update { state -> state.copy(username = action.username) }
            }

            is ProfileSetupAction.AgeChanged -> {
                _uiState.update { state -> state.copy(ageInput = action.age) }
            }

            ProfileSetupAction.ProfileImageClicked -> viewModelScope.launch {
                eventChannel.send(ProfileSetupEvent.ProfileImageSelectionRequested)
            }

            ProfileSetupAction.SaveClicked -> saveProfile()
        }
    }

    private fun saveProfile() {
        val state = uiState.value
        if (!state.canSave) return

        val username = state.username.trim()
        val age = state.ageInput.takeIf(String::isNotBlank)?.toIntOrNull()
        _uiState.update { currentState -> currentState.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                val userId = userRepository.getCurrentUserId()
                val now = Instant.now()
                userRepository.upsertUser(
                    User(
                        id = userId,
                        username = username,
                        profileImagePath = null,
                        age = age,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                _uiState.update { currentState -> currentState.copy(isSaving = false) }
                eventChannel.send(ProfileSetupEvent.ProfileSaved)
            } catch (_: Exception) {
                _uiState.update { currentState -> currentState.copy(isSaving = false) }
                eventChannel.send(ProfileSetupEvent.ShowError(SAVE_ERROR_MESSAGE))
            }
        }
    }

    private companion object {
        const val SAVE_ERROR_MESSAGE = "We couldn't save your profile. Please try again."
    }
}
