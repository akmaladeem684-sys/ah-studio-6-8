package com.example.engine.export

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.example.domain.model.*
import com.example.engine.composition.VideoCompositionEngine
import com.example.engine.composition.gpu.EglCore
import com.example.engine.composition.gpu.GpuCompositionRenderer
import com.example.engine.composition.gpu.WindowSurface
import com.example.engine.media.MediaRelinkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil

/** GPU handle only. Video pixels never enter this object as Bitmap/ByteBuffer data. */
data class FramePacket(
  val frameIndex: Long,
  val presentationTimeUs: Long,
  val sourceTimestampUs: Long,
  val textureId: Int,
  val textureTarget: Int,
  val transformMatrix: FloatArray,
  val width: Int,
  val height: Int,
  val clipId: String
)

...