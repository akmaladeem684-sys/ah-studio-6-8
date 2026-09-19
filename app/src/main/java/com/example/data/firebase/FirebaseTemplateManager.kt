package com.example.data.firebase

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.TimelineSerializer
import com.example.data.presets.MediaPlaceholder
import com.example.data.presets.PlaceholderType
import com.example.data.presets.TextPlaceholder
import com.example.data.presets.VideoTemplate
import com.example.domain.model.AspectRatio
import com.example.domain.model.FrameRate
import com.example.domain.model.Resolution
import com.example.domain.model.Timeline
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class CreatorProfile(
  val creatorId: String = "",
  val displayName: String = "",
  val handle: String = "",
  val bio: String = "",
  val avatarUrl: String? = null,
  val tikTokHandle: String? = null,
  val category: String = "Reels & TikTok",
  val isProfileComplete: Boolean = false,
  val templatesCount: Long = 0L,
  val totalViews: Long = 0L,
  val totalUses: Long = 0L,
  val updatedAt: Long = System.currentTimeMillis()
)

object FirebaseTemplateManager {
  private const val TAG = "FirebaseTemplateMgr"
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var prefs: SharedPreferences? = null
  private var firestore: FirebaseFirestore? = null
  private var storage: FirebaseStorage? = null
  private var templatesListener: ListenerRegistration? = null

  private val _templates = MutableStateFlow<List<VideoTemplate>>(emptyList())
  val templates: StateFlow<List<VideoTemplate>> = _templates.asStateFlow()

  private val _creatorProfile = MutableStateFlow(CreatorProfile())
  val creatorProfile: StateFlow<CreatorProfile> = _creatorProfile.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  fun init(context: Context) {
    if (prefs != null) return
    val appContext = context.applicationContext
    prefs = appContext.getSharedPreferences("ah_creator_templates", Context.MODE_PRIVATE)

    // Load locally cached creator profile
    loadCachedProfile()

    // Initialize Firebase references safely
    try {
      if (FirebaseApp.getApps(appContext).isEmpty()) {
        val options = FirebaseOptions.Builder()
          .setApiKey(BuildConfig.FIREBASE_API_KEY)
          .setApplicationId("1:368906369830:android:fb75b229a82f28f8861fe7")
          .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
          .setStorageBucket("gen-lang-client-0291066258.firebasestorage.app")
          .build()
        FirebaseApp.initializeApp(appContext, options)
      }
      firestore = FirebaseFirestore.getInstance()
      storage = FirebaseStorage.getInstance()
      Log.d(TAG, "Firebase Firestore & Storage initialized for Templates")
      startTemplatesListener()
    } catch (e: Throwable) {
      Log.w(TAG, "Firebase safely skipped or in offline fallback mode: ${e.message}")
    }
  }

  private fun loadCachedProfile() {
    val p = prefs ?: return
    val creatorId = p.getString("creator_id", null) ?: "creator_${UUID.randomUUID().toString().take(8)}"
    val displayName = p.getString("display_name", "") ?: ""
    val handle = p.getString("handle", "") ?: ""
    val bio = p.getString("bio", "") ?: ""
    val tikTokHandle = p.getString("tiktok_handle", "") ?: ""
    val category = p.getString("category", "Reels & TikTok") ?: "Reels & TikTok"
    val isComplete = p.getBoolean("is_profile_complete", false)
    val templatesCount = p.getLong("templates_count", 0L)
    val totalViews = p.getLong("total_views", 0L)
    val totalUses = p.getLong("total_uses", 0L)

    val profile = CreatorProfile(
      creatorId = creatorId,
      displayName = displayName,
      handle = if (handle.startsWith("@")) handle else if (handle.isNotBlank()) "@$handle" else "",
      bio = bio,
      tikTokHandle = tikTokHandle,
      category = category,
      isProfileComplete = isComplete,
      templatesCount = templatesCount,
      totalViews = totalViews,
      totalUses = totalUses
    )
    _creatorProfile.value = profile
    p.edit().putString("creator_id", creatorId).apply()
  }

