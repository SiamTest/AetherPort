package com.forgeport.android.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.content.res.Configuration

@Preview(name = "Compact phone", widthDp = 320, heightDp = 640, showBackground = true)
@Preview(name = "Large text", widthDp = 360, heightDp = 800, fontScale = 2f, showBackground = true)
@Preview(name = "Tablet dark", widthDp = 840, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Landscape", widthDp = 640, heightDp = 360, showBackground = true)
@Composable
private fun ExpressiveComponentsPreview() {
    AetherPortTheme {
        Surface {
            AdaptiveContent {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Your workspace", style = MaterialTheme.typography.headlineLarge)
                    Text("Create. Connect. Explore.", style = MaterialTheme.typography.bodyLarge)
                    ExpressiveCard {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Publish a project", style = MaterialTheme.typography.titleLarge)
                            Text("Pick a saved repository and a project ZIP.")
                            ExpressiveTonalButton(onClick = {}) { Text("Select project ZIP") }
                        }
                    }
                    var value by remember { mutableStateOf("main") }
                    OutlinedTextField(value, { value = it }, label = { Text("Branch") }, modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExpressiveFilterChip(true, {}, { Text("Publish") })
                        ExpressiveFilterChip(false, {}, { Text("Projects") })
                    }
                    ExpressiveButton({}, Modifier.fillMaxWidth()) { Text("Publish to GitHub") }
                }
            }
        }
    }
}
