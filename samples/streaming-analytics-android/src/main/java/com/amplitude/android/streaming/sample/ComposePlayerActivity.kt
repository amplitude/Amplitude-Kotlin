package com.amplitude.android.streaming.sample

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

class ComposePlayerActivity : ComponentActivity() {
    private val viewModel: ComposePlayerViewModel by injectedViewModel { composePlayerViewModel }

    private var inPip by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                if (inPip) {
                    PipContent(videoOne = viewModel.videoOne)
                } else {
                    ComposePlayerScreen(
                        videoOne = viewModel.videoOne,
                        videoTwo = viewModel.videoTwo,
                        audio = viewModel.audio,
                        onEnterPip = { enterPipIfPossible() },
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.onHostStopped(isInPictureInPictureMode, isFinishing)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            viewModel.onEnterPip()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposePlayerScreen(
    videoOne: DemoPlayer,
    videoTwo: DemoPlayer,
    audio: DemoPlayer,
    onEnterPip: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.compose_screen_title)) }) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlayerSection(
                label = stringResource(R.string.video_one_label),
                demoPlayer = videoOne,
                showSurface = true,
            )
            PlayerSection(
                label = stringResource(R.string.video_two_label),
                demoPlayer = videoTwo,
                showSurface = true,
            )
            PlayerSection(
                label = stringResource(R.string.audio_label),
                demoPlayer = audio,
                showSurface = false,
            )
            Button(onClick = onEnterPip, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.enter_pip))
            }
        }
    }
}

/** PiP is a tiny window: video 1 only, no chrome, no padding, no insets. */
@Composable
private fun PipContent(videoOne: DemoPlayer) {
    PlayerSurface(
        demoPlayer = videoOne,
        useController = false,
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    )
}
