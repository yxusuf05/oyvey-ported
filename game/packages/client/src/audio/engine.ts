/**
 * Procedural audio.
 *
 * Every sound in the game is synthesised — there are no audio files. The architecture that
 * makes that affordable is sends: three convolvers with generated impulse responses serve
 * every source, instead of one reverb per voice.
 *
 * Two things here carry most of the atmosphere. The drone is a single patch whose detune
 * and partial stack are driven by the descent, taking it from "warm pad" to "wrong"
 * without ever cutting. And the music box motif that plays over the bright opening comes
 * back at the end detuned, slowed and reversed through the long reverb — recognising the
 * cheerful tune in its rotted form is the strongest tonal payoff available, and it costs
 * one parameter ramp.
 *
 * Sound is also gameplay information, so every cue registers a subtitle key. Captions are
 * not an afterthought bolted on later; nothing can play without one.
 */

import { clamp, clamp01, lerp } from '@game/shared/math';

export interface AudioSettings {
  master: number;
  sfx: number;
  music: number;
  ambience: number;
}

export type SubtitleListener = (key: string) => void;

type ZoneName = 'small' | 'corridor' | 'hall';

/** Generates an impulse response: filtered noise with an exponential decay. */
function makeImpulseResponse(ctx: BaseAudioContext, seconds: number, decay: number, preDelay: number): AudioBuffer {
  const rate = ctx.sampleRate;
  const length = Math.max(1, Math.floor(rate * seconds));
  const delaySamples = Math.floor(rate * preDelay);
  const buffer = ctx.createBuffer(2, length, rate);
  for (let channel = 0; channel < 2; channel++) {
    const data = buffer.getChannelData(channel);
    let lowpass = 0;
    for (let i = 0; i < length; i++) {
      if (i < delaySamples) {
        data[i] = 0;
        continue;
      }
      const t = (i - delaySamples) / (length - delaySamples);
      const envelope = Math.pow(1 - t, decay);
      const white = Math.random() * 2 - 1;
      // A gentle one-pole lowpass gives the tail the darker character of a real room
      // rather than the hiss of raw noise.
      lowpass += (white - lowpass) * 0.36;
      data[i] = lowpass * envelope;
    }
  }
  return buffer;
}

const SCALE_MAJOR = [0, 2, 4, 7, 9, 12, 14, 16];
/** The opening motif, in scale degrees. Simple enough to recognise when it comes back wrong. */
const MOTIF = [0, 2, 4, 3, 5, 4, 2, 0, 4, 5, 7, 5, 4, 2, 1, 0];

export class AudioEngine {
  private ctx: AudioContext | null = null;
  private master!: GainNode;
  private limiter!: DynamicsCompressorNode;
  private sfxBus!: GainNode;
  private musicBus!: GainNode;
  private ambienceBus!: GainNode;
  private zoneSends: Record<ZoneName, GainNode> = {} as Record<ZoneName, GainNode>;

  private droneGain!: GainNode;
  private droneOscillators: OscillatorNode[] = [];
  private droneFilter!: BiquadFilterNode;

  private schedulerTimer: ReturnType<typeof setInterval> | null = null;
  private nextNoteTime = 0;
  private motifIndex = 0;

  private descent = 0;
  private sanity = 1;
  private settings: AudioSettings = { master: 0.9, sfx: 1, music: 0.7, ambience: 0.85 };
  private scareIntensity = 1;
  private subtitleListeners = new Set<SubtitleListener>();
  private listenerPos = { x: 0, y: 1.6, z: 0 };

  get enabled(): boolean {
    return this.ctx !== null;
  }

