package com.example.data.remote

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class EspnHeadshotAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): EspnHeadshotRef? {
        return when (reader.peek()) {
            JsonReader.Token.STRING -> EspnHeadshotRef(reader.nextString())
            JsonReader.Token.BEGIN_OBJECT -> {
                var href: String? = null
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() == "href" && reader.peek() == JsonReader.Token.STRING) {
                        href = reader.nextString()
                    } else {
                        reader.skipValue()
                    }
                }
                reader.endObject()
                EspnHeadshotRef(href)
            }
            JsonReader.Token.NULL -> {
                reader.nextNull<Unit>()
                null
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: EspnHeadshotRef?) {
        writer.value(value?.href)
    }
}

object NetworkClient {

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(EspnHeadshotAdapter())
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    val espnApiService: EspnApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://site.api.espn.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(EspnApiService::class.java)
    }

    val oddsApiService: OddsApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.the-odds-api.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OddsApiService::class.java)
    }
}
