/**
 * The world shader.
 *
 * Two decisions carry the whole look:
 *
 * 1. Static light is a propagated grid sampled as a texture, not light sources. A floor
 *    has hundreds of fluorescent fixtures; as real lights that is impossible, as a texture
 *    lookup it is free and spills through doorways in exactly the right shape.
 *
 * 2. Dynamic lights (the flashlight) get shadows without shadow maps, by marching the wall
 *    bitmap between the fragment and the light. That single detail — a beam that is cut off
 *    by a doorframe and wraps around a corner — is what makes the place read as real.
 *
 * Every palette uniform is driven by the descent value, so the transition from rainbow to
 * rot is one continuous interpolation rather than a set of swapped materials.
 */

import { Color, DoubleSide, ShaderMaterial, Texture, Vector2, Vector3, Vector4 } from 'three';

export const MAX_DYNAMIC_LIGHTS = 8;

export interface WorldMaterialOptions {
  wall: Texture;
  carpet: Texture;
  ceiling: Texture;
  lightGrid: Texture;
  wallMask: Texture;
  /** 256x1 red texture holding each room's theme-shift stage in [0, 1]. */
  roomRot: Texture;
  gridWidth: number;
  gridHeight: number;
  tileSize: number;
}

const VERTEX = /* glsl */ `
  attribute float aSurface;
  attribute float aRoom;

  varying vec3 vWorld;
  varying vec3 vNormal;
  varying vec2 vUv;
  varying float vSurface;
  varying float vRoom;

  void main() {
    vec4 world = modelMatrix * vec4(position, 1.0);
    vWorld = world.xyz;
    vNormal = normalize(mat3(modelMatrix) * normal);
    vUv = uv;
    vSurface = aSurface;
    vRoom = aRoom;
    gl_Position = projectionMatrix * viewMatrix * world;
  }
`;

const FRAGMENT = /* glsl */ `
  #define MAX_LIGHTS ${MAX_DYNAMIC_LIGHTS}

  precision highp float;

  uniform sampler2D uWallTex;
  uniform sampler2D uCarpetTex;
  uniform sampler2D uCeilTex;
  uniform sampler2D uLightGrid;
  uniform sampler2D uWallMask;

  uniform vec2 uGridSize;
  uniform float uTileSize;

  uniform vec3 uWallA;
  uniform vec3 uWallB;
  uniform vec3 uCarpetCol;
  uniform vec3 uCeilCol;
  uniform vec3 uTrimCol;
  uniform vec3 uAmbient;
  uniform vec3 uStaticLightCol;
  uniform float uStaticLightIntensity;
  uniform float uRainbow;
  uniform float uRot;
  uniform float uSaturation;

  uniform vec3 uFogColor;
  uniform float uFogDensity;
  uniform vec3 uCamPos;

  uniform int uLightCount;
  uniform vec3 uLightPos[MAX_LIGHTS];
  uniform vec3 uLightDir[MAX_LIGHTS];
  uniform vec3 uLightCol[MAX_LIGHTS];
  uniform vec4 uLightParam[MAX_LIGHTS];

  uniform float uTime;
  uniform float uFlicker;
  /**
   * Per-room theme-shift stage, as a 256x1 texture rather than a uniform array: GLSL ES
   * 1.0 does not portably allow dynamic indexing into uniform arrays, and a level can have
   * more rooms than any array size worth hard-coding.
   */
  uniform sampler2D uRoomRotTex;

  varying vec3 vWorld;
  varying vec3 vNormal;
  varying vec2 vUv;
  varying float vSurface;
  varying float vRoom;

  vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
  }

  vec2 worldToGridUv(vec2 worldXZ) {
    vec2 tileF = worldXZ / uTileSize + uGridSize * 0.5;
    return tileF / uGridSize;
  }

  /**
   * Marches the wall bitmap from the fragment toward a light. Fixed-step rather than a
   * true DDA: at two metres per tile, twenty steps over the flashlight's range samples
   * well below tile resolution, and the uniform stride keeps the loop cheap.
   */
  float lightVisibility(vec3 from, vec3 to, float steps) {
    vec2 a = from.xz / uTileSize + uGridSize * 0.5;
    vec2 b = to.xz / uTileSize + uGridSize * 0.5;
    vec2 delta = b - a;
    float n = max(steps, 1.0);
    for (int i = 1; i < 32; i++) {
      if (float(i) >= n) break;
      vec2 p = a + delta * (float(i) / n);
      if (texture2D(uWallMask, p / uGridSize).r > 0.5) return 0.0;
    }
    return 1.0;
  }

  void main() {
    float isWall = step(0.5, vSurface) * step(vSurface, 1.5);
    float isCeil = step(1.5, vSurface);
    float isFloor = 1.0 - isWall - isCeil;

    vec4 wallTex = texture2D(uWallTex, vUv);
    vec4 carpetTex = texture2D(uCarpetTex, vUv);
    vec4 ceilTex = texture2D(uCeilTex, vUv);

    vec4 tex = wallTex * isWall + carpetTex * isFloor + ceilTex * isCeil;

    // --- Base colour -------------------------------------------------------
    // At descent 0 each wallpaper stripe gets its own pastel hue; as the rot rises they
    // collapse toward a single sick colour. The geometry never changes, only this mix.
    float roomRot = texture2D(uRoomRotTex, vec2((vRoom + 0.5) / 256.0, 0.5)).r;
    float rot = clamp(uRot + roomRot * 0.35, 0.0, 1.0);
    float rainbow = clamp(uRainbow - roomRot * 0.5, 0.0, 1.0);

    // Pastel, not primary. Saturated hues per stripe read as a clown suit rather than as
    // wallpaper that is trying a little too hard to be pleasant.
    vec3 pastel = hsv2rgb(vec3(fract(tex.g * 1.7 + 0.05), 0.21, 1.0));
    pastel *= pastel; // approximate sRGB to linear, matching the palette uniforms
    vec3 wallBase = mix(uWallA, pastel * 0.92, rainbow * 0.6);
    wallBase = mix(wallBase, uWallB, step(0.5, fract(tex.g * 4.0)) * 0.28);

    vec3 base = wallBase * isWall + uCarpetCol * isFloor + uCeilCol * isCeil;

    // Grime darkens and desaturates as the level rots.
    float grime = tex.b * rot;
    vec3 grimeCol = mix(base, vec3(0.10, 0.09, 0.07), 0.75);
    base = mix(base, grimeCol, grime * 0.85);

    // Skirting board where a wall meets the floor.
    float skirt = isWall * (1.0 - smoothstep(0.0, 0.06, vUv.y));
    base = mix(base, uTrimCol, skirt * 0.8);

    // Detail modulates the palette colour without ever brightening past it.
    vec3 albedo = base * (0.32 + tex.r * 0.68);

    // --- Static light grid -------------------------------------------------
    // Sampled slightly along the normal so a wall reads the light of the room it faces
    // rather than the dark interior of the wall it belongs to.
    vec2 gridUv = worldToGridUv(vWorld.xz + vNormal.xz * uTileSize * 0.55);
    float grid = texture2D(uLightGrid, gridUv).r;
    float flicker = 1.0 - uFlicker * 0.35 * step(0.5, fract(uTime * 7.3 + vRoom * 3.1));
    vec3 lighting = uStaticLightCol * grid * uStaticLightIntensity * flicker;

    // Fake normal response so flat surfaces are not uniformly flat.
    float facing = 0.72 + 0.28 * abs(vNormal.y);
    lighting *= facing;

    // --- Dynamic lights ----------------------------------------------------
    for (int i = 0; i < MAX_LIGHTS; i++) {
      if (i >= uLightCount) break;
      vec3 toLight = uLightPos[i] - vWorld;
      float dist = length(toLight);
      float range = uLightParam[i].x;
      if (dist > range) continue;
      vec3 L = toLight / max(dist, 0.0001);

      float ndotl = max(dot(vNormal, L), 0.0);
      if (ndotl <= 0.0) continue;

      float attenuation = 1.0 - dist / range;
      attenuation *= attenuation;

      float cone = 1.0;
      float cosInner = uLightParam[i].y;
      if (cosInner < 0.999) {
        float cosAngle = dot(normalize(-L), normalize(uLightDir[i]));
        cone = smoothstep(uLightParam[i].z, cosInner, cosAngle);
        if (cone <= 0.0) continue;
      }

      float shadow = lightVisibility(vWorld + vNormal * 0.25, uLightPos[i], uLightParam[i].w);
      lighting += uLightCol[i] * ndotl * attenuation * cone * shadow;
    }

    vec3 color = albedo * (lighting + uAmbient);

    // --- Fog ---------------------------------------------------------------
    // The same exponential fog is warm white sun haze at descent 0 and near-black at 1.
    // One constant, opposite feelings.
    float dist = length(vWorld - uCamPos);
    float fogFactor = exp(-pow(dist * uFogDensity, 2.0));
    color = mix(uFogColor, color, clamp(fogFactor, 0.0, 1.0));

    // Saturation is pushed above 1 early on; the world is deliberately too cheerful.
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luma), color, uSaturation);

    gl_FragColor = vec4(color, 1.0);
  }
`;

