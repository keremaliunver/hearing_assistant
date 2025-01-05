import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.Switch
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onStartRealtime: () -> Unit,
    onStopRealtime: () -> Unit,
    onStartRecorded: () -> Unit,
    onStopRecorded: () -> Unit,
    transcribedText: String? = null
) {
    var isRealtime by remember { mutableStateOf(true) }
    var isListenPushed by remember { mutableStateOf<Boolean?>(null) }

    val shownText = transcribedText ?: "Hello Androidx!"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hearing Assistant by Rex Team") },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isRealtime) "Real Time" else "Recorded",
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
                    icon = Icons.Filled.Close,
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

/**
 * A circular button with an icon, colored for a “pushed” effect.
 * @param isPushed If true, darken the button and slightly raise elevation.
 * @param iconColors The button color set (normal/pushed).
 * @param icon The ImageVector resource for the icon (e.g., Icons.Filled.PlayArrow).
 * @param onClick Callback for button clicks.
 */
@Composable
fun CircularIconButton(
    isPushed: Boolean,
    iconColors: ButtonColorSet,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape
) {
    val backgroundColor = if (isPushed) iconColors.pushedColor else iconColors.normalColor
    val contentColor = if (isPushed) iconColors.pushedContent else iconColors.normalContent
    val elevation = if (isPushed) ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
    else ButtonDefaults.buttonElevation(defaultElevation = 2.dp)

    Button(
        onClick = onClick,
        modifier = modifier.size(80.dp), // You can adjust size as needed
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor, contentColor = contentColor),
        elevation = elevation
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
    }
}

/**
 * Data class holding normal vs pushed colors for the circular buttons.
 */
data class ButtonColorSet(
    val normalColor: Color,
    val pushedColor: Color,
    val normalContent: Color,
    val pushedContent: Color
)

/**
 * Example color sets for Listen (green) vs. Stop (yellow).
 */
val ListenButtonColors = ButtonColorSet(
    normalColor = Color(0xFF4CAF50),     // Green 500
    pushedColor = Color(0xFF388E3C),     // Green 700
    normalContent = Color.White,
    pushedContent = Color.White
)

val StopButtonColors = ButtonColorSet(
    normalColor = Color(0xFFFFC107),     // Yellow 500
    pushedColor = Color(0xFFFFB300),     // Yellow 700
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
            transcribedText = "This is a text message"
        )
    }
}
