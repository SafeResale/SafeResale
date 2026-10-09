package com.saferesale.app.core.di

import android.content.Context
import com.saferesale.app.data.apps.InstalledAppsRepository
import com.saferesale.app.data.audio.AudioInfoRepository
import com.saferesale.app.data.battery.BatteryInfoRepository
import com.saferesale.app.data.benchmark.BenchmarkRepository
import com.saferesale.app.data.camera.CameraInfoRepository
import com.saferesale.app.data.cpu.CpuInfoRepository
import com.saferesale.app.data.device.DeviceInfoRepository
import com.saferesale.app.data.gps.GpsRepository
import com.saferesale.app.data.network.NetworkRepository
import com.saferesale.app.data.network.NetworkToolsRepository
import com.saferesale.app.data.ram.RamInfoRepository
import com.saferesale.app.data.security.SecurityInfoRepository
import com.saferesale.app.data.sensor.SensorRepository
import com.saferesale.app.data.storage.StorageInfoRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDeviceInfoRepository(@ApplicationContext context: Context) =
        DeviceInfoRepository(context)

    @Provides @Singleton
    fun provideCpuInfoRepository() = CpuInfoRepository()

    @Provides @Singleton
    fun provideRamInfoRepository(@ApplicationContext context: Context) =
        RamInfoRepository(context)

    @Provides @Singleton
    fun provideStorageInfoRepository(@ApplicationContext context: Context) =
        StorageInfoRepository(context)

    @Provides @Singleton
    fun provideBatteryInfoRepository(@ApplicationContext context: Context) =
        BatteryInfoRepository(context)

    @Provides @Singleton
    fun provideSensorRepository(@ApplicationContext context: Context) =
        SensorRepository(context)

    @Provides @Singleton
    fun provideNetworkRepository(@ApplicationContext context: Context) =
        NetworkRepository(context)

    @Provides @Singleton
    fun provideNetworkToolsRepository() = NetworkToolsRepository()

    @Provides @Singleton
    fun provideCameraInfoRepository(@ApplicationContext context: Context) =
        CameraInfoRepository(context)

    @Provides @Singleton
    fun provideAudioInfoRepository(@ApplicationContext context: Context) =
        AudioInfoRepository(context)

    @Provides @Singleton
    fun provideSecurityInfoRepository(@ApplicationContext context: Context) =
        SecurityInfoRepository(context)

    @Provides @Singleton
    fun provideInstalledAppsRepository(@ApplicationContext context: Context) =
        InstalledAppsRepository(context)

    @Provides @Singleton
    fun provideGpsRepository(@ApplicationContext context: Context) =
        GpsRepository(context)

    @Provides @Singleton
    fun provideBenchmarkRepository() = BenchmarkRepository()
}
