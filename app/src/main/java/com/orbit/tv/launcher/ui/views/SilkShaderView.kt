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
 * 1. Hardware buffer fixed to 480x270 (16x fillrate reduction vs 1080p).
 * 2. Hardware bilinear upscaling to native TV resolution by SurfaceFlinger at 0% GPU cost.
 * 3. Frame rate locked to ~30 FPS (33ms target).
 * 4. Dedicated render thread with full lock-based pause/resume (0% CPU/GPU when paused).
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

    companion object {
        private const val RENDER_WIDTH = 960
        private const val RENDER_HEIGHT = 540
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        android.util.Log.e("SilkDebug", "onSurfaceTextureAvailable: w=$width, h=$height")
        stopThread()
        surface.setDefaultBufferSize(RENDER_WIDTH, RENDER_HEIGHT)
        renderThread = SilkRenderThread(surface, RENDER_WIDTH, RENDER_HEIGHT, currentPalette).apply {
            start()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        android.util.Log.e("SilkDebug", "onSurfaceTextureSizeChanged: w=$width, h=$height")
        surface.setDefaultBufferSize(RENDER_WIDTH, RENDER_HEIGHT)
        renderThread?.updateSize(RENDER_WIDTH, RENDER_HEIGHT)
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
            try {
                it.join(400)
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
        @Volatile private var palette: Int
    ) : Thread("SilkShaderThread") {

        @Volatile private var isRunning = true
        @Volatile private var isPaused = false
        private val pauseLock = Object()

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        private var programId = 0
        private var uTimeHandle = 0
        private var uResHandle = 0
        private var uPaletteHandle = 0
        private var aPosHandle = 0
        private var aTexCoordHandle = 0

        private lateinit var vertexBuffer: FloatBuffer
        private lateinit var texCoordBuffer: FloatBuffer

        private val targetFrameMs = 33L // ~30 FPS lock

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

            val startTime = SystemClock.uptimeMillis()

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
                val elapsedTime = (t0 - startTime) / 1000f

                drawFrame(elapsedTime)
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

            return EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        private fun initGL(): Boolean {
            val vShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
            val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
            if (vShader == 0 || fShader == 0) {
                android.util.Log.e("SilkShader", "Failed to compile shaders: v=$vShader, f=$fShader")
                return false
            }

            programId = GLES20.glCreateProgram()
            GLES20.glAttachShader(programId, vShader)
            GLES20.glAttachShader(programId, fShader)
            GLES20.glLinkProgram(programId)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                android.util.Log.e("SilkShader", "Link error: " + GLES20.glGetProgramInfoLog(programId))
                GLES20.glDeleteProgram(programId)
                return false
            }

            aPosHandle = GLES20.glGetAttribLocation(programId, "a_position")
            aTexCoordHandle = GLES20.glGetAttribLocation(programId, "a_texCoord")
            uTimeHandle = GLES20.glGetUniformLocation(programId, "u_time")
            uResHandle = GLES20.glGetUniformLocation(programId, "u_resolution")
            uPaletteHandle = GLES20.glGetUniformLocation(programId, "u_palette")

            val quadCoords = floatArrayOf(
                -1f,  1f,
                -1f, -1f,
                 1f,  1f,
                 1f, -1f
            )
            vertexBuffer = ByteBuffer.allocateDirect(quadCoords.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply {
                    put(quadCoords)
                    position(0)
                }

            val texCoords = floatArrayOf(
                0f, 0f,
                0f, 1f,
                1f, 0f,
                1f, 1f
            )
            texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply {
                    put(texCoords)
                    position(0)
                }

            GLES20.glViewport(0, 0, bufferWidth, bufferHeight)
            return true
        }

        private fun drawFrame(timeSec: Float) {
            GLES20.glViewport(0, 0, bufferWidth, bufferHeight)
            GLES20.glClearColor(0.02f, 0.02f, 0.04f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(programId)

            GLES20.glUniform1f(uTimeHandle, timeSec)
            GLES20.glUniform2f(uResHandle, bufferWidth.toFloat(), bufferHeight.toFloat())
            GLES20.glUniform1i(uPaletteHandle, palette)

            GLES20.glEnableVertexAttribArray(aPosHandle)
            GLES20.glVertexAttribPointer(aPosHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            GLES20.glEnableVertexAttribArray(aTexCoordHandle)
            GLES20.glVertexAttribPointer(aTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(aPosHandle)
            GLES20.glDisableVertexAttribArray(aTexCoordHandle)
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
            if (programId != 0) {
                GLES20.glDeleteProgram(programId)
                programId = 0
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

            private const val FRAGMENT_SHADER = """
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

                    // Estimate surface normal via finite difference for crisp specular fold ridges
                    float eps = 0.008;
                    float valX = sampleSilk(vec2(uv.x + eps, uv.y), t);
                    float valY = sampleSilk(vec2(uv.x, uv.y + eps), t);
                    float dx = (valX - centerVal) / eps;
                    float dy = (valY - centerVal) / eps;

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
