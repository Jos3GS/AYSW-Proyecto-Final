package com.itm.servicedesk.core.di

import com.itm.servicedesk.core.config.resolveApiBaseUrl
import com.itm.servicedesk.core.network.DefaultApiJson
import com.itm.servicedesk.core.network.createHttpClient
import com.itm.servicedesk.core.network.createPlatformHttpClientEngine
import com.itm.servicedesk.core.storage.SessionEventBus
import com.itm.servicedesk.data.mapper.AttentionMapper
import com.itm.servicedesk.data.mapper.AttentionUiMapper
import com.itm.servicedesk.data.mapper.AuthMapper
import com.itm.servicedesk.data.mapper.ErrorMapper
import com.itm.servicedesk.data.mapper.HealthMapper
import com.itm.servicedesk.data.mapper.ReportMapper
import com.itm.servicedesk.data.mapper.ReportUiMapper
import com.itm.servicedesk.data.mapper.RequestMapper
import com.itm.servicedesk.data.mapper.RequestUiMapper
import com.itm.servicedesk.data.mapper.StaffMapper
import com.itm.servicedesk.data.mapper.StaffUiMapper
import com.itm.servicedesk.data.remote.AttentionRemoteDataSource
import com.itm.servicedesk.data.remote.AuthRemoteDataSource
import com.itm.servicedesk.data.remote.KtorAttentionRemoteDataSource
import com.itm.servicedesk.data.remote.KtorAuthRemoteDataSource
import com.itm.servicedesk.data.remote.KtorReportRemoteDataSource
import com.itm.servicedesk.data.remote.KtorRequestRemoteDataSource
import com.itm.servicedesk.data.remote.KtorStaffRemoteDataSource
import com.itm.servicedesk.data.remote.KtorSystemHealthRemoteDataSource
import com.itm.servicedesk.data.remote.ReportRemoteDataSource
import com.itm.servicedesk.data.remote.RequestRemoteDataSource
import com.itm.servicedesk.data.remote.StaffRemoteDataSource
import com.itm.servicedesk.data.remote.SystemHealthRemoteDataSource
import com.itm.servicedesk.data.repository.AttentionRepositoryImpl
import com.itm.servicedesk.data.repository.AuthRepositoryImpl
import com.itm.servicedesk.data.repository.ReportRepositoryImpl
import com.itm.servicedesk.data.repository.ServiceRequestRepositoryImpl
import com.itm.servicedesk.data.repository.StaffRepositoryImpl
import com.itm.servicedesk.data.repository.SystemHealthRepositoryImpl
import com.itm.servicedesk.domain.repository.AttentionRepository
import com.itm.servicedesk.domain.repository.AuthRepository
import com.itm.servicedesk.domain.repository.ReportRepository
import com.itm.servicedesk.domain.repository.ServiceRequestRepository
import com.itm.servicedesk.domain.repository.StaffRepository
import com.itm.servicedesk.domain.repository.SystemHealthRepository
import com.itm.servicedesk.domain.validation.AttentionFormValidator
import com.itm.servicedesk.domain.validation.ReportFilterValidator
import com.itm.servicedesk.viewmodel.CreateStaffViewModel
import com.itm.servicedesk.viewmodel.LoginViewModel
import com.itm.servicedesk.viewmodel.RegisterViewModel
import com.itm.servicedesk.viewmodel.SessionViewModel
import com.itm.servicedesk.viewmodel.StaffListViewModel
import com.itm.servicedesk.viewmodel.StartupUiMapper
import com.itm.servicedesk.viewmodel.StartupViewModel
import com.itm.servicedesk.viewmodel.attention.AgentInboxViewModel
import com.itm.servicedesk.viewmodel.attention.AttentionDetailViewModel
import com.itm.servicedesk.viewmodel.attention.CoordinationPendingViewModel
import com.itm.servicedesk.viewmodel.reports.CoordinationReportsViewModel
import com.itm.servicedesk.viewmodel.requests.CreateRequestViewModel
import com.itm.servicedesk.viewmodel.requests.MyRequestsViewModel
import com.itm.servicedesk.viewmodel.requests.RequestDetailViewModel
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
        single { AuthMapper() }
        single { StaffMapper() }
        single { StaffUiMapper() }
        single { RequestMapper() }
        single { RequestUiMapper() }
        single { AttentionMapper() }
        single { AttentionUiMapper() }
        single { AttentionFormValidator() }
        single { ReportMapper() }
        single { ReportUiMapper() }
        single { ReportFilterValidator() }
        single { ErrorMapper(json = get()) }
        single<SystemHealthRemoteDataSource> { KtorSystemHealthRemoteDataSource(httpClient = get()) }
        single<AuthRemoteDataSource> { KtorAuthRemoteDataSource(httpClient = get()) }
        single<StaffRemoteDataSource> { KtorStaffRemoteDataSource(httpClient = get()) }
        single<RequestRemoteDataSource> { KtorRequestRemoteDataSource(httpClient = get()) }
        single<AttentionRemoteDataSource> { KtorAttentionRemoteDataSource(httpClient = get()) }
        single<ReportRemoteDataSource> { KtorReportRemoteDataSource(httpClient = get()) }
        single<SystemHealthRepository> {
            SystemHealthRepositoryImpl(
                remoteDataSource = get(),
                healthMapper = get(),
                errorMapper = get(),
            )
        }
        single<AuthRepository> {
            AuthRepositoryImpl(
                remoteDataSource = get(),
                sessionStorage = get(),
                sessionEventBus = get(),
                authMapper = get(),
                errorMapper = get(),
            )
        }
        single<StaffRepository> {
            StaffRepositoryImpl(
                remoteDataSource = get(),
                staffMapper = get(),
                authMapper = get(),
                errorMapper = get(),
            )
        }
        single<ServiceRequestRepository> {
            ServiceRequestRepositoryImpl(
                remoteDataSource = get(),
                requestMapper = get(),
                errorMapper = get(),
            )
        }
        single<AttentionRepository> {
            AttentionRepositoryImpl(
                remoteDataSource = get(),
                attentionMapper = get(),
                errorMapper = get(),
            )
        }
        single<ReportRepository> {
            ReportRepositoryImpl(
                remoteDataSource = get(),
                reportMapper = get(),
                errorMapper = get(),
            )
        }
    }

