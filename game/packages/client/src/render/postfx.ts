/**
 * Post-processing.
 *
 * Hand-rolled rather than three's `EffectComposer` so the whole chain is four small passes
 * with no example-module coupling, and so the volumetric pass can share the very same wall
 * bitmap the world shader marches for its shadows.
 *
 * The grade pass is where the descent lives: bloom, vignette, grain, aberration and the
 * scare response are all uniforms on one shader, which is also why the accessibility
 * scalars are a single number each instead of forty special cases.
 */

import {
  BufferGeometry,
  Camera,
  Color,
  DepthTexture,
  Float32BufferAttribute,
  LinearFilter,
  Mesh,
  NearestFilter,
  OrthographicCamera,
  PerspectiveCamera,
  RGBAFormat,
  Scene,
  ShaderMaterial,
  Texture,
  UnsignedIntType,
  Vector2,
  Vector3,
  Vector4,
  WebGLRenderTarget,
  WebGLRenderer,
} from 'three';

function fullscreenQuad(): BufferGeometry {
  const geometry = new BufferGeometry();
  geometry.setAttribute('position', new Float32BufferAttribute([-1, -1, 0, 3, -1, 0, -1, 3, 0], 3));
  geometry.setAttribute('uv', new Float32BufferAttribute([0, 0, 2, 0, 0, 2], 2));
  return geometry;
}

const QUAD_VERTEX = /* glsl */ `
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = vec4(position, 1.0);
  }
`;

const BRIGHT_FRAGMENT = /* glsl */ `
  uniform sampler2D tDiffuse;
  uniform float uThreshold;
  varying vec2 vUv;
  void main() {
    vec3 c = texture2D(tDiffuse, vUv).rgb;
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float contribution = max(0.0, luma - uThreshold) / max(luma, 0.0001);
    gl_FragColor = vec4(c * contribution, 1.0);
  }
`;

const BLUR_FRAGMENT = /* glsl */ `
  uniform sampler2D tDiffuse;
  uniform vec2 uDirection;
  varying vec2 vUv;
  void main() {
    // Nine-tap gaussian, separable.
    vec3 sum = texture2D(tDiffuse, vUv).rgb * 0.227027;
    sum += texture2D(tDiffuse, vUv + uDirection * 1.3846).rgb * 0.316216;
    sum += texture2D(tDiffuse, vUv - uDirection * 1.3846).rgb * 0.316216;
    sum += texture2D(tDiffuse, vUv + uDirection * 3.2308).rgb * 0.070270;
    sum += texture2D(tDiffuse, vUv - uDirection * 3.2308).rgb * 0.070270;
    gl_FragColor = vec4(sum, 1.0);
  }
`;

/**
 * Screen-space volumetric scattering for the flashlight.
 *
 * Marches the view ray, evaluating the same cone and the same wall occlusion the surface
 * shader uses, so the shaft of light in the air agrees with the pool of light on the
 * floor. Dithered with a noise texture and run at half resolution, which is what keeps a
 * sixteen-step march affordable.
 */
