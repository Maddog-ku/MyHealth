import { useEffect, useRef, useState } from "react";
import * as THREE from "three";
import { cn } from "@/lib/utils";

type AssistantAvatarMood = "idle" | "active" | "thinking";
type AssistantAvatarPersona = "male" | "female";

interface AssistantAvatar3DProps {
  avatarSrc: string;
  mood?: AssistantAvatarMood;
  persona?: AssistantAvatarPersona;
  className?: string;
}

const PALETTES = {
  male: {
    accent: 0x14b8a6,
    secondary: 0x6366f1,
  },
  female: {
    accent: 0x10b981,
    secondary: 0x8b5cf6,
  },
} as const;

interface AvatarCrop {
  key: string;
  x: number;
  y: number;
  w: number;
  h: number;
  anchorX?: number;
  anchorY?: number;
  z: number;
  order: number;
}

interface AvatarPart {
  key: string;
  group: THREE.Group;
  material: THREE.MeshBasicMaterial;
  texture: THREE.Texture;
  baseX: number;
  baseY: number;
  baseZ: number;
}

const CROP_SETS: Record<AssistantAvatarPersona, AvatarCrop[]> = {
  female: [
    { key: "platform", x: 0, y: 382, w: 366, h: 130, z: -0.18, order: 1 },
    { key: "leftHair", x: 0, y: 0, w: 150, h: 320, anchorX: 98, anchorY: 160, z: -0.05, order: 2 },
    { key: "legs", x: 24, y: 310, w: 318, h: 130, anchorX: 190, anchorY: 360, z: 0.02, order: 3 },
    { key: "body", x: 92, y: 214, w: 170, h: 145, anchorX: 180, anchorY: 238, z: 0.08, order: 4 },
    { key: "bookArm", x: 58, y: 245, w: 118, h: 120, anchorX: 124, anchorY: 282, z: 0.14, order: 5 },
    { key: "rightArm", x: 244, y: 160, w: 106, h: 172, anchorX: 264, anchorY: 230, z: 0.18, order: 6 },
    { key: "head", x: 54, y: 0, w: 258, h: 242, anchorX: 184, anchorY: 202, z: 0.22, order: 7 },
  ],
  male: [
    { key: "platform", x: 0, y: 365, w: 365, h: 147, z: -0.18, order: 1 },
    { key: "legs", x: 38, y: 310, w: 295, h: 135, anchorX: 184, anchorY: 355, z: 0.02, order: 2 },
    { key: "body", x: 92, y: 180, w: 178, h: 185, anchorX: 184, anchorY: 218, z: 0.08, order: 3 },
    { key: "bookArm", x: 50, y: 222, w: 120, h: 130, anchorX: 118, anchorY: 260, z: 0.14, order: 4 },
    { key: "rightArm", x: 246, y: 150, w: 110, h: 175, anchorX: 266, anchorY: 215, z: 0.18, order: 5 },
    { key: "head", x: 48, y: 12, w: 250, h: 210, anchorX: 184, anchorY: 188, z: 0.22, order: 6 },
  ],
};