  /**
   * Must be called from a user gesture. Headless environments have no audio device, so
   * the whole engine stays null and every method degrades to a no-op rather than throwing
   * on the first footstep.
   */
  async start(): Promise<boolean> {
    if (this.ctx) {
      if (this.ctx.state === 'suspended') await this.ctx.resume();
      return true;
    }
    const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return false;

    try {
      this.ctx = new Ctor();
    } catch {
      return false;
    }

    const ctx = this.ctx;
    this.limiter = ctx.createDynamicsCompressor();
    this.limiter.threshold.value = -8;
    this.limiter.knee.value = 6;
    this.limiter.ratio.value = 12;
    this.limiter.attack.value = 0.004;
    this.limiter.release.value = 0.18;
    this.limiter.connect(ctx.destination);

    this.master = ctx.createGain();
    this.master.gain.value = this.settings.master;
    this.master.connect(this.limiter);

    this.sfxBus = ctx.createGain();
    this.sfxBus.gain.value = this.settings.sfx;
    this.sfxBus.connect(this.master);

    this.musicBus = ctx.createGain();
    this.musicBus.gain.value = this.settings.music;
    this.musicBus.connect(this.master);

    this.ambienceBus = ctx.createGain();
    this.ambienceBus.gain.value = this.settings.ambience;
    this.ambienceBus.connect(this.master);

    // Three shared reverbs, addressed by sends. Thirty per-source convolvers would be the
    // obvious implementation and would also be thirty times the cost.
    const zones: [ZoneName, number, number, number][] = [
      ['small', 0.45, 2.6, 0.005],
      ['corridor', 1.15, 2.0, 0.018],
      ['hall', 2.6, 1.5, 0.035],
    ];
    for (const [name, seconds, decay, preDelay] of zones) {
      const convolver = ctx.createConvolver();
      convolver.buffer = makeImpulseResponse(ctx, seconds, decay, preDelay);
      const send = ctx.createGain();
      send.gain.value = name === 'corridor' ? 0.35 : 0;
      send.connect(convolver);
      convolver.connect(this.master);
      this.zoneSends[name] = send;
    }

    this.startDrone();
    this.startScheduler();
    if (ctx.state === 'suspended') await ctx.resume();
    return true;
  }

  stop(): void {
    if (this.schedulerTimer) clearInterval(this.schedulerTimer);
    this.schedulerTimer = null;
    for (const osc of this.droneOscillators) {
      try {
        osc.stop();
      } catch {
        // Already stopped; nothing to do.
      }
    }
    this.droneOscillators = [];
    this.ctx?.close();
    this.ctx = null;
  }

  onSubtitle(listener: SubtitleListener): () => void {
    this.subtitleListeners.add(listener);
    return () => this.subtitleListeners.delete(listener);
  }

  applySettings(settings: AudioSettings, scareIntensity: number): void {
    this.settings = settings;
    this.scareIntensity = clamp01(scareIntensity);
    if (!this.ctx) return;
    this.master.gain.value = settings.master;
    this.sfxBus.gain.value = settings.sfx;
    this.musicBus.gain.value = settings.music;
    this.ambienceBus.gain.value = settings.ambience;
  }

  setListener(x: number, y: number, z: number, yaw: number): void {
    this.listenerPos = { x, y, z };
    if (!this.ctx) return;
    const listener = this.ctx.listener;
    const forwardX = Math.cos(yaw);
    const forwardZ = Math.sin(yaw);
    if (listener.positionX) {
      const now = this.ctx.currentTime;
      listener.positionX.setValueAtTime(x, now);
      listener.positionY.setValueAtTime(y, now);
      listener.positionZ.setValueAtTime(z, now);
      listener.forwardX.setValueAtTime(forwardX, now);
      listener.forwardY.setValueAtTime(0, now);
      listener.forwardZ.setValueAtTime(forwardZ, now);
      listener.upX.setValueAtTime(0, now);
      listener.upY.setValueAtTime(1, now);
      listener.upZ.setValueAtTime(0, now);
    } else {
      listener.setPosition(x, y, z);
      listener.setOrientation(forwardX, 0, forwardZ, 0, 1, 0);
    }
  }

  setZone(zone: ZoneName): void {
    if (!this.ctx) return;
    const now = this.ctx.currentTime;
    for (const [name, send] of Object.entries(this.zoneSends) as [ZoneName, GainNode][]) {
      send.gain.setTargetAtTime(name === zone ? 0.4 : 0, now, 0.25);
    }
  }

  setDescent(descent: number): void {
    this.descent = clamp01(descent);
    if (!this.ctx) return;
    const now = this.ctx.currentTime;

    // Same five oscillators throughout: the detune widening from 3 to 40 cents and the
    // filter closing is the entire journey from pleasant to unbearable.
    this.droneOscillators.forEach((osc, i) => {
      const spread = lerp(3, 40, this.descent);
      const offset = (i - 2) * spread;
      // The partial stack goes from harmonic to inharmonic, which is what makes it *wrong*
      // rather than merely dark.
      const inharmonic = lerp(1, 1.0 + i * 0.037, this.descent);
      osc.detune.setTargetAtTime(offset, now, 1.5);
      osc.frequency.setTargetAtTime(55 * (1 + i * 0.5) * inharmonic, now, 1.5);
    });
    this.droneFilter.frequency.setTargetAtTime(lerp(900, 220, this.descent), now, 1.5);
    this.droneGain.gain.setTargetAtTime(lerp(0.035, 0.14, this.descent), now, 1.5);
  }

