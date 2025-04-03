package com.example.thang4ngay3.api;

import com.example.thang4ngay3.model.UploadResponse;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface ApiService {
    @Multipart
    @POST("updateimages.php")
    Call<UploadResponse> uploadImage(
            @Part MultipartBody.Part image,
            @Part("description") RequestBody description
    );
}