const VOLUMETRIC_FRAGMENT = /* glsl */ `
  precision highp float;

  uniform sampler2D tDepth;
  uniform sampler2D uWallMask;
  uniform sampler2D uNoise;
  uniform mat4 uInverseProjection;
  uniform mat4 uInverseView;
  uniform vec3 uCamPos;
  uniform vec2 uGridSize;
  uniform float uTileSize;
  uniform float uNear;
  uniform float uFar;
  uniform float uTime;
  uniform float uStrength;

  uniform vec3 uLightPos;
  uniform vec3 uLightDir;
  uniform vec3 uLightCol;
  uniform vec4 uLightParam; // range, cosInner, cosOuter, unused

  varying vec2 vUv;

  float linearDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    return (2.0 * uNear * uFar) / (uFar + uNear - z * (uFar - uNear));
  }

  bool insideWall(vec3 p) {
    vec2 uv = (p.xz / uTileSize + uGridSize * 0.5) / uGridSize;
    return texture2D(uWallMask, uv).r > 0.5;
  }

  void main() {
    float depth = texture2D(tDepth, vUv).x;
    float sceneDistance = depth >= 1.0 ? uFar : linearDepth(depth);

    // Reconstruct the view ray for this pixel.
    vec4 clip = vec4(vUv * 2.0 - 1.0, -1.0, 1.0);
    vec4 viewPos = uInverseProjection * clip;
    viewPos /= viewPos.w;
    vec3 rayView = normalize(viewPos.xyz);
    vec3 rayWorld = normalize((uInverseView * vec4(rayView, 0.0)).xyz);
    // Depth is measured along the view axis, not along the ray.
    float cosAngle = max(-rayView.z, 0.0001);
    float maxDist = min(sceneDistance / cosAngle, uLightParam.x * 1.4);

    float steps = 16.0;
    float stepSize = maxDist / steps;
    // Dither the start offset so banding turns into noise, which the grain then hides.
    float jitter = texture2D(uNoise, vUv * 37.0 + vec2(uTime * 0.37, uTime * 0.19)).r;

    vec3 accum = vec3(0.0);
    for (int i = 0; i < 16; i++) {
      float t = (float(i) + jitter) * stepSize;
      if (t > maxDist) break;
      vec3 p = uCamPos + rayWorld * t;
      if (insideWall(p)) continue;

      vec3 toLight = uLightPos - p;
      float dist = length(toLight);
      if (dist > uLightParam.x) continue;
      vec3 L = toLight / max(dist, 0.0001);

      float cone = smoothstep(uLightParam.z, uLightParam.y, dot(-L, normalize(uLightDir)));
      if (cone <= 0.0) continue;

      float attenuation = 1.0 - dist / uLightParam.x;
      attenuation *= attenuation;

      // Cheap occlusion: three taps along the segment toward the light are enough to cut
      // the shaft at a doorframe without a second full march per step.
      float blocked = 0.0;
      for (int s = 1; s <= 3; s++) {
        if (insideWall(p + toLight * (float(s) / 4.0))) { blocked = 1.0; break; }
      }
      if (blocked > 0.5) continue;

      accum += uLightCol * cone * attenuation * stepSize;
    }

    gl_FragColor = vec4(accum * uStrength, 1.0);
  }
`;