function makeGlowTexture() {
  const canvas = document.createElement("canvas");
  canvas.width = 256;
  canvas.height = 256;
  const ctx = canvas.getContext("2d");
  if (!ctx) return null;
  const gradient = ctx.createRadialGradient(128, 128, 14, 128, 128, 126);
  gradient.addColorStop(0, "rgba(94, 234, 212, 0.58)");
  gradient.addColorStop(0.42, "rgba(16, 185, 129, 0.24)");
  gradient.addColorStop(1, "rgba(99, 102, 241, 0)");
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

function pixelToScene(x: number, y: number, imageWidth: number, imageHeight: number, avatarHeight: number) {
  return {
    x: ((x - imageWidth / 2) / imageHeight) * avatarHeight,
    y: ((imageHeight / 2 - y) / imageHeight) * avatarHeight,
  };
}

export function AssistantAvatar3D({
  avatarSrc,
  mood = "idle",
  persona = "male",
  className,
}: AssistantAvatar3DProps) {
  const mountRef = useRef<HTMLDivElement>(null);
  const moodRef = useRef(mood);
  const [fallback, setFallback] = useState(false);

  useEffect(() => {
    moodRef.current = mood;
  }, [mood]);

  useEffect(() => {
    const mount = mountRef.current;
    if (!mount) return;

    let frame = 0;
    let disposed = false;
    let renderer: THREE.WebGLRenderer;

    try {
      renderer = new THREE.WebGLRenderer({
        alpha: true,
        antialias: true,
        // Decorative avatar — prefer the integrated GPU to cut battery/thermal load.
        powerPreference: "low-power",
        preserveDrawingBuffer: true,
      });
    } catch {
      setFallback(true);
      return;
    }

    renderer.setClearColor(0x000000, 0);
    // Cap pixel ratio (1.5 instead of 2) to cut fragment work on hi-DPI screens.
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
    renderer.domElement.style.display = "block";
    renderer.domElement.style.width = "100%";
    renderer.domElement.style.height = "100%";
    mount.appendChild(renderer.domElement);

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(30, 1, 0.1, 100);
    camera.position.set(0, 0, 6);

    const palette = PALETTES[persona];
    const root = new THREE.Group();
    scene.add(root);

    const avatarRig = new THREE.Group();
    avatarRig.position.z = 0.28;
    root.add(avatarRig);

    const glowTexture = makeGlowTexture();
    const glowMat = new THREE.SpriteMaterial({
      map: glowTexture ?? undefined,
      color: 0xffffff,
      transparent: true,
      opacity: 0.9,
      depthWrite: false,
    });
    const glow = new THREE.Sprite(glowMat);
    glow.position.set(0, -0.04, -0.42);
    glow.scale.set(2.8, 2.8, 1);
    root.add(glow);

    const shadowMat = new THREE.MeshBasicMaterial({
      color: 0x0f172a,
      transparent: true,
      opacity: 0.18,
      depthWrite: false,
    });
    const shadow = new THREE.Mesh(new THREE.CircleGeometry(0.72, 48), shadowMat);
    shadow.position.set(0, -1.08, -0.05);
    shadow.rotation.x = -Math.PI / 2.8;
    shadow.scale.set(1.4, 0.42, 1);
    root.add(shadow);

    const loader = new THREE.TextureLoader();
    const avatarParts: AvatarPart[] = [];
    const textureCopies: THREE.Texture[] = [];

    const buildRig = (loaded: THREE.Texture) => {
      const image = loaded.image as HTMLImageElement | undefined;
      const imageWidth = image?.naturalWidth || 365;
      const imageHeight = image?.naturalHeight || 512;
      const avatarHeight = 2.48;

      CROP_SETS[persona].forEach((crop) => {
        const cropTexture = loaded.clone();
        cropTexture.colorSpace = THREE.SRGBColorSpace;
        cropTexture.wrapS = THREE.ClampToEdgeWrapping;
        cropTexture.wrapT = THREE.ClampToEdgeWrapping;
        cropTexture.repeat.set(crop.w / imageWidth, crop.h / imageHeight);
        cropTexture.offset.set(crop.x / imageWidth, 1 - (crop.y + crop.h) / imageHeight);
        cropTexture.needsUpdate = true;
        textureCopies.push(cropTexture);

        const width = (crop.w / imageHeight) * avatarHeight;
        const height = (crop.h / imageHeight) * avatarHeight;
        const material = new THREE.MeshBasicMaterial({
          map: cropTexture,
          transparent: true,
          depthWrite: false,
          alphaTest: 0.03,
          side: THREE.DoubleSide,
        });
        const plane = new THREE.Mesh(new THREE.PlaneGeometry(width, height), material);
        plane.renderOrder = crop.order;

        const anchor = pixelToScene(
          crop.anchorX ?? crop.x + crop.w / 2,
          crop.anchorY ?? crop.y + crop.h / 2,
          imageWidth,
          imageHeight,
          avatarHeight,
        );
        const center = pixelToScene(crop.x + crop.w / 2, crop.y + crop.h / 2, imageWidth, imageHeight, avatarHeight);
        const group = new THREE.Group();
        group.position.set(anchor.x, anchor.y - 0.03, crop.z);
        plane.position.set(center.x - anchor.x, center.y - anchor.y, 0);
        group.add(plane);
        avatarRig.add(group);
        avatarParts.push({
          key: crop.key,
          group,
          material,
          texture: cropTexture,
          baseX: group.position.x,
          baseY: group.position.y,
          baseZ: group.position.z,
        });
      });
    };

    const texture = loader.load(
      avatarSrc,
      (loaded) => {
        if (disposed) return;
        loaded.colorSpace = THREE.SRGBColorSpace;
        loaded.anisotropy = Math.min(renderer.capabilities.getMaxAnisotropy(), 8);
        buildRig(loaded);
      },
      undefined,
      () => {
        if (!disposed) setFallback(true);
      },
    );
    texture.colorSpace = THREE.SRGBColorSpace;

    const ringMat = new THREE.MeshBasicMaterial({
      color: palette.accent,
      transparent: true,
      opacity: 0.6,
      depthWrite: false,
    });
    const ringA = new THREE.Mesh(new THREE.TorusGeometry(1.12, 0.014, 10, 100), ringMat);
    ringA.position.y = -0.08;
    ringA.position.z = -0.2;
    ringA.rotation.x = Math.PI / 2.35;
    root.add(ringA);

    const ringB = new THREE.Mesh(new THREE.TorusGeometry(0.88, 0.01, 10, 100), ringMat.clone());
    ringB.position.y = -0.12;
    ringB.position.z = -0.28;
    ringB.rotation.x = Math.PI / 2.18;
    ringB.rotation.z = Math.PI / 2;
    root.add(ringB);

    const sparkleMat = new THREE.MeshBasicMaterial({
      color: palette.secondary,
      transparent: true,
      opacity: 0.72,
      depthWrite: false,
    });
    const sparkles: THREE.Mesh[] = [];
    for (let i = 0; i < 18; i += 1) {
      const sparkle = new THREE.Mesh(new THREE.SphereGeometry(0.024 + (i % 3) * 0.006, 10, 8), sparkleMat.clone());
      sparkle.userData.phase = (i / 18) * Math.PI * 2;
      sparkle.userData.radius = 1 + (i % 4) * 0.08;
      sparkle.userData.lift = -0.04 + (i % 5) * 0.04;
      sparkles.push(sparkle);
      root.add(sparkle);
    }

    const resize = () => {
      const rect = mount.getBoundingClientRect();
      const width = Math.max(1, Math.floor(rect.width));
      const height = Math.max(1, Math.floor(rect.height));
      renderer.setSize(width, height, false);
      camera.aspect = width / height;
      camera.updateProjectionMatrix();
    };
    const observer = new ResizeObserver(resize);
    observer.observe(mount);
    resize();

    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const clock = new THREE.Clock();

    // Cap the render rate (~30 FPS) to roughly halve the GPU/CPU cost of the always-on avatar.
    const minInterval = 1 / 30;
    let last = -minInterval;

    const animate = () => {
      if (disposed) return;
      frame = window.requestAnimationFrame(animate);
      const t = clock.getElapsedTime();
      if (t - last < minInterval) return;
      last = t;
      const currentMood = moodRef.current;
      const energy = currentMood === "thinking" ? 1.75 : currentMood === "active" ? 1.2 : 0.82;
      const still = reduced ? 0 : 1;

      root.position.y = Math.sin(t * 1.28) * 0.08 * still;
      avatarRig.rotation.y = Math.sin(t * 0.66) * 0.08 * still;
      avatarRig.rotation.z = Math.sin(t * 0.62) * 0.018 * still;
      avatarRig.scale.setScalar(1 + Math.sin(t * 1.7) * 0.012 * still);

      avatarParts.forEach((part) => {
        part.group.position.set(part.baseX, part.baseY, part.baseZ);
        part.group.rotation.set(0, 0, 0);
        part.group.scale.setScalar(1);

        if (part.key === "head") {
          part.group.position.y = part.baseY + Math.sin(t * 1.32) * 0.026 * still;
          part.group.rotation.y = Math.sin(t * 0.95) * 0.16 * still;
          part.group.rotation.z = Math.sin(t * 1.12) * 0.025 * still;
          part.group.scale.setScalar(1 + Math.sin(t * 1.7) * 0.012 * still);
        }
        if (part.key === "rightArm") {
          part.group.rotation.z =
            currentMood === "thinking"
              ? Math.sin(t * 5.2) * 0.075 * still
              : currentMood === "active"
                ? Math.sin(t * 2.4) * 0.09 * still
                : Math.sin(t * 1.2) * 0.026 * still;
          part.group.position.y = part.baseY + Math.sin(t * 1.8) * 0.012 * still;
        }
        if (part.key === "bookArm") {
          part.group.rotation.z = Math.sin(t * 1.5 + 0.8) * -0.034 * still;
          part.group.position.y = part.baseY + Math.sin(t * 1.4 + 1.2) * 0.012 * still;
        }
        if (part.key === "body") {
          part.group.rotation.z = Math.sin(t * 0.92) * 0.012 * still;
          part.group.scale.set(1 + Math.sin(t * 1.25) * 0.008 * still, 1 + Math.sin(t * 1.25) * 0.014 * still, 1);
        }
        if (part.key === "leftHair") {
          part.group.rotation.z = Math.sin(t * 1.05 + 0.5) * 0.025 * still;
          part.group.position.x = part.baseX + Math.sin(t * 0.9) * 0.01 * still;
        }
        if (part.key === "legs") {
          part.group.position.y = part.baseY + Math.sin(t * 1.1 + 1.6) * 0.008 * still;
        }
        if (part.key === "platform") {
          part.group.rotation.z = Math.sin(t * 0.8) * 0.006 * still;
        }
      });

      ringA.rotation.z += 0.012 * energy * still;
      ringB.rotation.z -= 0.018 * energy * still;
      ringA.scale.setScalar(1 + Math.sin(t * 2.2) * 0.028 * still);
      ringB.scale.setScalar(1 + Math.cos(t * 2.4) * 0.024 * still);
      ringMat.opacity = currentMood === "thinking" ? 0.72 + Math.sin(t * 8) * 0.14 : 0.58;
      glowMat.opacity = currentMood === "thinking" ? 0.98 : 0.78 + Math.sin(t * 2.1) * 0.08 * still;
      glow.scale.setScalar(currentMood === "thinking" ? 2.98 + Math.sin(t * 7.5) * 0.12 : 2.78);
      shadow.scale.set(1.4 + Math.sin(t * 1.28) * 0.05 * still, 0.42, 1);

      sparkles.forEach((sparkle, index) => {
        const phase = sparkle.userData.phase as number;
        const radius = sparkle.userData.radius as number;
        const lift = sparkle.userData.lift as number;
        const speed = 0.35 + (index % 5) * 0.04;
        sparkle.position.set(
          Math.cos(t * speed * energy + phase) * radius,
          lift + Math.sin(t * (0.78 + index * 0.012) + phase) * 0.46,
          -0.1 + Math.sin(t * speed * energy + phase) * 0.28,
        );
        sparkle.scale.setScalar(0.75 + Math.sin(t * 2.8 + phase) * 0.22);
        const mat = sparkle.material as THREE.MeshBasicMaterial;
        mat.opacity = currentMood === "thinking" ? 0.85 : 0.56 + Math.sin(t * 1.8 + phase) * 0.14;
      });

      renderer.render(scene, camera);
    };

    // Always paint one frame. When the user prefers reduced motion the pose is static, so we
    // skip the rAF loop entirely; otherwise run the throttled loop while the tab is visible.
    renderer.render(scene, camera);
    if (!reduced) frame = window.requestAnimationFrame(animate);

    // Pause the loop while the tab is hidden; resume on return. Saves work in background tabs.
    const onVisibility = () => {
      if (document.hidden) {
        if (frame) {
          window.cancelAnimationFrame(frame);
          frame = 0;
        }
      } else if (!disposed && !reduced && !frame) {
        last = -minInterval;
        frame = window.requestAnimationFrame(animate);
      }
    };
    document.addEventListener("visibilitychange", onVisibility);

    return () => {
      disposed = true;
      window.cancelAnimationFrame(frame);
      document.removeEventListener("visibilitychange", onVisibility);
      observer.disconnect();
      scene.traverse((object) => {
        if (object instanceof THREE.Mesh) {
          object.geometry.dispose();
          const material = object.material;
          if (Array.isArray(material)) material.forEach((m) => m.dispose());
          else material.dispose();
        }
      });
      glowTexture?.dispose();
      texture.dispose();
      textureCopies.forEach((copy) => copy.dispose());
      renderer.dispose();
      renderer.domElement.remove();
    };
  }, [avatarSrc, persona]);

  return (
    <div
      ref={mountRef}
      data-testid="assistant-avatar-3d"
      data-rig="layered-2.5d"
      className={cn("relative h-full w-full overflow-hidden", className)}
      aria-hidden="true"
    >
      {fallback && (
        <div className="absolute inset-0 grid place-items-center bg-gradient-to-br from-emerald-100 via-white to-indigo-100 dark:from-emerald-950 dark:via-slate-950 dark:to-indigo-950">
          <img src={avatarSrc} alt="" className="h-full w-full object-contain p-1" />
        </div>
      )}
    </div>
  );
}
