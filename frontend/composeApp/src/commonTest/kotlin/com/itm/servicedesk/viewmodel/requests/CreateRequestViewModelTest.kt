package com.itm.servicedesk.viewmodel.requests

import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.RequestCategory
import com.itm.servicedesk.domain.validation.RequestFormValidator
import com.itm.servicedesk.testing.FakeServiceRequestRepository
import com.itm.servicedesk.testing.RequestTestFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateRequestViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `validates Tabla 2 locally before calling repository`() =
        runTest(testDispatcher) {
            val repo = FakeServiceRequestRepository()
            val viewModel = CreateRequestViewModel(repository = repo, dispatcher = testDispatcher)

            viewModel.onSubjectChange("ABC")
            viewModel.onDescriptionChange("Corta")
            viewModel.onPreviousRequestCodeChange("INVALIDO")
            viewModel.submit()
            advanceUntilIdle()

            assertTrue(repo.createCalls.isEmpty())
            val errors = viewModel.uiState.value.fieldErrors
            assertNotNull(errors[RequestFormValidator.FIELD_CATEGORY])
            assertNotNull(errors[RequestFormValidator.FIELD_SUBJECT])
            assertNotNull(errors[RequestFormValidator.FIELD_DESCRIPTION])
            assertNotNull(errors[RequestFormValidator.FIELD_PREVIOUS_REQUEST_CODE])
        }

    @Test
    fun `prefills previousRequestCode, submits valid request, and emits Created event`() =
        runTest(testDispatcher) {
            val repo =
                FakeServiceRequestRepository(
                    createHandler = { cmd ->
                        AppResult.Success(
                            RequestTestFixtures.requestDetail(
                                code = "SOL-2026-00055",
                                category = cmd.category,
                                subject = cmd.subject,
                                description = cmd.description,
                                previousRequestCode = cmd.previousRequestCode,
                            ),
                        )
                    },
                )
            val viewModel = CreateRequestViewModel(repository = repo, dispatcher = testDispatcher)

            viewModel.prefillPreviousRequestCode("sol-2026-00012")
            assertEquals("SOL-2026-00012", viewModel.uiState.value.previousRequestCode)

            viewModel.onCategorySelected(RequestCategory.ORIENTACION)
            viewModel.onSubjectChange("  Seguimiento a respuesta previa  ")
            viewModel.onDescriptionChange(
                "  Solicito aclaración adicional sobre el certificado emitido anteriormente.  ",
            )
            viewModel.submit()
            advanceUntilIdle()

            assertEquals(1, repo.createCalls.size)
            assertEquals("SOL-2026-00012", repo.createCalls.first().previousRequestCode)
            assertEquals("SOL-2026-00055", viewModel.uiState.value.createdCode)
            assertFalse(viewModel.uiState.value.isSubmitting)

            val event = assertIs<CreateRequestEvent.Created>(viewModel.events.first())
            assertEquals("SOL-2026-00055", event.detail.code)
        }

    @Test
    fun `maps server 400 error on previousRequestCode preserving user input`() =
        runTest(testDispatcher) {
            val repo =
                FakeServiceRequestRepository(
                    createHandler = {
                        AppResult.Failure(
                            DomainError.BadRequest(
                                userMessage = "Errores de validación.",
                                fieldErrors =
                                    mapOf(
                                        RequestFormValidator.FIELD_PREVIOUS_REQUEST_CODE to
                                            "La solicitud anterior no está Resuelta ni Cerrada.",
                                    ),
                            ),
                        )
                    },
                )
            val viewModel = CreateRequestViewModel(repository = repo, dispatcher = testDispatcher)

            viewModel.onCategorySelected(RequestCategory.TRAMITE)
            viewModel.onSubjectChange("Revisión de documento")
            viewModel.onDescriptionChange("Necesito revisar el estado del documento enviado previamente.")
            viewModel.onPreviousRequestCodeChange("SOL-2026-00010")
            viewModel.submit()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(
                "La solicitud anterior no está Resuelta ni Cerrada.",
                state.fieldErrors[RequestFormValidator.FIELD_PREVIOUS_REQUEST_CODE],
            )
            assertEquals("Revisión de documento", state.subject)
            assertNull(state.createdCode)
        }
}
