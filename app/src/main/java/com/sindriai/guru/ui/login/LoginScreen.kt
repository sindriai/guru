package com.sindriai.guru.ui.login

import android.content.Intent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.sindriai.guru.ui.main.MainActivity

@Composable
fun OtpLoginScreen(
    viewModel: LoginViewModel
) {
    val phoneNumber by viewModel.phoneNumber.observeAsState("")
    val isLoading by viewModel.isLoading.observeAsState(false)
    val message by viewModel.message.observeAsState("")
    val currentStep by viewModel.currentStep.observeAsState(LoginStep.PHONE)
    val navigateToMain by viewModel.navigateToMain.observeAsState(false)
    val context  = LocalContext.current

    LaunchedEffect(navigateToMain) {
        if (navigateToMain) {
            // 👉 Navigate to MainActivity
            // Example (if using Activity):
            context.startActivity(Intent(context, MainActivity::class.java))
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Mobile OTP Login",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (currentStep) {
                LoginStep.PHONE -> {
                    PhoneStep(
                        phoneNumber = phoneNumber,
                        isLoading = isLoading,
                        onPhoneChange = viewModel::onPhoneNumberChange,
                        onGetOtpClick = { viewModel.sendOtp() }
                    )
                }

                LoginStep.OTP -> {
                    OtpStep(
                        phoneNumber = phoneNumber,
                        isLoading = isLoading,
                        onOtpComplete = { finalOtp ->
                            viewModel.onOtpChange(finalOtp)
                            viewModel.verifyOtp()
                        }
                    )
                }

                LoginStep.PROFILE -> {
                    ProfileStep(
                        phoneNumber = phoneNumber,
                        onContinue = { name, dob, email, gender, city ->
                            viewModel.submitProfile(
                                token = viewModel.getSavedToken() ?: "",
                                name = name,
                                dob = dob,
                                email = email,
                                gender = gender,
                                city = city
                            )
                        }
                    )
                }
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(20.dp))
                CircularProgressIndicator()
            }

            if (message.isNotBlank()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PhoneStep(
    phoneNumber: String,
    isLoading: Boolean,
    onPhoneChange: (String) -> Unit,
    onGetOtpClick: () -> Unit
) {
    OutlinedTextField(
        value = phoneNumber,
        onValueChange = { input ->
            onPhoneChange(input.filter { it.isDigit() }.take(10))
        },
        label = { Text("Mobile Number") },
        placeholder = { Text("Enter mobile number") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(16.dp))

    Button(
        onClick = onGetOtpClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Get OTP")
    }
}

@Composable
private fun OtpStep(
    phoneNumber: String,
    isLoading: Boolean,
    onOtpComplete: (String) -> Unit
) {
    Text(
        text = "Enter OTP sent to $phoneNumber",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(20.dp))

    OtpInputBoxes(
        isEnabled = !isLoading,
        onOtpComplete = onOtpComplete
    )
}

@Composable
private fun OtpInputBoxes(
    isEnabled: Boolean,
    onOtpComplete: (String) -> Unit
) {
    val otpValues = remember { mutableStateListOf("", "", "", "", "", "") }
    val focusRequesters = remember {
        List(6) { FocusRequester() }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        repeat(6) { index ->
            OutlinedTextField(
                value = otpValues[index],
                onValueChange = { value ->
                    if (!isEnabled) return@OutlinedTextField

                    val filtered = value.filter { it.isDigit() }

                    if (filtered.length <= 1) {
                        otpValues[index] = filtered

                        if (filtered.isNotEmpty() && index < 5) {
                            focusRequesters[index + 1].requestFocus()
                        }

                        val finalOtp = otpValues.joinToString("")
                        if (finalOtp.length == 6 && otpValues.all { it.length == 1 }) {
                            onOtpComplete(finalOtp)
                        }
                    }
                },
                modifier = Modifier
                    .width(52.dp)
                    .focusRequester(focusRequesters[index]),
                singleLine = true,
                enabled = isEnabled,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequesters.first().requestFocus()
    }
}

@Composable
private fun ProfileStep(
    phoneNumber: String,
    onContinue: (
        name: String,
        dob: String,
        email: String,
        gender: String,
        city: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var showMoreDetails by remember { mutableStateOf(false) }

    var dob by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }
    var city by remember { mutableStateOf("") }

    Text(
        text = "Enter your name",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Mobile: $phoneNumber",
        style = MaterialTheme.typography.bodyMedium
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name") },
        placeholder = { Text("Enter your name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(12.dp))

    TextButton(
        onClick = { showMoreDetails = !showMoreDetails }
    ) {
        Text(
            if (showMoreDetails) "Hide extra details"
            else "Want to share more details?"
        )
    }

    if (showMoreDetails) {
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = dob,
            onValueChange = { dob = it },
            label = { Text("Date of Birth") },
            placeholder = { Text("DD/MM/YYYY") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            placeholder = { Text("Enter email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Gender",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = gender == "male",
                    onClick = { gender = "male" }
                )
                Text("Male")
            }

            Spacer(modifier = Modifier.width(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = gender == "female",
                    onClick = { gender = "female" }
                )
                Text("Female")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            label = { Text("City") },
            placeholder = { Text("Enter city") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = {
            if (name.isBlank()) {
                onContinue("", dob, email, gender, city)
            } else {
                onContinue(name.trim(), dob.trim(), email.trim(), gender, city.trim())
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Continue")
    }
}