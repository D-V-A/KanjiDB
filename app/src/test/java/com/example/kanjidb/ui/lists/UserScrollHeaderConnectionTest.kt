package com.example.kanjidb.ui.lists

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UserScrollHeaderConnectionTest {
    private class RecordingConnection : NestedScrollConnection {
        val deltas = mutableListOf<Float>()
        var flings = 0
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            assertEquals(0f, available.x)
            deltas += available.y
            return available
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            deltas += consumed.y + available.y
            return available
        }
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            assertEquals(0f, consumed.x)
            assertEquals(0f, available.x)
            flings++
            return available
        }
    }

    @Test fun verticalDragForwardsBothDirectionsImmediately() {
        val delegate = RecordingConnection()
        val connection = UserScrollHeaderConnection(delegate) { true }
        assertEquals(Offset(0f, -35f), connection.onPreScroll(Offset(3f, -35f), NestedScrollSource.UserInput))
        assertEquals(Offset(0f, 4f), connection.onPreScroll(Offset(2f, 4f), NestedScrollSource.UserInput))
        assertEquals(listOf(-35f, 4f), delegate.deltas)
    }

    @Test fun programmaticCardRevealDoesNotMoveOrSettleHeader() = runBlocking {
        val delegate = RecordingConnection()
        val connection = UserScrollHeaderConnection(delegate) { true }
        assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, 80f), NestedScrollSource.SideEffect))
        assertEquals(Offset.Zero, connection.onPostScroll(Offset(0f, 80f), Offset.Zero, NestedScrollSource.SideEffect))
        connection.onPostFling(Velocity.Zero, Velocity.Zero)
        assertEquals(emptyList<Float>(), delegate.deltas)
        assertEquals(0, delegate.flings)
    }

    @Test fun horizontalPagerGestureDoesNotMoveOrSettleHeader() = runBlocking {
        val delegate = RecordingConnection()
        val connection = UserScrollHeaderConnection(delegate) { true }
        connection.onPreScroll(Offset(50f, 0f), NestedScrollSource.UserInput)
        connection.onPostScroll(Offset(50f, 0f), Offset.Zero, NestedScrollSource.UserInput)
        connection.onPostFling(Velocity(400f, 0f), Velocity.Zero)
        assertEquals(emptyList<Float>(), delegate.deltas)
        assertEquals(0, delegate.flings)
    }

    @Test fun verticalReleaseSettlesOnceEvenWithZeroRemainingVelocity() = runBlocking {
        val delegate = RecordingConnection()
        val connection = UserScrollHeaderConnection(delegate) { true }
        connection.onPreScroll(Offset(0f, -20f), NestedScrollSource.UserInput)
        connection.onPostFling(Velocity.Zero, Velocity.Zero)
        connection.onPostFling(Velocity(500f, 0f), Velocity.Zero)
        assertEquals(1, delegate.flings)
    }

    @Test fun userFlingContinuesHeaderMotionButLaterAutoscrollDoesNot() = runBlocking {
        val delegate = RecordingConnection()
        val connection = UserScrollHeaderConnection(delegate) { true }
        connection.onPreScroll(Offset(0f, -20f), NestedScrollSource.UserInput)
        connection.onPreFling(Velocity(0f, -300f))
        connection.onPreScroll(Offset(0f, -10f), NestedScrollSource.SideEffect)
        connection.onPostFling(Velocity(0f, -300f), Velocity.Zero)
        connection.onPreScroll(Offset(0f, 80f), NestedScrollSource.SideEffect)
        assertEquals(listOf(-20f, -10f), delegate.deltas)
        assertEquals(1, delegate.flings)
    }

    @Test fun disabledHeaderIgnoresDragAndPendingFling() = runBlocking {
        val delegate = RecordingConnection()
        var enabled = true
        val connection = UserScrollHeaderConnection(delegate) { enabled }
        connection.onPreScroll(Offset(0f, -20f), NestedScrollSource.UserInput)
        enabled = false
        connection.onPreScroll(Offset(0f, 30f), NestedScrollSource.UserInput)
        connection.onPostScroll(Offset(0f, 30f), Offset.Zero, NestedScrollSource.UserInput)
        connection.onPostFling(Velocity.Zero, Velocity(0f, 100f))
        assertEquals(listOf(-20f), delegate.deltas)
        assertEquals(0, delegate.flings)
    }
}
