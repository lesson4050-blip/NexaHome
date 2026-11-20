package com.orbit.tv.launcher.ui.views

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.SystemClock
import android.util.AttributeSet
import android.view.TextureView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Ultra-optimized native OpenGL ES 2.0 animated silk shader view for Android TV.
 *
 * Performance architecture:
 * 1. Offscreen FBO fixed at 640x360 with native GPU derivatives (dFdx/dFdy).
 * 2. Hardware bilinear upscaling via full-screen blit to native 1080p display buffer.
 * 3. Constant-step time integration (zero phase judder/jerkiness).
 * 4. Automatic screen dimension tracking (never clips or leaves empty corners on activity transitions).
 * 5. Dedicated render thread with full lock-based pause/resume (0% CPU/GPU when paused).
 */
class SilkShaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener {

    private var renderThread: SilkRenderThread? = null
    @Volatile
    private var currentPalette: Int = 0

    init {
        surfaceTextureListener = this
        isOpaque = true
    }

    fun setPalette(paletteId: Int) {
        currentPalette = paletteId.coerceIn(0, 4)
        renderThread?.setPalette(currentPalette)
    }

    fun pauseRendering() {
        renderThread?.pauseRendering()
    }

    fun resumeRendering() {
        renderThread?.resumeRendering()
    }

