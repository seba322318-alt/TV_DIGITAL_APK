package com.tvdigital.app.data

import com.tvdigital.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

data class LoginRequest(val username:String,val password:String,val deviceKey:String,val deviceName:String="Android")
data class UserDto(val id:String,val name:String,val username:String,val role:String,val status:String,val expiresAt:String?,val maxDevices:Int=1,val maxConcurrent:Int=1)
data class LoginResponse(val token:String,val user:UserDto,val sessionId:String?=null)
data class ContentDto(val id:String,val type:String,val title:String,val description:String?,val posterUrl:String?,val logoUrl:String?,val streamUrl:String,val year:Int?=null)
data class PingResponse(val ok:Boolean,val serverTime:String?=null)

interface TvApi {
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): LoginResponse
    @POST("auth/ping") suspend fun ping(@Header("Authorization") bearer:String): PingResponse
    @POST("auth/logout") suspend fun logout(@Header("Authorization") bearer:String)
    @GET("content") suspend fun content(@Header("Authorization") bearer:String,@Query("type") type:String?=null): List<ContentDto>
}

object ApiProvider {
    val api: TvApi by lazy {
        val logging=HttpLoggingInterceptor().apply{level=HttpLoggingInterceptor.Level.BASIC}
        Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL)
            .client(OkHttpClient.Builder().addInterceptor(logging).build())
            .addConverterFactory(GsonConverterFactory.create()).build().create(TvApi::class.java)
    }
}
