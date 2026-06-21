package com.simplyroutine.data

import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    private val firestore = Firebase.firestore

    fun observeSharedTasks(householdId: String): Flow<List<Task>> = callbackFlow {
        val listener = firestore
            .collection("households/$householdId/tasks")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val tasks = snapshot?.documents?.mapNotNull { it.toTask() } ?: emptyList()
                trySend(tasks)
            }
        awaitClose { listener.remove() }
    }

    suspend fun pushTask(householdId: String, task: Task) {
        firestore.collection("households/$householdId/tasks")
            .document(task.syncId)
            .set(task.toFirestoreMap())
            .await()
    }

    suspend fun deleteTask(householdId: String, task: Task) {
        firestore.collection("households/$householdId/tasks")
            .document(task.syncId)
            .delete()
            .await()
    }

    suspend fun createHousehold(): String {
        val code = generateCode()
        firestore.collection("households")
            .document(code)
            .set(mapOf("created" to System.currentTimeMillis()))
            .await()
        return code
    }

    suspend fun joinHousehold(code: String): Boolean = try {
        firestore.collection("households").document(code).get().await().exists()
    } catch (_: Exception) { false }

    private fun generateCode(): String {
        val words = listOf("ROBIN", "MAPLE", "EMBER", "CLOUD", "RIVER", "STONE", "HAVEN", "CEDAR")
        return "${words.random()}-${(1000..9999).random()}"
    }
}

private fun DocumentSnapshot.toTask(): Task? {
    return try {
        val title = getString("title") ?: return null
        val frequencyDays = getLong("frequencyDays")?.toInt() ?: return null
        Task(
            id = 0,
            title = title,
            frequencyDays = frequencyDays,
            frequencyUnit = getString("frequencyUnit") ?: "days",
            lastCompleted = getLong("lastCompleted"),
            syncId = getString("syncId") ?: id,
            shared = true,
        )
    } catch (_: Exception) { null }
}

private fun Task.toFirestoreMap(): Map<String, Any?> = mapOf(
    "syncId" to syncId,
    "title" to title,
    "frequencyDays" to frequencyDays,
    "frequencyUnit" to frequencyUnit,
    "lastCompleted" to lastCompleted,
)
