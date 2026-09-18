package com.example.data.repository

import androidx.compose.ui.graphics.Color
import com.example.domain.model.EffectType
import com.example.ui.components.effects.EffectItem
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class EffectsRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    /**
     * Firestore ke 'effects_library' collection se real-time updates lena
     */
    fun getEffectsStream(): Flow<List<EffectItem>> = callbackFlow {
        val listener = firestore.collection("effects_library")
            .orderBy("order")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val items = snapshot?.documents?.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val name = doc.getString("name") ?: "Unknown"
                    val category = doc.getString("category") ?: "All"
                    val isAi = doc.getBoolean("isAI") ?: false
                    val isPro = doc.getBoolean("isPro") ?: false

                    // Firestore ki ID ko domain EffectType ke sath map karna
                    val matchedType = runCatching {
                        EffectType.valueOf(id.uppercase())
                    }.getOrNull()

                    EffectItem(
                        id = id,
                        name = name,
                        effectType = matchedType,
                        category = category,
                        tag = if (isAi) "AI" else if (isPro) "PRO" else "",
                        accentColor = if (isAi) Color(0xFF9C27B0) else Color(0xFF00C2FF)
                    )
                } ?: emptyList()

                trySend(items)
            }

        awaitClose { listener.remove() }
    }
}