const GRADE_FRAGMENT = /* glsl */ `
  precision highp float;

  uniform sampler2D tScene;
  uniform sampler2D tBloom;
  uniform sampler2D tVolumetric;
  uniform sampler2D uNoise;
  uniform vec2 uResolution;
  uniform float uTime;

  uniform float uExposure;
  uniform float uBloom;
  uniform float uVignette;
  uniform float uGrain;
  uniform float uAberration;
  uniform float uSanity;
  uniform float uScare;
  uniform float uVhs;
  uniform vec3 uScareTint;

  varying vec2 vUv;

  /**
   * Filmic tonemap. Everything upstream works in linear light, where lighting genuinely
   * multiplies; without a rolloff here the bright opening clips straight to neon and the
   * whole "too cheerful" idea reads as a broken shader instead.
   */
  vec3 tonemap(vec3 x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
  }

  vec3 linearToSrgb(vec3 c) {
    return mix(1.055 * pow(max(c, vec3(0.0)), vec3(1.0 / 2.4)) - 0.055, c * 12.92, step(c, vec3(0.0031308)));
  }

  void main() {
    vec2 uv = vUv;
    vec2 centred = uv - 0.5;

    // Low sanity warps the frame: a slow barrel wobble that the player notices before they
    // can name it.
    float insanity = 1.0 - uSanity;
    if (insanity > 0.01) {
      float wobble = sin(uTime * 1.7 + uv.y * 9.0) * 0.0016 + sin(uTime * 0.9 + uv.x * 6.0) * 0.0012;
      uv += centred * insanity * (0.012 * dot(centred, centred)) + wobble * insanity;
    }

    // A scare kicks the frame outward for a fraction of a second.
    if (uScare > 0.001) {
      uv += centred * uScare * 0.035;
    }

    // Horizontal tear, late descent only.
    if (uVhs > 0.001) {
      float band = step(0.985, fract(uv.y * 3.0 + uTime * 0.7));
      uv.x += band * uVhs * 0.02 * (texture2D(uNoise, vec2(uTime * 0.5, uv.y)).r - 0.5);
    }

    float aberration = uAberration / uResolution.x * (1.0 + uScare * 3.0);
    vec3 color;
    if (aberration > 0.0001) {
      color.r = texture2D(tScene, uv + centred * aberration * 2.0).r;
      color.g = texture2D(tScene, uv).g;
      color.b = texture2D(tScene, uv - centred * aberration * 2.0).b;
    } else {
      color = texture2D(tScene, uv).rgb;
    }

    color += texture2D(tVolumetric, uv).rgb;
    color += texture2D(tBloom, uv).rgb * uBloom;

    // Vignette: barely there under the sun, oppressive by the end.
    float vignette = 1.0 - uVignette * dot(centred, centred) * 2.4;
    color *= clamp(vignette, 0.0, 1.0);

    color = mix(color, color * uScareTint, uScare);

    // Tonemap and encode last, so grain sits on the displayed image rather than being
    // squashed by the rolloff. Exposure opens up as the level darkens — the world loses
    // light but the eye adapts, which keeps late-descent play readable without ever making
    // it feel bright.
    color = linearToSrgb(tonemap(color * uExposure));

    if (uGrain > 0.0001) {
      float noise = texture2D(uNoise, uv * uResolution / 64.0 + vec2(uTime * 13.7, uTime * 7.3)).g;
      color += (noise - 0.5) * uGrain;
    }

    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
  }
`;

export interface GradeParams {
  exposure: number;
  bloom: number;
  vignette: number;
  grain: number;
  aberration: number;
  sanity: number;
  scare: number;
  vhs: number;
}

export interface VolumetricParams {
  enabled: boolean;
  position: Vector3;
  direction: Vector3;
  color: Color;
  range: number;
  cosInner: number;
  cosOuter: number;
  strength: number;
}

export class Composer {
  private readonly renderer: WebGLRenderer;
  private readonly quadScene = new Scene();
  private readonly quadCamera = new OrthographicCamera(-1, 1, 1, -1, 0, 1);
  private readonly quadMesh: Mesh;

  private sceneTarget!: WebGLRenderTarget;
  private bloomA!: WebGLRenderTarget;
  private bloomB!: WebGLRenderTarget;
  private volumetricTarget!: WebGLRenderTarget;

  private readonly brightMaterial: ShaderMaterial;
  private readonly blurMaterial: ShaderMaterial;
  private readonly volumetricMaterial: ShaderMaterial;
  private readonly gradeMaterial: ShaderMaterial;

  private width = 1;
  private height = 1;

