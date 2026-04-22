package com.example.dotsandboxes



import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var state by remember { mutableStateOf(newGameState()) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(state.currentPlayer) {
                if (state.currentPlayer == Player.BOT && !state.gameOver) {
                    kotlinx.coroutines.delay(300)
                    val s = state.copy()
                    botMove(s, scope)
                    state = s
                }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    "You: ${state.humanScore}   Bot: ${state.botScore}",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    if (state.gameOver)
                        if (state.humanScore > state.botScore) "You Win 🎉"
                        else "Bot Wins 🤖"
                    else
                        "Turn: ${state.currentPlayer}",
                    style = MaterialTheme.typography.titleMedium
                )


                GameBoard(state) { line ->
                    val s = state.copy()
                    playMove(line, s, scope)
                    state = s
                }


                Button(onClick = { state = newGameState() }) {
                    Text("New Game")
                }
            }
        }
    }
}
