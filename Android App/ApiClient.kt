package com.example.nfccheckin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object ApiClient {

    // Change this to your server's IP (e.g. hotspot host: 192.168.43.1)
    var BASE_URL = "http://192.168.43.1:5000"

    private suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        return withContext(Dispatchers.IO) {
            val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            JSONObject(BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).readText())
        }
    }

    private suspend fun requestList(path: String): JSONArray {
        return withContext(Dispatchers.IO) {
            val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            JSONArray(BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).readText())
        }
    }

    suspend fun checkin(nfcId: String): JSONObject {
        return request("POST", "/checkin", JSONObject().put("nfc_id", nfcId))
    }

    suspend fun getGuests(): JSONArray = requestList("/guests")

    suspend fun addGuest(g: Guest): JSONObject {
        val body = JSONObject()
            .put("nfc_id", g.nfc_id)
            .put("ho_ten", g.ho_ten)
            .put("loi_chao", g.loi_chao)
            .put("ghi_chu", g.ghi_chu)
        return request("POST", "/guests", body)
    }

    suspend fun updateGuest(g: Guest): JSONObject {
        val body = JSONObject()
            .put("ho_ten", g.ho_ten)
            .put("loi_chao", g.loi_chao)
            .put("ghi_chu", g.ghi_chu)
        return request("PUT", "/guests/${g.nfc_id}", body)
    }

    suspend fun deleteGuest(nfcId: String): JSONObject {
        return request("DELETE", "/guests/$nfcId")
    }
}