  setSanity(sanity01: number): void {
    this.sanity = clamp01(sanity01);
  }

  // ---------------------------------------------------------------------------
  // Drone and music
  // ---------------------------------------------------------------------------

  private startDrone(): void {
    const ctx = this.ctx!;
    this.droneFilter = ctx.createBiquadFilter();
    this.droneFilter.type = 'lowpass';
    this.droneFilter.frequency.value = 900;
    this.droneFilter.Q.value = 0.7;

    this.droneGain = ctx.createGain();
    this.droneGain.gain.value = 0.035;
    this.droneFilter.connect(this.droneGain);
    this.droneGain.connect(this.ambienceBus);
    this.droneGain.connect(this.zoneSends.hall);

    // A slow filter LFO keeps the bed from sitting perfectly still, which the ear reads as
    // synthetic within seconds.
    const lfo = ctx.createOscillator();
    lfo.frequency.value = 0.07;
    const lfoGain = ctx.createGain();
    lfoGain.gain.value = 140;
    lfo.connect(lfoGain);
    lfoGain.connect(this.droneFilter.frequency);
    lfo.start();

    for (let i = 0; i < 5; i++) {
      const osc = ctx.createOscillator();
      osc.type = 'sawtooth';
      osc.frequency.value = 55 * (1 + i * 0.5);
      osc.detune.value = (i - 2) * 3;
      const gain = ctx.createGain();
      gain.gain.value = 0.2 / (i + 1);
      osc.connect(gain);
      gain.connect(this.droneFilter);
      osc.start();
      this.droneOscillators.push(osc);
    }
  }

  /** 100 ms lookahead against `currentTime`; never schedule audio from requestAnimationFrame. */
  private startScheduler(): void {
    const ctx = this.ctx!;
    this.nextNoteTime = ctx.currentTime + 0.2;
    this.schedulerTimer = setInterval(() => {
      if (!this.ctx) return;
      const lookahead = this.ctx.currentTime + 0.1;
      while (this.nextNoteTime < lookahead) {
        this.scheduleMotifNote(this.nextNoteTime);
        const bpm = lerp(96, 46, this.descent);
        this.nextNoteTime += 60 / bpm / 2;
      }
    }, 25);
  }

  private scheduleMotifNote(time: number): void {
    const ctx = this.ctx!;
    // Voices thin out as the descent progresses: eventually only every fourth note sounds.
    const density = lerp(1, 0.28, this.descent);
    const step = this.motifIndex % MOTIF.length;
    this.motifIndex++;
    if (Math.random() > density) return;

    // Past the halfway point the motif starts playing backwards. Same notes, same
    // intervals, read in reverse — familiar and wrong at once.
    const reversed = this.descent > 0.5 && Math.random() < (this.descent - 0.5) * 2;
    const degree = MOTIF[reversed ? MOTIF.length - 1 - step : step];
    const semitone = SCALE_MAJOR[degree % SCALE_MAJOR.length] + Math.floor(degree / SCALE_MAJOR.length) * 12;
    const base = 523.25; // C5
    const detune = lerp(0, 55, this.descent) * (Math.random() * 2 - 1);
    const frequency = base * Math.pow(2, semitone / 12);

    const osc = ctx.createOscillator();
    osc.type = 'triangle';
    osc.frequency.value = frequency;
    osc.detune.value = detune;

    const gain = ctx.createGain();
    const peak = lerp(0.09, 0.05, this.descent);
    const decay = lerp(0.55, 2.4, this.descent);
    gain.gain.setValueAtTime(0.0001, time);
    gain.gain.exponentialRampToValueAtTime(peak, time + 0.012);
    gain.gain.exponentialRampToValueAtTime(0.0001, time + decay);

    osc.connect(gain);
    gain.connect(this.musicBus);
    // The reverb send opens up as things get worse, until the melody is more room than note.
    const send = ctx.createGain();
    send.gain.value = lerp(0.15, 0.85, this.descent);
    gain.connect(send);
    send.connect(this.zoneSends.hall);

    osc.start(time);
    osc.stop(time + decay + 0.05);
  }

