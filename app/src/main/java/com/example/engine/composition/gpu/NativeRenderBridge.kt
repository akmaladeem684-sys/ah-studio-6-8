package com.example.engine.composition.gpu

import android.util.Log

enum class NativeBlendMode(val id:Int){NORMAL(0),ADDITIVE(1),MULTIPLY(2),SCREEN(3),PREMULTIPLIED(4)}
enum class NativeLayerType(val id:Int){BASE_VIDEO(0),VIDEO(1),IMAGE_STICKER(2),EFFECT_OVERLAY(3),TEXT(4)}
data class NativeLayer(val id:Long=0L,val textureId:Int=0,val type:NativeLayerType=NativeLayerType.BASE_VIDEO,val isVisible:Boolean=true,val zOrder:Int=0,val posX:Float=0f,val posY:Float=0f,val scaleX:Float=1f,val scaleY:Float=1f,val rotation:Float=0f,val width:Float=1f,val height:Float=1f,val opacity:Float=1f,val uOffset:Float=0f,val vOffset:Float=0f,val uScale:Float=1f,val vScale:Float=1f,val blendMode:NativeBlendMode=NativeBlendMode.NORMAL,val useCustomMatrix:Boolean=false,val transformMatrix:FloatArray?=null)
object NativeRenderBridge{
 private const val TAG="NativeRenderBridge";private const val STRIDE=35;private const val CAP=64
 @Volatile private var loaded=false
 private val fallback=ThreadLocal<Boolean>()
 private val buffers=object:ThreadLocal<FloatArray>(){override fun initialValue()=FloatArray(CAP*STRIDE)}
 val isLoaded:Boolean get()=loaded&&fallback.get()!=true&&runCatching{nativeIsReady()}.getOrDefault(false)
 fun loadLibrary():Boolean{if(loaded)return true;return synchronized(this){if(loaded)return true;try{System.loadLibrary("ah_engine");loaded=true;true}catch(t:Throwable){Log.w(TAG,"Native engine unavailable; Kotlin renderer fallback remains active",t);false}}}
 fun isLibraryAvailable()=loaded
 fun forceKotlinFallbackOnThisThread(enabled:Boolean){fallback.set(if(enabled)true else null)}
 fun isKotlinFallbackForced()=fallback.get()==true
 fun init(width:Int,height:Int):Boolean{if(width<=0||height<=0)return false;if(!loaded&&!loadLibrary())return false;return runCatching{nativeInit(width,height)}.getOrDefault(false)}
 fun resize(width:Int,height:Int){if(width>0&&height>0&&isLoaded)runCatching{nativeResize(width,height)}}
 fun renderExternalTexture(textureId:Int,texMatrix:FloatArray?=null){if(textureId>0&&isLoaded)runCatching{nativeRenderExternalTexture(textureId,texMatrix)}}
 fun renderFrame(layers:List<NativeLayer>){if(layers.isEmpty()||!isLoaded)return;var b=buffers.get();if(b.size<layers.size*STRIDE){b=FloatArray((layers.size+32)*STRIDE);buffers.set(b)};var o=0;for(l in layers){b[o]=l.id.toFloat();b[o+1]=l.textureId.toFloat();b[o+2]=l.type.id.toFloat();b[o+3]=if(l.isVisible)1f else 0f;b[o+4]=l.zOrder.toFloat();b[o+5]=l.posX;b[o+6]=l.posY;b[o+7]=l.scaleX;b[o+8]=l.scaleY;b[o+9]=l.rotation;b[o+10]=l.width;b[o+11]=l.height;b[o+12]=l.opacity;b[o+13]=l.uOffset;b[o+14]=l.vOffset;b[o+15]=l.uScale;b[o+16]=l.vScale;b[o+17]=l.blendMode.id.toFloat();val m=l.transformMatrix;val custom=l.useCustomMatrix&&m!=null&&m.size>=16;b[o+18]=if(custom)1f else 0f;if(custom)System.arraycopy(m!!,0,b,o+19,16);o+=STRIDE};runCatching{nativeRenderFrame(b,layers.size)}}
 fun beginOffscreen(){if(isLoaded)runCatching{nativeBeginOffscreen()}}
 fun endOffscreen()=if(isLoaded)runCatching{nativeEndOffscreen()}.getOrDefault(0) else 0
 fun onContextLost(){if(loaded)runCatching{nativeOnContextLost()}}
 fun release(){if(loaded)runCatching{nativeRelease()}}
 private external fun nativeInit(width:Int,height:Int):Boolean
 private external fun nativeIsReady():Boolean
 private external fun nativeResize(width:Int,height:Int)
 private external fun nativeRenderFrame(data:FloatArray,count:Int)
 private external fun nativeRenderExternalTexture(textureId:Int,texMatrix:FloatArray?)
 private external fun nativeBeginOffscreen()
 private external fun nativeEndOffscreen():Int
 private external fun nativeOnContextLost()
 private external fun nativeRelease()
}
