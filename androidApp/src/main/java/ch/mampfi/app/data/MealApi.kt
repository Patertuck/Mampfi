package ch.mampfi.app.data

import okhttp3.MultipartBody
import retrofit2.http.*

interface MealApi {
    @GET("api/mahlzeiten") suspend fun all(): List<Mahlzeit>
    @POST("api/mahlzeiten") suspend fun create(@Body meal: Mahlzeit): Mahlzeit
    @PUT("api/mahlzeiten/{id}") suspend fun update(@Path("id") id: String, @Body meal: Mahlzeit): Mahlzeit
    @POST("api/mahlzeiten/{id}/eintraege") suspend fun createEntry(@Path("id") id: String, @Body entry: MahlzeitEintrag): MahlzeitEintrag
    @PUT("api/mahlzeiten/{id}/eintraege/{entryId}") suspend fun updateEntry(@Path("id") id: String, @Path("entryId") entryId: String, @Body entry: MahlzeitEintrag): MahlzeitEintrag
    @DELETE("api/mahlzeiten/{id}/eintraege/{entryId}") suspend fun deleteEntry(@Path("id") id: String, @Path("entryId") entryId: String)
    @Multipart @POST("api/mahlzeiten/{id}/eintraege/{entryId}/bilder") suspend fun upload(
        @Path("id") id: String, @Path("entryId") entryId: String, @Part image: MultipartBody.Part,
    ): MahlzeitBild
}