val viewModelModule: Module =
    module {
        factory { StartupUiMapper() }
        viewModel { StartupViewModel(repository = get(), uiMapper = get()) }
        viewModel {
            SessionViewModel(
                authRepository = get(),
                sessionStorage = get(),
                sessionEventBus = get(),
            )
        }
        viewModel { LoginViewModel(authRepository = get()) }
        viewModel { RegisterViewModel(authRepository = get()) }
        viewModel { StaffListViewModel(staffRepository = get(), staffUiMapper = get()) }
        viewModel { CreateStaffViewModel(staffRepository = get(), staffUiMapper = get()) }
        viewModel { MyRequestsViewModel(repository = get(), uiMapper = get()) }
        viewModel { CreateRequestViewModel(repository = get()) }
        viewModel { RequestDetailViewModel(repository = get(), uiMapper = get()) }
        viewModel { CoordinationPendingViewModel(attentionRepository = get()) }
        viewModel { AgentInboxViewModel(attentionRepository = get()) }
        viewModel {
            AttentionDetailViewModel(
                attentionRepository = get(),
                staffRepository = get(),
                validator = get(),
            )
        }
        viewModel {
            CoordinationReportsViewModel(
                reportRepository = get(),
                staffRepository = get(),
                validator = get(),
                uiMapper = get(),
            )
        }
    }

val appModules: List<Module>
    get() =
        listOf(
            platformModule(),
            networkModule,
            repositoryModule,
            viewModelModule,
        )