  suspend fun saveCreatorProfile(profile: CreatorProfile): Result<Unit> {
    return try {
      val normalizedHandle = if (profile.handle.startsWith("@")) profile.handle else "@${profile.handle.trim()}"
      val updated = profile.copy(
        handle = normalizedHandle,
        isProfileComplete = true,
        updatedAt = System.currentTimeMillis()
      )
      _creatorProfile.value = updated

      // Save locally
      prefs?.edit()?.apply {
        putString("creator_id", updated.creatorId)
        putString("display_name", updated.displayName)
        putString("handle", updated.handle)
        putString("bio", updated.bio)
        putString("tiktok_handle", updated.tikTokHandle ?: "")
        putString("category", updated.category)
        putBoolean("is_profile_complete", true)
        putLong("templates_count", updated.templatesCount)
        putLong("total_views", updated.totalViews)
        putLong("total_uses", updated.totalUses)
        apply()
      }

      // Sync to Firestore if available
      val db = firestore
      if (db != null) {
        val map = mapOf(
          "creatorId" to updated.creatorId,
          "displayName" to updated.displayName,
          "handle" to updated.handle,
          "bio" to updated.bio,
          "tikTokHandle" to (updated.tikTokHandle ?: ""),
          "category" to updated.category,
          "isProfileComplete" to true,
          "templatesCount" to updated.templatesCount,
          "totalViews" to updated.totalViews,
          "totalUses" to updated.totalUses,
          "updatedAt" to updated.updatedAt
        )
        db.collection("creators").document(updated.creatorId).set(map).await()
      }
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Error saving creator profile: ${e.message}", e)
      Result.success(Unit)
    }
  }

  private fun startTemplatesListener() {
    val db = firestore ?: return
    templatesListener?.remove()
    try {
      templatesListener = db.collection("templates")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .addSnapshotListener { snapshots, error ->
          if (error != null) {
            Log.w(TAG, "Firestore templates snapshot error: ${error.message}")
            return@addSnapshotListener
          }
          if (snapshots != null) {
            val list = mutableListOf<VideoTemplate>()
            for (doc in snapshots.documents) {
              try {
                val tpl = parseDocumentToVideoTemplate(doc.id, doc.data ?: emptyMap())
                if (tpl != null) {
                  list.add(tpl)
                }
              } catch (e: Exception) {
                Log.e(TAG, "Failed to parse template ${doc.id}: ${e.message}")
              }
            }
            if (list.isNotEmpty()) {
              _templates.value = list
            }
          }
        }
    } catch (e: Throwable) {
      Log.w(TAG, "Failed to listen to Firestore templates: ${e.message}")
    }
  }

  private fun parseDocumentToVideoTemplate(id: String, map: Map<String, Any>): VideoTemplate? {
    val title = map["title"] as? String ?: return null
    val category = map["category"] as? String ?: "Social"
    val description = map["description"] as? String ?: ""
    val aspectStr = map["aspectRatio"] as? String ?: "RATIO_9_16"
    val durationMs = (map["durationMs"] as? Number)?.toLong() ?: 5000L
    val thumbnailGradientStart = (map["thumbnailGradientStart"] as? Number)?.toLong() ?: 0xFF3B82F6
    val thumbnailGradientEnd = (map["thumbnailGradientEnd"] as? Number)?.toLong() ?: 0xFF8B5CF6
    val iconEmoji = map["iconEmoji"] as? String ?: "🎬"
    val audioTitle = map["audioTitle"] as? String ?: "Original Audio"
    val previewVideoUrl = map["previewVideoUrl"] as? String
    val previewThumbnailUrl = map["previewThumbnailUrl"] as? String
    val creatorId = map["creatorId"] as? String ?: ""
    val creatorName = map["creatorName"] as? String ?: "Community Creator"
    val creatorHandle = map["creatorHandle"] as? String ?: "@creator"
    val viewsCount = (map["viewsCount"] as? Number)?.toLong() ?: 0L
    val cutsCount = (map["cutsCount"] as? Number)?.toLong() ?: 0L
    val isPro = map["isPro"] as? Boolean ?: false
    val timelineJson = map["timelineJson"] as? String
    val placeholdersJson = map["placeholdersJson"] as? String

    val aspect = try { AspectRatio.valueOf(aspectStr) } catch (_: Exception) { AspectRatio.RATIO_9_16 }
    val mediaPlaceholders = parseMediaPlaceholders(placeholdersJson)
    val textPlaceholders = parseTextPlaceholders(placeholdersJson)

    return VideoTemplate(
      id = id,
      title = title,
      category = category,
      description = description,
      aspectRatio = aspect,
      resolution = Resolution.RES_1080P,
      fps = FrameRate.FPS_30,
      durationMs = durationMs,
      thumbnailGradientStart = thumbnailGradientStart,
      thumbnailGradientEnd = thumbnailGradientEnd,
      iconEmoji = iconEmoji,
      audioTitle = audioTitle,
      previewVideoUrl = previewVideoUrl,
      previewThumbnailUrl = previewThumbnailUrl,
      creatorId = creatorId,
      creatorName = creatorName,
      creatorHandle = creatorHandle,
      viewsCount = viewsCount,
      cutsCount = cutsCount,
      isPro = isPro,
      mediaPlaceholders = mediaPlaceholders,
      textPlaceholders = textPlaceholders,
      createTimeline = { w, h ->
        if (timelineJson != null) {
          TimelineSerializer.deserializeTimeline(timelineJson)
        } else {
          Timeline()
        }
      }
    )
  }

