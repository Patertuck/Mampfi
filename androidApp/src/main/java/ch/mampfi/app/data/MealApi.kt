package ch.mampfi.app.data

import okhttp3.MultipartBody
import retrofit2.http.*

interface MealApi {
    @GET("api/mahlzeiten") suspend fun all(): List<Mahlzeit>
    @POST("api/mahlzeiten") suspend fun create(@Body meal: Mahlzeit): Mahlzeit
    @PUT("api/mahlzeiten/{id}") suspend fun update(@Path("id") id: String, @Body meal: Mahlzeit): Mahlzeit
    @DELETE("api/mahlzeiten/{id}") suspend fun delete(@Path("id") id: String)
    @DELETE("api/mahlzeiten/{id}/termine/{datum}") suspend fun removeDate(@Path("id") id: String, @Path("datum") date: String)
    @Multipart @POST("api/bilder") suspend fun upload(@Part image: MultipartBody.Part): UploadResult
}
@kotlinx.serialization.Serializable data class UploadResult(val url: String)