  // ---------------------------------------------------------------------------
  // One-shots
  // ---------------------------------------------------------------------------

  private noiseBuffer: AudioBuffer | null = null;

  private getNoise(): AudioBuffer {
    if (this.noiseBuffer) return this.noiseBuffer;
    const ctx = this.ctx!;
    const length = Math.floor(ctx.sampleRate * 1.5);
    const buffer = ctx.createBuffer(1, length, ctx.sampleRate);
    const data = buffer.getChannelData(0);
    for (let i = 0; i < length; i++) data[i] = Math.random() * 2 - 1;
    this.noiseBuffer = buffer;
    return buffer;
  }

  private spatial(x: number, z: number): PannerNode | null {
    if (!this.ctx) return null;
    const panner = this.ctx.createPanner();
    panner.panningModel = 'HRTF';
    panner.distanceModel = 'inverse';
    panner.refDistance = 2.5;
    panner.rolloffFactor = 1.1;
    panner.maxDistance = 60;
    if (panner.positionX) {
      panner.positionX.value = x;
      panner.positionY.value = 1.2;
      panner.positionZ.value = z;
    } else {
      panner.setPosition(x, 1.2, z);
    }
    return panner;
  }

  /** Plays a cue and always emits its subtitle key, whether or not audio is running. */
  play(key: string, x = this.listenerPos.x, z = this.listenerPos.z, gain = 1): void {
    for (const listener of this.subtitleListeners) listener(key);
    if (!this.ctx) return;

    const ctx = this.ctx;
    const now = ctx.currentTime;
    const panner = this.spatial(x, z);
    const out = ctx.createGain();
    out.gain.value = gain;
    if (panner) {
      out.connect(panner);
      panner.connect(this.sfxBus);
      const send = ctx.createGain();
      send.gain.value = 0.3;
      panner.connect(send);
      send.connect(this.zoneSends.corridor);
    } else {
      out.connect(this.sfxBus);
    }

    switch (key) {
      case 'player.step':
      case 'player.stepCrouch':
        this.footstep(out, now, key === 'player.stepCrouch' ? 0.35 : 1);
        break;
      case 'entity.step':
        this.footstep(out, now, 1.5, 260, 3.2);
        break;
      case 'entity.run':
        this.footstep(out, now, 2.1, 180, 4);
        break;
      case 'entity.alerted':
        this.sting(out, now, 380, 90, 0.5, 0.7);
        break;
      case 'entity.telegraph':
        this.sting(out, now, 140, 40, 0.9, 1);
        break;
      case 'item.flashlightClick':
        this.click(out, now, 2400);
        break;
      case 'item.batteryDead':
        this.click(out, now, 700, 0.14);
        break;
      case 'item.pickup':
        this.click(out, now, 1800, 0.07);
        break;
      case 'item.drop':
        this.clunk(out, now, 120);
        break;
      case 'item.useConsumable':
        this.click(out, now, 520, 0.22);
        break;
      case 'item.glowstickCrack':
        // A dry snap, not a click: the crack is the moment the light arrives, and it wants
        // to be recognisable through a wall.
        this.click(out, now, 3100, 0.05);
        break;
      case 'objective.fusePickup':
        this.click(out, now, 1400, 0.1);
        break;
      case 'objective.fuseInsert':
        this.clunk(out, now, 180);
        break;
      case 'objective.exitOpen':
        this.chime(out, now);
        break;
      case 'objective.extract':
        this.chime(out, now, 1.5);
        break;
      case 'player.hurt':
        this.sting(out, now, 220, 60, 0.35, 0.8);
        break;
      case 'player.died':
        this.sting(out, now, 90, 32, 1.6, 0.9);
        break;
      case 'player.revived':
        this.chime(out, now, 0.8);
        break;
      case 'hallucination.whisper':
        this.whisper(out, now);
        break;
      case 'hallucination.step':
        this.footstep(out, now, 0.8, 420, 2.4);
        break;
      default:
        this.click(out, now, 1000, 0.05);
        break;
    }
  }

