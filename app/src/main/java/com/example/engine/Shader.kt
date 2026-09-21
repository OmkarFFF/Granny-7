package com.example.engine

import android.opengl.GLES20
import android.util.Log

class HorrorShader {

    companion object {
        private const val TAG = "HorrorShader"

        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uModelMatrix;
            uniform mat4 uNormalMatrix;

            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec4 aColor;

            varying vec3 vWorldPos;
            varying vec3 vNormal;
            varying vec4 vColor;

            void main() {
                vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
                vWorldPos = worldPos.xyz;
                vNormal = normalize((uNormalMatrix * vec4(aNormal, 0.0)).xyz);
                vColor = aColor;
                gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;

            uniform vec3 uCameraPos;
            uniform vec3 uFlashlightDir;
            uniform float uFlashlightActive;
            uniform vec3 uAmbientLight;
            uniform vec3 uMoonLightDir;
            uniform vec3 uMoonLightColor;
            uniform vec3 uHorrorGlowPos;
            uniform vec3 uHorrorGlowColor;
            uniform float uHorrorGlowIntensity;

            uniform vec4 uObjectColor;
            uniform float uUseObjectColor;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;

            varying vec3 vWorldPos;
            varying vec3 vNormal;
            varying vec4 vColor;

            void main() {
                vec4 baseColor = (uUseObjectColor > 0.5) ? uObjectColor : vColor;
                vec3 norm = normalize(vNormal);

                // 1. Ambient lighting (subtle dark blue/warm wood tone)
                vec3 lighting = uAmbientLight;

                // 2. Moonlight (directional soft blue-white)
                float moonDiff = max(dot(norm, -uMoonLightDir), 0.0);
                lighting += uMoonLightColor * moonDiff;

                // 3. Flashlight Spotlight
                if (uFlashlightActive > 0.5) {
                    vec3 toLight = uCameraPos - vWorldPos;
                    float dist = length(toLight);
                    vec3 lightDir = normalize(toLight);

                    // Cosine of angle between flashlight direction and vector to fragment
                    float spotEffect = dot(-lightDir, uFlashlightDir);
                    float innerCutoff = 0.94; // ~19 deg
                    float outerCutoff = 0.82; // ~35 deg

                    if (spotEffect > outerCutoff && dist > 0.1) {
                        float intensity = clamp((spotEffect - outerCutoff) / (innerCutoff - outerCutoff), 0.0, 1.0);
                        float attenuation = 1.0 / (1.0 + 0.07 * dist + 0.015 * dist * dist);
                        float diff = max(dot(norm, lightDir), 0.0);
                        vec3 spotColor = vec3(1.0, 0.96, 0.85); // Warm incandescent flashlight
                        lighting += spotColor * (diff * intensity * attenuation * 2.8);
                    }
                }

                // 4. Eerie Entity Horror Glow (Granny / Sledrina red/crimson point light)
                if (uHorrorGlowIntensity > 0.01) {
                    vec3 toGlow = uHorrorGlowPos - vWorldPos;
                    float glowDist = length(toGlow);
                    if (glowDist < 12.0) {
                        float glowAtten = (1.0 - glowDist / 12.0);
                        lighting += uHorrorGlowColor * (glowAtten * uHorrorGlowIntensity);
                    }
                }

                vec3 finalRgb = baseColor.rgb * lighting;

                // 5. Atmospheric Horror Fog
                float viewDist = length(uCameraPos - vWorldPos);
                float fogFactor = clamp((uFogEnd - viewDist) / (uFogEnd - uFogStart), 0.0, 1.0);
                vec3 finalColorWithFog = mix(uFogColor, finalRgb, fogFactor);

                gl_FragColor = vec4(finalColorWithFog, baseColor.a);
            }
        """
    }

    var program = 0
        private set

    var uMVPMatrixLoc = -1
    var uModelMatrixLoc = -1
    var uNormalMatrixLoc = -1
    var aPositionLoc = -1
    var aNormalLoc = -1
    var aColorLoc = -1

    var uCameraPosLoc = -1
    var uFlashlightDirLoc = -1
    var uFlashlightActiveLoc = -1
    var uAmbientLightLoc = -1
    var uMoonLightDirLoc = -1
    var uMoonLightColorLoc = -1
    var uHorrorGlowPosLoc = -1
    var uHorrorGlowColorLoc = -1
    var uHorrorGlowIntensityLoc = -1

    var uObjectColorLoc = -1
    var uUseObjectColorLoc = -1
    var uFogColorLoc = -1
    var uFogStartLoc = -1
    var uFogEndLoc = -1

    fun create(): Boolean {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        if (vertexShader == 0) return false

        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        if (fragmentShader == 0) return false

        program = GLES20.glCreateProgram()
        if (program == 0) return false

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: " + GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            program = 0
            return false
        }

        // Cache uniform locations
        uMVPMatrixLoc = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uModelMatrixLoc = GLES20.glGetUniformLocation(program, "uModelMatrix")
        uNormalMatrixLoc = GLES20.glGetUniformLocation(program, "uNormalMatrix")

        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aNormalLoc = GLES20.glGetAttribLocation(program, "aNormal")
        aColorLoc = GLES20.glGetAttribLocation(program, "aColor")

        uCameraPosLoc = GLES20.glGetUniformLocation(program, "uCameraPos")
        uFlashlightDirLoc = GLES20.glGetUniformLocation(program, "uFlashlightDir")
        uFlashlightActiveLoc = GLES20.glGetUniformLocation(program, "uFlashlightActive")
        uAmbientLightLoc = GLES20.glGetUniformLocation(program, "uAmbientLight")
        uMoonLightDirLoc = GLES20.glGetUniformLocation(program, "uMoonLightDir")
        uMoonLightColorLoc = GLES20.glGetUniformLocation(program, "uMoonLightColor")
        uHorrorGlowPosLoc = GLES20.glGetUniformLocation(program, "uHorrorGlowPos")
        uHorrorGlowColorLoc = GLES20.glGetUniformLocation(program, "uHorrorGlowColor")
        uHorrorGlowIntensityLoc = GLES20.glGetUniformLocation(program, "uHorrorGlowIntensity")

        uObjectColorLoc = GLES20.glGetUniformLocation(program, "uObjectColor")
        uUseObjectColorLoc = GLES20.glGetUniformLocation(program, "uUseObjectColor")
        uFogColorLoc = GLES20.glGetUniformLocation(program, "uFogColor")
        uFogStartLoc = GLES20.glGetUniformLocation(program, "uFogStart")
        uFogEndLoc = GLES20.glGetUniformLocation(program, "uFogEnd")

        return true
    }

    fun use() {
        GLES20.glUseProgram(program)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader != 0) {
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader $type: " + GLES20.glGetShaderInfoLog(shader))
                GLES20.glDeleteShader(shader)
                return 0
            }
        }
        return shader
    }
}
