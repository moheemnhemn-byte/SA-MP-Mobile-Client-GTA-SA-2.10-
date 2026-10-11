
package com.russia.launcher.async.dto.response

import android.app.Activity
import android.content.Context
import android.util.Log
import com.russia.launcher.NetworkService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

interface MonitoringDataLoaderListener {
    fun monitoringDataLoadedSuccess()
}

data class MonitoringData(
    var news: List<News> = emptyList(),
    var servers: List<Servers> = emptyList()
) : Serializable

object ServersList {
    const val file = "servers.dat"

    var news: List<News> = emptyList()
    var servers: List<Servers> = emptyList()

    fun load(
        activity: Activity,
        net: NetworkService,
        listener: MonitoringDataLoaderListener
    ) {
        try {
            val call = net.monitoringData

            call.enqueue(object : Callback<MonitoringData?> {
                override fun onResponse(
                    call: Call<MonitoringData?>,
                    response: Response<MonitoringData?>
                ) {
                    val data = response.body()

                    if (response.isSuccessful && data != null) {
                        news = data.news
                        servers = data.servers
                        saveToFile(activity, data)
                        notifyLoaded(listener)
                    } else {
                        loadFromFile(activity, listener)
                    }
                }

                override fun onFailure(
                    call: Call<MonitoringData?>,
                    t: Throwable
                ) {
                    Log.e("ServersList", "Monitoring request failed", t)
                    loadFromFile(activity, listener)
                }
            })
        } catch (e: Exception) {
            Log.e("ServersList", "Could not start monitoring request", e)
            loadFromFile(activity, listener)
        }
    }

    fun saveToFile(context: Context, data: MonitoringData) {
        try {
            context.openFileOutput(file, Context.MODE_PRIVATE).use { output ->
                ObjectOutputStream(output).use { stream ->
                    stream.writeObject(data)
                }
            }
        } catch (e: Exception) {
            Log.e("ServersList", "Could not save monitoring cache", e)
        }
    }

    fun loadFromFile(
        context: Context,
        listener: MonitoringDataLoaderListener
    ) {
        try {
            context.openFileInput(file).use { input ->
                ObjectInputStream(input).use { stream ->
                    val data = stream.readObject() as MonitoringData
                    news = data.news
                    servers = data.servers
                }
            }
        } catch (e: Exception) {
            // First launch or missing/corrupt cache: continue with empty data.
            news = emptyList()
            servers = emptyList()
            Log.w("ServersList", "No usable cached monitoring data", e)
        } finally {
            // Always release the splash screen loading gate.
            notifyLoaded(listener)
        }
    }

    private fun notifyLoaded(listener: MonitoringDataLoaderListener) {
        try {
            listener.monitoringDataLoadedSuccess()
        } catch (e: Exception) {
            Log.e("ServersList", "Loading callback failed", e)
        }
    }
}