    private val isLowRam: Boolean by lazy {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        am?.isLowRamDevice == true
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        android.util.Log.e("SilkDebug", "onSurfaceTextureAvailable: w=$width, h=$height, isLowRam=$isLowRam")
        stopThread()
        renderThread = SilkRenderThread(surface, width, height, currentPalette, isLowRam).apply {
            start()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        android.util.Log.e("SilkDebug", "onSurfaceTextureSizeChanged: w=$width, h=$height")
        renderThread?.updateSize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        android.util.Log.e("SilkDebug", "onSurfaceTextureDestroyed")
        stopThread()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // No-op
    }

    private fun stopThread() {
        renderThread?.let {
            it.stopRendering()
            it.interrupt()
            try {
                it.join(500)
            } catch (ignored: InterruptedException) {}
        }
        renderThread = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopThread()
    }

    private class SilkRenderThread(
        private val surfaceTexture: SurfaceTexture,
        private var bufferWidth: Int,
        private var bufferHeight: Int,
        @Volatile private var palette: Int,
        private val isLowRam: Boolean
    ) : Thread("SilkShaderThread") {

        @Volatile private var isRunning = true
        @Volatile private var isPaused = false
        private val pauseLock = Object()

        private val fboWidth: Int = if (isLowRam) 960 else (if (bufferWidth > 0) bufferWidth else 1920)
        private val fboHeight: Int = if (isLowRam) 540 else (if (bufferHeight > 0) bufferHeight else 1080)
        private val surfWidth = IntArray(1)
        private val surfHeight = IntArray(1)

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        // Silk generation program
        private var silkProgramId = 0
        private var uTimeHandle = 0
        private var uResHandle = 0
        private var uPaletteHandle = 0
        private var aPosHandle = 0
        private var aTexCoordHandle = 0

        // Fullscreen blit program
        private var blitProgramId = 0
        private var uBlitTextureHandle = 0
        private var aBlitPosHandle = 0
        private var aBlitTexCoordHandle = 0

        // FBO for offscreen low-res rendering
        private var fboId = 0
        private var fboTextureId = 0

        private lateinit var quadVertexBuffer: FloatBuffer
        private lateinit var silkTexCoordBuffer: FloatBuffer
        private lateinit var blitTexCoordBuffer: FloatBuffer

        private val targetFrameMs = 16L // Native 60 FPS VSYNC lock

        fun setPalette(newPalette: Int) {
            palette = newPalette
        }

        fun updateSize(width: Int, height: Int) {
            bufferWidth = width
            bufferHeight = height
        }

        fun pauseRendering() {
            synchronized(pauseLock) {
                isPaused = true
            }
        }

        fun resumeRendering() {
            synchronized(pauseLock) {
                isPaused = false
                pauseLock.notifyAll()
            }
        }

        fun stopRendering() {
            isRunning = false
            resumeRendering()
        }

        override fun run() {
            android.util.Log.e("SilkDebug", "SilkRenderThread run() started")
            val eglOk = initEGL()
            android.util.Log.e("SilkDebug", "initEGL result: $eglOk")
            if (!eglOk) {
                return
            }
            val glOk = initGL()
            android.util.Log.e("SilkDebug", "initGL result: $glOk")
            if (!glOk) {
                releaseEGL()
                return
            }

            var virtualTime = 0.0f
            var frameCount = 0
            var lastLogTime = SystemClock.uptimeMillis()
            var totalDrawMs = 0L
            var totalSwapMs = 0L
            var totalSleepMs = 0L

            while (isRunning) {
                synchronized(pauseLock) {
                    while (isPaused && isRunning) {
                        try {
                            pauseLock.wait()
                        } catch (e: InterruptedException) {
                            return
                        }
                    }
                }

                if (!isRunning) break

                val t0 = SystemClock.uptimeMillis()
                // Constant time progression ensures zero spatial jerkiness
                virtualTime += 0.0166667f
                if (virtualTime > 3600.0f) virtualTime -= 3600.0f

                drawFrame(virtualTime)
                val t1 = SystemClock.uptimeMillis()

                val swapOk = EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                val t2 = SystemClock.uptimeMillis()

                if (!swapOk) {
                    break
                }

                val frameDuration = t2 - t0
                val sleepTime = targetFrameMs - frameDuration
                if (sleepTime > 0) {
                    try {
                        sleep(sleepTime)
                    } catch (ignored: InterruptedException) {
                        break
                    }
                }
                val t3 = SystemClock.uptimeMillis()

                frameCount++
                totalDrawMs += (t1 - t0)
                totalSwapMs += (t2 - t1)
                totalSleepMs += (t3 - t2)

                if (t3 - lastLogTime >= 5000L) {
                    val elapsedSec = (t3 - lastLogTime) / 1000.0
                    val fps = frameCount / elapsedSec
                    val avgDraw = totalDrawMs.toFloat() / frameCount
                    val avgSwap = totalSwapMs.toFloat() / frameCount
                    val avgSleep = totalSleepMs.toFloat() / frameCount
                    android.util.Log.i("SilkPerf", "FPS: ${"%.1f".format(fps)} | draw: ${"%.1f".format(avgDraw)}ms | swap: ${"%.1f".format(avgSwap)}ms | sleep: ${"%.1f".format(avgSleep)}ms")
                    frameCount = 0
                    totalDrawMs = 0L
                    totalSwapMs = 0L
                    totalSleepMs = 0L
                    lastLogTime = t3
                }
            }

            releaseGL()
            releaseEGL()
        }

        private fun initEGL(): Boolean {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) return false

            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return false

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 0,
                EGL14.EGL_DEPTH_SIZE, 0,
                EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
                return false
            }
            val eglConfig = configs[0] ?: return false

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) return false

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, surfaceAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) return false

            val ok = EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            if (ok) {
                EGL14.eglSwapInterval(eglDisplay, 1)
            }
            return ok
        }

        private fun initGL(): Boolean {
            // 1. Compile Silk generation program
            val vShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
            val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
            if (vShader == 0 || fShader == 0) {
                android.util.Log.e("SilkShader", "Failed to compile silk shaders: v=$vShader, f=$fShader")
                return false
            }

            silkProgramId = GLES20.glCreateProgram()
            GLES20.glAttachShader(silkProgramId, vShader)
            GLES20.glAttachShader(silkProgramId, fShader)
            GLES20.glLinkProgram(silkProgramId)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(silkProgramId, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                android.util.Log.e("SilkShader", "Link error: " + GLES20.glGetProgramInfoLog(silkProgramId))
                GLES20.glDeleteProgram(silkProgramId)
                return false
            }

            aPosHandle = GLES20.glGetAttribLocation(silkProgramId, "a_position")
            aTexCoordHandle = GLES20.glGetAttribLocation(silkProgramId, "a_texCoord")
            uTimeHandle = GLES20.glGetUniformLocation(silkProgramId, "u_time")
            uResHandle = GLES20.glGetUniformLocation(silkProgramId, "u_resolution")
            uPaletteHandle = GLES20.glGetUniformLocation(silkProgramId, "u_palette")

            // 2. Compile Fullscreen Blit program
            val blitVShader = compileShader(GLES20.GL_VERTEX_SHADER, BLIT_VERTEX_SHADER)
            val blitFShader = compileShader(GLES20.GL_FRAGMENT_SHADER, BLIT_FRAGMENT_SHADER)
            if (blitVShader == 0 || blitFShader == 0) {
                android.util.Log.e("SilkShader", "Failed to compile blit shaders")
                return false
            }

            blitProgramId = GLES20.glCreateProgram()
            GLES20.glAttachShader(blitProgramId, blitVShader)
            GLES20.glAttachShader(blitProgramId, blitFShader)
            GLES20.glLinkProgram(blitProgramId)

            GLES20.glGetProgramiv(blitProgramId, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                android.util.Log.e("SilkShader", "Blit link error: " + GLES20.glGetProgramInfoLog(blitProgramId))
                GLES20.glDeleteProgram(blitProgramId)
                return false
            }

            aBlitPosHandle = GLES20.glGetAttribLocation(blitProgramId, "a_position")
            aBlitTexCoordHandle = GLES20.glGetAttribLocation(blitProgramId, "a_texCoord")
            uBlitTextureHandle = GLES20.glGetUniformLocation(blitProgramId, "u_texture")

            // 3. Setup Vertex & Texture buffers
            val quadCoords = floatArrayOf(
                -1f,  1f,   // top-left
                -1f, -1f,   // bottom-left
                 1f,  1f,   // top-right
                 1f, -1f    // bottom-right
            )
            quadVertexBuffer = ByteBuffer.allocateDirect(quadCoords.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply {
                    put(quadCoords)
                    position(0)
                }

            val silkTexCoords = floatArrayOf(
                0f, 0f,
                0f, 1f,
                1f, 0f,
                1f, 1f
            )
            silkTexCoordBuffer = ByteBuffer.allocateDirect(silkTexCoords.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply {
                    put(silkTexCoords)
                    position(0)
                }

            val blitTexCoords = floatArrayOf(
                0f, 1f,   // top-left samples top of FBO
                0f, 0f,   // bottom-left samples bottom of FBO
                1f, 1f,   // top-right samples top of FBO
                1f, 0f    // bottom-right samples bottom of FBO
            )
            blitTexCoordBuffer = ByteBuffer.allocateDirect(blitTexCoords.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply {
                    put(blitTexCoords)
                    position(0)
                }

            // 4. Create Offscreen FBO (640x360) with Hardware Bilinear Filtering
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            fboTextureId = textures[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                fboWidth, fboHeight, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            val fbos = IntArray(1)
            GLES20.glGenFramebuffers(1, fbos, 0)
            fboId = fbos[0]
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, fboTextureId, 0
            )

            val fboStatus = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            if (fboStatus != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                android.util.Log.e("SilkShader", "FBO incomplete: $fboStatus")
                return false
            }

            return true
        }

        private fun drawFrame(timeSec: Float) {
            // === PASS 1: Render silk into FBO ===
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
            GLES20.glViewport(0, 0, fboWidth, fboHeight)
            GLES20.glClearColor(0.02f, 0.02f, 0.04f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(silkProgramId)

            GLES20.glUniform1f(uTimeHandle, timeSec)
            GLES20.glUniform2f(uResHandle, fboWidth.toFloat(), fboHeight.toFloat())
            GLES20.glUniform1i(uPaletteHandle, palette)

            GLES20.glEnableVertexAttribArray(aPosHandle)
            GLES20.glVertexAttribPointer(aPosHandle, 2, GLES20.GL_FLOAT, false, 0, quadVertexBuffer)

            GLES20.glEnableVertexAttribArray(aTexCoordHandle)
            GLES20.glVertexAttribPointer(aTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, silkTexCoordBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(aPosHandle)
            GLES20.glDisableVertexAttribArray(aTexCoordHandle)

            // === PASS 2: Bilinear blit from FBO to physical window surface (Full Screen 1080p) ===
            EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_WIDTH, surfWidth, 0)
            EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_HEIGHT, surfHeight, 0)
            val screenW = if (surfWidth[0] > 0) surfWidth[0] else if (bufferWidth > 0) bufferWidth else 1920
            val screenH = if (surfHeight[0] > 0) surfHeight[0] else if (bufferHeight > 0) bufferHeight else 1080

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, screenW, screenH)

            GLES20.glUseProgram(blitProgramId)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId)
            GLES20.glUniform1i(uBlitTextureHandle, 0)

            GLES20.glEnableVertexAttribArray(aBlitPosHandle)
            GLES20.glVertexAttribPointer(aBlitPosHandle, 2, GLES20.GL_FLOAT, false, 0, quadVertexBuffer)

            GLES20.glEnableVertexAttribArray(aBlitTexCoordHandle)
            GLES20.glVertexAttribPointer(aBlitTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, blitTexCoordBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(aBlitPosHandle)
            GLES20.glDisableVertexAttribArray(aBlitTexCoordHandle)
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                val err = GLES20.glGetShaderInfoLog(shader)
                android.util.Log.e("SilkShader", "Shader compile failed ($type):\n$err\nSource:\n$source")
                GLES20.glDeleteShader(shader)
                return 0
            }
            return shader
        }

        private fun releaseGL() {
            if (fboId != 0) {
                GLES20.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
                fboId = 0
            }
            if (fboTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(fboTextureId), 0)
                fboTextureId = 0
            }
            if (silkProgramId != 0) {
                GLES20.glDeleteProgram(silkProgramId)
                silkProgramId = 0
            }
            if (blitProgramId != 0) {
                GLES20.glDeleteProgram(blitProgramId)
                blitProgramId = 0
            }
        }

        private fun releaseEGL() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    eglSurface = EGL14.EGL_NO_SURFACE
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                    eglContext = EGL14.EGL_NO_CONTEXT
                }
                EGL14.eglTerminate(eglDisplay)
                eglDisplay = EGL14.EGL_NO_DISPLAY
            }
        }

        companion object {
            private const val VERTEX_SHADER = """
                attribute vec4 a_position;
                attribute vec2 a_texCoord;
                varying vec2 v_texCoord;
                void main() {
                    gl_Position = a_position;
                    v_texCoord = a_texCoord;
                }
            """

            private const val BLIT_VERTEX_SHADER = """
                attribute vec4 a_position;
                attribute vec2 a_texCoord;
                varying vec2 v_texCoord;
                void main() {
                    gl_Position = a_position;
                    v_texCoord = a_texCoord;
                }
            """

            private const val BLIT_FRAGMENT_SHADER = """
                precision mediump float;
                varying vec2 v_texCoord;
                uniform sampler2D u_texture;
                void main() {
                    gl_FragColor = texture2D(u_texture, v_texCoord);
                }
            """

            private const val FRAGMENT_SHADER = """
                #extension GL_OES_standard_derivatives : enable
                precision mediump float;
                varying vec2 v_texCoord;
                uniform vec2 u_resolution;
                uniform float u_time;
                uniform int u_palette;

                vec3 getC0(int p) {
                    if (p == 1) return vec3(0.02, 0.10, 0.14);
                    if (p == 2) return vec3(0.02, 0.10, 0.06);
                    if (p == 3) return vec3(0.02, 0.04, 0.13);
                    if (p == 4) return vec3(0.13, 0.02, 0.05);
                    return vec3(0.08, 0.04, 0.18);
                }

                vec3 getC1(int p) {
                    if (p == 1) return vec3(0.04, 0.19, 0.26);
                    if (p == 2) return vec3(0.04, 0.21, 0.11);
                    if (p == 3) return vec3(0.04, 0.08, 0.24);
                    if (p == 4) return vec3(0.26, 0.04, 0.09);
                    return vec3(0.16, 0.04, 0.33);
                }

                vec3 getC2(int p) {
                    if (p == 1) return vec3(0.05, 0.33, 0.45);
                    if (p == 2) return vec3(0.05, 0.38, 0.20);
                    if (p == 3) return vec3(0.07, 0.16, 0.46);
                    if (p == 4) return vec3(0.46, 0.07, 0.15);
                    return vec3(0.24, 0.01, 0.64);
                }

                vec3 getC3(int p) {
                    if (p == 1) return vec3(0.09, 0.59, 0.75);
                    if (p == 2) return vec3(0.09, 0.66, 0.34);
                    if (p == 3) return vec3(0.13, 0.32, 0.75);
                    if (p == 4) return vec3(0.75, 0.11, 0.25);
                    return vec3(0.37, 0.20, 0.92);
                }

                vec3 getC4(int p) {
                    if (p == 1) return vec3(0.27, 0.83, 1.00);
                    if (p == 2) return vec3(0.28, 0.95, 0.53);
                    if (p == 3) return vec3(0.37, 0.56, 1.00);
                    if (p == 4) return vec3(1.00, 0.37, 0.51);
                    return vec3(0.49, 0.36, 1.00);
                }

                vec3 getSheenTint(int p) {
                    if (p == 1) return vec3(0.20, 0.45, 0.55);
                    if (p == 2) return vec3(0.20, 0.55, 0.35);
                    if (p == 3) return vec3(0.25, 0.35, 0.60);
                    if (p == 4) return vec3(0.60, 0.25, 0.35);
                    return vec3(0.42, 0.30, 0.65);
                }

                float sampleSilk(vec2 pt, float t) {
                    vec2 p = pt;
                    p.x += 0.350 * sin(2.2 * p.y + 0.7 + t * 0.15);
                    p.y += 0.350 * cos(2.2 * p.x + 1.1 + t * 0.12);

                    p.x += 0.175 * sin(4.4 * p.y + 0.7 + t * 0.15);
                    p.y += 0.175 * cos(4.4 * p.x + 1.1 + t * 0.12);

                    p.x += 0.117 * sin(6.6 * p.y + 0.7 + t * 0.15);
                    p.y += 0.117 * cos(6.6 * p.x + 1.1 + t * 0.12);

                    p.x += 0.088 * sin(8.8 * p.y + 0.7 + t * 0.15);
                    p.y += 0.088 * cos(8.8 * p.x + 1.1 + t * 0.12);

                    p.x += 0.070 * sin(11.0 * p.y + 0.7 + t * 0.15);
                    p.y += 0.070 * cos(11.0 * p.x + 1.1 + t * 0.12);

                    float f1 = sin(p.x * 3.2 + p.y * 1.8 + t * 0.10) * 0.5 + 0.5;
                    float f2 = cos(p.x * 2.0 - p.y * 2.6 - t * 0.08) * 0.5 + 0.5;
                    float f3 = sin(p.y * 3.6 + p.x * 1.4 + t * 0.14) * 0.5 + 0.5;

                    float s = f1 * 0.55 + f2 * 0.30 + f3 * 0.15;
                    float diag = sin(pt.x * 0.7 - pt.y * 1.1 + 0.5 + t * 0.06) * 0.5 + 0.5;
                    return s * 0.70 + diag * 0.30;
                }

                void main() {
                    vec2 uv;
                    uv.x = (v_texCoord.x * 2.0 - 1.0) * 2.2;
                    uv.y = (v_texCoord.y * 2.0 - 1.0) * (2.2 * (u_resolution.y / u_resolution.x));

                    float t = mod(u_time, 3600.0);

                    float centerVal = sampleSilk(uv, t);

                    // Native GPU screen-space derivatives: exact normal estimation with ZERO extra trigonometric calls
                    float dx = dFdx(centerVal) * 120.0;
                    float dy = dFdy(centerVal) * 120.0;

                    vec3 norm = normalize(vec3(-dx * 0.7, -dy * 0.7, 1.0));
                    vec3 lightDir = normalize(vec3(0.35, 0.65, 0.75));
                    vec3 viewDir = vec3(0.0, 0.0, 1.0);
                    vec3 halfDir = normalize(lightDir + viewDir);

                    float ndoth = max(dot(norm, halfDir), 0.0);
                    float specular = pow(ndoth, 12.0) * 0.40;

                    float r_val = clamp(centerVal, 0.0, 1.0);

                    vec3 c0 = getC0(u_palette);
                    vec3 c1 = getC1(u_palette);
                    vec3 c2 = getC2(u_palette);
                    vec3 c3 = getC3(u_palette);
                    vec3 c4 = getC4(u_palette);

                    vec3 col;
                    if (r_val < 0.25) {
                        col = mix(c0, c1, smoothstep(0.0, 0.25, r_val));
                    } else if (r_val < 0.55) {
                        col = mix(c1, c2, smoothstep(0.25, 0.55, r_val));
                    } else if (r_val < 0.82) {
                        col = mix(c2, c3, smoothstep(0.55, 0.82, r_val));
                    } else {
                        col = mix(c3, c4, smoothstep(0.82, 1.0, r_val));
                    }

                    col += getSheenTint(u_palette) * specular;

                    // Subtle edge vignette
                    float distSq = uv.x * uv.x + uv.y * uv.y;
                    col *= clamp(1.0 - distSq * 0.05, 0.75, 1.0);

                    // Screen-space triangular dither
                    float dither = (fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) * (1.8 / 255.0);
                    col += dither;

                    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
                }
            """
        }
    }
}