  /** Noise burst through a surface-dependent bandpass, plus a low thump for the body. */
  private footstep(out: GainNode, time: number, level: number, centre = 520, q = 1.2): void {
    const ctx = this.ctx!;
    const source = ctx.createBufferSource();
    source.buffer = this.getNoise();
    source.playbackRate.value = 1 + Math.random() * 0.2;

    const filter = ctx.createBiquadFilter();
    filter.type = 'bandpass';
    filter.frequency.value = centre * (0.9 + Math.random() * 0.2);
    filter.Q.value = q;

    const envelope = ctx.createGain();
    envelope.gain.setValueAtTime(0.0001, time);
    envelope.gain.exponentialRampToValueAtTime(0.35 * level, time + 0.004);
    envelope.gain.exponentialRampToValueAtTime(0.0001, time + 0.13);

    source.connect(filter);
    filter.connect(envelope);
    envelope.connect(out);
    source.start(time, Math.random());
    source.stop(time + 0.2);

    const thump = ctx.createOscillator();
    thump.type = 'sine';
    thump.frequency.setValueAtTime(72, time);
    thump.frequency.exponentialRampToValueAtTime(44, time + 0.09);
    const thumpGain = ctx.createGain();
    thumpGain.gain.setValueAtTime(0.0001, time);
    thumpGain.gain.exponentialRampToValueAtTime(0.16 * level, time + 0.006);
    thumpGain.gain.exponentialRampToValueAtTime(0.0001, time + 0.1);
    thump.connect(thumpGain);
    thumpGain.connect(out);
    thump.start(time);
    thump.stop(time + 0.15);
  }

  /**
   * FM pair plus noise through a hard clipper, with a sub drop underneath. Scaled by the
   * accessibility scare setting — at 0 it is effectively silent, and that is the whole
   * implementation of "turn the jump scares down".
   */
  private sting(out: GainNode, time: number, startHz: number, endHz: number, length: number, level: number): void {
    const ctx = this.ctx!;
    const amount = level * this.scareIntensity;
    if (amount < 0.02) return;

    const carrier = ctx.createOscillator();
    carrier.type = 'sawtooth';
    carrier.frequency.setValueAtTime(startHz, time);
    carrier.frequency.exponentialRampToValueAtTime(Math.max(20, endHz), time + length);

    const modulator = ctx.createOscillator();
    modulator.frequency.value = startHz * 1.61;
    const modGain = ctx.createGain();
    modGain.gain.value = startHz * 0.8;
    modulator.connect(modGain);
    modGain.connect(carrier.frequency);

    const shaper = ctx.createWaveShaper();
    const curve = new Float32Array(256);
    for (let i = 0; i < 256; i++) {
      const t = (i / 255) * 2 - 1;
      curve[i] = Math.tanh(t * 3.4);
    }
    shaper.curve = curve;

    const envelope = ctx.createGain();
    envelope.gain.setValueAtTime(0.0001, time);
    envelope.gain.exponentialRampToValueAtTime(0.4 * amount, time + 0.02);
    envelope.gain.exponentialRampToValueAtTime(0.0001, time + length);

    carrier.connect(shaper);
    shaper.connect(envelope);
    envelope.connect(out);
    const send = ctx.createGain();
    send.gain.value = 0.5;
    envelope.connect(send);
    send.connect(this.zoneSends.hall);

    carrier.start(time);
    modulator.start(time);
    carrier.stop(time + length + 0.1);
    modulator.stop(time + length + 0.1);
  }

  private click(out: GainNode, time: number, frequency: number, length = 0.03): void {
    const ctx = this.ctx!;
    const osc = ctx.createOscillator();
    osc.type = 'square';
    osc.frequency.value = frequency;
    const envelope = ctx.createGain();
    envelope.gain.setValueAtTime(0.0001, time);
    envelope.gain.exponentialRampToValueAtTime(0.12, time + 0.002);
    envelope.gain.exponentialRampToValueAtTime(0.0001, time + length);
    osc.connect(envelope);
    envelope.connect(out);
    osc.start(time);
    osc.stop(time + length + 0.02);
  }

