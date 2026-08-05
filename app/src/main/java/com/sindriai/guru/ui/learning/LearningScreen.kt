package com.sindriai.guru.ui.learning

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sindriai.guru.ui.learning.helpers.rememberImagePickerManager
import com.sindriai.guru.ui.learning.nav.LearningDrawerContent
import com.sindriai.guru.ui.learning.nav.SelectedPath
import com.sindriai.guru.ui.learning.nav.ncertClass10Subjects
import com.sindriai.guru.ui.learning.ui_components.AttachOptionsBottomSheet
import com.sindriai.guru.ui.learning.ui_components.DraggableTitleBar
import com.sindriai.guru.ui.learning.ui_components.PromptBar
import com.sindriai.guru.ui.learning.ui_components.TopScreen
import com.sindriai.guru.ui.learning.ui_components.topic_screen.TopicScreen
import com.sindriai.guru.ui.main.MainViewModel
import kotlinx.coroutines.launch

sealed class AttachmentUiState {
    data object None : AttachmentUiState()
    data class StudyMaterial(val assetFileName: String) : AttachmentUiState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningScreen(
    context: Context,
    learningViewModel: LearningViewModel,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onOpenCourseStore: () -> Unit
) {
    val attachedImageUri by learningViewModel.attachedImageUri.collectAsStateWithLifecycle()
    val chatHistory by learningViewModel.chatHistory.collectAsStateWithLifecycle()
    val streamingGuruText by learningViewModel.streamingGuruText.collectAsStateWithLifecycle()
    val currentGemmaState by learningViewModel.gemmaState.collectAsStateWithLifecycle()
    val isTtsSpeaking by learningViewModel.isTtsSpeaking.collectAsStateWithLifecycle()
    val isMicOn by learningViewModel.isListening.collectAsStateWithLifecycle()

    var isFullScreen by remember { mutableStateOf(false) }
    var isAttachSheetVisible by remember { mutableStateOf(false) }
    var showPromptBar by remember { mutableStateOf(false) }

    val promptSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )

    val screenHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()

    var dividerPositionDp by remember {
        mutableStateOf(screenHeightDp * 0.35f)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val subjects = remember { ncertClass10Subjects(context) }

    var selectedTopic by remember {
        mutableStateOf(
            SelectedPath(
                course = "Science",
                chapter = "Life Processes",
                topic = "Introduction",
                id = "P001C0050101"
            )
        )
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        learningViewModel.initializeHeavyComponents()
        learningViewModel.setSelectedTopicId(selectedTopic.id)
    }

    val scope = rememberCoroutineScope()

    val imagePickerManager = rememberImagePickerManager(
        context = context,
        onImagePicked = { uri ->
            learningViewModel.setAttachedImage(uri)
        }
    )

    val attachmentState = if (selectedTopic.id.isNotBlank()) {
        AttachmentUiState.StudyMaterial(
            assetFileName = "course_materials/${selectedTopic.id.take(8)}/${selectedTopic.id}/${selectedTopic.id}.pdf"
        )
    } else {
        AttachmentUiState.None
    }

    ModalBottomSheet(
        onDismissRequest = { },
        sheetState = promptSheetState,
        dragHandle = null,
        sheetGesturesEnabled = false,
        containerColor = Color.White,
        tonalElevation = 0.dp,
        scrimColor = Color.Transparent,
        modifier = modifier.fillMaxSize()
    ) {
        BackHandler { }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                LearningDrawerContent(
                    context = context,
                    drawerState = drawerState,
                    userName = "Guest",
                    isLoggedIn = false,
                    subjects = subjects,
                    selected = selectedTopic,
                    onTopicSelected = { newSelected ->
                        Log.d("TopicID","New topic selected on list"+newSelected)
                        scope.launch {
                            selectedTopic = newSelected
                            learningViewModel.setSelectedTopicId(newSelected.id)
                            promptSheetState.show()
                            drawerState.close()
                        }
                    },
                    onCloseClick = { scope.launch { drawerState.close() } },
                    onOpenCourseStore = onOpenCourseStore
                )
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {

                    if (isFullScreen) {
                        TopScreen(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(dividerPositionDp.dp)
                                .clipToBounds()
                                .background(Color(0x0A000000))
                                .padding(PaddingValues(10.dp)),
                            state = attachmentState,
                            onClear = { }
                        )
                    }

                    DraggableTitleBar(
                        selectedTopic = selectedTopic,
                        dividerPositionDp = dividerPositionDp,
                        screenHeightDp = screenHeightDp,
                        onDividerPositionChange = { newPos ->
                            dividerPositionDp = newPos
                        },
                        onMenuClick = {
                            scope.launch { drawerState.open() }
                        },
                        onShowNotesClick = { boolean ->
                            isFullScreen = boolean
                            Log.d("CLICK","boolean = "+boolean)
                        }
                    )

                    TopicScreen(
                        chatHistory = chatHistory,
                        streamingGuruText = streamingGuruText,
                        topicId = selectedTopic.id,
                        currentGemmaState = currentGemmaState,
                        mainViewModel = mainViewModel,
                        showPromptBar = { boolean ->
                            showPromptBar = boolean
                        },
                        onModelReady = {
                            learningViewModel.initializeGemmaEngine()
                        },
                        onStartLearning = { selectedTopic.id.let { learningViewModel.startTopicWarmup(it) } }
                    )

                }

                if(showPromptBar) {

                    PromptBar(
                        context = context,
                        currentGemmaState = currentGemmaState,
                        isTtsSpeaking = isTtsSpeaking,
                        isMicOn = isMicOn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 6.dp, vertical = 0.dp),
                        onCloseClick = { scope.launch { promptSheetState.hide() } },
                        onStylusClick = { },
                        onWhiteboardGenerated = { uri ->
                            learningViewModel.setAttachedImage(uri)
                        },
                        onFullScreenClick = { fullScreen ->
                            //isFullScreen = fullScreen
                        },
                        onAttachClick = {
                            isAttachSheetVisible = true
                        },
                        onMicClick = { micOn ->
                            if (micOn) {
                                learningViewModel.startRecordingForWhisper()
                            } else {
                                learningViewModel.stopRecordingForWhisper()
                            }
                        },
                        onSendClick = { inputText ->
                            learningViewModel.startGemmaInference(inputText)
                        },
                        onClickStopInference = { learningViewModel.stopGemmaInference() },
                        attachedImageUri = attachedImageUri,
                        onAttachedImageRemove = {
                            learningViewModel.clearAttachedImage()
                        }
                    )
                }
            }
        }
    }

    if (isAttachSheetVisible) {
        AttachOptionsBottomSheet(
            onDismiss = { isAttachSheetVisible = false },
            onPdfClick = {
                isAttachSheetVisible = false
            },
            onImageClick = {
                isAttachSheetVisible = false
                imagePickerManager.pickFromGallery()
            },
            onCameraClick = {
                isAttachSheetVisible = false
                imagePickerManager.takeFromCamera()
            }
        )
    }
}