  private fun parseMediaPlaceholders(json: String?): List<MediaPlaceholder> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
      val obj = JSONObject(json)
      val arr = obj.optJSONArray("media") ?: return emptyList()
      val list = mutableListOf<MediaPlaceholder>()
      for (i in 0 until arr.length()) {
        val item = arr.getJSONObject(i)
        list.add(
          MediaPlaceholder(
            id = item.optString("id", UUID.randomUUID().toString()),
            label = item.optString("label", "Clip ${i + 1}"),
            targetDurationMs = item.optLong("durationMs", 3000L),
            type = if (item.optString("type") == "PHOTO") PlaceholderType.PHOTO else PlaceholderType.VIDEO_OR_PHOTO
          )
        )
      }
      list
    } catch (_: Exception) {
      emptyList()
    }
  }

  private fun parseTextPlaceholders(json: String?): List<TextPlaceholder> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
      val obj = JSONObject(json)
      val arr = obj.optJSONArray("text") ?: return emptyList()
      val list = mutableListOf<TextPlaceholder>()
      for (i in 0 until arr.length()) {
        val item = arr.getJSONObject(i)
        list.add(
          TextPlaceholder(
            id = item.optString("id", UUID.randomUUID().toString()),
            defaultText = item.optString("text", "Text ${i + 1}"),
            label = item.optString("label", "Title")
          )
        )
      }
      list
    } catch (_: Exception) {
      emptyList()
    }
  }

  suspend fun uploadAndPublishTemplate(
    title: String,
    category: String,
    description: String,
    timeline: Timeline,
    aspectRatio: AspectRatio,
    previewVideoFile: File?,
    previewThumbnail: Bitmap?,
    mediaPlaceholders: List<MediaPlaceholder>,
    textPlaceholders: List<TextPlaceholder>,
    isPro: Boolean = false
  ): Result<VideoTemplate> {
    val prof = _creatorProfile.value
    val tplId = "tpl_${UUID.randomUUID().toString().take(8)}"
    val timelineJson = TimelineSerializer.serializeTimeline(timeline)

    val placeholdersObj = JSONObject().apply {
      put("media", JSONArray().apply {
        mediaPlaceholders.forEach {
          put(JSONObject().apply {
            put("id", it.id)
            put("label", it.label)
            put("durationMs", it.targetDurationMs)
            put("type", it.type.name)
          })
        }
      })
      put("text", JSONArray().apply {
        textPlaceholders.forEach {
          put(JSONObject().apply {
            put("id", it.id)
            put("text", it.defaultText)
            put("label", it.label)
          })
        }
      })
    }

    var previewVideoUrl: String? = null
    var previewThumbnailUrl: String? = null

    // 1. Upload video preview to Firebase Storage if file exists
    if (previewVideoFile != null && previewVideoFile.exists() && storage != null) {
      try {
        val ref = storage!!.reference.child("templates/$tplId/preview.mp4")
        val uploadTask = ref.putFile(Uri.fromFile(previewVideoFile)).await()
        previewVideoUrl = ref.downloadUrl.await().toString()
      } catch (e: Throwable) {
        Log.w(TAG, "Firebase Storage video upload skipped/failed: ${e.message}")
      }
    }

    // 2. Upload thumbnail to Firebase Storage if bitmap exists
    if (previewThumbnail != null && storage != null) {
      try {
        val tempThumb = File.createTempFile("thumb_$tplId", ".jpg")
        val out = FileOutputStream(tempThumb)
        previewThumbnail.compress(Bitmap.CompressFormat.JPEG, 85, out)
        out.flush()
        out.close()

        val ref = storage!!.reference.child("templates/$tplId/thumbnail.jpg")
        ref.putFile(Uri.fromFile(tempThumb)).await()
        previewThumbnailUrl = ref.downloadUrl.await().toString()
        tempThumb.delete()
      } catch (e: Throwable) {
        Log.w(TAG, "Firebase Storage thumbnail upload failed: ${e.message}")
      }
    }

    val docData = mutableMapOf<String, Any>(
      "id" to tplId,
      "title" to title,
      "category" to category,
      "description" to description,
      "aspectRatio" to aspectRatio.name,
      "durationMs" to timeline.totalDurationMs,
      "thumbnailGradientStart" to 0xFF3B82F6,
      "thumbnailGradientEnd" to 0xFF8B5CF6,
      "iconEmoji" to "🎬",
      "audioTitle" to (timeline.audioClips.firstOrNull()?.name ?: "Original Audio"),
      "creatorId" to prof.creatorId,
      "creatorName" to prof.displayName.ifBlank { "Creator" },
      "creatorHandle" to prof.handle.ifBlank { "@creator" },
      "viewsCount" to 0L,
      "cutsCount" to 0L,
      "isPro" to isPro,
      "timelineJson" to timelineJson,
      "placeholdersJson" to placeholdersObj.toString(),
      "createdAt" to System.currentTimeMillis()
    )

    if (previewVideoUrl != null) docData["previewVideoUrl"] = previewVideoUrl
    if (previewThumbnailUrl != null) docData["previewThumbnailUrl"] = previewThumbnailUrl

    val db = firestore
    if (db != null) {
      try {
        db.collection("templates").document(tplId).set(docData).await()
        // Increment creator's templates count
        db.collection("creators").document(prof.creatorId)
          .update("templatesCount", FieldValue.increment(1))
          .await()
      } catch (e: Throwable) {
        Log.w(TAG, "Firestore sync failed: ${e.message}")
      }
    }

    val createdTemplate = VideoTemplate(
      id = tplId,
      title = title,
      category = category,
      description = description,
      aspectRatio = aspectRatio,
      resolution = Resolution.RES_1080P,
      fps = FrameRate.FPS_30,
      durationMs = timeline.totalDurationMs,
      thumbnailGradientStart = 0xFF3B82F6,
      thumbnailGradientEnd = 0xFF8B5CF6,
      iconEmoji = "🎬",
      audioTitle = timeline.audioClips.firstOrNull()?.name ?: "Original Audio",
      previewVideoUrl = previewVideoUrl,
      previewThumbnailUrl = previewThumbnailUrl,
      creatorId = prof.creatorId,
      creatorName = prof.displayName.ifBlank { "Creator" },
      creatorHandle = prof.handle.ifBlank { "@creator" },
      viewsCount = 0L,
      cutsCount = 0L,
      isPro = isPro,
      mediaPlaceholders = mediaPlaceholders,
      textPlaceholders = textPlaceholders,
      createTimeline = { _, _ -> timeline.copy() }
    )

    _templates.value = listOf(createdTemplate) + _templates.value
    return Result.success(createdTemplate)
  }

  fun recordTemplateView(templateId: String, creatorId: String) {
    scope.launch {
      val db = firestore ?: return@launch
      try {
        db.collection("templates").document(templateId)
          .update("viewsCount", FieldValue.increment(1))
        if (creatorId.isNotBlank()) {
          db.collection("creators").document(creatorId)
            .update("totalViews", FieldValue.increment(1))
        }
      } catch (e: Throwable) {
        Log.w(TAG, "Failed to record template view: ${e.message}")
      }
    }
  }

  fun recordTemplateUse(templateId: String, creatorId: String) {
    scope.launch {
      val db = firestore ?: return@launch
      try {
        db.collection("templates").document(templateId)
          .update("cutsCount", FieldValue.increment(1))
        if (creatorId.isNotBlank()) {
          db.collection("creators").document(creatorId)
            .update("totalUses", FieldValue.increment(1))
        }
      } catch (e: Throwable) {
        Log.w(TAG, "Failed to record template cut: ${e.message}")
      }
    }
  }
}
