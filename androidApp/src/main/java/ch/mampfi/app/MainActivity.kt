package ch.mampfi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import ch.mampfi.app.data.*
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

class MainActivity : ComponentActivity() {
    private val db by lazy { Room.databaseBuilder(applicationContext, MealDatabase::class.java, "mampfi.db").build() }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent {
        var baseUrl by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) { baseUrl = ServerEndpointResolver.resolve() }
        if (baseUrl == null) {
            MampfiTheme { Box(Modifier.fillMaxSize(), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("Server wird verbunden …", color = MaterialTheme.colorScheme.onBackground) } } }
        } else {
            val api = remember(baseUrl) { Retrofit.Builder().baseUrl(baseUrl!!).addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType())).build().create(MealApi::class.java) }
            val vm: MealViewModel = viewModel(key = baseUrl, factory = MealViewModelFactory(MealRepository(api, db.meals(), baseUrl!!)))
            MampfiApp(vm)
        }
    } }
}