export function createWorldMaterial(options: WorldMaterialOptions): ShaderMaterial {
  return new ShaderMaterial({
    vertexShader: VERTEX,
    fragmentShader: FRAGMENT,
    side: DoubleSide,
    uniforms: {
      uWallTex: { value: options.wall },
      uCarpetTex: { value: options.carpet },
      uCeilTex: { value: options.ceiling },
      uLightGrid: { value: options.lightGrid },
      uWallMask: { value: options.wallMask },
      uGridSize: { value: new Vector2(options.gridWidth, options.gridHeight) },
      uTileSize: { value: options.tileSize },

      uWallA: { value: new Color(0xffe9a8) },
      uWallB: { value: new Color(0xffc9de) },
      uCarpetCol: { value: new Color(0xf7b45f) },
      uCeilCol: { value: new Color(0xfff6e0) },
      uTrimCol: { value: new Color(0xfff2c4) },
      uAmbient: { value: new Color(0x6b5f42) },
      uStaticLightCol: { value: new Color(0xfff3d0) },
      uStaticLightIntensity: { value: 1 },
      uRainbow: { value: 1 },
      uRot: { value: 0 },
      uSaturation: { value: 1.35 },

      uFogColor: { value: new Color(0xfff0cf) },
      uFogDensity: { value: 0.012 },
      uCamPos: { value: new Vector3() },

      uLightCount: { value: 0 },
      uLightPos: { value: Array.from({ length: MAX_DYNAMIC_LIGHTS }, () => new Vector3()) },
      uLightDir: { value: Array.from({ length: MAX_DYNAMIC_LIGHTS }, () => new Vector3(0, 0, -1)) },
      uLightCol: { value: Array.from({ length: MAX_DYNAMIC_LIGHTS }, () => new Vector3()) },
      uLightParam: { value: Array.from({ length: MAX_DYNAMIC_LIGHTS }, () => new Vector4(1, 1, 1, 0)) },

      uTime: { value: 0 },
      uFlicker: { value: 0 },
      uRoomRotTex: { value: options.roomRot },
    },
  });
}
