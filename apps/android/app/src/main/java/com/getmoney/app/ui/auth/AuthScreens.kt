package com.getmoney.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.getmoney.app.R
import com.getmoney.app.data.auth.AuthRepository
import com.getmoney.app.ui.theme.CarbonButtonDefaults
import com.getmoney.app.ui.theme.ErrorRed
import com.getmoney.app.ui.theme.InkMuted
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onLoginSuccess: () -> Unit,
    onNavigateRegister: () -> Unit,
) {
    AuthForm(
        title = "Sign in",
        submitLabel = "Sign in",
        alternatePrompt = "Need an account?",
        alternateActionLabel = "Register",
        onAlternateAction = onNavigateRegister,
    ) { email, password, setError, setLoading ->
        val result = authRepository.login(email, password)
        result.fold(
            onSuccess = { onLoginSuccess() },
            onFailure = { setError(it.message ?: "Sign in failed") },
        )
    }
}

@Composable
fun RegisterScreen(
    authRepository: AuthRepository,
    onRegisterSuccess: () -> Unit,
    onNavigateLogin: () -> Unit,
) {
    AuthForm(
        title = "Create account",
        submitLabel = "Register",
        alternatePrompt = "Already have an account?",
        alternateActionLabel = "Sign in",
        onAlternateAction = onNavigateLogin,
    ) { email, password, setError, setLoading ->
        val result = authRepository.register(email, password)
        result.fold(
            onSuccess = { onRegisterSuccess() },
            onFailure = { setError(it.message ?: "Registration failed") },
        )
    }
}

@Composable
private fun AuthForm(
    title: String,
    submitLabel: String,
    alternatePrompt: String,
    alternateActionLabel: String,
    onAlternateAction: () -> Unit,
    onSubmit: suspend (
        email: String,
        password: String,
        setError: (String) -> Unit,
        setLoading: (Boolean) -> Unit,
    ) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = "GetMoney logo",
            modifier = Modifier
                .size(88.dp)
                .align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(32.dp))

        CarbonTextField(
            value = email,
            onValueChange = {
                email = it
                error = null
            },
            label = "Email",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            ),
        )
        Spacer(modifier = Modifier.height(16.dp))

        CarbonTextField(
            value = password,
            onValueChange = {
                password = it
                error = null
            },
            label = "Password",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (!loading && email.isNotBlank() && password.isNotBlank()) {
                        scope.launch {
                            loading = true
                            onSubmit(email, password, { error = it }, { loading = it })
                            loading = false
                        }
                    }
                },
            ),
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = error!!,
                style = MaterialTheme.typography.bodyMedium,
                color = ErrorRed,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    loading = true
                    onSubmit(email, password, { error = it }, { loading = it })
                    loading = false
                }
            },
            enabled = !loading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            colors = CarbonButtonDefaults.primaryButtonColors(),
            elevation = CarbonButtonDefaults.primaryButtonElevation(),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(submitLabel)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onAlternateAction) {
            Text(
                text = "$alternatePrompt $alternateActionLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
            )
        }
    }
}

@Composable
private fun CarbonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
    )
}
