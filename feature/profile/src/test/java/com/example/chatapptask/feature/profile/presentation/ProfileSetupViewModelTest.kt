package com.example.chatapptask.feature.profile.presentation

import com.example.chatapptask.core.domain.model.User
import com.example.chatapptask.core.domain.repository.UserRepository
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileSetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun usernameChanged_updatesState() {
        val viewModel = ProfileSetupViewModel(FakeUserRepository())

        viewModel.onAction(ProfileSetupAction.UsernameChanged("Alex"))

        assertEquals("Alex", viewModel.uiState.value.username)
    }

    @Test
    fun ageChanged_updatesState() {
        val viewModel = ProfileSetupViewModel(FakeUserRepository())

        viewModel.onAction(ProfileSetupAction.AgeChanged("24"))

        assertEquals("24", viewModel.uiState.value.ageInput)
    }

    @Test
    fun blankUsername_doesNotSave() = runTest(dispatcher) {
        val repository = FakeUserRepository()
        val viewModel = ProfileSetupViewModel(repository)

        viewModel.onAction(ProfileSetupAction.UsernameChanged("   "))
        viewModel.onAction(ProfileSetupAction.SaveClicked)
        advanceUntilIdle()

        assertTrue(repository.upsertedUsers.isEmpty())
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun invalidAge_doesNotSave() = runTest(dispatcher) {
        val repository = FakeUserRepository()
        val viewModel = ProfileSetupViewModel(repository)

        viewModel.onAction(ProfileSetupAction.UsernameChanged("Alex"))
        viewModel.onAction(ProfileSetupAction.AgeChanged("0"))
        viewModel.onAction(ProfileSetupAction.SaveClicked)
        advanceUntilIdle()

        assertTrue(repository.upsertedUsers.isEmpty())
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun ageAbovePreviousUiMaximum_isAcceptedBecauseContractDefinesNoMaximum() =
        runTest(dispatcher) {
            val repository = FakeUserRepository()
            val viewModel = ProfileSetupViewModel(repository)

            viewModel.onAction(ProfileSetupAction.UsernameChanged("Alex"))
            viewModel.onAction(ProfileSetupAction.AgeChanged("151"))
            viewModel.onAction(ProfileSetupAction.SaveClicked)
            advanceUntilIdle()

            assertEquals(151, repository.upsertedUsers.single().age)
        }

    @Test
    fun validProfile_usesExistingIdentityAndExpectedProfileData() = runTest(dispatcher) {
        val repository = FakeUserRepository()
        val viewModel = ProfileSetupViewModel(repository)

        viewModel.onAction(ProfileSetupAction.UsernameChanged("  Alex  "))
        viewModel.onAction(ProfileSetupAction.AgeChanged("24"))
        viewModel.onAction(ProfileSetupAction.SaveClicked)
        advanceUntilIdle()

        val savedUser = repository.upsertedUsers.single()
        assertEquals(repository.currentUserId, savedUser.id)
        assertEquals("Alex", savedUser.username)
        assertEquals(24, savedUser.age)
        assertNull(savedUser.profileImagePath)
        assertEquals(savedUser.createdAt, savedUser.updatedAt)
        assertEquals(1, repository.currentUserIdRequests)
    }

    @Test
    fun successfulSave_emitsSuccessEvent() = runTest(dispatcher) {
        val viewModel = ProfileSetupViewModel(FakeUserRepository())

        viewModel.onAction(ProfileSetupAction.UsernameChanged("Alex"))
        viewModel.onAction(ProfileSetupAction.SaveClicked)
        advanceUntilIdle()

        assertEquals(ProfileSetupEvent.ProfileSaved, viewModel.events.first())
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun repositoryFailure_emitsSafeImmediateError() = runTest(dispatcher) {
        val repository = FakeUserRepository().apply {
            upsertFailure = IllegalStateException("backend details")
        }
        val viewModel = ProfileSetupViewModel(repository)

        viewModel.onAction(ProfileSetupAction.UsernameChanged("Alex"))
        viewModel.onAction(ProfileSetupAction.SaveClicked)
        advanceUntilIdle()

        assertEquals(
            ProfileSetupEvent.ShowError("We couldn't save your profile. Please try again."),
            viewModel.events.first(),
        )
        assertFalse(viewModel.uiState.value.isSaving)
    }
}

private class FakeUserRepository : UserRepository {
    val currentUserId: UUID = UUID.fromString("47ff16cf-fc7b-40a2-8eaa-d58ae8cb0e9d")
    val upsertedUsers = mutableListOf<User>()
    var currentUserIdRequests = 0
    var upsertFailure: Exception? = null

    override suspend fun getCurrentUserId(): UUID {
        currentUserIdRequests += 1
        return currentUserId
    }

    override suspend fun upsertUser(user: User) {
        upsertFailure?.let { exception -> throw exception }
        upsertedUsers += user
    }

    override suspend fun getUser(userId: UUID): User? = null

    override fun observeUser(userId: UUID): Flow<User?> = flowOf(null)
}