  private clunk(out: GainNode, time: number, frequency: number): void {
    const ctx = this.ctx!;
    const osc = ctx.createOscillator();
    osc.type = 'square';
    osc.frequency.setValueAtTime(frequency, time);
    osc.frequency.exponentialRampToValueAtTime(frequency * 0.5, time + 0.12);
    const envelope = ctx.createGain();
    envelope.gain.setValueAtTime(0.0001, time);
    envelope.gain.exponentialRampToValueAtTime(0.22, time + 0.005);
    envelope.gain.exponentialRampToValueAtTime(0.0001, time + 0.22);
    osc.connect(envelope);
    envelope.connect(out);
    osc.start(time);
    osc.stop(time + 0.3);
  }

  private chime(out: GainNode, time: number, length = 1.1): void {
    const ctx = this.ctx!;
    for (const [i, ratio] of [1, 1.5, 2.02].entries()) {
      const osc = ctx.createOscillator();
      osc.type = 'sine';
      osc.frequency.value = 440 * ratio;
      const envelope = ctx.createGain();
      envelope.gain.setValueAtTime(0.0001, time + i * 0.05);
      envelope.gain.exponentialRampToValueAtTime(0.1 / (i + 1), time + i * 0.05 + 0.02);
      envelope.gain.exponentialRampToValueAtTime(0.0001, time + length);
      osc.connect(envelope);
      envelope.connect(out);
      osc.start(time + i * 0.05);
      osc.stop(time + length + 0.1);
    }
  }

  /**
   * Granular noise through three slowly-drifting formant filters. It reads as a human
   * voice without any recorded material, and it is what the hallucinations are built from.
   */
  private whisper(out: GainNode, time: number): void {
    const ctx = this.ctx!;
    const length = 1.2 + Math.random() * 0.8;
    const source = ctx.createBufferSource();
    source.buffer = this.getNoise();
    source.loop = true;
    source.playbackRate.value = 0.6 + Math.random() * 0.3;

    let node: AudioNode = source;
    for (const [centre, q, gain] of [
      [700, 9, 12],
      [1180, 11, 9],
      [2500, 13, 7],
    ]) {
      const formant = ctx.createBiquadFilter();
      formant.type = 'peaking';
      formant.frequency.value = centre * (0.9 + Math.random() * 0.2);
      formant.Q.value = q;
      formant.gain.value = gain;
      // A slow random walk stops it sitting on one vowel.
      formant.frequency.setTargetAtTime(centre * (0.8 + Math.random() * 0.4), time + 0.4, 0.6);
      node.connect(formant);
      node = formant;
    }

    const bandpass = ctx.createBiquadFilter();
    bandpass.type = 'bandpass';
    bandpass.frequency.value = 1400;
    bandpass.Q.value = 0.8;
    node.connect(bandpass);

    const envelope = ctx.createGain();
    envelope.gain.setValueAtTime(0.0001, time);
    envelope.gain.exponentialRampToValueAtTime(0.055, time + 0.35);
    envelope.gain.exponentialRampToValueAtTime(0.0001, time + length);
    bandpass.connect(envelope);
    envelope.connect(out);
    const send = ctx.createGain();
    send.gain.value = 0.7;
    envelope.connect(send);
    send.connect(this.zoneSends.hall);

    source.start(time, Math.random());
    source.stop(time + length + 0.1);
  }

  /** Local player footsteps, driven by distance travelled rather than a timer. */
  private stepAccumulator = 0;

  updateFootsteps(speed: number, crouching: boolean, dt: number): void {
    if (speed < 0.4) {
      this.stepAccumulator = 0;
      return;
    }
    this.stepAccumulator += speed * dt;
    const stride = crouching ? 1.5 : speed > 4 ? 1.35 : 1.75;
    if (this.stepAccumulator >= stride) {
      this.stepAccumulator = 0;
      this.play(crouching ? 'player.stepCrouch' : 'player.step');
    }
  }

  /** Ambient unease driven by sanity: the lower it gets, the more you hear that is not there. */
  updateHallucinations(dt: number, enabled: boolean, hallucinate: (key: string) => void): void {
    if (!enabled) return;
    const pressure = clamp01(1 - this.sanity);
    if (pressure < 0.25) return;
    const chancePerSecond = clamp((pressure - 0.25) * 0.55, 0, 0.5);
    if (Math.random() < chancePerSecond * dt) {
      hallucinate(Math.random() < 0.6 ? 'hallucination.whisper' : 'hallucination.step');
    }
  }
}