  constructor(renderer: WebGLRenderer, noise: Texture, wallMask: Texture, gridSize: Vector2, tileSize: number) {
    this.renderer = renderer;
    const geometry = fullscreenQuad();
    this.quadMesh = new Mesh(geometry);
    this.quadMesh.frustumCulled = false;
    this.quadScene.add(this.quadMesh);

    this.brightMaterial = new ShaderMaterial({
      vertexShader: QUAD_VERTEX,
      fragmentShader: BRIGHT_FRAGMENT,
      // High enough that only the fixtures themselves bloom. A low threshold makes every
      // lit wall glow, which reads as fog rather than as light sources.
      uniforms: { tDiffuse: { value: null }, uThreshold: { value: 0.92 } },
      depthTest: false,
      depthWrite: false,
    });

    this.blurMaterial = new ShaderMaterial({
      vertexShader: QUAD_VERTEX,
      fragmentShader: BLUR_FRAGMENT,
      uniforms: { tDiffuse: { value: null }, uDirection: { value: new Vector2() } },
      depthTest: false,
      depthWrite: false,
    });

    this.volumetricMaterial = new ShaderMaterial({
      vertexShader: QUAD_VERTEX,
      fragmentShader: VOLUMETRIC_FRAGMENT,
      uniforms: {
        tDepth: { value: null },
        uWallMask: { value: wallMask },
        uNoise: { value: noise },
        uInverseProjection: { value: null },
        uInverseView: { value: null },
        uCamPos: { value: new Vector3() },
        uGridSize: { value: gridSize },
        uTileSize: { value: tileSize },
        uNear: { value: 0.1 },
        uFar: { value: 200 },
        uTime: { value: 0 },
        uStrength: { value: 0.35 },
        uLightPos: { value: new Vector3() },
        uLightDir: { value: new Vector3(0, 0, -1) },
        uLightCol: { value: new Vector3(1, 0.95, 0.85) },
        uLightParam: { value: new Vector4(22, 0.93, 0.75, 0) },
      },
      depthTest: false,
      depthWrite: false,
    });

    this.gradeMaterial = new ShaderMaterial({
      vertexShader: QUAD_VERTEX,
      fragmentShader: GRADE_FRAGMENT,
      uniforms: {
        tScene: { value: null },
        tBloom: { value: null },
        tVolumetric: { value: null },
        uNoise: { value: noise },
        uResolution: { value: new Vector2(1, 1) },
        uTime: { value: 0 },
        uExposure: { value: 0.55 },
        uBloom: { value: 0.8 },
        uVignette: { value: 0.1 },
        uGrain: { value: 0.02 },
        uAberration: { value: 0 },
        uSanity: { value: 1 },
        uScare: { value: 0 },
        uVhs: { value: 0 },
        uScareTint: { value: new Vector3(1.25, 0.7, 0.7) },
      },
      depthTest: false,
      depthWrite: false,
    });

    this.setSize(1, 1);
  }

  setSize(width: number, height: number): void {
    const w = Math.max(1, Math.floor(width));
    const h = Math.max(1, Math.floor(height));
    if (w === this.width && h === this.height && this.sceneTarget) return;
    this.width = w;
    this.height = h;

    this.sceneTarget?.dispose();
    this.bloomA?.dispose();
    this.bloomB?.dispose();
    this.volumetricTarget?.dispose();

    const depthTexture = new DepthTexture(w, h);
    depthTexture.type = UnsignedIntType;
    this.sceneTarget = new WebGLRenderTarget(w, h, {
      format: RGBAFormat,
      minFilter: LinearFilter,
      magFilter: LinearFilter,
      depthTexture,
      depthBuffer: true,
      stencilBuffer: false,
    });

    const bw = Math.max(1, w >> 2);
    const bh = Math.max(1, h >> 2);
    const bloomOptions = { format: RGBAFormat, minFilter: LinearFilter, magFilter: LinearFilter, depthBuffer: false };
    this.bloomA = new WebGLRenderTarget(bw, bh, bloomOptions);
    this.bloomB = new WebGLRenderTarget(bw, bh, bloomOptions);

    this.volumetricTarget = new WebGLRenderTarget(Math.max(1, w >> 1), Math.max(1, h >> 1), {
      format: RGBAFormat,
      minFilter: LinearFilter,
      magFilter: LinearFilter,
      depthBuffer: false,
    });

    (this.gradeMaterial.uniforms.uResolution.value as Vector2).set(w, h);
  }

  get renderTarget(): WebGLRenderTarget {
    return this.sceneTarget;
  }

  private blit(material: ShaderMaterial, target: WebGLRenderTarget | null): void {
    this.quadMesh.material = material;
    this.renderer.setRenderTarget(target);
    this.renderer.render(this.quadScene, this.quadCamera);
  }

