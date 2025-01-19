import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.rexteam.hearingimpaired.ui.theme.HearingImpairedTheme

// Either import or redefine this enum here if you prefer
enum class TranscriptionMethod {
    OPENAI,
    VOSK
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onStartRealtime: () -> Unit,
    onStopRealtime: () -> Unit,
    onStartRecorded: () -> Unit,
    onStopRecorded: () -> Unit,
    transcribedText: String? = null,
    // NEW: callback for changing transcription method
    onTranscriptionMethodChanged: (TranscriptionMethod) -> Unit
) {
    var isRealtime by remember { mutableStateOf(true) }

    // NEW: a local state to track which method is selected for "Recorded"
    var selectedMethod by remember { mutableStateOf(TranscriptionMethod.OPENAI) }

    var isListenPushed by remember { mutableStateOf<Boolean?>(null) }
    val shownText = transcribedText ?: "Çeviri için bir ses kaydı yapın"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text="İşitme Desteği",
                    fontSize = TextUnit(16f, TextUnitType.Sp),
                ) },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isRealtime) "Gerçek zamanlı" else "Kayıtlı",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = isRealtime,
                            onCheckedChange = { isRealtime = it }
                        )
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = Color.Blue,
                    titleContentColor = Color.Magenta
                ),
            )
        },
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Cyan
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Large surface displaying transcription
            Surface(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(370.dp)
                    .height(550.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(3.dp, Color.Black),
                color = Color.Green
            ) {
                Text(
                    text = shownText,
                    modifier = Modifier
                        .padding(all = 8.dp)
                        .fillMaxWidth(),
                    fontSize = TextUnit(24f, TextUnitType.Sp)
                )
            }

            // NEW: Show this additional row of switches ONLY in Recorded mode:
            if (!isRealtime) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Online/Offline:",
                        modifier = Modifier.padding(end = 8.dp),
                        color = Color.Black
                    )
                    // You can use a Switch or two RadioButtons.
                    // Below example uses a Switch that toggles between OpenAI <-> Vosk
                    // Alternatively, you might prefer two separate radio buttons.
                    val isOpenAi = (selectedMethod == TranscriptionMethod.OPENAI)
                    Switch(
                        checked = isOpenAi,
                        onCheckedChange = { checked ->
                            selectedMethod = if (checked) TranscriptionMethod.OPENAI else TranscriptionMethod.VOSK
                            // Notify Activity
                            onTranscriptionMethodChanged(selectedMethod)
                        }
                    )
                    Text(
                        text = if (isOpenAi) "Online-OpenAI" else "Offline-Vosk",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // Listen / Stop row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Listen (Green, Play Icon)
                CircularIconButton(
                    isPushed = (isListenPushed == true),
                    iconColors = ListenButtonColors,
                    icon = Icons.Filled.PlayArrow,
                    onClick = {
                        isListenPushed = true
                        Log.d("MainScreen", "Listen button clicked")
                        if (isRealtime) onStartRealtime() else onStartRecorded()
                    }
                )
                // Stop (Yellow, Stop Icon)
                CircularIconButton(
                    isPushed = (isListenPushed == false),
                    iconColors = StopButtonColors,
                    icon = Icons.Filled.Menu,
                    onClick = {
                        isListenPushed = false
                        Log.d("MainScreen", "Stop button clicked")
                        if (isRealtime) onStopRealtime() else onStopRecorded()
                    }
                )
            }
        }
    }
}

@Composable
fun CircularIconButton(
    isPushed: Boolean,
    iconColors: ButtonColorSet,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape
) {
    val backgroundColor =
        if (isPushed) iconColors.pushedColor else iconColors.normalColor
    val contentColor =
        if (isPushed) iconColors.pushedContent else iconColors.normalContent

    val elevation = if (isPushed)
        ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
    else
        ButtonDefaults.buttonElevation(defaultElevation = 2.dp)

    Button(
        onClick = onClick,
        modifier = modifier.size(80.dp),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        elevation = elevation
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
    }
}

data class ButtonColorSet(
    val normalColor: Color,
    val pushedColor: Color,
    val normalContent: Color,
    val pushedContent: Color
)

val ListenButtonColors = ButtonColorSet(
    normalColor = Color(0xFF4CAF50), // Green 500
    pushedColor = Color(0xFF388E3C), // Green 700
    normalContent = Color.White,
    pushedContent = Color.White
)

val StopButtonColors = ButtonColorSet(
    normalColor = Color(0xFFFFC107), // Yellow 500
    pushedColor = Color(0xFFFFB300), // Yellow 700
    normalContent = Color.Black,
    pushedContent = Color.White
)

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    HearingImpairedTheme {
        MainScreen(
            onStartRealtime = {},
            onStopRealtime = {},
            onStartRecorded = {},
            onStopRecorded = {},
            transcribedText = "Bir ses kaydı başlatın...",
            onTranscriptionMethodChanged = {}
        )
    }
}