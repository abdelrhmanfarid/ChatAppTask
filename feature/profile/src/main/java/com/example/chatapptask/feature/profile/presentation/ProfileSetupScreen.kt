package com.example.chatapptask.feature.profile.presentation

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chatapptask.feature.profile.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProfileSetupRoute(
    onProfileSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.onAction(ProfileSetupAction.ProfileImageSelected(uri.toString()))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ProfileSetupEvent.ProfileSaved -> onProfileSaved()
                ProfileSetupEvent.ProfileImageSelectionRequested -> {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }

                is ProfileSetupEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    ProfileSetupScreen(
        state = state,
        onAction = viewModel::onAction,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
fun ProfileSetupScreen(
    state: ProfileSetupUiState,
    onAction: (ProfileSetupAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val addPhotoDescription = stringResource(R.string.profile_setup_add_photo)
    val previewBitmap = rememberProfilePreviewBitmap(state.selectedImageUri)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.profile_setup_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.profile_setup_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))

                Surface(
                    onClick = { onAction(ProfileSetupAction.ProfileImageClicked) },
                    modifier = Modifier
                        .size(128.dp)
                        .semantics { contentDescription = addPhotoDescription },
                    enabled = !state.isSaving,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap,
                            contentDescription = addPhotoDescription,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Light,
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { onAction(ProfileSetupAction.ProfileImageClicked) },
                    enabled = !state.isSaving,
                ) {
                    Text(
                        stringResource(
                            if (state.selectedImageUri == null) {
                                R.string.profile_setup_add_photo
                            } else {
                                R.string.profile_setup_change_photo
                            },
                        ),
                    )
                }
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = state.username,
                    onValueChange = { username ->
                        onAction(ProfileSetupAction.UsernameChanged(username))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSaving,
                    label = { Text(stringResource(R.string.profile_setup_username_label)) },
                    supportingText = {
                        Text(stringResource(R.string.profile_setup_username_supporting_text))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.ageInput,
                    onValueChange = { age -> onAction(ProfileSetupAction.AgeChanged(age)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSaving,
                    label = { Text(stringResource(R.string.profile_setup_age_label)) },
                    supportingText = if (!state.isAgeValid) {
                        { Text(stringResource(R.string.profile_setup_age_error)) }
                    } else {
                        null
                    },
                    isError = !state.isAgeValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() },
                    ),
                )
                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = { onAction(ProfileSetupAction.SaveClicked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = state.canSave,
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.profile_setup_saving))
                    } else {
                        Text(stringResource(R.string.profile_setup_continue))
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberProfilePreviewBitmap(uriString: String?): ImageBitmap? {
    val context = LocalContext.current
    val preview by produceState<ImageBitmap?>(initialValue = null, uriString, context) {
        value = null
        val uri = uriString?.let(Uri::parse) ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    return preview
}

@Preview(
    name = "Light",
    showBackground = true,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Composable
private fun ProfileSetupLightPreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        ProfileSetupScreen(
            state = ProfileSetupUiState(username = "Alex", ageInput = "24"),
            onAction = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@Preview(
    name = "Dark",
    showBackground = true,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProfileSetupDarkPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        ProfileSetupScreen(
            state = ProfileSetupUiState(username = "Alex", ageInput = "24"),
            onAction = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
