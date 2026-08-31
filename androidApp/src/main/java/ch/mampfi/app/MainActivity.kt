package ch.mampfi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import ch.mampfi.app.data.*
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent {
        val db = Room.databaseBuilder(applicationContext, MealDatabase::class.java, "mampfi.db").build()
        val api = Retrofit.Builder().baseUrl(ApiConfig.BASE_URL).addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType())).build().create(MealApi::class.java)
        val vm: MealViewModel = viewModel(factory = MealViewModelFactory(MealRepository(api, db.meals())))
        MampfiApp(vm)
    } }
}
