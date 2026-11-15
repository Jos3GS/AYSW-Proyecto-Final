package com.itm.servicedesk.core.di

import com.itm.servicedesk.core.config.resolveApiBaseUrl
import com.itm.servicedesk.core.network.DefaultApiJson
import com.itm.servicedesk.core.network.createHttpClient
import com.itm.servicedesk.core.network.createPlatformHttpClientEngine
import com.itm.servicedesk.core.storage.SessionEventBus
import com.itm.servicedesk.data.mapper.ErrorMapper
import com.itm.servicedesk.data.mapper.HealthMapper
import com.itm.servicedesk.data.remote.KtorSystemHealthRemoteDataSource
import com.itm.servicedesk.data.remote.SystemHealthRemoteDataSource
import com.itm.servicedesk.data.repository.SystemHealthRepositoryImpl
import com.itm.servicedesk.domain.repository.SystemHealthRepository
import com.itm.servicedesk.viewmodel.StartupUiMapper
import com.itm.servicedesk.viewmodel.StartupViewModel
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

expect fun platformModule(): Module

val networkModule: Module =
    module {
        single { DefaultApiJson }
        single { SessionEventBus() }
        single<HttpClientEngine> { createPlatformHttpClientEngine() }
        single {
            createHttpClient(
                engine = get(),
                baseUrl = resolveApiBaseUrl(),
                sessionStorage = get(),
                sessionEventBus = get(),
                json = get(),
            )
        }
    }

val repositoryModule: Module =
    module {
        single { HealthMapper() }
        single { ErrorMapper(json = get()) }
        single<SystemHealthRemoteDataSource> { KtorSystemHealthRemoteDataSource(httpClient = get()) }
        single<SystemHealthRepository> {
            SystemHealthRepositoryImpl(
                remoteDataSource = get(),
                healthMapper = get(),
                errorMapper = get(),
            )
        }
    }

val viewModelModule: Module =
    module {
        factory { StartupUiMapper() }
        viewModel { StartupViewModel(repository = get(), uiMapper = get()) }
    }

val appModules: List<Module>
    get() =
        listOf(
            platformModule(),
            networkModule,
            repositoryModule,
            viewModelModule,
        )
