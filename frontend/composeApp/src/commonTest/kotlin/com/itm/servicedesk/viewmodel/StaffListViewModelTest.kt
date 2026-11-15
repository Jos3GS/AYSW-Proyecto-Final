package com.itm.servicedesk.viewmodel

import com.itm.servicedesk.domain.model.AppResult
import com.itm.servicedesk.domain.model.DomainError
import com.itm.servicedesk.domain.model.UserRole
import com.itm.servicedesk.testing.AuthTestFixtures
import com.itm.servicedesk.testing.FakeStaffRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class StaffListViewModelTest {
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
    fun `loads staff with fixed size 20, formats dates, and paginates`() =
        runTest(testDispatcher) {
            val repo =
                FakeStaffRepository(
                    listStaffHandler = { page, size, _ ->
                        AppResult.Success(
                            AuthTestFixtures.staffPage(
                                page = page,
                                size = size,
                                totalItems = 25,
                                totalPages = 2,
                            ),
                        )
                    },
                )
            val viewModel = StaffListViewModel(staffRepository = repo, dispatcher = testDispatcher)

            viewModel.loadStaff()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1, state.page)
            assertEquals(20, state.size)
            assertEquals(2, state.totalPages)
            assertEquals("Personal de atención", state.items.first().roleLabel)
            assertEquals("14/11/2026 17:15", state.items.first().formattedCreatedAt)

            viewModel.nextPage()
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.page)
            assertEquals(Triple(2, 20, null), repo.listCalls.last())

            viewModel.selectRoleFilter(UserRole.COORDINADOR)
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.page)
            assertEquals(Triple(1, 20, UserRole.COORDINADOR), repo.listCalls.last())

            viewModel.previousPage()
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.page)
        }

    @Test
    fun `displays controlled error message on 403 Forbidden`() =
        runTest(testDispatcher) {
            val repo =
                FakeStaffRepository(
                    listStaffHandler = { _, _, _ ->
                        AppResult.Failure(DomainError.Forbidden("No tienes permisos para ver el personal."))
                    },
                )
            val viewModel = StaffListViewModel(staffRepository = repo, dispatcher = testDispatcher)

            viewModel.loadStaff()
            advanceUntilIdle()

            assertEquals("No tienes permisos para ver el personal.", viewModel.uiState.value.errorMessage)
        }
}
