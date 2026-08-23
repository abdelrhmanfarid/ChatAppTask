package com.example.chatapptask.feature.profile.presentation

data class ProfileSetupUiState(
    val username: String = "",
    val ageInput: String = "",
    val isSaving: Boolean = false,
) {
    val isUsernameValid: Boolean
        get() = username.isNotBlank()

    val isAgeValid: Boolean
        get() = ageInput.isBlank() || ageInput.toIntOrNull()?.let { age -> age > 0 } == true

    val canSave: Boolean
        get() = isUsernameValid && isAgeValid && !isSaving

}

sealed interface ProfileSetupAction {
    data class UsernameChanged(val username: String) : ProfileSetupAction

    data class AgeChanged(val age: String) : ProfileSetupAction

    data object ProfileImageClicked : ProfileSetupAction

    data object SaveClicked : ProfileSetupAction
}

sealed interface ProfileSetupEvent {
    data object ProfileSaved : ProfileSetupEvent

    data object ProfileImageSelectionRequested : ProfileSetupEvent

    data class ShowError(val message: String) : ProfileSetupEvent
}