  /** Draw calls and triangles for the world pass only, without the post-processing blits. */
  sceneDrawCalls = 0;
  sceneTriangles = 0;

  /** Renders the scene into the internal target; call before {@link present}. */
  renderScene(scene: Scene, camera: Camera): void {
    this.renderer.setRenderTarget(this.sceneTarget);
    this.renderer.clear();
    this.renderer.render(scene, camera);
    // Read the counters here: three resets them on every render call, and the post passes
    // that follow would otherwise report a permanent "1 draw, 1 triangle".
    this.sceneDrawCalls = this.renderer.info.render.calls;
    this.sceneTriangles = this.renderer.info.render.triangles;
  }

  present(camera: PerspectiveCamera, time: number, grade: GradeParams, volumetric: VolumetricParams): void {
    // --- Volumetric ---
    if (volumetric.enabled) {
      const u = this.volumetricMaterial.uniforms;
      u.tDepth.value = this.sceneTarget.depthTexture;
      u.uInverseProjection.value = camera.projectionMatrixInverse;
      u.uInverseView.value = camera.matrixWorld;
      (u.uCamPos.value as Vector3).copy(camera.position);
      u.uNear.value = camera.near;
      u.uFar.value = camera.far;
      u.uTime.value = time;
      u.uStrength.value = volumetric.strength;
      (u.uLightPos.value as Vector3).copy(volumetric.position);
      (u.uLightDir.value as Vector3).copy(volumetric.direction);
      (u.uLightCol.value as Vector3).set(volumetric.color.r, volumetric.color.g, volumetric.color.b);
      (u.uLightParam.value as Vector4).set(volumetric.range, volumetric.cosInner, volumetric.cosOuter, 0);
      this.blit(this.volumetricMaterial, this.volumetricTarget);
    } else {
      this.renderer.setRenderTarget(this.volumetricTarget);
      this.renderer.setClearColor(0x000000, 1);
      this.renderer.clear();
    }

    // --- Bloom ---
    if (grade.bloom > 0.001) {
      this.brightMaterial.uniforms.tDiffuse.value = this.sceneTarget.texture;
      this.blit(this.brightMaterial, this.bloomA);

      const texel = new Vector2(1 / this.bloomA.width, 1 / this.bloomA.height);
      this.blurMaterial.uniforms.tDiffuse.value = this.bloomA.texture;
      (this.blurMaterial.uniforms.uDirection.value as Vector2).set(texel.x, 0);
      this.blit(this.blurMaterial, this.bloomB);

      this.blurMaterial.uniforms.tDiffuse.value = this.bloomB.texture;
      (this.blurMaterial.uniforms.uDirection.value as Vector2).set(0, texel.y);
      this.blit(this.blurMaterial, this.bloomA);
    } else {
      this.renderer.setRenderTarget(this.bloomA);
      this.renderer.setClearColor(0x000000, 1);
      this.renderer.clear();
    }

    // --- Grade to screen ---
    const g = this.gradeMaterial.uniforms;
    g.tScene.value = this.sceneTarget.texture;
    g.tBloom.value = this.bloomA.texture;
    g.tVolumetric.value = this.volumetricTarget.texture;
    g.uTime.value = time;
    g.uExposure.value = grade.exposure;
    g.uBloom.value = grade.bloom;
    g.uVignette.value = grade.vignette;
    g.uGrain.value = grade.grain;
    g.uAberration.value = grade.aberration;
    g.uSanity.value = grade.sanity;
    g.uScare.value = grade.scare;
    g.uVhs.value = grade.vhs;
    this.blit(this.gradeMaterial, null);
  }

  dispose(): void {
    this.sceneTarget.dispose();
    this.bloomA.dispose();
    this.bloomB.dispose();
    this.volumetricTarget.dispose();
    this.brightMaterial.dispose();
    this.blurMaterial.dispose();
    this.volumetricMaterial.dispose();
    this.gradeMaterial.dispose();
  }
}
