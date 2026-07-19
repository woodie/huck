import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.netpress.humane.Humane

// Skeleton stage -- wiringCheck below only proves the humane-kotlin composite-build dependency resolves; see docs/COMMENTS.md.
fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = "Huck") {
            val wiringCheck = remember { Humane.humanSize(225_935) }

            MaterialTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("huck", style = MaterialTheme.typography.h3)
                    Text("Windows client for scan servers -- skeleton stage.")
                    Text("humane-kotlin wired in: Humane.humanSize(225_935) = \"$wiringCheck\"")
                }
            }
        }
    }
